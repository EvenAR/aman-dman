package no.vaccsca.amandman.model.navigation

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

data class LatLng(
    val lat: Double,
    val lon: Double
)

fun LatLng.distanceTo(latLng: LatLng): Double {
    val lat1 = Math.toRadians(this.lat)
    val lon1 = Math.toRadians(this.lon)
    val lat2 = Math.toRadians(latLng.lat)
    val lon2 = Math.toRadians(latLng.lon)

    val dlon = lon2 - lon1
    val dlat = lat2 - lat1

    val a = sin(dlat / 2).pow(2.0) + cos(lat1) * cos(lat2) * sin(dlon / 2).pow(2.0)
    val c = 2 * asin(sqrt(a))
    val radius = 3440.065 // in nautical miles
    return radius * c
}

fun LatLng.bearingTo(latLng: LatLng): Int {
    val lat1 = Math.toRadians(this.lat)
    val lon1 = Math.toRadians(this.lon)
    val lat2 = Math.toRadians(latLng.lat)
    val lon2 = Math.toRadians(latLng.lon)

    val dlon = lon2 - lon1

    val y = sin(dlon) * cos(lat2)
    val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dlon)

    val bearing = Math.toDegrees(atan2(y, x))

    return ((bearing + 360) % 360).roundToInt()
}