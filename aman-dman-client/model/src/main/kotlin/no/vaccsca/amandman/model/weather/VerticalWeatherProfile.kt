package no.vaccsca.amandman.model.weather

import kotlinx.datetime.Instant
import no.vaccsca.amandman.model.navigation.LatLng

data class VerticalWeatherProfile(
    val time: Instant,
    val position: LatLng,
    val weatherLayers: MutableList<WeatherLayer>
)