package no.vaccsca.amandman.model.domain.service

import no.vaccsca.amandman.model.data.integration.MasterSlaveSharedState
import no.vaccsca.amandman.model.domain.valueobjects.NonSequencedEvent
import no.vaccsca.amandman.model.domain.valueobjects.RunwayStatus
import no.vaccsca.amandman.model.domain.valueobjects.timelineEvent.TimelineEvent
import no.vaccsca.amandman.model.domain.valueobjects.weather.VerticalWeatherProfile

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
}
