package no.vaccsca.amandman.model.cdm

import no.vaccsca.amandman.model.integration.IntegrationStatus

interface CdmProvider {
    fun fetchCdmDepartures(airportIcao: String): List<CdmData>?
    fun getIntegrationStatus(airportIcao: String): IntegrationStatus
}
