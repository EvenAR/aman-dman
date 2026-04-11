import no.vaccsca.amandman.model.airport.ArrivalFixExpectation
import no.vaccsca.amandman.model.airport.RunwayArrivalProfile
import no.vaccsca.amandman.model.airport.RunwayThreshold
import no.vaccsca.amandman.model.navigation.LatLng
import kotlin.test.Test
import kotlin.test.assertEquals

class RunwayThresholdArrivalProfileTest {

    @Test
    fun `arrivalFixExpectationsFor merges matching arrival profiles with later fixes overriding earlier ones`() {
        val runway = RunwayThreshold(
            id = "19L",
            latLng = LatLng(60.2, 11.1),
            elevation = 681f,
            trueHeading = 194f,
            arrivalProfiles = listOf(
                RunwayArrivalProfile(
                    arrivalNamePattern = "*",
                    fixExpectations = listOf(
                        ArrivalFixExpectation(fixName = "TITLA", typicalSpeedIas = 220),
                        ArrivalFixExpectation(fixName = "OSPAD", typicalSpeedIas = 180),
                    ),
                ),
                RunwayArrivalProfile(
                    arrivalNamePattern = "INREX*",
                    fixExpectations = listOf(
                        ArrivalFixExpectation(fixName = "TITLA", typicalSpeedIas = 200),
                        ArrivalFixExpectation(fixName = "INREX", typicalSpeedIas = 250),
                    ),
                ),
            ),
        )

        assertEquals(
            listOf("TITLA", "OSPAD", "INREX"),
            runway.arrivalFixExpectationsFor("INREX4M").map { it.fixName },
        )
        assertEquals(200, runway.arrivalFixExpectationsFor("INREX4M")[0].typicalSpeedIas)
        assertEquals(180, runway.arrivalFixExpectationsFor("INREX4M")[1].typicalSpeedIas)
        assertEquals(250, runway.arrivalFixExpectationsFor("INREX4M")[2].typicalSpeedIas)

        assertEquals(listOf("TITLA", "OSPAD"), runway.arrivalFixExpectationsFor("ESEBA4M").map { it.fixName })
        assertEquals(220, runway.arrivalFixExpectationsFor("ESEBA4M")[0].typicalSpeedIas)
        assertEquals(180, runway.arrivalFixExpectationsFor("ESEBA4M")[1].typicalSpeedIas)
    }
}
