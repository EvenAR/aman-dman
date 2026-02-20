package no.vaccsca.amandman.model.weather

sealed class WindProfileResult {
    data class Success(val profile: VerticalWeatherProfile?) : WindProfileResult()
    data class Failure(val error: WindProfileError) : WindProfileResult()
}

sealed class WindProfileError(open val message: String) {
    data class Network(override val message: String) : WindProfileError(message)
    data class Parse(override val message: String) : WindProfileError(message)
    data class NoForecastAvailable(override val message: String) : WindProfileError(message)
}

interface WindProfileProvider {
    fun getVerticalProfileAtPoint(latitude: Double, longitude: Double): WindProfileResult
}
