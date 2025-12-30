package no.vaccsca.amandman.common.domain.valueobjects

data class Airport(
    val icao: String,
    val location: LatLng,
    val runways: Map<String, Runway>,
)

data class Runway(
    val id: String,
    val location: LatLng,
    val elevation: Float,
    val trueHeading: Float,
    val stars: List<Star>,
)