package koren.swiftescaper.domain.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import com.minew.beaconset.MinewBeacon
import koren.swiftescaper.domain.dto.Brightness
import koren.swiftescaper.util.KalmanRssiFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class MainViewModel: ViewModel() {

    // 비콘 목록을 상태로 관리하는 MutableStateFlow
    private val _beacons = MutableStateFlow<MutableMap<MinewBeacon, KalmanRssiFilter>>(mutableMapOf())
    val beacons: StateFlow<MutableMap<MinewBeacon, KalmanRssiFilter>> = _beacons.asStateFlow()

    private val _filteringBeacons = MutableStateFlow<List<MinewBeacon>>(mutableListOf())
    val filteringBeacons: StateFlow<List<MinewBeacon>> = _filteringBeacons.asStateFlow()

    private val _brightness = MutableStateFlow<List<Brightness>>(mutableListOf())
    val brightness :StateFlow<List<Brightness>> = _brightness.asStateFlow()

    // x 좌표를 상태로 관리하는 MutableStateFlow
    private val _lat = MutableStateFlow<Double>(0.0)
    val lat: StateFlow<Double> = _lat

    // y 좌표를 상태로 관리하는 MutableStateFlow
    private val _lng = MutableStateFlow<Double>(0.0)
    val lng: StateFlow<Double> = _lng

    // 비콘 목록을 설정하고 위치 추정을 시작하는 함수
    fun setBeacons(beacons: List<MinewBeacon>) {
        val filteredBeacons = beacons.filter { it.uuid == "FDA50693-A4E2-4FB1-AFCF-C6EB07647825" }
        val sortedBeacons = filteredBeacons.sortedBy { it.name }

        _filteringBeacons.value = sortedBeacons //비콘 리스트

        val existingBeaconsMap = _beacons.value.toMutableMap()

        val brightnessList = sortedBeacons.mapNotNull { beacon ->
            if (!existingBeaconsMap.containsKey(beacon)) {   //기존 필터 없을때
                existingBeaconsMap[beacon] = KalmanRssiFilter()
            }
            rssiToBrightness(beacon)
        }
        _brightness.value = brightnessList
        calculateWeightedPosition(interval = 10) //m단위
    }
    fun rssiToBrightness(beacon: MinewBeacon): Brightness {
        val minRssi = -120
        val maxRssi = -20

        // 변환된 밝기 값 계산
        val brightness = ((beacon.rssi - minRssi).toDouble() / (maxRssi - minRssi) * 255).toInt()

        // 0에서 255 범위로 제한
        return Brightness(beacon, max(0, min(255, brightness)))
    }

    fun calculateWeightedPosition(interval : Int) {
        val strongestBrightness = _brightness.value.maxByOrNull { it.brightness }
        val strongestIndex = _brightness.value.indexOf(strongestBrightness)

        val strongest = _brightness.value[strongestIndex]
        val second = _brightness.value.getOrNull(strongestIndex - 1)?: Brightness(MinewBeacon(), 0)
        val third = _brightness.value.getOrNull(strongestIndex + 1)?: Brightness(MinewBeacon(), 0)

        // 각 비콘의 밝기에 기반한 가중치 계산
        val totalBrightness = strongest.brightness + second.brightness + third.brightness
        val weightStrongest = strongest.brightness.toDouble() / totalBrightness
        val weightSecond = second.brightness.toDouble() / totalBrightness
        val weightThird = third.brightness.toDouble() / totalBrightness

        // 비콘의 실제 좌표를 가져와야 합니다.
        val strongestPosition = interval*strongestIndex
        val secondPosition = if (strongestIndex > 0) interval * (strongestIndex - 1) else 0 // 경계 처리
        val thirdPosition = if (strongestIndex < _brightness.value.size - 1) interval * (strongestIndex + 1) else 0 // 경계 처리

        // 가중치 기반 위치 추정
        _lng.value = weightStrongest * strongestPosition +
                weightSecond * secondPosition +
                weightThird * thirdPosition
    }

}
