package no.vaccsca.amandman.model.config

interface TimelineSettingsStore {
    fun getTimelines(reload: Boolean = false): Map<String, AirportTimelines>
    fun saveAirportTimelines(airportIcao: String, timelines: AirportTimelines?)
}
