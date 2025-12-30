package no.vaccsca.amandman.backend.data.repository

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import no.vaccsca.amandman.backend.data.config.mapper.toDomain
import no.vaccsca.amandman.backend.data.config.yaml.AirportDataJson
import no.vaccsca.amandman.backend.data.config.yaml.AmanDmanSettingsYaml
import no.vaccsca.amandman.backend.data.config.yaml.StarYamlFile
import no.vaccsca.amandman.common.domain.valueobjects.Airport
import no.vaccsca.amandman.common.domain.valueobjects.AmanDmanSettings
import java.io.File
import java.io.FileNotFoundException

object SettingsRepository {

    private var settings: AmanDmanSettings? = null
    private var airportData: List<Airport>? = null

    private const val SETTINGS_ROOT = "C:\\Users\\Even\\Documents\\hobbyprosjekter\\aman-dman\\aman-dman2\\aman-dman-backend\\config"

    private const val SETTINGS_FILE_PATH = "$SETTINGS_ROOT/settings.yaml"
    private const val AIRPORTS_FILE_PATH = "$SETTINGS_ROOT/airports.yaml"
    private const val STARS_FOLDER_PATH = "$SETTINGS_ROOT/stars"

    private val yamlMapper = YAMLMapper().apply { registerKotlinModule() }

    init {
        loadSettings()
        loadAirportData()
    }

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
        airportData = airportsJson.airports.map { (icao, airportJson) ->
            val stars = readYamlFile<StarYamlFile>("$STARS_FOLDER_PATH/$icao.yaml")
            airportJson.toDomain(icao, stars)
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
