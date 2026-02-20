package no.vaccsca.amandman.model.weather

import no.vaccsca.amandman.model.weather.VerticalWeatherProfile


class WeatherDataRepository(
    private val windApi: WindApi = WindApi()
) {
    fun getWindData(lat: Double, lng: Double): VerticalWeatherProfile? {
        return windApi.getVerticalProfileAtPoint(lat, lng)
    }
}