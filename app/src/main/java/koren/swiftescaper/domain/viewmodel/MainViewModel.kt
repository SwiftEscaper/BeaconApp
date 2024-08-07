package koren.swiftescaper.domain.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import com.minew.beaconset.MinewBeacon
import koren.swiftescaper.domain.remote.LocationService
import koren.swiftescaper.util.KalmanFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.pow

class MainViewModel:ViewModel() {
    private val _beacons = MutableStateFlow<List<MinewBeacon>>(listOf())
    val beacons :StateFlow<List<MinewBeacon>> = _beacons.asStateFlow()

    private val _x = MutableStateFlow<Double>(0.0)
    val x :StateFlow<Double> = _x

    private val _y = MutableStateFlow<Double>(0.0)
    val y :StateFlow<Double> = _y

    fun setBeacons(beacons : List<MinewBeacon>) {
        val filteredBeacons = beacons.filter { it.uuid == "FDA50693-A4E2-4FB1-AFCF-C6EB07647825" }
        _beacons.value = filteredBeacons.toList()
        estimatePosition(beacons)
    }

    fun estimatePosition(beacons: List<MinewBeacon>) {
        require(beacons.size >= 3 ) {
            "At least 3 beacons and corresponding RSSIs are required."
        }

        val sortBeacons =beacons.sortedByDescending { it.rssi }
        // 칼만 필터 초기화
        val processNoise = 0.1 // 예시 값
        val measurementNoise = 1.0 // 예시 값
        val estimatedError = 1.0 // 예시 값

        val kalmanX = KalmanFilter(processNoise, measurementNoise, estimatedError)
        val kalmanY = KalmanFilter(processNoise, measurementNoise, estimatedError)

        // 비콘 3개를 사용한 삼변측량
        val (beacon1, beacon2, beacon3) = sortBeacons

        val (x1, y1) = 0.0 to 0.0
        val (x2, y2) = 0.0 to 200.0
        val (x3, y3) = 100.0 to 50.0

        val A = 2 * (x2 - x1)
        val B = 2 * (y2 - y1)
        val C = 2 * (x3 - x1)
        val D = 2 * (y3 - y1)

        val E = beacon1.distance.pow(2) - beacon2.distance.pow(2) + x2.pow(2) - x1.pow(2) + y2.pow(2) - y1.pow(2)
        val F = beacon1.distance.pow(2) - beacon3.distance.pow(2) + x3.pow(2) - x1.pow(2) + y3.pow(2) - y1.pow(2)

        val x = (E - F * (A / C)) / (B - D * (A / C))
        val y = (E - A * x) / B

        // 칼만 필터를 사용한 위치 추정
        kalmanX.update(x)
        kalmanY.update(y)

        _x.value = kalmanX.getEstimate()
        _y.value = kalmanY.getEstimate()
    }

}