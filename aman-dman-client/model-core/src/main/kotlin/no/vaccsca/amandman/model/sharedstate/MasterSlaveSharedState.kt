package no.vaccsca.amandman.model.sharedstate

import no.vaccsca.amandman.model.timeline.event.NonSequencedEvent
import no.vaccsca.amandman.model.airport.RunwayStatus
import no.vaccsca.amandman.model.timeline.event.timeline.TimelineEvent
import no.vaccsca.amandman.model.weather.VerticalWeatherProfile

interface MasterSlaveSharedState {
    fun sendTimelineEvents(airportIcao: String, timelineEvents: List<TimelineEvent>)
    fun getTimelineEvents(airportIcao: String): List<TimelineEvent>
    fun getRunwayStatuses(airportIcao: String): Map<String, RunwayStatus>
    fun sendRunwayStatuses(airportIcao: String, runwayStatuses: Map<String, RunwayStatus>)
    fun sendWeatherData(airportIcao: String, weatherData: VerticalWeatherProfile?)
    fun getWeatherData(airportIcao: String): VerticalWeatherProfile?
    fun getMinimumSpacing(airportIcao: String): Double
    fun sendMinimumSpacing(airportIcao: String, minimumSpacingNm: Double)
    fun acquireMasterRole(airportIcao: String): Boolean
    fun hasMasterRoleStatus(airportIcao: String): Boolean
    fun releaseMasterRole(airportIcao: String)
    fun checkVersionCompatibility(): VersionCompatibilityResult
    fun sendNonSequencedList(airportIcao: String, nonSequencedList: List<NonSequencedEvent>)
    fun getNonSequencedList(airportIcao: String): List<NonSequencedEvent>
}