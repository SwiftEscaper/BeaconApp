package koren.swiftescaper.ui.view.main

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.minew.beaconset.*
import koren.swiftescaper.domain.viewmodel.MainViewModel
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener

class MainActivity : ComponentActivity() {

    private val minewBeaconManager: MinewBeaconManager = MinewBeaconManager.getInstance(this)
    private lateinit var mainViewModel: MainViewModel
    private var webSocket: WebSocket? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    private val REQUEST_CODE_PERMISSIONS = 1001
    private val LOCATION_PERMISSION_REQUEST_CODE = 1
    private var fcmToken: String? = null

    private var handler: Handler? = null
    private var runnable: Runnable? = null
    private val period = 200L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 권한 확인 및 설정
        checkPermissions()
        // GPS 위치 클라이언트 초기화
        initLocationClient()

        // WebSocket 연결
        connectWebSocket()
        // 비콘 리스너 초기화
        initBeaconListener()
    }

    override fun onDestroy() {
        super.onDestroy()
        // WebSocket 연결 종료
        webSocket?.close(1000, null)
        // GPS 위치 업데이트 중단
        stopLocationUpdates()
    }

    // WebSocket 연결 메소드
    private fun connectWebSocket() {
        val client = OkHttpClient()
        val request = Request.Builder().url("ws://210.102.180.145:80/location/send").build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            // WebSocket 연결 성공
            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                Log.d("WebSocket", "WebSocket Opened")
                this@MainActivity.webSocket = webSocket
            }

            // WebSocket 메시지 수신
            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d("WebSocket", "Received: $text")
            }

            // WebSocket 오류 발생 시 재연결 시도
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                t.printStackTrace()
                Log.e("WebSocket", "Error: ${t.message}")
                reconnectWebSocket()
            }
        })
    }

    // WebSocket 재연결 로직
    private fun reconnectWebSocket() {
        connectWebSocket()
    }

    // GPS 위치 클라이언트 초기화
    private fun initLocationClient() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        locationCallback = object : LocationCallback() {
            // GPS 위치 결과 처리
            override fun onLocationResult(locationResult: LocationResult) {
                val location: Location? = locationResult.lastLocation
                location?.let {
                    val latitude = it.latitude
                    val longitude = it.longitude
                    sendGpsDataToWebSocket(latitude, longitude)  // GPS 데이터를 WebSocket으로 전송
                }
            }
        }
    }

    // GPS 위치 업데이트 시작
    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.create().apply {
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY  // 높은 정확도로 위치 추적
            interval = 10000  // 위치 업데이트 간격 (10초)
            fastestInterval = 5000  // 최소 위치 업데이트 간격 (5초)
        }

        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        } else {
            // 위치 권한이 없는 경우 요청
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST_CODE)
        }
    }

    // GPS 위치 업데이트 중단
    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    // GPS 데이터를 WebSocket으로 전송
    private fun sendGpsDataToWebSocket(latitude: Double, longitude: Double) {
        if (fcmToken != null) {
            val locationJson = "{\"latitude\": $latitude, \"longitude\": $longitude, \"tunnelId\": \"GPS\", \"fcmToken\": \"$fcmToken\"}"
            webSocket?.send(locationJson)
            Log.d("WebSocket", "GPS Location sent: Lat:$latitude, Lon:$longitude")
        } else {
            Log.e("WebSocket", "FCM token is null, unable to send location.")
        }
    }

    // 권한 체크 및 요청
    private fun checkPermissions() {
        val permissions = arrayOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        // 필요한 권한 중 승인되지 않은 권한 필터링
        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        // 권한 요청이 필요한 경우 요청
        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), REQUEST_CODE_PERMISSIONS)
        } else {
            initBeaconListener()
        }
    }

    // 권한 요청 결과 처리
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                // 위치 권한이 승인된 경우 GPS 업데이트 시작
                startLocationUpdates()
            } else {
                Log.e("Permissions", "Location permission denied")
                // 권한 거부 시 처리 로직 추가 가능
            }
        }
    }

    // 비콘 리스너 초기화
    private fun initBeaconListener() {
        minewBeaconManager.setMinewbeaconManagerListener(object : MinewBeaconManagerListener {
            // 비콘이 감지된 경우
            override fun onRangeBeacons(beacons: MutableList<MinewBeacon>?) {
                if (beacons.isNullOrEmpty()) {
                    // 비콘이 없을 때 GPS 위치 가져오기
                    startLocationUpdates()
                } else {
                    // 비콘이 감지되면 GPS 업데이트 중단하고 비콘 데이터 처리
                    stopLocationUpdates()
                    mainViewModel.setBeacons(beacons)
                }
            }

            override fun onAppearBeacons(beacons: MutableList<MinewBeacon>?) {}
            override fun onDisappearBeacons(beacons: MutableList<MinewBeacon>?) {}
            override fun onUpdateBluetoothState(bluetoothState: BluetoothState?) {}
        })
        // 비콘 스캔 시작
        minewBeaconManager.startScan()
    }
}
