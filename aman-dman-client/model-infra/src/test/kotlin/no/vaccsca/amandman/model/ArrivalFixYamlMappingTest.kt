package no.vaccsca.amandman.model

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import no.vaccsca.amandman.model.airport.ArrivalFixRole
import no.vaccsca.amandman.model.config.mapper.toDomain
import no.vaccsca.amandman.model.config.yaml.AirportDataJson
import kotlin.test.Test
import kotlin.test.assertFails
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ArrivalFixYamlMappingTest {

    private val yamlMapper = YAMLMapper()
        .registerKotlinModule()
        .registerModule(JavaTimeModule())

    @Test
    fun `runway keyed arrival profiles map fixes in profile order`() {
        val airport = parseAirport(
            arrivalProfilesYaml = """
                arrivalProfiles:
                  19L:
                    - arrivalName: INREX*
                      fixes:
                        - { fix: INREX, speed: 250 }
                        - { fix: TITLA, role: IAF, altitude: 5000, speed: 200 }
                        - { fix: OSPAD, role: IF, altitude: 4000, speed: 180 }
                    - arrivalName: "*"
                      fixes:
                        - { fix: BAVAD, role: IAF, altitude: 5000, speed: 200 }
                        - { fix: XIVTA, altitude: 3500, speed: 170 }
            """.trimIndent()
        )

        val runway19L = airport.runways.getValue("19L")
        val inrexProfile = runway19L.arrivalProfiles.first { it.arrivalNamePattern == "INREX*" }
        val fallbackProfile = runway19L.arrivalProfiles.first { it.arrivalNamePattern == "*" }

        assertEquals(listOf("INREX", "TITLA", "OSPAD"), inrexProfile.fixExpectations.map { it.fixName })
        assertEquals(ArrivalFixRole.IAF, inrexProfile.fixExpectations[1].role)
        assertEquals(ArrivalFixRole.IF, inrexProfile.fixExpectations[2].role)
        assertEquals(5000, inrexProfile.fixExpectations[1].typicalAltitude)
        assertEquals(170, fallbackProfile.fixExpectations.last().typicalSpeedIas)
    }

    @Test
    fun `matching arrival profiles are merged with later profiles overriding earlier fixes`() {
        val airport = parseAirport(
            arrivalProfilesYaml = """
                arrivalProfiles:
                  19L:
                    - arrivalName: "*"
                      fixes:
                        - { fix: TITLA, speed: 220 }
                        - { fix: OSPAD, altitude: 4000, speed: 180 }
                    - arrivalName: INREX*
                      fixes:
                        - { fix: TITLA, speed: 200 }
                        - { fix: INREX, speed: 250 }
            """.trimIndent()
        )

        val runway19L = airport.runways.getValue("19L")

        assertEquals(listOf("TITLA", "OSPAD", "INREX"), runway19L.arrivalFixExpectationsFor("INREX4M").map { it.fixName })
        assertEquals(200, runway19L.arrivalFixExpectationsFor("INREX4M")[0].typicalSpeedIas)
        assertEquals(4000, runway19L.arrivalFixExpectationsFor("INREX4M")[1].typicalAltitude)
        assertEquals(250, runway19L.arrivalFixExpectationsFor("INREX4M")[2].typicalSpeedIas)

        assertEquals(listOf("TITLA", "OSPAD"), runway19L.arrivalFixExpectationsFor("ESEBA4M").map { it.fixName })
        assertEquals(220, runway19L.arrivalFixExpectationsFor("ESEBA4M")[0].typicalSpeedIas)
    }

    @Test
    fun `runway wildcard applies profiles to every matching runway`() {
        val airport = parseAirport(
            arrivalProfilesYaml = """
                arrivalProfiles:
                  19*:
                    - arrivalName: "*"
                      fixes:
                        - { fix: TITLA, role: IAF, altitude: 5000, speed: 200 }
            """.trimIndent()
        )

        assertEquals(1, airport.runways.getValue("19L").arrivalProfiles.size)
        assertEquals(1, airport.runways.getValue("19R").arrivalProfiles.size)
        assertEquals(5000, airport.runways.getValue("19L").arrivalFixExpectationsFor("ANY").single().typicalAltitude)
    }

    @Test
    fun `empty arrival profile map parses to runways without profiles`() {
        val airport = parseAirport(
            arrivalProfilesYaml = """
                arrivalProfiles: {}
            """.trimIndent()
        )

        assertTrue(airport.runways.values.all { it.arrivalProfiles.isEmpty() })
    }

    @Test
    fun `arrival profile may omit fixes`() {
        val airport = parseAirport(
            arrivalProfilesYaml = """
                arrivalProfiles:
                  19L:
                    - arrivalName: "*"
            """.trimIndent()
        )

        assertEquals(1, airport.runways.getValue("19L").arrivalProfiles.size)
        assertTrue(airport.runways.getValue("19L").arrivalProfiles.single().fixExpectations.isEmpty())
    }

    @Test
    fun `arrival profile may define empty fixes list`() {
        val airport = parseAirport(
            arrivalProfilesYaml = """
                arrivalProfiles:
                  19L:
                    - arrivalName: "*"
                      fixes: []
            """.trimIndent()
        )

        assertTrue(airport.runways.getValue("19L").arrivalProfiles.single().fixExpectations.isEmpty())
    }

    @Test
    fun `missing arrival profile map defaults to empty`() {
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

        assertTrue(airport.runways.values.all { it.arrivalProfiles.isEmpty() })
    }

    @Test
    fun `arrival profiles reject unknown runway keys`() {
        assertFailsWith<IllegalArgumentException> {
            parseAirport(
                arrivalProfilesYaml = """
                    arrivalProfiles:
                      01L:
                        - arrivalName: "*"
                          fixes:
                            - { fix: TITLA, role: IAF }
                """.trimIndent()
            )
        }
    }

    @Test
    fun `arrival profile grouped list format is rejected`() {
        assertFails {
            parseAirport(
                arrivalProfilesYaml = """
                    arrivalProfiles:
                      - runways: ["19L", "19R"]
                        arrivals:
                          - arrivalName: "*"
                            fixes: []
                """.trimIndent()
            )
        }
    }

    @Test
    fun `duplicate arrivalName patterns per runway are rejected`() {
        assertFailsWith<IllegalArgumentException> {
            parseAirport(
                arrivalProfilesYaml = """
                    arrivalProfiles:
                      19L:
                        - arrivalName: INREX*
                          fixes:
                            - { fix: TITLA, role: IAF }
                        - arrivalName: inrex*
                          fixes:
                            - { fix: OSPAD, role: IF }
                """.trimIndent()
            )
        }
    }

    @Test
    fun `duplicate fixes within the same profile are rejected`() {
        assertFailsWith<IllegalArgumentException> {
            parseAirport(
                arrivalProfilesYaml = """
                    arrivalProfiles:
                      19L:
                        - arrivalName: "*"
                          fixes:
                            - { fix: TITLA, role: IAF }
                            - { fix: titla, speed: 200 }
                """.trimIndent()
            )
        }
    }

    @Test
    fun `fix rows must define at least one value`() {
        assertFailsWith<IllegalArgumentException> {
            parseAirport(
                arrivalProfilesYaml = """
                    arrivalProfiles:
                      19L:
                        - arrivalName: "*"
                          fixes:
                            - { fix: TITLA }
                """.trimIndent()
            )
        }
    }

    private fun parseAirport(arrivalProfilesYaml: String) =
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
                    """.trimIndent() + "\n" + arrivalProfilesYaml
                )
            )
            .toDomain("TEST")
}
