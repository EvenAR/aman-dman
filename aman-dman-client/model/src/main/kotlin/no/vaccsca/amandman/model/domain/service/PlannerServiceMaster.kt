package no.vaccsca.amandman.model.domain.service

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Instant
import no.vaccsca.amandman.common.NtpClock
import no.vaccsca.amandman.model.data.integration.AtcClient
import no.vaccsca.amandman.model.data.repository.AircraftPerformanceData
import no.vaccsca.amandman.model.data.repository.CdmClient
import no.vaccsca.amandman.model.data.repository.WeatherDataRepository
import no.vaccsca.amandman.model.domain.enums.NonSequencedReason
import no.vaccsca.amandman.model.domain.exception.HasLandedException
import no.vaccsca.amandman.model.domain.exception.NoAssignedRunwayException
import no.vaccsca.amandman.model.domain.exception.ReachedEndOfRouteException
import no.vaccsca.amandman.model.domain.exception.UnknownAircraftTypeException
import no.vaccsca.amandman.model.domain.valueobjects.*
import no.vaccsca.amandman.model.domain.valueobjects.atcClient.AtcClientArrivalData
import no.vaccsca.amandman.model.domain.valueobjects.atcClient.AtcClientDepartureData
import no.vaccsca.amandman.model.domain.valueobjects.atcClient.ControllerInfoData
import no.vaccsca.amandman.model.domain.valueobjects.sequence.AircraftSequenceCandidate
import no.vaccsca.amandman.model.domain.valueobjects.sequence.Sequence
import no.vaccsca.amandman.model.domain.valueobjects.sequence.SequenceStatus
import no.vaccsca.amandman.model.domain.valueobjects.timelineEvent.DepartureEvent
import no.vaccsca.amandman.model.domain.valueobjects.timelineEvent.RunwayArrivalEvent
import no.vaccsca.amandman.model.domain.valueobjects.timelineEvent.TimelineEvent
import no.vaccsca.amandman.model.domain.valueobjects.weather.VerticalWeatherProfile
import org.slf4j.LoggerFactory
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * This is only used in the Master and Local instances of the application
 */
class PlannerServiceMaster(
    private val airport: Airport,
    private val weatherDataRepository: WeatherDataRepository,
    private val atcClient: AtcClient,
    private val cdmClient: CdmClient,
    private vararg val dataUpdateListeners: DataUpdateListener,
) : PlannerService(airport.icao) {

    private val logger = LoggerFactory.getLogger(javaClass)

    private val plannerState = PlannerState()
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, exception ->
        logger.error("Unhandled exception in coroutine", exception)
    })

    // Mutable state encapsulated in one object
    private data class PlannerState(
        var arrivalsCache: List<RunwayArrivalEvent> = emptyList(),
        var departuresCache: List<DepartureEvent> = emptyList(),
        var sequence: Sequence = Sequence(emptyList()),
        var minimumSpacingNm: Double = 3.0,
        var availableRunways: List<String>? = null,
        var weatherData: VerticalWeatherProfile? = null,
        var nonSequencedList: List<NonSequencedEvent> = emptyList(),
    )

    private suspend fun <T> withStateLock(block: PlannerState.() -> T): T =
        mutex.withLock { plannerState.block() }

    private var controllerInfo: ControllerInfoData? = null
    private var fetchCdmData = false
    private var cdmDepartures: List<CdmData>? = null

    init {
        // Periodic refresh of CDM data
        scope.runEvery(1.minutes) {
            if (fetchCdmData) {
                refreshCdmData()
            }
        }

        // Periodic refresh of weather data
        scope.runEvery(15.minutes) {
            refreshWeatherData()
        }

        // Periodic cleanup of old cached data
        scope.runEvery(1.seconds) {
            val cutoff = NtpClock.now().minus(5.seconds)
            withStateLock {
                arrivalsCache = arrivalsCache.filter { it.lastTimestamp >= cutoff }
                departuresCache = departuresCache.filter { it.lastTimestamp >= cutoff }
            }
            onSequenceUpdated()
        }
    }

    override fun stop() {
        scope.cancel()
        atcClient.stopCollectingMovementsFor(airportIcao)
    }

    override fun start() {
        // Notify listeners of the initial spacing
        scope.launch {
            withStateLock {
                dataUpdateListeners.forEach {
                    it.onMinimumSpacingUpdated(airportIcao, minimumSpacingNm)
                }
            }
        }

        // Start the ATC client data collection
        atcClient.start(
            onControllerInfoData = {
                controllerInfo = it
            }
        )
    }

    override fun getAvailableRunways(): Result<List<String>> =
        runCatching { plannerState.availableRunways ?: emptyList() }

    override fun setShowDepartures(showDepartures: Boolean) {
        this.fetchCdmData = showDepartures
        if (showDepartures) {
            refreshCdmData()
        } else {
            scope.launch {
                withStateLock {
                    cdmDepartures = emptyList()
                    plannerState.departuresCache = emptyList()
                }
                onSequenceUpdated()
            }
        }
    }

    override fun startDataCollection() {
        atcClient.collectDataFor(airportIcao,
            onArrivalsReceived = { arrivals ->
                scope.launch { handleArrivalsUpdateFromAtcClient(arrivals) }
            },
            onDeparturesReceived = { departures ->
                scope.launch { handleDeparturesUpdateFromAtcClient(departures) }
            },
            onRunwaySelectionChanged = { runways ->
                val map = runways.associate { it.runway to RunwayStatus(it.allowArrivals, it.allowDepartures) }
                dataUpdateListeners.forEach {
                    it.onRunwayModesUpdated(airportIcao, map)
                }
                scope.launch { withStateLock { availableRunways = runways.map { it.runway } } }
            },
        )
    }

    private suspend fun handleArrivalsUpdateFromAtcClient(arrivals: List<AtcClientArrivalData>) {
        withStateLock {
            val (runwayArrivalEvents, nonSeq) = makeRunwayArrivalEvents(arrivals)

            nonSequencedList = nonSeq

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

            val cleanedSequence = SequenceService.removeFromSequence(sequence, *aircraftToRemove.toTypedArray())
            sequence = SequenceService.updateSequence(cleanedSequence, sequenceItems, minimumSpacingNm)

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
            val departureEvents = makeDepartureEvents(departures)
            logger.debug("Converted ${departures.size} departures into ${departureEvents.size} departure events.")
            departuresCache = departureEvents
        }
        onSequenceUpdated()
    }

    private fun makeRunwayArrivalEvents(arrivals: List<AtcClientArrivalData>): Pair<List<RunwayArrivalEvent>, List<NonSequencedEvent>> {
        val runwayArrivalEvents = mutableListOf<RunwayArrivalEvent>()
        val nonSequencedEvents = mutableListOf<NonSequencedEvent>()

        arrivals.forEach { arrival ->
            try {
                val arrivalEvent = ArrivalEventService.createRunwayArrivalEvent(airport, arrival, plannerState.weatherData)
                runwayArrivalEvents.add(arrivalEvent)
            } catch (e: NoAssignedRunwayException) {
                nonSequencedEvents.add(
                    makeNonSequencedEvent(arrival, NonSequencedReason.NO_ASSIGNED_RUNWAY)
                )
            } catch (e: UnknownAircraftTypeException) {
                nonSequencedEvents.add(
                    makeNonSequencedEvent(arrival, NonSequencedReason.MISSING_PERFORMANCE_DATA)
                )
            } catch (e: ReachedEndOfRouteException) {
                nonSequencedEvents.add(
                    makeNonSequencedEvent(arrival, NonSequencedReason.EMPTY_ROUTE)
                )
            } catch (e: HasLandedException) {
                // Do nothing
            } catch (e: Exception) {
                logger.warn("Failed to create arrival event from ${arrival.callsign}: ${e.message}")
                nonSequencedEvents.add(
                    makeNonSequencedEvent(arrival, NonSequencedReason.UNKNOWN_ERROR)
                )
            }
        }

        return Pair(runwayArrivalEvents, nonSequencedEvents)
    }

    private fun makeNonSequencedEvent(arrival: AtcClientArrivalData, reason: NonSequencedReason): NonSequencedEvent  {
        val wtc = try {
            val performanceData = AircraftPerformanceData.get(arrival.icaoType)
            performanceData.takeOffWTC
        } catch (_: IllegalArgumentException) {
           null
        }

        return NonSequencedEvent(
            callsign = arrival.callsign,
            aircraftType = arrival.icaoType,
            wakeCategory = wtc,
            reason = reason,
        )
    }

    private fun makeDepartureEvents(departures: List<AtcClientDepartureData>): List<DepartureEvent> =
        departures.mapNotNull { departure ->
            try {
                DepartureEventService.createRunwayDepartureEvent(departure, cdmDepartures)
            } catch (e: Exception) {
                logger.warn("Failed to create departure event from ${departure.callsign}: ${e.message}")
                null
            }
        }

    override fun setMinimumSpacing(minimumSpacingDistanceNm: Double): Result<Unit> =
        runCatching {
            scope.launch {
                withStateLock {
                    plannerState.minimumSpacingNm = minimumSpacingDistanceNm
                    plannerState.sequence = SequenceService.reSchedule(plannerState.sequence)
                }
                onSequenceUpdated()
                // Notify listeners of the spacing change
                dataUpdateListeners.forEach { listener ->
                    listener.onMinimumSpacingUpdated(airportIcao, minimumSpacingDistanceNm)
                }
            }
        }

    override fun refreshWeatherData(): Result<Unit> {
        scope.launch {
            logger.info("Fetching weather data for $airportIcao")
            val weather = weatherDataRepository.getWindData(airport.location.lat, airport.location.lon)
            withStateLock { plannerState.weatherData = weather }
            dataUpdateListeners.forEach { listener ->
                listener.onWeatherDataUpdated(airportIcao, weather)
            }
            if (weather != null) {
                logger.info("Weather data for ${weather.time} updated for $airportIcao")
            } else {
                logger.warn("No weather data available for $airportIcao")
            }
        }
        return Result.success(Unit)
    }

    override fun refreshCdmData(): Result<Unit> {
        scope.launch {
            logger.info("Fetching CDM departures for $airportIcao")
            cdmDepartures = cdmClient.fetchCdmDepartures(airportIcao)
        }
        return Result.success(Unit)
    }

    override fun suggestScheduledTime(timelineEvent: TimelineEvent, scheduledTime: Instant, newRunway: String?): Result<Unit> =
        runCatching {
            if (timelineEvent !is RunwayArrivalEvent) {
                throw IllegalArgumentException("Only RunwayArrivalEvent is supported at the moment")
            }
            scope.launch {
                withStateLock {
                    if (checkTimeSlotAvailable(timelineEvent, scheduledTime)) {
                        plannerState.sequence = SequenceService.suggestScheduledTime(
                            plannerState.sequence, timelineEvent.callsign, scheduledTime, plannerState.minimumSpacingNm
                        )
                        if (newRunway != null) {
                            atcClient.assignRunway(timelineEvent.callsign, newRunway)
                        }
                    } else {
                        logger.info("Time slot is not available for ${timelineEvent.callsign} at $scheduledTime")
                    }
                }
                onSequenceUpdated()
            }
        }

    override fun reSchedule(callSign: String?): Result<Unit> =
        runCatching {
            scope.launch {
                withStateLock {
                    plannerState.sequence = if (callSign == null) {
                        SequenceService.reSchedule(plannerState.sequence)
                    } else {
                        SequenceService.removeFromSequence(plannerState.sequence, callSign)
                    }
                }
                onSequenceUpdated()
            }
        }

    override fun isTimeSlotAvailable(timelineEvent: TimelineEvent, scheduledTime: Instant): Result<Boolean> =
        runCatching { checkTimeSlotAvailable(timelineEvent, scheduledTime) }

    private fun checkTimeSlotAvailable(timelineEvent: TimelineEvent, scheduledTime: Instant): Boolean =
        if (timelineEvent !is RunwayArrivalEvent) false
        else SequenceService.isTimeSlotAvailable(plannerState.sequence, timelineEvent, scheduledTime, plannerState.minimumSpacingNm)

    override fun getDescentProfileForCallsign(callsign: String): Result<List<TrajectoryPoint>?> =
        runCatching { ArrivalEventService.getDescentProfileForCallsign(callsign) }

    private suspend fun onSequenceUpdated() {
        withStateLock {
            val updatedArrivals = plannerState.arrivalsCache
                .map { it.updateScheduledTime(plannerState.sequence) }
                .sortedByDescending { it.scheduledTime }

            val departures = plannerState.departuresCache

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

            plannerState.arrivalsCache = sequencedArrivals

            dataUpdateListeners.forEach { listener ->
                listener.onTimelineEventsUpdated(airportIcao, sequencedArrivals + departures)
                listener.onNonSequencedListUpdated(airportIcao, plannerState.nonSequencedList)
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
}