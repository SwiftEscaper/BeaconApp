package koren.swiftescaper.ui.view.main

import android.Manifest
import android.content.ContentValues.TAG
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.UserManager
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.minew.beaconset.BluetoothState
import com.minew.beaconset.MinewBeacon
import com.minew.beaconset.MinewBeaconManager
import com.minew.beaconset.MinewBeaconManagerListener
import koren.swiftescaper.domain.viewmodel.MainViewModel
import koren.swiftescaper.ui.theme.BlueGray
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import kotlin.math.pow
import kotlin.math.sqrt

class MainActivity : ComponentActivity(), SensorEventListener {

    private val minewBeaconManager: MinewBeaconManager = MinewBeaconManager.getInstance(this)
    private val REQUEST_CODE_PERMISSIONS = 1001
    private lateinit var mainViewModel: MainViewModel

    private var fcmToken: String? = null // FCM 토큰을 저장할 변수

    private var webSocket: WebSocket? = null
    private var handler: Handler? = null
    private var runnable: Runnable? = null
    private val period = 200L // 웹소켓으로 전송하는 간격

    private lateinit var sensorManager: SensorManager
    private lateinit var senAccelerometer: Sensor
    private var lastUpdate = 0L
    private val COLLISION_THRESHOLD = 5000
    private var lastX = 0f
    private var lastY = 0f
    private var lastZ = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initSensor()

        initializeFirebase() // Firebase 초기화

        checkPermissions()  // 권한 체크
        minewBeaconManager.startService()   // 서비스 시작

        mainViewModel = ViewModelProvider(this).get(MainViewModel::class.java)

        // UI 설정 및 x, y 값이 정해지면 WebSocket을 통해 송신
        setContent {
            MainScreen(mainViewModel = mainViewModel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        webSocket!!.close(1000, null)  // 액티비티가 파괴될 때 WebSocket 연결 종료
    }

    private fun initSensor() {
        sensorManager = this.getSystemService(SENSOR_SERVICE) as SensorManager
        senAccelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)!!
        sensorManager.registerListener(this, senAccelerometer, SensorManager.SENSOR_DELAY_NORMAL)
    }

    private fun connectWebSocket() {
        // WebSocket 초기화 및 연결 설정
        val client = OkHttpClient()
        val request = Request.Builder().url("ws://210.102.180.145:80/location/send")
            .build() //BackEnd VM - SPRING
        webSocket = client.newWebSocket(request, object : WebSocketListener() {

            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                Log.d(TAG, "WebSocket Opened")
                this@MainActivity.webSocket = webSocket;
                startSendingMessages(mainViewModel, "광암터널", fcmToken!!)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "Received: $text")
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                Log.d(TAG, "Received: ${bytes.hex()}")
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                Log.d(TAG, "WebSocket Closing: $reason")
            }

            override fun onFailure(
                webSocket: WebSocket,
                t: Throwable,
                response: okhttp3.Response?
            ) {
                t.printStackTrace()
                Log.e(TAG, "WebSocket Failure", t)
            }
        })
    }

    private fun initializeFirebase() {
        FirebaseApp.initializeApp(this) // Firebase 앱 초기화
        FirebaseMessaging.getInstance().token.addOnCompleteListener(OnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w(TAG, "FCM Failure", task.exception)
                return@OnCompleteListener
            }
            fcmToken = task.result
            Log.d(TAG, "FCM Token: $fcmToken")

            connectWebSocket()  // WebSocket 연결
        })
    }

    private fun initBeaconListener() {
        minewBeaconManager.setMinewbeaconManagerListener(object : MinewBeaconManagerListener {
            override fun onUpdateBluetoothState(bluetoothState: BluetoothState?) {
                bluetoothState?.let {
                    when (it) {
                        BluetoothState.BluetoothStatePowerOn -> Log.d(TAG, "블루투스가 켜졌습니다")
                        BluetoothState.BluetoothStatePowerOff -> Log.d(TAG, "블루투스가 꺼졌습니다")
                        BluetoothState.BluetoothStateNotSupported -> Log.d(TAG, "블루투스가 지원되지 않습니다")
                    }
                }
            }

            override fun onRangeBeacons(beacons: MutableList<MinewBeacon>?) {
                beacons?.let {
                    if (it.size > 0) {
                        Log.d("beacons", beacons.size.toString())
                        mainViewModel.setBeacons(beacons)  // 새로운 비콘 데이터를 ViewModel에 설정
                    } else {
                        Log.i(TAG, "범위 내에 비콘이 없습니다")
                    }
                }
            }

            override fun onAppearBeacons(beacons: MutableList<MinewBeacon>?) {
                beacons?.let {
                    if (it.isNotEmpty()) {
                        for (beacon in it) {
                            Log.i(TAG, "새로운 비콘 감지됨: ${beacon.toString()}")
                            // 비콘이 나타났을 때 추가로 처리할 로직
                        }
                    }
                }
            }

            override fun onDisappearBeacons(beacons: MutableList<MinewBeacon>?) {
                // 비콘이 사라졌을 때 처리할 로직 (필요시 구현)
            }
        })
        minewBeaconManager.startScan() // 비콘 스캔 시작
    }

    private fun checkPermissions() {
        val permissions = arrayOf(
            Manifest.permission.BLUETOOTH_CONNECT,      // API 30 이상에서 블루투스 연결 권한
            Manifest.permission.BLUETOOTH_SCAN,     // API 30 이상에서 블루투스 스캔 권한
            Manifest.permission.BLUETOOTH,          // 블루투스 권한
            Manifest.permission.BLUETOOTH_ADMIN,    // 블루투스 관리 권한
            Manifest.permission.ACCESS_FINE_LOCATION,   // 정확한 위치 권한
            Manifest.permission.ACCESS_COARSE_LOCATION  // 대략적인 위치 권한
        )
        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toTypedArray(),
                REQUEST_CODE_PERMISSIONS
            )
        } else {
            initBeaconListener() // 권한이 모두 허용되었을 때 비콘 리스너 초기화
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (grantResults.isEmpty() || grantResults.any { it != PackageManager.PERMISSION_GRANTED }) {
                Toast.makeText(this, "비콘 스캔을 위해 권한이 필요합니다", Toast.LENGTH_SHORT).show()
            } else {
                initBeaconListener() // 권한이 모두 허용되었을 때 비콘 리스너 초기화
            }
        }
    }

    private fun startSendingMessages(
        mainViewModel: MainViewModel,
        tunnelId: String,
        fcmToken: String
    ) {
        handler = Handler(Looper.getMainLooper())
        runnable = object : Runnable {
            override fun run() {
                if (webSocket != null) {
                    val pos = mainViewModel.pos.value
                    val locationJson =
                        "{\"position\": $pos, \"tunnelId\": \"$tunnelId\", \"fcmToken\": \"$fcmToken\"}"
                    webSocket!!.send(locationJson)
                    Log.d("WebSocket", "Position: $pos")
                }
                handler!!.postDelayed(
                    this,
                    period
                ) // Re-run this runnable after the specified period
            }
        }
        handler!!.post(runnable!!) // Start sending messages
    }

    override fun onSensorChanged(event: SensorEvent?) {
        val sensor = event!!.sensor
        if (sensor.type == Sensor.TYPE_LINEAR_ACCELERATION) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            Log.d(TAG, "Collision detected : " + x.toString() +" "+y.toString()+" "+z.toString())

            val curTime = System.currentTimeMillis()
            if (curTime - lastUpdate > 100) {
                val diffTime = (curTime - lastUpdate)
                lastUpdate = curTime

                val collisionDetect = sqrt(
                    (z - lastZ).toDouble().pow(2.0) * 100 +
                            (x - lastX).toDouble().pow(2.0) * 10 +
                            (y - lastY).toDouble().pow(2.0) * 10
                ) / diffTime * 10000

                if (collisionDetect > COLLISION_THRESHOLD) {
                    Toast.makeText(this, "충돌!!", Toast.LENGTH_SHORT).show()
                    //webSocket?.send("{\"collision\": true, \"timestamp\": $curTime}")
                    //HPC 전송할 예정... collision 값, timestamp, 터널명
                    Log.d(TAG, "Collision detected and reported.")
                }

                lastX = x
                lastY = y
                lastZ = z
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
    }
}


@Composable
fun MainScreen(mainViewModel: MainViewModel) {
    val pos = mainViewModel.pos.collectAsState()
    Surface(modifier = Modifier
        .fillMaxSize()
        .background(Color.White)) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .background(Color.White),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text ="Beacons",
                fontSize = 20.sp,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.W600)
            Spacer(modifier = Modifier.height(20.dp))
            BeaconList(modifier = Modifier, viewModel = mainViewModel)
            Spacer(modifier = Modifier.height(10.dp))
            Text(text = "Estimated Coordination : (${pos.value.toFloat()}m)",
                color = BlueGray)
            Spacer(modifier = Modifier.height(10.dp))
            GridScreen(viewModel = mainViewModel)
            
        }
    }
}