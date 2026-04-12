package no.vaccsca.amandman.model.integration
/**
 * Base interface for any airport data source.
 * Provides lifecycle management and data collection capabilities.
 * Both local planners and remote data mirrors implement this.
 */
interface AirportDataSource {
    val airportIcao: String
    val isReadOnly: Boolean

    fun start()
    fun stop()
    fun startDataCollection()
    fun getIntegrationStatuses(): AirportIntegrationStatuses
}
