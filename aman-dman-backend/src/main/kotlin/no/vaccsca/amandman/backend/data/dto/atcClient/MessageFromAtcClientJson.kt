package no.vaccsca.amandman.backend.data.dto.atcClient

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

/**
 * Base class for messages received from the EuroScope plugin.
 * The `type` property is used to determine the specific subclass.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = ArrivalsUpdateFromServerJson::class, name = "arrivals"),
    JsonSubTypes.Type(value = DeparturesUpdateFromServerJson::class, name = "departures"),
    JsonSubTypes.Type(value = RunwayStatusesUpdateFromServerJson::class, name = "runwayStatuses"),
    JsonSubTypes.Type(value = ControllerInfoFromServerJson::class, name = "controllerInfo"),
)
sealed class MessageFromServerJson()

data class DeparturesUpdateFromServerJson(
    val outbounds: List<DepartureJson>
) : MessageFromServerJson()

data class ArrivalsUpdateFromServerJson(
    val inbounds: List<ArrivalJson>
) : MessageFromServerJson()

data class RunwayStatusesUpdateFromServerJson(
    val airports: Map<String, Map<String, RunwayStatusJson>>
) : MessageFromServerJson()

data class ControllerInfoFromServerJson(
    val me: ControllerInfoJson
) : MessageFromServerJson()

data class RunwayStatusJson(
    val arrivals: Boolean,
    val departures: Boolean
)

data class DepartureJson(
    val callsign: String,
    val icaoType: String,
    val wakeCategory: Char,
    val estimatedDepartureTime: Long,
    val departureAirportIcao: String,
    val runway: String,
    val sid: String,
    val scratchPad: String?,
    val trackingController: String?,
)

data class ArrivalJson(
    val callsign: String,
    val icaoType: String,
    val assignedRunway: String?,
    val assignedStar: String?,
    val assignedDirect: String?,
    val trackingController: String?,
    val scratchPad: String?,
    val latitude: Double,
    val longitude: Double,
    val flightLevel: Int,
    val pressureAltitude: Int,
    val groundSpeed: Int,
    val track: Int,
    val route: List<FixPointJson>,
    val arrivalAirportIcao: String,
    val flightPlanTas: Int?,
)

data class FixPointJson(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val isPassed: Boolean,
)

data class ControllerInfoJson(
    val positionId: String?,
    val callsign: String?,
    val facilityType: Int?,
)