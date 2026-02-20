package no.vaccsca.amandman.model.aircraft

import no.vaccsca.amandman.model.navigation.LatLng

data class AircraftPosition(
    val latLng: LatLng,
    val altitudeFt: Int,
    val flightLevel: Int,
    val groundspeedKts: Int,
    val trackDeg: Int
)
