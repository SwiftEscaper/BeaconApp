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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener


class MainActivity : ComponentActivity() {

    private val minewBeaconManager: MinewBeaconManager = MinewBeaconManager.getInstance(this)
    private val REQUEST_CODE_PERMISSIONS = 1001
    private lateinit var mainViewModel: MainViewModel

    private var webSocket: WebSocket? = null
    private var handler: Handler? = null
    private var runnable: Runnable? = null
    private val period = 200L // 0.2초 간격

    private var token :String = ""


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initializeFirebase()

        checkPermissions()  //권한 체크
        minewBeaconManager.startService()   //서비스 시작

        mainViewModel = ViewModelProvider(this).get(MainViewModel::class.java)

        val client = OkHttpClient()

        val request: Request = Request.Builder()
            .url("ws://10.0.2.2:8080/location/send")
            .build()

        val listener: WebSocketListener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket opened")
                this@MainActivity.webSocket = webSocket
                // Start sending messages periodically
                startSendingMessages(0.0,0.0,1,token)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "Received message: $text")
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing: code $code reason: $reason")
                webSocket.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: code $code reason: $reason")
            }
        }

        webSocket = client.newWebSocket(request, listener)
        client.dispatcher.executorService.shutdown()

        //UI
        setContent {
            MainScreen(mainViewModel = mainViewModel)
        }
    }

    private fun initializeFirebase() {
        FirebaseApp.initializeApp(this)
        FirebaseMessaging.getInstance().token.addOnCompleteListener(OnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w(ContentValues.TAG, "Fetching FCM registration token failed", task.exception)
                return@OnCompleteListener
            }
            token = task.result
            Log.d(ContentValues.TAG, token!!)
        })
    }

    private fun initBeaconListener() {
        minewBeaconManager.setMinewbeaconManagerListener(object : MinewBeaconManagerListener {
            override fun onUpdateBluetoothState(bluetoothState: BluetoothState?) {
                bluetoothState?.let {
                    when (it) {
                        BluetoothState.BluetoothStatePowerOn -> Log.d(TAG, "Bluetooth is ON")
                        BluetoothState.BluetoothStatePowerOff -> Log.d(TAG, "Bluetooth is OFF")
                        BluetoothState.BluetoothStateNotSupported -> Log.d(TAG, "Bluetooth is Unsupported")
                    }
                }
            }

            override fun onRangeBeacons(beacons: MutableList<MinewBeacon>?) {
                beacons?.let {
                    if (it.isNotEmpty()) {
                        mainViewModel.setBeacons(beacons)  //새로운 데이터로 변경
                    } else {
                        Log.i(TAG, "No beacons in range")
                    }
                }
            }

            override fun onAppearBeacons(beacons: MutableList<MinewBeacon>?) {
                beacons?.let {
                    if (it.isNotEmpty()) {
                        for (beacon in it) {
                            Log.i(TAG, "Beacon appeared: ${beacon.toString()}")
                            // Additional logic when a beacon appears
                        }
                    }
                }
            }

            override fun onDisappearBeacons(beacons: MutableList<MinewBeacon>?) {
                // Handle disappear beacons if needed
            }
        })
        minewBeaconManager.startScan()
    }

    private fun checkPermissions() {
        val permissions = arrayOf(
            Manifest.permission.BLUETOOTH_CONNECT,      //API 30이상
            Manifest.permission.BLUETOOTH_SCAN,     //API 30이상
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), REQUEST_CODE_PERMISSIONS)
        } else {
            initBeaconListener()
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
                Toast.makeText(this, "Permissions required for beacon scanning", Toast.LENGTH_SHORT).show()
            } else {
                initBeaconListener()
            }
        }
    }

    private fun startSendingMessages(lat : Double, lng : Double, tunnelId : Long, fcmToken : String) {
        handler = Handler(Looper.getMainLooper())
        runnable = object : Runnable {
            override fun run() {
                if (webSocket != null) {
                    val locationJson = "{\"lat\": $lat, \"lng\": $lng, \"tunnelId\": $tunnelId, \"fcmToken\": \"$fcmToken\"}"
                    webSocket!!.send(locationJson)
                }
                handler!!.postDelayed(this, period) // Re-run this runnable after the specified period
            }
        }
        handler!!.post(runnable!!) // Start sending messages
    }
}
@Composable
fun MainScreen(mainViewModel: MainViewModel) {

    val x = mainViewModel.x.collectAsState()
    val y = mainViewModel.y.collectAsState()

    Surface(modifier = Modifier.fillMaxSize()) {
        Column (horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center){
            Text(text = x.value.toString()+ "," + y.value.toString())
        }
    }
}

