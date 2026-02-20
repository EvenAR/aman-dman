package no.vaccsca.amandman.model.weather

import no.vaccsca.amandman.model.weather.data.NoaaApiClient

class WeatherDataRepository(
    private val windProvider: WindProfileProvider = NoaaApiClient()
) {
    fun getWindData(lat: Double, lng: Double): WindProfileResult {
        return windProvider.getVerticalProfileAtPoint(lat, lng)
    }
}
