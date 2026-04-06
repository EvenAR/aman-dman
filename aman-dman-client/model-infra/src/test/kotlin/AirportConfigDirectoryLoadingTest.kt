import no.vaccsca.amandman.model.config.SettingsRepository
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class AirportConfigDirectoryLoadingTest {

    @Test
    fun `loadAirportDataFromDirectory loads airport files and ignores schema file`() {
        val directory = Files.createTempDirectory("airport-config-test")
        Files.writeString(directory.resolve("airport.schema.yaml"), "type: object")
        Files.writeString(
            directory.resolve("engm.yaml"),
            """
            location:
              latitude: 60.0
              longitude: 11.0
            feederFixes: [LUNIP]
            runwayThresholds:
              19L:
                location:
                  latitude: 60.1
                  longitude: 11.1
                elevation: 681
                trueHeading: 194
            """.trimIndent()
        )
        Files.writeString(
            directory.resolve("enbr.yaml"),
            """
            location:
              latitude: 60.2
              longitude: 5.2
            runwayThresholds:
              35:
                location:
                  latitude: 60.3
                  longitude: 5.3
                elevation: 146
                trueHeading: 170
            """.trimIndent()
        )

        val airports = SettingsRepository.loadAirportDataFromDirectory(directory.toFile())

        assertEquals(listOf("ENBR", "ENGM"), airports.map { it.icao })
        assertEquals(listOf("LUNIP"), airports.first { it.icao == "ENGM" }.feederFixes)
    }
}
