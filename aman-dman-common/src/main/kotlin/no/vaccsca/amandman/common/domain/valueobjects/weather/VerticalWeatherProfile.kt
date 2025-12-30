package no.vaccsca.amandman.common.domain.valueobjects.weather

import kotlinx.datetime.Instant
import no.vaccsca.amandman.common.domain.valueobjects.LatLng

data class VerticalWeatherProfile(
    val time: Instant,
    val position: LatLng,
    val weatherLayers: MutableList<WeatherLayer>
)