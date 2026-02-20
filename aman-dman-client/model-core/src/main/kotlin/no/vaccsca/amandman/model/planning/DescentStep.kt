package no.vaccsca.amandman.model.planning

import no.vaccsca.amandman.model.navigation.LatLng
import no.vaccsca.amandman.model.weather.WindVector

data class DescentStep(
    val position: LatLng,
    val altitudeFt: Int,
    val groundSpeed: Int,
    val tas: Int,
    val ias: Int,
    val windVector: WindVector
)
