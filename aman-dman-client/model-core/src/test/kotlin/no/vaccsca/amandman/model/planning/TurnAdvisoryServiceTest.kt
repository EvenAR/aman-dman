package no.vaccsca.amandman.model.planning

import kotlinx.datetime.Instant
import no.vaccsca.amandman.model.aircraft.AircraftPosition
import no.vaccsca.amandman.model.airport.Airport
import no.vaccsca.amandman.model.airport.AirportArea
import no.vaccsca.amandman.model.airport.ArrivalFixExpectation
import no.vaccsca.amandman.model.airport.ArrivalFixRole
import no.vaccsca.amandman.model.airport.RunwayArrivalProfile
import no.vaccsca.amandman.model.airport.RunwayThreshold
import no.vaccsca.amandman.model.atc.AtcClientArrivalData
import no.vaccsca.amandman.model.navigation.LatLng
import no.vaccsca.amandman.model.navigation.Waypoint
import no.vaccsca.amandman.model.weather.WindVector
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class TurnAdvisoryServiceTest {

    private val advisoryArea = AirportArea.fromBoundary(
        id = "turnArea",
        boundary = listOf(
            LatLng(59.95, 10.95),
            LatLng(59.95, 11.25),
            LatLng(60.15, 11.25),
            LatLng(60.15, 10.95),
        ),
    )

    private val airport = Airport(
        icao = "TEST",
        location = LatLng(60.0, 11.0),
        runways = mapOf(
            "19L" to RunwayThreshold(
                id = "19L",
                latLng = LatLng(60.2, 11.2),
                elevation = 600f,
                trueHeading = 194f,
                arrivalProfiles = listOf(
                    RunwayArrivalProfile(
                        arrivalNamePattern = "*",
                        fixExpectations = listOf(
                            ArrivalFixExpectation(
                                fixName = "IAF1",
                                role = ArrivalFixRole.IAF,
                                typicalAltitude = 5000,
                                typicalSpeedIas = 200,
                            )
                        ),
                        turnAdvisoryAreaIds = listOf("turnArea"),
                    )
                ),
            )
        ),
        areas = mapOf(advisoryArea.id to advisoryArea),
        independentRunwaySystems = listOf(setOf("19L")),
        sequencingHorizon = 30.minutes,
        lockedHorizon = 10.minutes,
    )

    private val now = Instant.parse("2026-04-13T12:00:00Z")
    private val iafPosition = LatLng(60.0, 11.2)

    @Test
    fun `inside area with no delay advises now`() {
        val arrival = arrival(trackDeg = 270)
        val advisory = TurnAdvisoryService.turnToIafAdvisoryTime(
            airport = airport,
            assignedRunway = arrival.assignedRunway,
            assignedStar = arrival.assignedStar,
            currentPosition = arrival.currentPosition,
            estimatedRunwayTime = now + 20.minutes,
            scheduledRunwayTime = now + 20.minutes,
            trajectory = trajectory(iafEto = now + 1.minutes),
            advisoryBankAngleDeg = 25.0,
            now = now,
        )

        assertEquals(now, advisory)
    }

    @Test
    fun `inside area with no delay advises now even if computed candidate would be future`() {
        val arrival = arrival(trackDeg = 0)
        val advisory = TurnAdvisoryService.turnToIafAdvisoryTime(
            airport = airport,
            assignedRunway = arrival.assignedRunway,
            assignedStar = arrival.assignedStar,
            currentPosition = arrival.currentPosition,
            estimatedRunwayTime = now + 20.minutes,
            scheduledRunwayTime = now + 20.minutes,
            trajectory = trajectory(iafEto = now + 40.minutes),
            advisoryBankAngleDeg = 25.0,
            now = now,
        )

        assertEquals(now, advisory)
    }

    @Test
    fun `inside area with runway delay advises future timestamp`() {
        val arrival = arrival(trackDeg = 0)
        val advisory = TurnAdvisoryService.turnToIafAdvisoryTime(
            airport = airport,
            assignedRunway = arrival.assignedRunway,
            assignedStar = arrival.assignedStar,
            currentPosition = arrival.currentPosition,
            estimatedRunwayTime = now + 20.minutes,
            scheduledRunwayTime = now + 30.minutes,
            trajectory = trajectory(iafEto = now + 5.minutes),
            advisoryBankAngleDeg = 25.0,
            now = now,
        )

        assertNotNull(advisory)
        assertTrue(advisory > now)
    }

    @Test
    fun `larger heading change moves advisory earlier`() {
        val trajectory = trajectory(iafEto = now + 8.minutes)
        val smallTurnArrival = arrival(trackDeg = 90)
        val largeTurnArrival = arrival(trackDeg = 270)

        val smallTurnAdvisory = TurnAdvisoryService.turnToIafAdvisoryTime(
            airport = airport,
            assignedRunway = smallTurnArrival.assignedRunway,
            assignedStar = smallTurnArrival.assignedStar,
            currentPosition = smallTurnArrival.currentPosition,
            estimatedRunwayTime = now + 20.minutes,
            scheduledRunwayTime = now + 40.minutes,
            trajectory = trajectory,
            advisoryBankAngleDeg = 25.0,
            now = now,
        )
        val largeTurnAdvisory = TurnAdvisoryService.turnToIafAdvisoryTime(
            airport = airport,
            assignedRunway = largeTurnArrival.assignedRunway,
            assignedStar = largeTurnArrival.assignedStar,
            currentPosition = largeTurnArrival.currentPosition,
            estimatedRunwayTime = now + 20.minutes,
            scheduledRunwayTime = now + 40.minutes,
            trajectory = trajectory,
            advisoryBankAngleDeg = 25.0,
            now = now,
        )

        assertNotNull(smallTurnAdvisory)
        assertNotNull(largeTurnAdvisory)
        assertTrue(largeTurnAdvisory < smallTurnAdvisory)
    }

    @Test
    fun `outside advisory area returns null`() {
        val arrivalOutsideArea = arrival(position = LatLng(61.0, 12.0))
        val advisory = TurnAdvisoryService.turnToIafAdvisoryTime(
            airport = airport,
            assignedRunway = arrivalOutsideArea.assignedRunway,
            assignedStar = arrivalOutsideArea.assignedStar,
            currentPosition = arrivalOutsideArea.currentPosition,
            estimatedRunwayTime = now + 20.minutes,
            scheduledRunwayTime = now + 30.minutes,
            trajectory = trajectory(iafEto = now + 5.minutes),
            advisoryBankAngleDeg = 25.0,
            now = now,
        )

        assertNull(advisory)
    }

    @Test
    fun `active direct to IAF returns null`() {
        val arrival = arrival(trackDeg = 0)
        val advisory = TurnAdvisoryService.turnToIafAdvisoryTime(
            airport = airport,
            assignedRunway = arrival.assignedRunway,
            assignedStar = arrival.assignedStar,
            currentPosition = arrival.currentPosition,
            estimatedRunwayTime = now + 20.minutes,
            scheduledRunwayTime = now + 30.minutes,
            trajectory = trajectory(iafEto = now + 5.minutes),
            advisoryBankAngleDeg = 25.0,
            isDirectToIafActive = true,
            now = now,
        )

        assertNull(advisory)
    }

    @Test
    fun `missing trajectory returns null`() {
        val arrival = arrival()
        val advisory = TurnAdvisoryService.turnToIafAdvisoryTime(
            airport = airport,
            assignedRunway = arrival.assignedRunway,
            assignedStar = arrival.assignedStar,
            currentPosition = arrival.currentPosition,
            estimatedRunwayTime = now + 20.minutes,
            scheduledRunwayTime = now + 30.minutes,
            trajectory = null,
            advisoryBankAngleDeg = 25.0,
            now = now,
        )

        assertNull(advisory)
    }

    private fun arrival(
        position: LatLng = LatLng(60.0, 11.0),
        trackDeg: Int = 0,
    ) = AtcClientArrivalData(
        callsign = "SAS123",
        icaoType = "B738",
        assignedStar = "INREX4M",
        assignedDirect = null,
        trackingController = "APP",
        scratchPad = null,
        currentPosition = AircraftPosition(
            latLng = position,
            altitudeFt = 9000,
            flightLevel = 90,
            groundspeedKts = 240,
            trackDeg = trackDeg,
        ),
        remainingWaypoints = listOf(Waypoint("IAF1", iafPosition)),
        assignedRunway = "19L",
        arrivalAirportIcao = "TEST",
        flightPlanTas = 420,
        recvTimestamp = now,
    )

    private fun trajectory(iafEto: Instant): List<TrajectoryPoint> =
        listOf(
            TrajectoryPoint(
                fixId = null,
                latLng = LatLng(60.0, 11.0),
                altitude = 9000,
                remainingDistance = 40f,
                time = now,
                groundSpeed = 240,
                tas = 250,
                ias = 220,
                windVector = WindVector(0, 0),
                heading = 0,
            ),
            TrajectoryPoint(
                fixId = "IAF1",
                latLng = iafPosition,
                altitude = 5000,
                remainingDistance = 20f,
                time = iafEto,
                groundSpeed = 220,
                tas = 230,
                ias = 200,
                windVector = WindVector(0, 0),
                heading = 0,
                fixRole = ArrivalFixRole.IAF,
            ),
        )
}
