import kotlinx.datetime.Instant
import no.vaccsca.amandman.model.airport.Airport
import no.vaccsca.amandman.model.navigation.LatLng
import no.vaccsca.amandman.model.planning.DynamicFromTrajectoryFeederFixTimingStrategy
import no.vaccsca.amandman.model.planning.FeederFixTimingService
import no.vaccsca.amandman.model.planning.SequenceStatus
import no.vaccsca.amandman.model.planning.TrajectoryPoint
import no.vaccsca.amandman.model.timeline.event.timeline.RunwayArrivalEvent
import no.vaccsca.amandman.model.weather.WindVector
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class FeederFixTimingServiceTest {

    private val referenceEta = Instant.parse("2026-03-07T12:30:00Z")
    private val referenceSta = Instant.parse("2026-03-07T12:35:00Z")

    @Test
    fun `buildState should derive feeder fix ETA and STA from trajectory points`() {
        val service = FeederFixTimingService()
        val airport = airportWithFixes("F1", "F2")
        val arrival = sampleArrival(estimatedTime = referenceEta, scheduledTime = referenceSta)

        val state = service.buildState(
            airport = airport,
            arrivals = listOf(arrival),
            trajectoryProvider = {
                listOf(
                    trajectoryPoint("F1", 20.minutes),
                    trajectoryPoint("F2", 10.minutes),
                    trajectoryPoint("19L", 0.minutes),
                )
            }
        )

        val callsignTimings = state.timingsByCallsign[arrival.callsign]
        assertNotNull(callsignTimings)

        val f1 = callsignTimings["F1"]
        assertNotNull(f1)
        assertEquals(Instant.parse("2026-03-07T12:10:00Z"), f1.eta)
        assertEquals(Instant.parse("2026-03-07T12:15:00Z"), f1.sta)

        val f2 = callsignTimings["F2"]
        assertNotNull(f2)
        assertEquals(Instant.parse("2026-03-07T12:20:00Z"), f2.eta)
        assertEquals(Instant.parse("2026-03-07T12:25:00Z"), f2.sta)
    }

    @Test
    fun `buildState should ignore fixes that are not present in trajectory`() {
        val service = FeederFixTimingService()
        val airport = airportWithFixes("F1", "UNKNOWN")
        val arrival = sampleArrival(estimatedTime = referenceEta, scheduledTime = referenceSta)

        val state = service.buildState(
            airport = airport,
            arrivals = listOf(arrival),
            trajectoryProvider = {
                listOf(
                    trajectoryPoint("F1", 12.minutes),
                    trajectoryPoint("19L", 0.minutes),
                )
            }
        )

        val timings = state.timingsByCallsign[arrival.callsign]
        assertNotNull(timings)
        assertTrue("F1" in timings)
        assertTrue("UNKNOWN" !in timings)
    }

    @Test
    fun `dynamic strategy should use first matching fix occurrence`() {
        val strategy = DynamicFromTrajectoryFeederFixTimingStrategy()
        val arrival = sampleArrival(estimatedTime = referenceEta, scheduledTime = referenceSta)

        val result = strategy.computeTimingsForArrival(
            arrival = arrival,
            trajectory = listOf(
                trajectoryPoint("F1", 18.minutes),
                trajectoryPoint("F1", 8.minutes),
            ),
            targetFixes = setOf("F1")
        )

        val timing = result["F1"]
        assertNotNull(timing)
        assertEquals(Instant.parse("2026-03-07T12:12:00Z"), timing.eta)
        assertEquals(Instant.parse("2026-03-07T12:17:00Z"), timing.sta)
    }

    private fun airportWithFixes(vararg fixes: String): Airport = Airport(
        icao = "TEST",
        location = LatLng(60.0, 11.0),
        runways = emptyMap(),
        independentRunwaySystems = emptyList(),
        sequencingHorizon = 30.minutes,
        lockedHorizon = 10.minutes,
        feederFixes = fixes.toList(),
    )

    private fun sampleArrival(estimatedTime: Instant, scheduledTime: Instant): RunwayArrivalEvent = RunwayArrivalEvent(
        scheduledTime = scheduledTime,
        estimatedTime = estimatedTime,
        lastTimestamp = estimatedTime,
        runway = "19L",
        callsign = "SAS123",
        icaoType = "B738",
        wakeCategory = 'M',
        airportIcao = "TEST",
        trackingController = null,
        assignedStar = null,
        flightLevel = 120,
        pressureAltitude = 10000,
        groundSpeed = 250,
        remainingDistance = 80f,
        withinActiveAdvisoryHorizon = false,
        sequenceStatus = SequenceStatus.OK,
        landingIas = 140,
        assignedDirect = null,
        scratchPad = null,
    )

    private fun trajectoryPoint(fixId: String, remainingTime: kotlin.time.Duration): TrajectoryPoint = TrajectoryPoint(
        fixId = fixId,
        latLng = LatLng(60.0, 11.0),
        altitude = 10000,
        remainingDistance = 100f,
        remainingTime = remainingTime,
        groundSpeed = 250,
        tas = 260,
        ias = 240,
        windVector = WindVector(0, 0),
        heading = 180,
    )
}
