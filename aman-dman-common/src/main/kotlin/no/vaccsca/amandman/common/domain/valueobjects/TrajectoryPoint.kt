package no.vaccsca.amandman.common.domain.valueobjects

import no.vaccsca.amandman.common.domain.valueobjects.weather.WindVector
import kotlin.time.Duration

data class TrajectoryPoint(
    val fixId: String?,
    val latLng: LatLng,
    val altitude: Int,
    val remainingDistance: Float,
    val remainingTime: Duration,
    val groundSpeed: Int,
    val tas: Int,
    val ias: Int,
    val windVector: WindVector,
    val heading: Int,
)