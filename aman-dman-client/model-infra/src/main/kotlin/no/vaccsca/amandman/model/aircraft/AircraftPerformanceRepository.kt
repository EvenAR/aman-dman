package no.vaccsca.amandman.model.aircraft

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import no.vaccsca.amandman.model.config.mapper.toDomain
import no.vaccsca.amandman.model.config.yaml.AircraftPerformanceConfigYaml
import java.io.File
import java.io.FileNotFoundException

object AircraftPerformanceData : AircraftPerformanceProvider {

    // Path to the external config file
    private const val CONFIG_FILE_PATH = "config/aircraft-performance.yaml"

    private val yamlMapper = YAMLMapper().apply { registerKotlinModule() }

    private val all by lazy {
        loadSettingsFromFile(CONFIG_FILE_PATH)
    }

    override fun get(icao: String): AircraftPerformance {
        return all[icao] ?: throw IllegalArgumentException("No aircraft performance data for ICAO $icao")
    }

    fun loadSettingsFromFile(filePath: String): Map<String, AircraftPerformance> {
        val file = File(filePath)
        if (!file.exists()) {
            throw FileNotFoundException("Settings file not found at: $filePath")
        }

        return yamlMapper.readValue<AircraftPerformanceConfigYaml>(file).aircraft.mapValues { it.value.toDomain() }
    }
}
