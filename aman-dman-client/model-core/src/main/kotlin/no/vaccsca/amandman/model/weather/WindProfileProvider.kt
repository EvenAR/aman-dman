package no.vaccsca.amandman.model.weather

import no.vaccsca.amandman.model.integration.IntegrationStatus

sealed class WindProfileResult {
    data class Success(
        val weatherField: SpatialWeatherField,
        val displayProfile: VerticalWeatherProfile
    ) : WindProfileResult()
    data class Failure(val error: WindProfileError) : WindProfileResult()
}

sealed class WindProfileError(open val message: String) {
    data class Network(override val message: String) : WindProfileError(message)
    data class Parse(override val message: String) : WindProfileError(message)
    data class NoForecastAvailable(override val message: String) : WindProfileError(message)
}

interface WindProfileProvider {
    fun getWeatherDataAroundAirport(
        airportIcao: String,
        latitude: Double,
        longitude: Double,
        weatherFetchRadiusNm: Double
    ): WindProfileResult
    fun getIntegrationStatus(airportIcao: String): IntegrationStatus
}
