package no.vaccsca.amandman.backend.data.repository

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import no.vaccsca.amandman.backend.data.config.mapper.toDomain
import no.vaccsca.amandman.backend.data.config.yaml.AircraftPerformanceConfigYaml
import no.vaccsca.amandman.common.domain.valueobjects.AircraftPerformance
import java.io.File
import java.io.FileNotFoundException

object AircraftPerformanceData {

    // Path to the external config file
    private const val CONFIG_FILE_PATH = "config/aircraft-performance.yaml"

    private val yamlMapper = YAMLMapper().apply { registerKotlinModule() }

    private val all by lazy {
        loadSettingsFromFile(CONFIG_FILE_PATH)
    }

    fun get(icao: String): AircraftPerformance {
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