package no.vaccsca.amandman.model.planning

import no.vaccsca.amandman.model.airport.ArrivalFixRole
import no.vaccsca.amandman.model.navigation.LatLng
import no.vaccsca.amandman.model.weather.WindVector
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
    val appliedAltitudeExpectation: Int? = null,
    val appliedSpeedExpectation: Int? = null,
    val fixRole: ArrivalFixRole? = null,
)
