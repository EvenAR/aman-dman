import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import no.vaccsca.amandman.model.config.mapper.toDomain
import no.vaccsca.amandman.model.config.yaml.AirportDataJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AirportWeatherFetchRadiusTest {

    private val yamlMapper = YAMLMapper()
        .registerKotlinModule()
        .registerModule(JavaTimeModule())

    @Test
    fun `airport weather fetch radius defaults to 200 NM`() {
        val airport = parseAirport(
            """
            location:
              latitude: 60.0
              longitude: 11.0
            runwayThresholds: {}
            """.trimIndent()
        )

        assertEquals(200.0, airport.weatherFetchRadiusNm)
    }

    @Test
    fun `airport weather fetch radius can be overridden from YAML`() {
        val airport = parseAirport(
            """
            location:
              latitude: 60.0
              longitude: 11.0
            weatherFetchRadiusNm: 150.5
            runwayThresholds: {}
            """.trimIndent()
        )

        assertEquals(150.5, airport.weatherFetchRadiusNm)
    }

    @Test
    fun `airport weather fetch radius must be positive`() {
        assertFailsWith<IllegalArgumentException> {
            parseAirport(
                """
                location:
                  latitude: 60.0
                  longitude: 11.0
                weatherFetchRadiusNm: 0
                runwayThresholds: {}
                """.trimIndent()
            )
        }

        assertFailsWith<IllegalArgumentException> {
            parseAirport(
                """
                location:
                  latitude: 60.0
                  longitude: 11.0
                weatherFetchRadiusNm: -10
                runwayThresholds: {}
                """.trimIndent()
            )
        }
    }

    private fun parseAirport(yaml: String) = yamlMapper
        .readValue<AirportDataJson>(yaml)
        .toDomain("TEST")
}
