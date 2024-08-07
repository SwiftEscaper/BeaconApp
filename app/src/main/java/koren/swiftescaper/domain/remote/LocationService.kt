package koren.swiftescaper.domain.remote

import android.util.Log
import koren.swiftescaper.util.getRetrofit
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class LocationService {
    fun postLocation(lat : Double, lng:Double, tunnalId:Long, userId:Long){
        val locationService = getRetrofit().create(LocationInterface::class.java)
        locationService.postLocation(lat, lng,tunnalId,userId)
    }
}