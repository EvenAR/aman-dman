import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import no.vaccsca.amandman.model.config.mapper.arrivalFixWarningLogger
import no.vaccsca.amandman.model.config.mapper.toDomain
import no.vaccsca.amandman.model.config.yaml.AirportDataJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ArrivalFixYamlMappingTest {

    private val yamlMapper = YAMLMapper()
        .registerKotlinModule()
        .registerModule(JavaTimeModule())

    @Test
    fun `shared runway arrival fix rows are normalized and mapped to each listed runway`() {
        val airport = parseAirport(
            arrivalFixYaml = """
                arrivalFixes:
                  - name: titla
                    runways: [19l, 19r]
                    typicalAltitude: 5000
                    typicalAirspeed: 200
                  - name: xivta
                    runways: [19L]
                    role: IF
                    typicalAltitude: 3500
                    typicalAirspeed: 170
            """.trimIndent()
        )

        val runway19LFixes = airport.runways.getValue("19L").arrivalFixExpectations
        val runway19RFixes = airport.runways.getValue("19R").arrivalFixExpectations

        assertEquals(setOf("TITLA", "XIVTA"), runway19LFixes.map { it.fixName }.toSet())
        assertEquals(listOf("TITLA"), runway19RFixes.map { it.fixName })

        val titla19L = runway19LFixes.first { it.fixName == "TITLA" }
        assertEquals(5000, titla19L.typicalAltitude)
        assertEquals(200, titla19L.typicalSpeedIas)
        assertEquals("TITLA", runway19RFixes.single().fixName)
    }

    @Test
    fun `empty arrival fix list parses to runways without expectations`() {
        val airport = parseAirport(
            arrivalFixYaml = """
                arrivalFixes: []
            """.trimIndent()
        )

        assertTrue(airport.runways.values.all { it.arrivalFixExpectations.isEmpty() })
    }

    @Test
    fun `missing arrival fix list defaults to empty`() {
        val airport = yamlMapper.readValue<AirportDataJson>(
            """
            location:
              latitude: 60.0
              longitude: 11.0
            runwayThresholds:
              19L:
                location:
                  latitude: 60.1
                  longitude: 11.1
                elevation: 681
                trueHeading: 194
            """.trimIndent()
        ).toDomain("TEST")

        assertTrue(airport.runways.values.all { it.arrivalFixExpectations.isEmpty() })
    }

    @Test
    fun `duplicate fix plus runway combinations are rejected`() {
        assertFailsWith<IllegalArgumentException> {
            parseAirport(
                arrivalFixYaml = """
                    arrivalFixes:
                      - name: TITLA
                        runways: [19L, 19R]
                        typicalAltitude: 5000
                        typicalAirspeed: 200
                      - name: TITLA
                        runways: [19R]
                        typicalAltitude: 4500
                        typicalAirspeed: 190
                """.trimIndent()
            )
        }
    }

    @Test
    fun `rows must define at least one runway identifier`() {
        assertFailsWith<IllegalArgumentException> {
            parseAirport(
                arrivalFixYaml = """
                    arrivalFixes:
                      - name: TITLA
                        runways: []
                        typicalAltitude: 5000
                """.trimIndent()
            )
        }
    }

    @Test
    fun `rows reject duplicate runway identifiers`() {
        assertFailsWith<IllegalArgumentException> {
            parseAirport(
                arrivalFixYaml = """
                    arrivalFixes:
                      - name: TITLA
                        runways: [19L, 19L]
                        typicalAltitude: 5000
                """.trimIndent()
            )
        }
    }

    @Test
    fun `runways may define multiple iaf fixes`() {
        val airport = parseAirport(
            arrivalFixYaml = """
                arrivalFixes:
                  - name: TITLA
                    runways: [19L]
                    role: IAF
                    typicalAltitude: 5000
                  - name: XIVTA
                    runways: [19L]
                    role: IAF
                    typicalAltitude: 3500
            """.trimIndent()
        )

        assertEquals(
            setOf("TITLA", "XIVTA"),
            airport.runways.getValue("19L").arrivalFixExpectations.map { it.fixName }.toSet()
        )
    }

    @Test
    fun `runways may define multiple if fixes but log warning`() {
        val warningMessages = mutableListOf<String>()
        val originalWarningLogger = arrivalFixWarningLogger
        arrivalFixWarningLogger = warningMessages::add

        try {
            val airport = parseAirport(
                arrivalFixYaml = """
                    arrivalFixes:
                      - name: TITLA
                        runways: [19L]
                        role: IF
                        typicalAltitude: 5000
                      - name: XIVTA
                        runways: [19L]
                        role: IF
                        typicalAltitude: 3500
                """.trimIndent()
            )

            assertEquals(
                setOf("TITLA", "XIVTA"),
                airport.runways.getValue("19L").arrivalFixExpectations.map { it.fixName }.toSet()
            )
            assertEquals(
                listOf("Airport TEST runway 19L has multiple IF fixes (TITLA, XIVTA)."),
                warningMessages
            )
        } finally {
            arrivalFixWarningLogger = originalWarningLogger
        }
    }

    private fun parseAirport(arrivalFixYaml: String) =
        yamlMapper
            .readValue<AirportDataJson>(
                (
                    """
                    location:
                      latitude: 60.0
                      longitude: 11.0
                    runwayThresholds:
                      19L:
                        location:
                          latitude: 60.1
                          longitude: 11.1
                        elevation: 681
                        trueHeading: 194
                      19R:
                        location:
                          latitude: 60.2
                          longitude: 11.2
                        elevation: 681
                        trueHeading: 194
                    """.trimIndent() + "\n" + arrivalFixYaml
                )
            )
            .toDomain("TEST")
}
