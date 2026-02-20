package no.vaccsca.amandman.model.sharedstate

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import no.vaccsca.amandman.model.sharedstate.MasterSlaveSharedState
import no.vaccsca.amandman.model.sharedstate.DataUpdateListener
import no.vaccsca.amandman.model.planning.AirportDataSource
import org.slf4j.LoggerFactory

/**
 * Read-only data source that mirrors state from a remote master.
 * Does not perform any planning - only observes and relays data updates.
 */
class RemoteDataMirror(
    override val airportIcao: String,
    private val sharedState: MasterSlaveSharedState,
    private val dataUpdateListener: DataUpdateListener
) : AirportDataSource {

    override val isReadOnly: Boolean = true

    private val logger = LoggerFactory.getLogger(javaClass)
    private val scope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, exception ->
            logger.error("Unhandled exception in coroutine", exception)
        })

    override fun start() {
        scope.launch {
            while (isActive) {
                fetchDataFromRemote()
                delay(1000)
            }
        }
    }

    override fun stop() {
        scope.cancel()
    }

    override fun startDataCollection() {
        fetchDataFromRemote()
    }

    private fun fetchDataFromRemote() {
        logger.debug("Fetching remote data for $airportIcao")

        runCatching { sharedState.getTimelineEvents(airportIcao) }
            .onSuccess { dataUpdateListener.onTimelineEventsUpdated(airportIcao, it) }
            .onFailure { logger.error("Failed to fetch timeline events: ${it.message}") }

        runCatching { sharedState.getRunwayStatuses(airportIcao) }
            .onSuccess { dataUpdateListener.onRunwayModesUpdated(airportIcao, it) }
            .onFailure { logger.error("Failed to fetch runway statuses: ${it.message}") }

        runCatching { sharedState.getWeatherData(airportIcao) }
            .onSuccess { dataUpdateListener.onWeatherDataUpdated(airportIcao, it) }
            .onFailure { logger.error("Failed to fetch weather data: ${it.message}") }

        runCatching { sharedState.getMinimumSpacing(airportIcao) }
            .onSuccess { dataUpdateListener.onMinimumSpacingUpdated(airportIcao, it) }
            .onFailure { logger.error("Failed to fetch minimum spacing: ${it.message}") }

        runCatching { sharedState.getNonSequencedList(airportIcao) }
            .onSuccess { dataUpdateListener.onNonSequencedListUpdated(airportIcao, it) }
            .onFailure { logger.error("Failed to fetch non-sequenced list: ${it.message}") }
    }
}