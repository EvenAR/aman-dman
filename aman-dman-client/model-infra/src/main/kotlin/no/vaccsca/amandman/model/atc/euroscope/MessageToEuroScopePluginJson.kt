package no.vaccsca.amandman.model.atc.euroscope


/**
 * Base class for messages sent to the EuroScope bridge plugin via JSON.
 */
sealed class MessageToEuroScopePluginJson(
    val type: String
)

data class RegisterAirportJson(
    val icao: String,
) : MessageToEuroScopePluginJson("registerAirport")

data class UnregisterAirportJson(
    val icao: String,
) : MessageToEuroScopePluginJson("unregisterAirport")

data class AssignRunwayJson(
    val callsign: String,
    val runway: String,
) : MessageToEuroScopePluginJson("assignRunway")