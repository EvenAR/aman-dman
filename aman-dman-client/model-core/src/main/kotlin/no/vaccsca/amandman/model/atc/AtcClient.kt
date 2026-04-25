package no.vaccsca.amandman.model.atc

import no.vaccsca.amandman.model.integration.IntegrationStatus
import no.vaccsca.amandman.model.navigation.LatLng
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
    fun showPolygon(
        label: String,
        boundary: List<LatLng>,
        color: String,
        lineWidth: Int,
        fillColor: String? = null,
        durationSeconds: Int
    )
    fun getIntegrationStatus(airportIcao: String): IntegrationStatus

    override fun close()
}
