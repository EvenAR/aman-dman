package no.vaccsca.amandman.model.atc

import no.vaccsca.amandman.model.atc.AtcClientArrivalData
import no.vaccsca.amandman.model.atc.AtcClientDepartureData
import no.vaccsca.amandman.model.atc.AtcClientRunwaySelectionData
import no.vaccsca.amandman.model.atc.ControllerInfoData
import no.vaccsca.amandman.model.integration.IntegrationStatus
import java.io.Closeable

interface AtcClient : Closeable {
    fun start(
        onControllerInfoData: (ControllerInfoData) -> Unit
    )

    fun collectDataFor(
        airportIcao: String,
        onArrivalsReceived: (List<AtcClientArrivalData>) -> Unit,
        onDeparturesReceived: (List<AtcClientDepartureData>) -> Unit,
        onRunwaySelectionChanged: (List<AtcClientRunwaySelectionData>) -> Unit,
    )

    fun stopCollectingMovementsFor(airportIcao: String)
    fun assignRunway(callsign: String, newRunway: String)
    fun getIntegrationStatus(airportIcao: String): IntegrationStatus

    override fun close()
}
