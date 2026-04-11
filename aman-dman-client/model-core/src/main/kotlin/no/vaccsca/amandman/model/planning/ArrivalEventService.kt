package no.vaccsca.amandman.model.planning

import no.vaccsca.amandman.common.NtpClock
import no.vaccsca.amandman.model.aircraft.AircraftPerformanceProvider
import no.vaccsca.amandman.model.navigation.NavigationUtils.isBehind
import no.vaccsca.amandman.model.aircraft.AircraftPosition
import no.vaccsca.amandman.model.airport.Airport
import no.vaccsca.amandman.model.airport.RunwayThreshold
import no.vaccsca.amandman.model.atc.AtcClientArrivalData
import no.vaccsca.amandman.model.navigation.distanceTo
import no.vaccsca.amandman.model.timeline.event.timeline.RunwayArrivalEvent
import no.vaccsca.amandman.model.weather.SpatialWeatherField
import kotlin.time.Duration.Companion.seconds

/**
 * Maps data from the ATC client to domain objects used for planning.
 */
object ArrivalEventService {

    private val descentTrajectoryCache = mutableMapOf<String, List<TrajectoryPoint>>()

    fun createRunwayArrivalEvent(
        airport: Airport,
        arrival: AtcClientArrivalData,
        weatherField: SpatialWeatherField?,
        aircraftPerformanceProvider: AircraftPerformanceProvider
    ): RunwayArrivalEvent {
        val aircraftPerformance = try {
            aircraftPerformanceProvider.get(arrival.icaoType)
        } catch (_: IllegalArgumentException) {
            throw UnknownAircraftTypeException("Unsupported aircraft type: ${arrival.icaoType}")
        }

        if (arrival.assignedRunway == null) {
            throw NoAssignedRunwayException("Arrival has no runway assigned")
        }

        val runwayInfo = airport.runways[arrival.assignedRunway]
            ?: throw UnknownRunwayException("Assigned runway ${arrival.assignedRunway} not found at airport ${airport.icao}")

        val hasLanded = hasLanded(arrival.currentPosition, runwayInfo)
        if (hasLanded) {
            throw HasLandedException("Aircraft ${arrival.callsign} has already landed")
        }

        val trajectory = DescentTrajectoryService.calculateDescentTrajectory(
            currentPosition = arrival.currentPosition,
            assignedRunway = arrival.assignedRunway,
            remainingWaypoints = arrival.remainingWaypoints,
            spatialWeatherField = weatherField,
            assignedStar = arrival.assignedStar,
            aircraftPerformance = aircraftPerformance,
            flightPlanTas = arrival.flightPlanTas,
            airport = airport
        )

        if (trajectory == null) {
            throw ReachedEndOfRouteException("Failed to calculate descent trajectory for ${arrival.callsign}")
        }

        descentTrajectoryCache[arrival.callsign] = trajectory.trajectoryPoints

        if (trajectory.trajectoryPoints.isEmpty()) {
            throw EmptyTrajectoryException("The descent trajectory is empty")
        }

        val estimatedTime = NtpClock.now() + (trajectory.trajectoryPoints.firstOrNull()?.remainingTime ?: 0.seconds)
        val assignedDirectRoutingState = assignedDirectRoutingState(arrival, airport)

        // Check if assigned direct routing is to an IAF or IF
        val directFixInfo = arrival.assignedDirect?.let { directFix ->
            runwayInfo.arrivalFixExpectationsFor(arrival.assignedStar).find { fix ->
                fix.fixName.equals(directFix, ignoreCase = true)
            }
        }
        val assignedDirectIsIAF = directFixInfo?.role == no.vaccsca.amandman.model.airport.ArrivalFixRole.IAF
        val assignedDirectIsIF = directFixInfo?.role == no.vaccsca.amandman.model.airport.ArrivalFixRole.IF

        return RunwayArrivalEvent(
            callsign = arrival.callsign,
            icaoType = arrival.icaoType,
            flightLevel = arrival.currentPosition.flightLevel,
            groundSpeed = arrival.currentPosition.groundspeedKts,
            wakeCategory = aircraftPerformance.takeOffWTC,
            assignedStar = arrival.assignedStar,
            trackingController = arrival.trackingController,
            runway = arrival.assignedRunway,
            estimatedTime = estimatedTime,
            scheduledTime = estimatedTime,
            pressureAltitude = arrival.currentPosition.altitudeFt,
            airportIcao = arrival.arrivalAirportIcao,
            remainingDistance = trajectory.trajectoryPoints.first().remainingDistance,
            withinActiveAdvisoryHorizon = false,
            sequenceStatus = SequenceStatus.AWAITING_FOR_SEQUENCE,
            landingIas = aircraftPerformance.landingVat,
            scratchPad = arrival.scratchPad,
            assignedDirect = arrival.assignedDirect,
            assignedDirectIsIAF = assignedDirectIsIAF,
            assignedDirectIsIF = assignedDirectIsIF,
            assignedDirectIsActive = assignedDirectRoutingState.isActive,
            assignedDirectIsAfterFeederFix = assignedDirectRoutingState.isAfterFeederFix,
            lastTimestamp = NtpClock.now()
        )
    }

    fun getDescentProfileForCallsign(callsign: String): List<TrajectoryPoint>? {
        return descentTrajectoryCache[callsign]
    }

    private fun hasLanded(aircraftPosition: AircraftPosition, assignedRunwayThreshold: RunwayThreshold): Boolean {
        val distanceToRunway = aircraftPosition.latLng.distanceTo(assignedRunwayThreshold.latLng)
        val thresholdIsBehindAircraft = assignedRunwayThreshold.latLng.isBehind(aircraftPosition.latLng, aircraftPosition.trackDeg)

        return thresholdIsBehindAircraft && distanceToRunway < 3 && aircraftPosition.groundspeedKts < 160
    }
}

internal fun assignedDirectRoutingState(
    arrival: AtcClientArrivalData,
    airport: Airport,
): AssignedDirectRoutingState {
    val assignedDirect = arrival.assignedDirect?.uppercase() ?: return AssignedDirectRoutingState()
    val isActive = arrival.remainingWaypoints.any { waypoint ->
        waypoint.id.uppercase() == assignedDirect
    }
    if (!isActive) {
        return AssignedDirectRoutingState()
    }

    val directIndex = arrival.extractedRoute.indexOfFirst { point ->
        point.id.uppercase() == assignedDirect
    }
    if (directIndex <= 0) {
        return AssignedDirectRoutingState(isActive = true)
    }

    val feederFixes = airport.feederFixes.map { it.uppercase() }.toSet()
    val isAfterFeederFix = arrival.extractedRoute
        .take(directIndex)
        .any { point -> point.id.uppercase() in feederFixes }

    return AssignedDirectRoutingState(
        isActive = true,
        isAfterFeederFix = isAfterFeederFix,
    )
}

internal data class AssignedDirectRoutingState(
    val isActive: Boolean = false,
    val isAfterFeederFix: Boolean = false,
)
