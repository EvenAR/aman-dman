package no.vaccsca.amandman.model.config

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import no.vaccsca.amandman.model.airport.Airport
import no.vaccsca.amandman.model.config.mapper.toDomain
import no.vaccsca.amandman.model.config.yaml.ArrivalFixYamlFile
import no.vaccsca.amandman.model.config.yaml.AirportDataJson
import no.vaccsca.amandman.model.config.yaml.AmanDmanSettingsYaml
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileNotFoundException

object SettingsRepository : SettingsProvider {

    private val logger = LoggerFactory.getLogger(javaClass)

    private var settings: AmanDmanSettings? = null
    private var airportData: List<Airport>? = null

    private const val SETTINGS_FILE_PATH = "config/settings.yaml"
    private const val AIRPORTS_FILE_PATH = "config/airports.yaml"

    private val yamlMapper = YAMLMapper()
        .registerKotlinModule()
        .registerModule(JavaTimeModule())

    override fun getSettings(reload: Boolean): AmanDmanSettings {
        if (settings == null || reload) loadSettings()
        return settings!!
    }

    override fun getAirportData(reload: Boolean): List<Airport> {
        if (airportData == null || reload) loadAirportData()
        return airportData!!
    }

    fun getSettings(): AmanDmanSettings = getSettings(reload = false)

    fun getAirportData(): List<Airport> = getAirportData(reload = false)

    private fun loadSettings() {
        settings = readYamlFile<AmanDmanSettingsYaml>(SETTINGS_FILE_PATH).toDomain()
        validateAirportMeteringTimelineLayouts()
    }

    private fun loadAirportData() {
        val airportsJson = readYamlFile<AirportDataJson>(AIRPORTS_FILE_PATH)
        logger.info("Loaded airport config for: ${airportsJson.airports.keys.joinToString(", ")}")
        airportData = airportsJson.airports.mapNotNull { (icao, airportJson) ->
            try {
                val arrivalFixes = readYamlFile<ArrivalFixYamlFile>("config/arrival-fixes/$icao.yaml")
                logger.info("Loaded arrival fix data for airport $icao")
                airportJson.toDomain(icao, arrivalFixes)
            } catch (e: FileNotFoundException) {
                logger.warn("Arrival fix data file not found for airport $icao. Trajectory calculations will have reduced accuracy.")
                airportJson.toDomain(icao, ArrivalFixYamlFile(emptyList()))
            } catch (e: Exception) {
                logger.error("Error loading arrival fix data for airport $icao: ${e.message}")
                airportJson.toDomain(icao, ArrivalFixYamlFile(emptyList()))
            }
        }
        validateAirportMeteringTimelineLayouts()
    }

    private fun validateAirportMeteringTimelineLayouts() {
        val loadedSettings = settings ?: return
        val loadedAirportData = airportData ?: return
        val availableArrivalLayouts = loadedSettings.arrivalLabelLayouts.keys

        loadedAirportData.forEach { airport ->
            val layoutId = airport.feederFixTimelineArrivalLabelLayoutId ?: return@forEach
            require(layoutId in availableArrivalLayouts) {
                "Airport ${airport.icao} uses unknown feederFixTimelineArrivalLabelLayoutId '$layoutId'. " +
                    "Available arrival layouts: ${availableArrivalLayouts.sorted().joinToString(", ")}"
            }
        }
    }

    private inline fun <reified T> readYamlFile(filePath: String): T {
        val localFile = File(filePath)
        if (!localFile.exists()) {
            throw FileNotFoundException("YAML file not found: $filePath")
        }
        return yamlMapper.readValue(localFile)
    }
}
