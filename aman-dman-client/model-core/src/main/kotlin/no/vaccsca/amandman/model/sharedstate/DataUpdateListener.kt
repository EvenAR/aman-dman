package no.vaccsca.amandman.model.sharedstate

import no.vaccsca.amandman.model.timeline.event.NonSequencedEvent
import no.vaccsca.amandman.model.timeline.event.timeline.TimelineEvent
import no.vaccsca.amandman.model.airport.RunwayStatus
import no.vaccsca.amandman.model.timeline.MeteringPointState
import no.vaccsca.amandman.model.weather.VerticalWeatherProfile

/**
 * Interface for handling data updates throughout the application.
 * This serves as the contract for components that need to be notified of data changes.
 */
interface DataUpdateListener {
    /**
     * Called when new timeline data is available
     */
    fun onTimelineEventsUpdated(airportIcao: String, timelineEvents: List<TimelineEvent>)

    /**
     * Called when runway status changes for an airport
     */
    fun onRunwayModesUpdated(airportIcao: String, runwayStatuses: Map<String, RunwayStatus>)

    /**
     * Called when minimum spacing configuration changes
     */
    fun onMinimumSpacingUpdated(airportIcao: String, minimumSpacingNm: Double)

    /**
     * Called when weather data is updated
     */
    fun onWeatherDataUpdated(airportIcao: String, data: VerticalWeatherProfile?)

    /**
     * Called when non-sequenced events are updated
     */
    fun onNonSequencedListUpdated(airportIcao: String, nonSequencedList: List<NonSequencedEvent>)

    /**
     * Called when metering point timings are updated
     */
    fun onMeteringPointStateUpdated(airportIcao: String, meteringPointState: MeteringPointState)
}
