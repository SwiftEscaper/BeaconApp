package koren.swiftescaper.domain.remote

import retrofit2.http.POST
import retrofit2.http.Query

interface LocationInterface {

    // 서버로 위치 정보를 POST 요청으로 전송하는 메서드
    @POST("/api/location/send")
    fun postLocation(
        @Query("lat") lat: Double,      // 위도
        @Query("lng") lng: Double,      // 경도
        @Query("tunnelId") tunnelId: Long, // 터널 ID
        @Query("userId") userId: Long      // 사용자 ID
    )
}
