package koren.swiftescaper.domain.remote

import android.util.Log
import koren.swiftescaper.util.getRetrofit
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class LocationService {

    // 서버로 위치 정보를 전송하는 함수
    fun postLocation(lat: Double, lng: Double, tunnelId: Long, userId: Long) {
        val locationService = getRetrofit().create(LocationInterface::class.java)
        // postLocation 메서드를 호출하여 위치 정보를 전송
        locationService.postLocation(lat, lng, tunnelId, userId)
    }
}
