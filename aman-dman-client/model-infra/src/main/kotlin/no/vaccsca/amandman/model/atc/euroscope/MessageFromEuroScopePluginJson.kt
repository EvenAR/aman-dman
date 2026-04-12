package no.vaccsca.amandman.model.atc.euroscope

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

/**
 * Base class for messages received from the EuroScope plugin.
 * The `type` property is used to determine the specific subclass.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = PluginVersionJson::class, name = "pluginVersion"),
    JsonSubTypes.Type(value = ArrivalsUpdateFromEuroScopePluginJson::class, name = "arrivals"),
    JsonSubTypes.Type(value = DeparturesUpdateFromEuroScopePluginJson::class, name = "departures"),
    JsonSubTypes.Type(value = RunwayStatusesUpdateFromEuroScopePluginJson::class, name = "runwayStatuses"),
    JsonSubTypes.Type(value = ControllerInfoFromEuroScopePluginJson::class, name = "controllerInfo"),
    JsonSubTypes.Type(value = AircraftSelectionFromEuroScopePluginJson::class, name = "aircraftSelection"),
)
sealed class MessageFromEuroScopePluginJson()

data class PluginVersionJson(
    val version: String
) : MessageFromEuroScopePluginJson()

data class DeparturesUpdateFromEuroScopePluginJson(
    val outbounds: List<DepartureJson>
) : MessageFromEuroScopePluginJson()

data class ArrivalsUpdateFromEuroScopePluginJson(
    val inbounds: List<ArrivalJson>
) : MessageFromEuroScopePluginJson()

data class RunwayStatusesUpdateFromEuroScopePluginJson(
    val airports: Map<String, Map<String, RunwayStatusJson>>
) : MessageFromEuroScopePluginJson()

data class ControllerInfoFromEuroScopePluginJson(
    val me: ControllerInfoJson
) : MessageFromEuroScopePluginJson()

data class AircraftSelectionFromEuroScopePluginJson(
    val callsign: String
) : MessageFromEuroScopePluginJson()

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
    val isActive: Boolean,
)

data class ControllerInfoJson(
    val positionId: String?,
    val callsign: String?,
    val facilityType: Int?,
)