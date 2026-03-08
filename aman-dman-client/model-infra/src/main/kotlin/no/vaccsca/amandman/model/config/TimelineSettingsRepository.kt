package no.vaccsca.amandman.model.config

import com.fasterxml.jackson.core.util.DefaultIndenter
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import no.vaccsca.amandman.model.config.mapper.toDomain
import no.vaccsca.amandman.model.config.mapper.toYaml
import no.vaccsca.amandman.model.config.yaml.TimelineSettingsYaml
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

object TimelineSettingsRepository : TimelineSettingsStore {

    private val logger = LoggerFactory.getLogger(javaClass)

    private var timelines: Map<String, AirportTimelines>? = null

    private const val TIMELINES_FILE_PATH = "config/timelines.yaml"

    private val yamlMapper = YAMLMapper()
        .registerKotlinModule()
        .registerModule(JavaTimeModule())

    private val yamlPrettyPrinter = DefaultPrettyPrinter().apply {
        indentObjectsWith(DefaultIndenter("    ", DefaultIndenter.SYS_LF))
        indentArraysWith(DefaultIndenter("    ", DefaultIndenter.SYS_LF))
    }

    override fun getTimelines(reload: Boolean): Map<String, AirportTimelines> {
        if (timelines == null || reload) {
            loadTimelines()
        }
        return timelines!!
    }

    override fun saveAirportTimelines(airportIcao: String, timelines: AirportTimelines?) {
        val updatedTimelines = LinkedHashMap(getTimelines(reload = true))
        if (timelines == null) {
            updatedTimelines.remove(airportIcao)
        } else {
            updatedTimelines[airportIcao] = timelines
        }
        writeTimelineFile(updatedTimelines)
        this.timelines = updatedTimelines
    }

    private fun loadTimelines() {
        val file = File(TIMELINES_FILE_PATH)
        if (!file.exists()) {
            logger.info("Timeline config file not found at {}. Starting with empty saved timelines.", TIMELINES_FILE_PATH)
            timelines = emptyMap()
            return
        }

        timelines = yamlMapper.readValue<TimelineSettingsYaml>(file).toDomain()
    }

    private fun writeTimelineFile(updatedTimelines: Map<String, AirportTimelines>) {
        val targetFile = File(TIMELINES_FILE_PATH)
        targetFile.parentFile?.mkdirs()

        val tempFile = File.createTempFile("timelines", ".yaml.tmp", targetFile.parentFile)
        try {
            yamlMapper.writer(yamlPrettyPrinter).writeValue(tempFile, updatedTimelines.toYaml())
            Files.move(
                tempFile.toPath(),
                targetFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (e: Exception) {
            tempFile.delete()
            throw e
        }
    }
}
