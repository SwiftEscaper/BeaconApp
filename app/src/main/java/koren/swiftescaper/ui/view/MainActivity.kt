package koren.swiftescaper.ui.view

import android.Manifest
import android.content.ContentValues
import android.content.ContentValues.TAG
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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
import koren.swiftescaper.domain.dto.Beacon
import koren.swiftescaper.domain.viewmodel.MainViewModel
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

class MainActivity : ComponentActivity() {

    private val minewBeaconManager: MinewBeaconManager = MinewBeaconManager.getInstance(this)
    private val REQUEST_CODE_PERMISSIONS = 1001
    private lateinit var mainViewModel: MainViewModel

    private var fcmToken: String? = null // FCM 토큰을 저장할 변수


    private var webSocket: WebSocket? = null
    private var handler: Handler? = null
    private var runnable: Runnable? = null
    private val period = 200L // 웹소켓으로 전송하는 간격

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

    private fun connectWebSocket() {
        // WebSocket 초기화 및 연결 설정
        val client = OkHttpClient()
        val request = Request.Builder().url("ws://192.168.0.10:8080/location/send").build() //BackEnd VM - SPRING
        webSocket = client.newWebSocket(request, object : WebSocketListener() {

            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                Log.d(TAG, "WebSocket Opened")
                this@MainActivity.webSocket = webSocket;
                startSendingMessages(mainViewModel,1,fcmToken!!)
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

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
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
                    if (it.isNotEmpty()) {
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
            ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), REQUEST_CODE_PERMISSIONS)
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

    private fun startSendingMessages(mainViewModel: MainViewModel, tunnelId : Long, fcmToken : String) {
        handler = Handler(Looper.getMainLooper())
        runnable = object : Runnable {
            override fun run() {
                if (webSocket != null) {
                    val lat = mainViewModel.lat.value
                    val lng = mainViewModel.lng.value
                    val locationJson = "{\"lat\": $lat, \"lng\": $lng, \"tunnelId\": $tunnelId, \"fcmToken\": \"$fcmToken\"}"
                    webSocket!!.send(locationJson)
                    Log.d("WebSocket", "Latitude: $lat, Longitude: $lng")
                }
                handler!!.postDelayed(this, period) // Re-run this runnable after the specified period
            }
        }
        handler!!.post(runnable!!) // Start sending messages
    }
}


@Composable
fun MainScreen(mainViewModel: MainViewModel) {


    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            LocationCanvas(mainViewModel)
        }
    }
}

@Composable
fun LocationCanvas(viewModel: MainViewModel) {
    val beacons by viewModel.beacons.collectAsState()
    val x by viewModel.lat.collectAsState()
    val y by viewModel.lng.collectAsState()

    Box(modifier = Modifier
        .fillMaxSize()
        .padding(30.dp)
        .background(Color.White),
        contentAlignment = Alignment.Center) {
        Text(text = x.toString() +" "+y.toString())
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            Log.d("size", canvasWidth.toString()+" "+canvasHeight.toString())

            // 비콘 좌표
            val beaconCoords = listOf(
                Beacon(0.0, 0.0),
                Beacon(0.0, 2.0),
                Beacon(1.0, 0.5)
            )

            // 캔버스 좌표 변환 비율 설정
            val xScale = canvasWidth / 2.0
            val yScale = canvasHeight / 2.0

            // 비콘 좌표를 캔버스 좌표로 변환하고 사각형으로 표시
            beaconCoords.forEach { beacon ->
                val xPos = beacon.lat * xScale
                val yPos = (2.0 - beacon.lng) * yScale // y축 역방향으로 변환

                drawRect(
                    color = Color.Red,
                    topLeft = Offset(xPos.toFloat(), yPos.toFloat()),
                    size = Size(20.dp.toPx(), 20.dp.toPx())
                )
            }
            Log.d("beacons", x.toString()+" "+y.toString())
            val currentXPos = x * xScale
            val currentYPos = (2.0 - y) * yScale // y축 역방향으로 변환
            // 현재 위치를 사각형으로 표시 (임시 값 사용)
            drawRect(
                color = Color.Blue,
                topLeft = Offset(currentXPos.toFloat(), currentYPos.toFloat()),
                size = Size(20.dp.toPx(), 20.dp.toPx())
            )
        }
    }
}

