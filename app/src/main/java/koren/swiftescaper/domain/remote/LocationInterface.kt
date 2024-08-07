package koren.swiftescaper.domain.remote

import retrofit2.http.POST
import retrofit2.http.Query

interface LocationInterface {
    @POST("/api/location/send")
    fun postLocation(@Query("lat") lat : Double,
                     @Query("lng") lng : Double,
                     @Query("tunnalId") tunnalId : Long,
                     @Query("userId") userId : Long)

}