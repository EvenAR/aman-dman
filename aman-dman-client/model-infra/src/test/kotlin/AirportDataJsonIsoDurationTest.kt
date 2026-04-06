import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.fasterxml.jackson.databind.exc.InvalidFormatException
import no.vaccsca.amandman.model.config.yaml.AirportDataJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AirportDataJsonIsoDurationTest {

    private val yamlMapper = YAMLMapper()
        .registerKotlinModule()
        .registerModule(JavaTimeModule())

    @Test
    fun `should parse transit duration minutes`() {
        val yaml = """
            location:
              latitude: 60.0
              longitude: 11.0
            runwayThresholds: {}
            feederFixTransitTimesMinutes:
              FIX1:
                "01L": 8
        """.trimIndent()

        val parsed = yamlMapper.readValue<AirportDataJson>(yaml)
        val minutes = parsed.feederFixTransitTimesMinutes?.get("FIX1")?.get("01L")
        assertEquals(8, minutes)
    }

    @Test
    fun `should reject non-integer transit duration minutes`() {
        val yaml = """
            location:
              latitude: 60.0
              longitude: 11.0
            runwayThresholds: {}
            feederFixTransitTimesMinutes:
              FIX1:
                "01L": PT8M30S
        """.trimIndent()

        assertFailsWith<InvalidFormatException> {
            yamlMapper.readValue<AirportDataJson>(yaml)
        }
    }
}
