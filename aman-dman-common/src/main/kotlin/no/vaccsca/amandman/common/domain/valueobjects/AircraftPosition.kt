package no.vaccsca.amandman.common.domain.valueobjects

data class AircraftPosition(
    val latLng: LatLng,
    val altitudeFt: Int,
    val flightLevel: Int,
    val groundspeedKts: Int,
    val trackDeg: Int
)