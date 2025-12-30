package no.vaccsca.amandman.backend.data.integration

import no.vaccsca.amandman.common.domain.valueobjects.RunwayStatus
import no.vaccsca.amandman.common.domain.valueobjects.timelineEvent.TimelineEvent
import no.vaccsca.amandman.common.domain.valueobjects.weather.VerticalWeatherProfile

interface SharedState {
    fun sendTimelineEvents(airportIcao: String, timelineEvents: List<TimelineEvent>)
    fun getTimelineEvents(airportIcao: String): List<TimelineEvent>
    fun getRunwayStatuses(airportIcao: String): Map<String, RunwayStatus>
    fun sendRunwayStatuses(airportIcao: String, runwayStatuses: Map<String, RunwayStatus>)
    fun sendWeatherData(airportIcao: String, weatherData: VerticalWeatherProfile?)
    fun getWeatherData(airportIcao: String): VerticalWeatherProfile?
    fun getMinimumSpacing(airportIcao: String): Double
    fun sendMinimumSpacing(airportIcao: String, minimumSpacingNm: Double)
    fun acquireMasterRole(airportIcao: String): Boolean
    fun checkMasterRoleStatus(airportIcao: String): Boolean
    fun releaseMasterRole(airportIcao: String)
}