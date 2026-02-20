package no.vaccsca.amandman.model.cdm

interface CdmProvider {
    fun fetchCdmDepartures(airportIcao: String): List<CdmData>?
}
