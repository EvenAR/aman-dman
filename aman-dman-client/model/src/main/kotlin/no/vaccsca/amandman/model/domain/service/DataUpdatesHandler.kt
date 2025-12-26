package no.vaccsca.amandman.model.domain.service

import kotlinx.datetime.Instant
import no.vaccsca.amandman.common.NtpClock
import no.vaccsca.amandman.model.data.integration.SharedState
import no.vaccsca.amandman.model.domain.valueobjects.RunwayStatus
import no.vaccsca.amandman.model.domain.valueobjects.timelineEvent.TimelineEvent
import no.vaccsca.amandman.model.domain.valueobjects.weather.VerticalWeatherProfile
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Responsible for notifying the presenter about data updates from the service.
 */
class GuiDataHandler : DataUpdateListener {
    lateinit var presenter: DataUpdateListener

    override fun onLiveData(airportIcao: String, timelineEvents: List<TimelineEvent>) {
        presenter.onLiveData(airportIcao, timelineEvents)
    }

    override fun onRunwayModesUpdated(airportIcao: String, runwayStatuses: Map<String, RunwayStatus>) {
        presenter.onRunwayModesUpdated(airportIcao, runwayStatuses)
    }

    override fun onWeatherDataUpdated(airportIcao: String, data: VerticalWeatherProfile?) {
        presenter.onWeatherDataUpdated(airportIcao, data)
    }

    override fun onMinimumSpacingUpdated(airportIcao: String, minimumSpacingNm: Double) {
        presenter.onMinimumSpacingUpdated(airportIcao, minimumSpacingNm)
    }

    override fun onWeatherFetchStatusUpdated(airportIcao: String, status: WeatherFetchStatus) {
        presenter.onWeatherFetchStatusUpdated(airportIcao, status)
    }
}

/**
 * Responsible for sending data updates to HTTP server.
 */
class DataUpdatesServerSender(
    private val sharedState: SharedState
) : DataUpdateListener {

    val sendUpdateMem = mutableMapOf<String, Instant>()

    override fun onLiveData(airportIcao: String, timelineEvents: List<TimelineEvent>) {
        val now = NtpClock.now()
        sendUpdateMem[airportIcao]
            ?.takeIf { now - it <= 2.seconds }
            ?: run {
                sharedState.sendTimelineEvents(airportIcao, timelineEvents)
                sendUpdateMem[airportIcao] = now
            }
    }

    override fun onRunwayModesUpdated(airportIcao: String, runwayStatuses: Map<String, RunwayStatus>) {
        sharedState.sendRunwayStatuses(airportIcao, runwayStatuses)
    }

    override fun onWeatherDataUpdated(airportIcao: String, data: VerticalWeatherProfile?) {
        sharedState.sendWeatherData(airportIcao, data)
    }

    override fun onMinimumSpacingUpdated(airportIcao: String, minimumSpacingNm: Double) {
        sharedState.sendMinimumSpacing(airportIcao, minimumSpacingNm)
    }

    override fun onWeatherFetchStatusUpdated(airportIcao: String, status: WeatherFetchStatus) {
        // No need to send fetch status to server
    }
}
