package no.vaccsca.amandman.model.config

import no.vaccsca.amandman.model.airport.Airport

interface SettingsProvider {
    fun getSettings(reload: Boolean = false): AmanDmanSettings
    fun getAirportData(reload: Boolean = false): List<Airport>
}
