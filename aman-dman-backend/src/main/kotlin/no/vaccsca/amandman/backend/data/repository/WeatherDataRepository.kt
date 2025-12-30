package no.vaccsca.amandman.backend.data.repository

import no.vaccsca.amandman.common.domain.valueobjects.weather.VerticalWeatherProfile


class WeatherDataRepository(
    private val windApi: WindApi = WindApi()
) {
    fun getWindData(lat: Double, lng: Double): VerticalWeatherProfile? {
        return windApi.getVerticalProfileAtPoint(lat, lng)
    }
}