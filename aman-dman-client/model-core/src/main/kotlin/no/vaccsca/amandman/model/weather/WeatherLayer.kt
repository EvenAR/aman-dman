package no.vaccsca.amandman.model.weather

data class WeatherLayer(
    val flightLevelFt: Int,
    val temperatureC: Int,
    val windVector: WindVector
)