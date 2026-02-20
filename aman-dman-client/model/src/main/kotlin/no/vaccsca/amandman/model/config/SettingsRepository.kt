package no.vaccsca.amandman.model.config

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import no.vaccsca.amandman.model.config.mapper.toDomain
import no.vaccsca.amandman.model.config.yaml.AirportDataJson
import no.vaccsca.amandman.model.config.yaml.AmanDmanSettingsYaml
import no.vaccsca.amandman.model.config.yaml.StarYamlFile
import no.vaccsca.amandman.model.airport.Airport
import no.vaccsca.amandman.model.config.AmanDmanSettings
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileNotFoundException
import kotlin.math.log

object SettingsRepository {

    private val logger = LoggerFactory.getLogger(javaClass)

    private var settings: AmanDmanSettings? = null
    private var airportData: List<Airport>? = null

    private const val SETTINGS_FILE_PATH = "config/settings.yaml"
    private const val AIRPORTS_FILE_PATH = "config/airports.yaml"

    private val yamlMapper = YAMLMapper().apply { registerKotlinModule() }

    fun getSettings(reload: Boolean = false): AmanDmanSettings {
        if (settings == null || reload) loadSettings()
        return settings!!
    }

    fun getAirportData(reload: Boolean = false): List<Airport> {
        if (airportData == null || reload) loadAirportData()
        return airportData!!
    }

    private fun loadSettings() {
        settings = readYamlFile<AmanDmanSettingsYaml>(SETTINGS_FILE_PATH).toDomain()
    }

    private fun loadAirportData() {
        val airportsJson = readYamlFile<AirportDataJson>(AIRPORTS_FILE_PATH)
        logger.info("Loaded airport config for: ${airportsJson.airports.keys.joinToString(", ")}")
        airportData = airportsJson.airports.mapNotNull { (icao, airportJson) ->
            try {
                val stars = readYamlFile<StarYamlFile>("config/stars/$icao.yaml")
                logger.info("Loaded STAR data for airport $icao")
                airportJson.toDomain(icao, stars)
            } catch (e: FileNotFoundException) {
                logger.warn("STAR data file not found for airport $icao. Trajectory calculations will have reduced accuracy.")
            } catch (e: Exception) {
                logger.error("Error loading STAR data for airport $icao: ${e.message}")
            }
            airportJson.toDomain(icao, StarYamlFile(emptyList()))
        }
    }

    private inline fun <reified T> readYamlFile(filePath: String): T {
        val localFile = File(filePath)
        if (!localFile.exists()) {
            throw FileNotFoundException("YAML file not found: $filePath")
        }
        return yamlMapper.readValue(localFile)
    }

    private fun saveSettings() {
        val yamlFile = File(SETTINGS_FILE_PATH)
        yamlFile.writeText(yamlMapper.writeValueAsString(settings))
    }
}
