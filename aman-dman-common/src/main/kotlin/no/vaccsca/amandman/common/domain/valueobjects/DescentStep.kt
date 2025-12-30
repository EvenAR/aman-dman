package no.vaccsca.amandman.common.domain.valueobjects

import no.vaccsca.amandman.common.domain.valueobjects.weather.WindVector

data class DescentStep(
    val position: LatLng,
    val altitudeFt: Int,
    val groundSpeed: Int,
    val tas: Int,
    val ias: Int,
    val windVector: WindVector
)