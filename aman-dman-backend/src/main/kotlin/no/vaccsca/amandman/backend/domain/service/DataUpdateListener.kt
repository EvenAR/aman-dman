package no.vaccsca.amandman.backend.domain.service

import no.vaccsca.amandman.common.domain.valueobjects.RunwayStatus
import no.vaccsca.amandman.common.domain.valueobjects.timelineEvent.TimelineEvent
import no.vaccsca.amandman.common.domain.valueobjects.weather.VerticalWeatherProfile

/**
 * Interface for handling data updates throughout the application.
 * This serves as the contract for components that need to be notified of data changes.
 */
interface DataUpdateListener {
    /**
     * Called when new timeline data is available
     */
    suspend fun onLiveData(airportIcao: String, timelineEvents: List<TimelineEvent>)

    /**
     * Called when runway status changes for an airport
     */
    suspend fun onRunwayModesUpdated(airportIcao: String, runwayStatuses: Map<String, RunwayStatus>)

    /**
     * Called when minimum spacing configuration changes
     */
    suspend fun onMinimumSpacingUpdated(airportIcao: String, minimumSpacingNm: Double)

    /**
     * Called when weather data is updated
     */
    suspend fun onWeatherDataUpdated(airportIcao: String, data: VerticalWeatherProfile?)
}