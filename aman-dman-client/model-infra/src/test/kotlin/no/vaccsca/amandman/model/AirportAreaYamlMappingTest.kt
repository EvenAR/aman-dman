package no.vaccsca.amandman.model

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import no.vaccsca.amandman.model.config.mapper.toDomain
import no.vaccsca.amandman.model.config.yaml.AirportDataJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class AirportAreaYamlMappingTest {

    private val yamlMapper = YAMLMapper()
        .registerKotlinModule()
        .registerModule(JavaTimeModule())

    @Test
    fun `object-form area parses and maps to arrival profile`() {
        val airport = parseAirport(
            """
            areas:
              commonLockedArea:
                boundary:
                  - N060.00.00.000 E011.00.00.000 N060.00.00.000 E011.10.00.000
                  - N060.00.00.000 E011.10.00.000 N060.10.00.000 E011.10.00.000
                  - N060.10.00.000 E011.10.00.000 N060.10.00.000 E011.00.00.000
                  - N060.10.00.000 E011.00.00.000 N060.00.00.000 E011.00.00.000
            arrivalProfiles:
              19L:
                - arrivalName: "*"
                  frozenSequenceArea: commonLockedArea
                  turnAdvisoryAreas: [commonLockedArea]
                  fixes:
                    - { fix: TITLA, role: IAF }
            """.trimIndent()
        )

        val area = airport.areas["commonLockedArea"]
        assertNotNull(area)
        assertEquals(4, area.boundary.size)
        assertEquals("commonLockedArea", airport.runways.getValue("19L").frozenSequenceAreaIdFor("INREX4M"))
        assertEquals(listOf("commonLockedArea"), airport.runways.getValue("19L").turnAdvisoryAreaIdsFor("INREX4M"))
    }

    @Test
    fun `object-form area parses optional ceiling`() {
        val airport = parseAirport(
            """
            areas:
              commonLockedArea:
                boundary:
                  - N060.00.00.000 E011.00.00.000 N060.00.00.000 E011.10.00.000
                  - N060.00.00.000 E011.10.00.000 N060.10.00.000 E011.10.00.000
                  - N060.10.00.000 E011.10.00.000 N060.10.00.000 E011.00.00.000
                  - N060.10.00.000 E011.00.00.000 N060.00.00.000 E011.00.00.000
                ceilingFt: 12000
            arrivalProfiles:
              19L:
                - arrivalName: "*"
                  frozenSequenceArea: commonLockedArea
                  fixes:
                    - { fix: TITLA, role: IAF }
            """.trimIndent()
        )

        assertEquals(12000, airport.areas.getValue("commonLockedArea").ceilingFt)
    }

    @Test
    fun `unknown frozenSequenceArea reference is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            parseAirport(
                """
                areas:
                  commonLockedArea:
                    boundary:
                      - N060.00.00.000 E011.00.00.000 N060.00.00.000 E011.10.00.000
                      - N060.00.00.000 E011.10.00.000 N060.10.00.000 E011.10.00.000
                      - N060.10.00.000 E011.10.00.000 N060.10.00.000 E011.00.00.000
                      - N060.10.00.000 E011.00.00.000 N060.00.00.000 E011.00.00.000
                arrivalProfiles:
                  19L:
                    - arrivalName: "*"
                      frozenSequenceArea: missingArea
                      fixes:
                        - { fix: TITLA, role: IAF }
                """.trimIndent()
            )
        }
    }

    @Test
    fun `unknown turnAdvisoryAreas reference is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            parseAirport(
                """
                areas:
                  commonLockedArea:
                    boundary:
                      - N060.00.00.000 E011.00.00.000 N060.00.00.000 E011.10.00.000
                      - N060.00.00.000 E011.10.00.000 N060.10.00.000 E011.10.00.000
                      - N060.10.00.000 E011.10.00.000 N060.10.00.000 E011.00.00.000
                      - N060.10.00.000 E011.00.00.000 N060.00.00.000 E011.00.00.000
                arrivalProfiles:
                  19L:
                    - arrivalName: "*"
                      turnAdvisoryAreas: [missingArea]
                      fixes:
                        - { fix: TITLA, role: IAF }
                """.trimIndent()
            )
        }
    }

    @Test
    fun `malformed boundary segment is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            parseAirport(
                """
                areas:
                  commonLockedArea:
                    boundary:
                      - INVALID SEGMENT
                arrivalProfiles:
                  19L:
                    - arrivalName: "*"
                      frozenSequenceArea: commonLockedArea
                      fixes:
                        - { fix: TITLA, role: IAF }
                """.trimIndent()
            )
        }
    }

    @Test
    fun `disconnected boundary segments are rejected`() {
        assertFailsWith<IllegalArgumentException> {
            parseAirport(
                """
                areas:
                  commonLockedArea:
                    boundary:
                      - N060.00.00.000 E011.00.00.000 N060.00.00.000 E011.10.00.000
                      - N060.20.00.000 E011.20.00.000 N060.20.00.000 E011.30.00.000
                arrivalProfiles:
                  19L:
                    - arrivalName: "*"
                      frozenSequenceArea: commonLockedArea
                      fixes:
                        - { fix: TITLA, role: IAF }
                """.trimIndent()
            )
        }
    }

    @Test
    fun `invalid ceiling is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            parseAirport(
                """
                areas:
                  commonLockedArea:
                    boundary:
                      - N060.00.00.000 E011.00.00.000 N060.00.00.000 E011.10.00.000
                      - N060.00.00.000 E011.10.00.000 N060.10.00.000 E011.10.00.000
                      - N060.10.00.000 E011.10.00.000 N060.10.00.000 E011.00.00.000
                      - N060.10.00.000 E011.00.00.000 N060.00.00.000 E011.00.00.000
                    ceilingFt: 0
                arrivalProfiles:
                  19L:
                    - arrivalName: "*"
                      frozenSequenceArea: commonLockedArea
                      fixes:
                        - { fix: TITLA, role: IAF }
                """.trimIndent()
            )
        }
    }


    private fun parseAirport(extraYaml: String) =
        yamlMapper.readValue<AirportDataJson>(
            listOf(
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
                """.trimIndent(),
                extraYaml.trimIndent(),
            ).joinToString(separator = "\n")
        ).toDomain("TEST")
}
