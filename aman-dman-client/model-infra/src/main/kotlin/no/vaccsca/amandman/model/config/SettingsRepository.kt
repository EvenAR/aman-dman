package no.vaccsca.amandman.model.config

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import no.vaccsca.amandman.model.airport.Airport
import no.vaccsca.amandman.model.config.mapper.toDomain
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
    private const val AIRPORTS_DIRECTORY_PATH = "config/airports"
    private const val AIRPORT_SCHEMA_FILE_NAME = "airport.schema.yaml"

    private val yamlMapper = createYamlMapper()

    internal fun createYamlMapper(): YAMLMapper = YAMLMapper().apply {
        registerKotlinModule()
        registerModule(JavaTimeModule())
    }

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
        airportData = loadAirportDataFromDirectory(File(AIRPORTS_DIRECTORY_PATH), yamlMapper)
        logger.info("Loaded airport config for: ${airportData!!.map { it.icao }.joinToString(", ")}")
        validateAirportMeteringTimelineLayouts()
    }

    internal fun loadAirportDataFromDirectory(directory: File, yamlMapper: YAMLMapper = createYamlMapper()): List<Airport> {
        require(directory.exists()) {
            "Airport config directory not found: ${directory.path}"
        }
        require(directory.isDirectory) {
            "Airport config path is not a directory: ${directory.path}"
        }

        return directory
            .listFiles { file ->
                file.isFile &&
                    file.extension.equals("yaml", ignoreCase = true) &&
                    file.name != AIRPORT_SCHEMA_FILE_NAME
            }
            ?.sortedBy { it.nameWithoutExtension.uppercase() }
            ?.map { file ->
                val icao = file.nameWithoutExtension.uppercase()
                yamlMapper.readValue<AirportDataJson>(file).toDomain(icao)
            }
            ?: emptyList()
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
