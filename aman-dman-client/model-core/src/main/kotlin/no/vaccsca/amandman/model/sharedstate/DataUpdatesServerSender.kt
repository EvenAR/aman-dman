package no.vaccsca.amandman.model.sharedstate

import no.vaccsca.amandman.model.timeline.event.NonSequencedEvent
import no.vaccsca.amandman.model.airport.RunwayStatus
import no.vaccsca.amandman.model.timeline.FeederFixState
import no.vaccsca.amandman.model.timeline.event.timeline.TimelineEvent
import no.vaccsca.amandman.model.weather.VerticalWeatherProfile

/**
 * Sends data updates to the shared state server for synchronization with slave instances.
 * Used only when running as Master.
 */
class DataUpdatesServerSender(
    private val sharedState: MasterSlaveSharedState
) : DataUpdateListener {

    override fun onTimelineEventsUpdated(airportIcao: String, timelineEvents: List<TimelineEvent>) {
        sharedState.sendTimelineEvents(airportIcao, timelineEvents)
    }

    override fun onRunwayModesUpdated(airportIcao: String, runwayStatuses: Map<String, RunwayStatus>) {
        sharedState.sendRunwayStatuses(airportIcao, runwayStatuses)
    }

    override fun onWeatherDataUpdated(airportIcao: String, data: VerticalWeatherProfile?) {
        sharedState.sendWeatherData(airportIcao, data)
    }

    override fun onNonSequencedListUpdated(airportIcao: String, nonSequencedList: List<NonSequencedEvent>) {
        sharedState.sendNonSequencedList(airportIcao, nonSequencedList)
    }

    override fun onMinimumSpacingUpdated(airportIcao: String, minimumSpacingNm: Double) {
        sharedState.sendMinimumSpacing(airportIcao, minimumSpacingNm)
    }

    override fun onFeederFixStateUpdated(airportIcao: String, feederFixState: FeederFixState) {
        sharedState.sendFeederFixState(airportIcao, feederFixState)
    }
}
