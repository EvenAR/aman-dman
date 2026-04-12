package no.vaccsca.amandman.model.planning

import no.vaccsca.amandman.model.integration.AirportDataSource
import org.slf4j.LoggerFactory

/**
 * Manages airport data sources (both local planners and remote mirrors).
 */
class AirportDataSourceManager {
    private val dataSources: MutableList<AirportDataSource> = mutableListOf()

    private val logger = LoggerFactory.getLogger(javaClass)

    fun register(dataSource: AirportDataSource) {
        dataSources.add(dataSource)
    }

    fun unregister(airportIcao: String) {
        val toRemove = dataSources.find { it.airportIcao == airportIcao }
        logger.info("Unregistering data source for ${toRemove?.airportIcao}")
        toRemove?.stop()
        dataSources.remove(toRemove)
    }

    fun getForAirport(airportIcao: String): AirportDataSource? {
        return dataSources.find { it.airportIcao == airportIcao }
    }

    fun getAllSequencePlanners(): List<SequencePlanner> {
        return dataSources.filterIsInstance<SequencePlanner>()
    }
}