package koren.swiftescaper.domain.dto

import com.minew.beaconset.MinewBeacon

data class Brightness(
    val beacon: MinewBeacon,
    val brightness: Int
)
