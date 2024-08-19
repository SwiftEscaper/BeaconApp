package koren.swiftescaper.domain.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import com.minew.beaconset.MinewBeacon
import koren.swiftescaper.util.KalmanRssiFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.pow

class MainViewModel: ViewModel() {

    // 비콘 목록을 상태로 관리하는 MutableStateFlow
    private val _beacons = MutableStateFlow<MutableMap<MinewBeacon, KalmanRssiFilter>>(mutableMapOf())
    val beacons: StateFlow<MutableMap<MinewBeacon, KalmanRssiFilter>> = _beacons.asStateFlow()

    private val _filteringBeacons = MutableStateFlow<MutableMap<MinewBeacon, Double>>(mutableMapOf())
    val filteringBeacons: StateFlow<MutableMap<MinewBeacon, Double>> = _filteringBeacons.asStateFlow()

    // x 좌표를 상태로 관리하는 MutableStateFlow
    private val _lat = MutableStateFlow<Double>(0.0)
    val lat: StateFlow<Double> = _lat

    // y 좌표를 상태로 관리하는 MutableStateFlow
    private val _lng = MutableStateFlow<Double>(0.0)
    val lng: StateFlow<Double> = _lng

    // 비콘 목록을 설정하고 위치 추정을 시작하는 함수
    fun setBeacons(beacons: List<MinewBeacon>) {
        val filteredBeacons = beacons.filter { it.uuid == "FDA50693-A4E2-4FB1-AFCF-C6EB07647825" }

        val existingBeaconsMap = _beacons.value.toMutableMap()

        filteredBeacons.forEach { beacon ->
            if (existingBeaconsMap.containsKey(beacon)) {   //기존 필터 사용
                existingBeaconsMap[beacon]?.let { filter ->
                    existingBeaconsMap[beacon] = filter
                }
            } else {    //새로운 필터 생성
                existingBeaconsMap[beacon] = KalmanRssiFilter()
            }
        }
        _beacons.value = existingBeaconsMap // 업데이트된 비콘 맵을 상태로 설정
        estimateRssi()
    }

    fun estimateRssi() {
        val beaconsMap = _beacons.value
        val updatedFilteringBeacons = mutableMapOf<MinewBeacon, Double>()
        // 모든 비콘의 RSSI 값을 필터링
        beaconsMap.forEach { (beacon, filter) ->
            val filteredRssi = filter.filtering(beacon.rssi.toDouble())  // RSSI 필터링
            updatedFilteringBeacons[beacon] = filteredRssi
            println("Filtered RSSI for beacon ${beacon.macAddress}: $filteredRssi")
        }
        _filteringBeacons.value = updatedFilteringBeacons
        estimatePosition()
    }

    // 비콘 신호를 바탕으로 위치를 추정하는 함수
    fun estimatePosition() {
        val rssiSet = _filteringBeacons.value
        require(rssiSet.size >= 3) {
            "적어도 3개의 비콘과 해당하는 RSSI가 필요합니다."
        }
        // 비콘 3개를 할당
        // RSSI 값을 거리로 변환
        val distances = rssiSet.mapValues { rssiToDistance(it.value) }

        val (beacon1, beacon2, beacon3) = distances.entries.take(3)

        // 비콘 좌표 설정 (예시로 설정)
        val (x1, y1) = 0.0 to 0.0
        val (x2, y2) = 0.0 to 200.0
        val (x3, y3) = 100.0 to 50.0

        // 삼각측량 계산을 위한 공식
        val A = 2 * (x2 - x1)
        val B = 2 * (y2 - y1)
        val C = 2 * (x3 - x1)
        val D = 2 * (y3 - y1)

        val E = beacon1.value.pow(2) - beacon2.value.pow(2) + x2.pow(2) - x1.pow(2) + y2.pow(2) - y1.pow(2)
        val F = beacon1.value.pow(2) - beacon3.value.pow(2) + x3.pow(2) - x1.pow(2) + y3.pow(2) - y1.pow(2)

        val x = (E - F * (A / C)) / (B - D * (A / C))
        val y = (E - A * x) / B

        _lat.value = x
        _lng.value = y
    }

    // RSSI를 거리로 변환하는 함수
    fun rssiToDistance(rssi: Double): Double {
        val A = -59.0  // 1미터 거리에서의 RSSI 값
        val n = 3.0    // 환경에 따라 다를 수 있는 경로 손실 지수 4~6, 2~3
        return 10.0.pow((A - rssi) / (10.0 * n))
    }
}
