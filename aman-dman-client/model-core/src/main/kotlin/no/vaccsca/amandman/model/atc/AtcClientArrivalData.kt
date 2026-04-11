package no.vaccsca.amandman.model.atc

import kotlinx.datetime.Instant
import no.vaccsca.amandman.model.aircraft.AircraftPosition
import no.vaccsca.amandman.model.navigation.LatLng
import no.vaccsca.amandman.model.navigation.Waypoint

/**
 * All data about an arrival received from the ATC client.
 *
 * @param callsign The callsign of the aircraft.
 * @param icaoType The ICAO type of the aircraft.
 * @param wakeCategory The wake turbulence category of the aircraft.
 * @param assignedStar The name of the STAR assigned to the aircraft, if any.
 * @param assignedDirect The direct waypoint assigned to the aircraft, if any.
 * @param trackingController The position ID of the controller currently tracking.
 * @param scratchPad The scratchpad text for the aircraft, if any.
 * @param currentPosition The current position of the aircraft.
 * @param extractedRoute The full extracted route from the ATC client, including already passed/skipped points.
 * @param remainingWaypoints All remaining waypoints between the aircraft position and the runway threshold. Runway threshold, airport or current position should not be included.
 * @param assignedRunway The runway assigned to the aircraft, if any.
 * @param arrivalAirportIcao The ICAO code of the arrival airport.
 * @param flightPlanTas The true airspeed (TAS) from the flight plan,
 * @param recvTimestamp The timestamp of when the data was received.
 */
data class AtcClientArrivalData(
    val callsign: String,
    val icaoType: String,
    val assignedStar: String?,
    val assignedDirect: String?,
    val trackingController: String?,
    val scratchPad: String?,
    val currentPosition: AircraftPosition,
    val extractedRoute: List<ExtractedRoutePoint> = emptyList(),
    val remainingWaypoints: List<Waypoint>,
    val assignedRunway: String?,
    val arrivalAirportIcao: String,
    val flightPlanTas: Int?,
    val recvTimestamp: Instant,
)

data class ExtractedRoutePoint(
    val id: String,
    val latLng: LatLng,
    val isPassed: Boolean,
)
