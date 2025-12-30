package no.vaccsca.amandman.backend.domain.service

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Instant
import no.vaccsca.amandman.backend.WsClientDataUpdateListener
import no.vaccsca.amandman.backend.data.repository.CdmClient
import no.vaccsca.amandman.backend.data.repository.WeatherDataRepository
import no.vaccsca.amandman.backend.domain.exception.DescentTrajectoryException
import no.vaccsca.amandman.backend.domain.service.DataUpdateListener
import no.vaccsca.amandman.common.NtpClock
import no.vaccsca.amandman.common.domain.valueobjects.*
import no.vaccsca.amandman.common.domain.valueobjects.atcClient.AtcClientArrivalData
import no.vaccsca.amandman.common.domain.valueobjects.atcClient.AtcClientDepartureData
import no.vaccsca.amandman.common.domain.valueobjects.atcClient.AtcClientRunwaySelectionData
import no.vaccsca.amandman.common.domain.valueobjects.atcClient.ControllerInfoData
import no.vaccsca.amandman.common.domain.valueobjects.timelineEvent.DepartureEvent
import no.vaccsca.amandman.common.domain.valueobjects.timelineEvent.RunwayArrivalEvent
import no.vaccsca.amandman.common.domain.valueobjects.timelineEvent.TimelineEvent
import no.vaccsca.amandman.common.domain.valueobjects.weather.VerticalWeatherProfile
import no.vaccsca.amandman.common.domain.valueobjects.CdmData
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * This is only used in the Master and Local instances of the application
 */
class PlannerService(
    private val airport: Airport,
    private val weatherDataRepository: WeatherDataRepository,
    private val cdmClient: CdmClient,
) {

    private val state = State()
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val dataUpdateListeners: MutableList<DataUpdateListener> = mutableListOf()

    // Mutable state encapsulated in one object
    private data class State(
        var arrivalsCache: List<RunwayArrivalEvent> = emptyList(),
        var departuresCache: List<DepartureEvent> = emptyList(),
        var sequence: Sequence = Sequence(emptyList()),
        var minimumSpacingNm: Double = 3.0,
        var availableRunways: Map<String, RunwayStatus>? = null,
        var weatherData: VerticalWeatherProfile? = null
    )

    private suspend fun <T> withStateLock(block: suspend State.() -> T): T =
        mutex.withLock { state.block() }

    fun getAirportIcao(): String = airport.icao

    private var controllerInfo: ControllerInfoData? = null
    private var fetchCdmData = false
    private var cdmDepartures: List<CdmData>? = null

    init {
        // Periodic refresh of CDM data
        scope.runEvery(2.minutes) {
            if (fetchCdmData) {
                println("Refreshing CDM data for ${airport.icao}")
                refreshCdmData()
            }
        }

        // Periodic refresh of weather data
        scope.runEvery(15.minutes) {
            println("Refreshing weather data for ${airport.icao}")
            refreshWeatherData()
        }

        scope.runEvery(5.seconds) {
            val cutoff = NtpClock.now().minus(5.seconds)
            withStateLock {
                // Periodic cleanup of old cached weather data
                arrivalsCache = arrivalsCache.filter { it.lastTimestamp >= cutoff }
                departuresCache = departuresCache.filter { it.lastTimestamp >= cutoff }
            }
            onSequenceUpdated()
        }
    }

    fun getAvailableRunways(): Result<List<String>> =
        runCatching { state.availableRunways?.map { it.key } ?: emptyList() }

    fun setShowDepartures(showDepartures: Boolean) {
        this.fetchCdmData = showDepartures
        if (showDepartures) {
            refreshCdmData()
        } else {
            scope.launch {
                withStateLock {
                    cdmDepartures = emptyList()
                    state.departuresCache = emptyList()
                }
                onSequenceUpdated()
            }
        }
    }

    suspend fun onArrivalsUpdateFromAtcClient(arrivals: List<AtcClientArrivalData>) {
        handleArrivalsUpdateFromAtcClient(arrivals)
    }

    suspend fun onDeparturesUpdateFromAtcClient(departures: List<AtcClientDepartureData>) {
        handleDeparturesUpdateFromAtcClient(departures)
    }

    suspend fun onRunwaySelectionChanged(runways: List<AtcClientRunwaySelectionData>) {
        val map = runways.associate { it.runway to RunwayStatus(it.allowArrivals, it.allowDepartures) }
        dataUpdateListeners.forEach {
            it.onRunwayModesUpdated(airport.icao, map)
        }
        withStateLock { state.availableRunways = map }
    }

    private suspend fun handleArrivalsUpdateFromAtcClient(arrivals: List<AtcClientArrivalData>) {
        withStateLock {
            val runwayArrivalEvents = makeRunwayArrivalEvents(arrivals)
            val sequenceItems = runwayArrivalEvents.map {
                AircraftSequenceCandidate(
                    callsign = it.callsign,
                    preferredTime = it.estimatedTime,
                    landingIas = it.landingIas,
                    wakeCategory = it.wakeCategory,
                    assignedRunway = it.runway
                )
            }

            val aircraftToRemove = sequence.sequecencePlaces.map { it.item.id }
                .filter { it !in runwayArrivalEvents.map { it.callsign } }

            val cleanedSequence = AmanDmanSequenceService.removeFromSequence(sequence, *aircraftToRemove.toTypedArray())
            sequence = AmanDmanSequenceService.updateSequence(cleanedSequence, sequenceItems, minimumSpacingNm)

            arrivalsCache = runwayArrivalEvents.map { arrivalEvent ->
                val sequenceSchedule = sequence.sequecencePlaces.find { it.item.id == arrivalEvent.callsign }?.scheduledTime
                arrivalEvent.copy(
                    scheduledTime = sequenceSchedule ?: arrivalEvent.scheduledTime,
                    sequenceStatus = if (sequenceSchedule != null) SequenceStatus.OK else SequenceStatus.AWAITING_FOR_SEQUENCE,
                )
            }
        }
        onSequenceUpdated()
    }

    private suspend fun handleDeparturesUpdateFromAtcClient(departures: List<AtcClientDepartureData>) {
        withStateLock {
            departuresCache = makeDepartureEvents(departures)
        }
        onSequenceUpdated()
    }

    private fun makeRunwayArrivalEvents(arrivals: List<AtcClientArrivalData>): List<RunwayArrivalEvent> =
        arrivals.mapNotNull { arrival ->
            try {
                ArrivalEventService.createRunwayArrivalEvent(airport, arrival, state.weatherData)
            } catch (e: DescentTrajectoryException) {
                println("Failed to map arrival ${arrival.callsign}: ${e.message}")
                null
            }
        }

    private fun makeDepartureEvents(departures: List<AtcClientDepartureData>): List<DepartureEvent> =
        departures.mapNotNull { departure ->
            try {
                DepartureEventService.createRunwayDepartureEvent(departure, cdmDepartures)
            } catch (e: Exception) {
                println("Failed to map departure ${departure.callsign}: ${e.message}")
                null
            }
        }

    fun setMinimumSpacing(minimumSpacingDistanceNm: Double): Result<Unit> =
        runCatching {
            scope.launch {
                withStateLock {
                    state.minimumSpacingNm = minimumSpacingDistanceNm
                    state.sequence = AmanDmanSequenceService.reSchedule(state.sequence)
                }
                onSequenceUpdated()
                // Notify listeners of the spacing change
                dataUpdateListeners.forEach { listener ->
                    listener.onMinimumSpacingUpdated(airport.icao, minimumSpacingDistanceNm)
                }
            }
        }

    fun refreshWeatherData(): Result<Unit> {
        scope.launch {
            val weather = weatherDataRepository.getWindData(airport.location.lat, airport.location.lon)
            withStateLock { state.weatherData = weather }
            dataUpdateListeners.forEach { listener ->
                listener.onWeatherDataUpdated(airport.icao, weather)
            }
        }
        return Result.success(Unit)
    }

    fun refreshCdmData(): Result<Unit> {
        scope.launch {
            cdmDepartures = cdmClient.fetchCdmDepartures(airport.icao)
        }
        return Result.success(Unit)
    }

    fun suggestScheduledTime(timelineEvent: TimelineEvent, scheduledTime: Instant, newRunway: String?): Result<Unit> =
        runCatching {
            if (timelineEvent !is RunwayArrivalEvent) {
                throw IllegalArgumentException("Only RunwayArrivalEvent is supported at the moment")
            }
            scope.launch {
                withStateLock {
                    if (checkTimeSlotAvailable(timelineEvent, scheduledTime)) {
                        state.sequence = AmanDmanSequenceService.suggestScheduledTime(
                            state.sequence, timelineEvent.callsign, scheduledTime, state.minimumSpacingNm
                        )
                        if (newRunway != null) {
                            // TODO: implement runway change
                            //atcClient.assignRunway(timelineEvent.callsign, newRunway)
                        }
                    } else {
                        println("Time slot is not available for ${timelineEvent.callsign} at $scheduledTime")
                    }
                }
                onSequenceUpdated()
            }
        }

    fun reSchedule(callSign: String?): Result<Unit> =
        runCatching {
            scope.launch {
                withStateLock {
                    state.sequence = if (callSign == null) {
                        AmanDmanSequenceService.reSchedule(state.sequence)
                    } else {
                        AmanDmanSequenceService.removeFromSequence(state.sequence, callSign)
                    }
                }
                onSequenceUpdated()
            }
        }

    fun isTimeSlotAvailable(timelineEvent: TimelineEvent, scheduledTime: Instant): Result<Boolean> =
        runCatching { checkTimeSlotAvailable(timelineEvent, scheduledTime) }

    private fun checkTimeSlotAvailable(timelineEvent: TimelineEvent, scheduledTime: Instant): Boolean =
        if (timelineEvent !is RunwayArrivalEvent) false
        else AmanDmanSequenceService.isTimeSlotAvailable(state.sequence, timelineEvent, scheduledTime, state.minimumSpacingNm)

    fun getDescentProfileForCallsign(callsign: String): Result<List<TrajectoryPoint>?> =
        runCatching { ArrivalEventService.getDescentProfileForCallsign(callsign) }

    private suspend fun onSequenceUpdated() {
        withStateLock {
            val updatedArrivals = state.arrivalsCache
                .map { it.updateScheduledTime(state.sequence) }
                .sortedByDescending { it.scheduledTime }

            val departures = state.departuresCache

            val sequencedArrivals = if (updatedArrivals.size <= 1) {
                updatedArrivals
            } else {
                val pairedArrivals = updatedArrivals.zipWithNext { a, b ->
                    a.copy(
                        distanceToPreceding = a.remainingDistance - b.remainingDistance,
                        timeToPreceding = a.estimatedTime - b.estimatedTime,
                    )
                }
                (pairedArrivals + updatedArrivals.last()).reversed()
            }

            state.arrivalsCache = sequencedArrivals

            dataUpdateListeners.forEach { listener ->
                listener.onLiveData(airport.icao, sequencedArrivals + departures)
            }
        }
    }

    private fun RunwayArrivalEvent.updateScheduledTime(sequence: Sequence): RunwayArrivalEvent {
        val sequenceSchedule = sequence.sequecencePlaces.find { it.item.id == this.callsign }?.scheduledTime
        return this.copy(
            scheduledTime = sequenceSchedule ?: this.scheduledTime,
            sequenceStatus = if (sequenceSchedule != null) SequenceStatus.OK else SequenceStatus.AWAITING_FOR_SEQUENCE,
        )
    }

    private fun CoroutineScope.runEvery(n: Duration, codeBlock: suspend () -> Unit) =
        launch {
            while (isActive) {
                codeBlock()
                delay(n)
            }
        }

    suspend fun addDataUpdateListener(listener: DataUpdateListener) {
        dataUpdateListeners.add(listener)
        listener.onWeatherDataUpdated(airport.icao, state.weatherData)
        listener.onRunwayModesUpdated(airport.icao, state.availableRunways ?: emptyMap())
        listener.onMinimumSpacingUpdated(airport.icao, state.minimumSpacingNm)
        listener.onLiveData(airport.icao, state.arrivalsCache + state.departuresCache)
    }

    fun removeDataUpdateListener(listener: WsClientDataUpdateListener) {
        dataUpdateListeners.remove(listener)
    }
}