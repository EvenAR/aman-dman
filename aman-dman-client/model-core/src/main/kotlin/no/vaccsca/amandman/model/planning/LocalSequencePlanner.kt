package no.vaccsca.amandman.model.planning

import kotlinx.coroutines.*
import kotlinx.datetime.Instant
import no.vaccsca.amandman.common.NtpClock
import no.vaccsca.amandman.model.aircraft.AircraftPerformanceProvider
import no.vaccsca.amandman.model.airport.Airport
import no.vaccsca.amandman.model.airport.RunwayStatus
import no.vaccsca.amandman.model.atc.*
import no.vaccsca.amandman.model.cdm.CdmData
import no.vaccsca.amandman.model.cdm.CdmProvider
import no.vaccsca.amandman.model.config.PlanningSettings
import no.vaccsca.amandman.model.integration.AirportIntegrationStatuses
import no.vaccsca.amandman.model.integration.IntegrationKind
import no.vaccsca.amandman.model.integration.IntegrationStatus
import no.vaccsca.amandman.model.integration.IntegrationStatusState
import no.vaccsca.amandman.model.sharedstate.DataUpdateListener
import no.vaccsca.amandman.model.sharedstate.MasterSlaveSharedState
import no.vaccsca.amandman.model.timeline.NonSequencedReason
import no.vaccsca.amandman.model.timeline.event.NonSequencedEvent
import no.vaccsca.amandman.model.timeline.event.timeline.DepartureEvent
import no.vaccsca.amandman.model.timeline.event.timeline.RunwayArrivalEvent
import no.vaccsca.amandman.model.timeline.event.timeline.TimelineEvent
import no.vaccsca.amandman.model.weather.SpatialWeatherField
import no.vaccsca.amandman.model.weather.WindProfileProvider
import no.vaccsca.amandman.model.weather.WindProfileResult
import org.slf4j.LoggerFactory
import java.util.concurrent.Executors
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Local sequence planner that performs actual arrival/departure planning.
 * Uses a single-threaded executor to avoid concurrency issues with state.
 */
class LocalSequencePlanner(
    private val airport: Airport,
    private val windProfileProvider: WindProfileProvider,
    private val atcClient: AtcClient,
    private val cdmClient: CdmProvider,
    private val sharedState: MasterSlaveSharedState? = null,
    private val aircraftPerformanceProvider: AircraftPerformanceProvider,
    private val planningSettings: PlanningSettings,
    private vararg val dataUpdateListeners: DataUpdateListener,
) : SequencePlanner {

    override val airportIcao: String = airport.icao

    private val logger = LoggerFactory.getLogger(javaClass)

    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "Planner-$airportIcao") }
    private val scope = CoroutineScope(
        SupervisorJob() + executor.asCoroutineDispatcher() + CoroutineExceptionHandler { _, e ->
            logger.error("Unhandled exception in planner coroutine", e)
        }
    )

    private val feederFixTimingService = FeederFixTimingService(
        DynamicFromTrajectoryFeederFixTimingStrategy(planningSettings.feederFixMaxAbeamDistanceNm)
    )

    private var arrivalsCache: List<RunwayArrivalEvent> = emptyList()
    private var extractedRoutesByCallsign: Map<String, List<ExtractedRoutePoint>> = emptyMap()
    private var departuresCache: List<DepartureEvent> = emptyList()
    private var sequenceSystems: List<AmanSequenceSystem> = airport.independentRunwaySystems.map { AmanSequenceSystem(it, emptyList()) }
    private var minimumSpacingNm: Double = 3.0
    private var availableRunways: List<String> = emptyList()
    private var weatherField: SpatialWeatherField? = null
    private var nonSequencedList: List<NonSequencedEvent> = emptyList()
    private var controllerInfo: ControllerInfoData? = null
    private var fetchCdmData = false
    private var cdmDepartures: List<CdmData>? = null

    init {
        scope.runEvery(1.minutes) { if (fetchCdmData) refreshCdmData() }
        scope.runEvery(15.minutes) { refreshWeatherData() }
        scope.runEvery(1.seconds) {
            val cutoff = NtpClock.now().minus(5.seconds)
            arrivalsCache = arrivalsCache.filter { it.lastTimestamp >= cutoff }
            departuresCache = departuresCache.filter { it.lastTimestamp >= cutoff }
            notifyListeners()
        }
    }

    override fun stop() {
        scope.cancel()
        executor.shutdown()
        atcClient.stopCollectingMovementsFor(airportIcao)
    }

    override fun start() {
        scope.launch {
            dataUpdateListeners.forEach { it.onMinimumSpacingUpdated(airportIcao, minimumSpacingNm) }
            publishFeederFixState(arrivalsCache)
        }
        atcClient.start { controllerInfo = it }
    }

    override fun getAvailableRunways(): List<String> = availableRunways

    override fun getIntegrationStatuses(): AirportIntegrationStatuses {
        val cdmStatus = if (fetchCdmData) {
            cdmClient.getIntegrationStatus(airportIcao)
        } else {
            IntegrationStatus(state = IntegrationStatusState.ERROR, relevant = false, detail = "CDM disabled")
        }

        val serverStatus = sharedState?.getIntegrationStatus(airportIcao)
            ?: IntegrationStatus(state = IntegrationStatusState.ERROR, relevant = false, detail = "Server not used")

        return AirportIntegrationStatuses(
            byKind = mapOf(
                IntegrationKind.ATC to atcClient.getIntegrationStatus(airportIcao),
                IntegrationKind.CDM to cdmStatus,
                IntegrationKind.SERVER to serverStatus,
                IntegrationKind.MET to windProfileProvider.getIntegrationStatus(airportIcao),
            )
        )
    }

    override fun setShowDepartures(showDepartures: Boolean) {
        this.fetchCdmData = showDepartures
        if (showDepartures) {
            refreshCdmData()
        } else {
            scope.launch {
                cdmDepartures = emptyList()
                departuresCache = emptyList()
                notifyListeners()
            }
        }
    }

    override fun startDataCollection() {
        atcClient.collectDataFor(
            airportIcao,
            onArrivalsReceived = { scope.launch { handleArrivalsUpdate(it) } },
            onDeparturesReceived = { scope.launch { handleDeparturesUpdate(it) } },
            onRunwaySelectionChanged = { runways ->
                val map = runways.associate { it.runway to RunwayStatus(it.allowArrivals, it.allowDepartures) }
                dataUpdateListeners.forEach { it.onRunwayModesUpdated(airportIcao, map) }
                scope.launch { availableRunways = runways.map { it.runway } }
            },
        )
    }

    private fun handleArrivalsUpdate(arrivals: List<AtcClientArrivalData>) {
        extractedRoutesByCallsign = arrivals.associate { arrival ->
            arrival.callsign to arrival.extractedRoute
        }

        val (runwayArrivalEvents, nonSeq) = makeRunwayArrivalEvents(arrivals)
        nonSequencedList = nonSeq

        val sequenceItems = runwayArrivalEvents.map {
            AircraftSequenceCandidate(
                callsign = it.callsign,
                preferredTime = it.estimatedTime,
                landingIas = it.landingIas,
                wakeCategory = it.wakeCategory,
                runway = it.runway
            )
        }

        val arrivalCallsigns = runwayArrivalEvents.map { it.callsign }.toSet()
        sequenceSystems = sequenceSystems.map { sequence ->
            val aircraftToRemove = sequence.places
                .map { it.item.id }
                .filter { it !in arrivalCallsigns }

            val cleanedSequence = SequenceService.removeFromSequence(
                sequence.places,
                *aircraftToRemove.toTypedArray()
            )

            sequence.copy(
                places = SequenceService.updateSequence(
                    currentSequence = cleanedSequence,
                    candidates = sequenceItems.filter { it.runway in sequence.runwaySystem },
                    config = SequencingOptions(
                        minimumSeparationNm = minimumSpacingNm,
                        sequencingHorizon = airport.sequencingHorizon,
                        lockedHorizon = airport.lockedHorizon
                    )
                )
            )
        }

        arrivalsCache = runwayArrivalEvents.map { arrivalEvent ->
            val sequenceSchedule = sequenceSystems.flatMap { it.places }.find { it.item.id == arrivalEvent.callsign }?.scheduledTime
            arrivalEvent.copy(
                scheduledTime = sequenceSchedule ?: arrivalEvent.scheduledTime,
                sequenceStatus = if (sequenceSchedule != null) SequenceStatus.OK else SequenceStatus.AWAITING_FOR_SEQUENCE,
            )
        }
        notifyListeners()
    }

    private fun handleDeparturesUpdate(departures: List<AtcClientDepartureData>) {
        val departureEvents = makeDepartureEvents(departures)
        logger.debug("Converted ${departures.size} departures into ${departureEvents.size} departure events.")
        departuresCache = departureEvents
        notifyListeners()
    }

    private fun makeRunwayArrivalEvents(arrivals: List<AtcClientArrivalData>): Pair<List<RunwayArrivalEvent>, List<NonSequencedEvent>> {
        val runwayArrivalEvents = mutableListOf<RunwayArrivalEvent>()
        val nonSequencedEvents = mutableListOf<NonSequencedEvent>()

        arrivals.forEach { arrival ->
            try {
                val arrivalEvent = ArrivalEventService.createRunwayArrivalEvent(
                    airport = airport,
                    arrival = arrival,
                    weatherField = weatherField,
                    aircraftPerformanceProvider = aircraftPerformanceProvider,
                    useGroundspeedOnDirectRouting = planningSettings.useGroundspeedOnDirectRouting,
                )
                runwayArrivalEvents.add(arrivalEvent)
            } catch (_: NoAssignedRunwayException) {
                nonSequencedEvents.add(makeNonSequencedEvent(arrival, NonSequencedReason.NO_ASSIGNED_RUNWAY))
            } catch (_: UnknownAircraftTypeException) {
                nonSequencedEvents.add(makeNonSequencedEvent(arrival, NonSequencedReason.MISSING_PERFORMANCE_DATA))
            } catch (_: ReachedEndOfRouteException) {
                nonSequencedEvents.add(makeNonSequencedEvent(arrival, NonSequencedReason.EMPTY_ROUTE))
            } catch (_: HasLandedException) {
                // Do nothing
            } catch (e: Exception) {
                logger.warn("Failed to create arrival event from ${arrival.callsign}: ${e.message}")
                nonSequencedEvents.add(makeNonSequencedEvent(arrival, NonSequencedReason.UNKNOWN_ERROR))
            }
        }

        return Pair(runwayArrivalEvents, nonSequencedEvents)
    }

    private fun makeNonSequencedEvent(arrival: AtcClientArrivalData, reason: NonSequencedReason): NonSequencedEvent {
        val wtc = try {
            aircraftPerformanceProvider.get(arrival.icaoType).takeOffWTC
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

    override fun setMinimumSpacing(minimumSpacingDistanceNm: Double) {
        scope.launch {
            minimumSpacingNm = minimumSpacingDistanceNm
            sequenceSystems = sequenceSystems.map { it.copy(places = emptyList()) }
            notifyListeners()
            dataUpdateListeners.forEach { it.onMinimumSpacingUpdated(airportIcao, minimumSpacingDistanceNm) }
        }
    }

    override fun refreshWeatherData() {
        scope.launch {
            logger.info("Fetching weather data for $airportIcao")
            when (
                val result = windProfileProvider.getWeatherDataAroundAirport(
                    airportIcao = airportIcao,
                    latitude = airport.location.lat,
                    longitude = airport.location.lon,
                    weatherFetchRadiusNm = airport.weatherFetchRadiusNm
                )
            ) {
                is WindProfileResult.Success -> {
                    weatherField = result.weatherField
                    dataUpdateListeners.forEach { it.onWeatherDataUpdated(airportIcao, result.displayProfile) }
                    logger.info("Weather data for ${result.displayProfile.time} updated for $airportIcao")
                }
                is WindProfileResult.Failure -> {
                    weatherField = null
                    dataUpdateListeners.forEach { it.onWeatherDataUpdated(airportIcao, null) }
                    logger.warn("Failed to fetch weather data for $airportIcao: ${result.error.message}")
                }
            }
        }
    }

    override fun refreshCdmData() {
        scope.launch {
            logger.info("Fetching CDM departures for $airportIcao")
            cdmDepartures = cdmClient.fetchCdmDepartures(airportIcao)
        }
    }

    override fun suggestScheduledTime(timelineEvent: TimelineEvent, scheduledTime: Instant, newRunway: String?) {
        if (timelineEvent !is RunwayArrivalEvent) {
            throw IllegalArgumentException("Only RunwayArrivalEvent is supported at the moment")
        }
        scope.launch {
            sequenceSystems = sequenceSystems.map { sequence ->
                if (newRunway != null && newRunway !in sequence.runwaySystem) {
                    return@map sequence
                }
                if (newRunway == null && !sequence.places.any { it.item.id == timelineEvent.callsign }) {
                    return@map sequence
                }
                if (sequence.checkTimeSlotAvailable(timelineEvent, scheduledTime, newRunway)) {
                    val updatedPlaces = SequenceService.suggestScheduledTime(
                        sequence.places, timelineEvent.callsign, scheduledTime, minimumSpacingNm
                    )
                    if (newRunway != null) {
                        atcClient.assignRunway(timelineEvent.callsign, newRunway)
                    }
                    sequence.copy(places = updatedPlaces)
                } else {
                    logger.warn("Cannot suggest new scheduled time $scheduledTime for ${timelineEvent.callsign} due to time slot unavailability.")
                    sequence
                }
            }
            notifyListeners()
        }
    }

    override fun reSchedule(callSign: String?) {
        scope.launch {
            sequenceSystems = sequenceSystems.map { sequence ->
                val updatedPlaces = if (callSign == null) {
                    SequenceService.reSchedule(sequence.places)
                } else {
                    SequenceService.removeFromSequence(sequence.places, callSign)
                }
                sequence.copy(places = updatedPlaces)
            }
            notifyListeners()
        }
    }

    override fun isTimeSlotAvailable(timelineEvent: TimelineEvent, scheduledTime: Instant, runway: String): Boolean =
        sequenceSystems.any { it.checkTimeSlotAvailable(timelineEvent, scheduledTime, runway) }

    private fun AmanSequenceSystem.checkTimeSlotAvailable(timelineEvent: TimelineEvent, scheduledTime: Instant, newRunway: String? = null): Boolean {
        if (timelineEvent !is RunwayArrivalEvent) return false
        val sequenceCandidate = AircraftSequenceCandidate(
            callsign = timelineEvent.callsign,
            preferredTime = timelineEvent.estimatedTime,
            landingIas = timelineEvent.landingIas,
            wakeCategory = timelineEvent.wakeCategory,
            runway = newRunway ?: timelineEvent.runway
        )
        return SequenceService.isTimeSlotAvailable(this.places, sequenceCandidate, scheduledTime, minimumSpacingNm)
    }


    override fun getDescentProfileForCallsign(callsign: String): List<TrajectoryPoint>? =
        ArrivalEventService.getDescentProfileForCallsign(callsign)

    private fun notifyListeners() {
        val allScheduledTimes = sequenceSystems
            .flatMap { it.places }
            .associate { it.item.id to it.scheduledTime }

        val updatedArrivals = arrivalsCache
            .map {
                it.copy(
                    scheduledTime = allScheduledTimes[it.callsign] ?: it.scheduledTime,
                    sequenceStatus = if (allScheduledTimes.containsKey(it.callsign)) SequenceStatus.OK else SequenceStatus.AWAITING_FOR_SEQUENCE,
                )
            }
            .sortedByDescending { it.scheduledTime }

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

        arrivalsCache = sequencedArrivals

        dataUpdateListeners.forEach { listener ->
            listener.onTimelineEventsUpdated(airportIcao, sequencedArrivals + departuresCache)
            listener.onNonSequencedListUpdated(airportIcao, nonSequencedList)
        }
        publishFeederFixState(sequencedArrivals)
    }

    private fun publishFeederFixState(arrivals: List<RunwayArrivalEvent>) {
        val feederFixState = feederFixTimingService.buildState(
            airport = airport,
            arrivals = arrivals,
            trajectoryProvider = ArrivalEventService::getDescentProfileForCallsign,
            extractedRouteProvider = extractedRoutesByCallsign::get,
        )
        dataUpdateListeners.forEach { listener ->
            listener.onFeederFixStateUpdated(airportIcao, feederFixState)
        }
    }

    private fun CoroutineScope.runEvery(interval: Duration, block: suspend () -> Unit) = launch {
        while (isActive) {
            block()
            delay(interval)
        }
    }
}
