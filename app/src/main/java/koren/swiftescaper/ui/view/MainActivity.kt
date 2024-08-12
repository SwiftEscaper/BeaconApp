package koren.swiftescaper.ui.view

import android.Manifest
import android.content.ContentValues
import android.content.ContentValues.TAG
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.RemoteException
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
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
import koren.swiftescaper.ui.theme.SwiftescaperTheme

class MainActivity : ComponentActivity() {

    private val minewBeaconManager: MinewBeaconManager = MinewBeaconManager.getInstance(this)
    private val REQUEST_CODE_PERMISSIONS = 1001
    private lateinit var mainViewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initializeFirebase() // Firebase 초기화

        checkPermissions()  // 권한 체크
        minewBeaconManager.startService()   // 서비스 시작

        mainViewModel = ViewModelProvider(this).get(MainViewModel::class.java)

        // UI 설정
        setContent {
            MainScreen(mainViewModel = mainViewModel)
        }
    }

    private fun initializeFirebase() {
        FirebaseApp.initializeApp(this) // Firebase 앱 초기화
        FirebaseMessaging.getInstance().token.addOnCompleteListener(OnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w(ContentValues.TAG, "FCM 등록 토큰 가져오기 실패", task.exception)
                return@OnCompleteListener
            }
            val token = task.result
            Log.d(ContentValues.TAG, token!!)
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
}

@Composable
fun MainScreen(mainViewModel: MainViewModel) {

    val x = mainViewModel.x.collectAsState()  // x 좌표 상태 가져오기
    val y = mainViewModel.y.collectAsState()  // y 좌표 상태 가져오기

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = x.value.toString() + "," + y.value.toString())  // x, y 좌표를 화면에 표시
        }
    }
}
