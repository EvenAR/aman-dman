package no.vaccsca.amandman.model

import kotlinx.datetime.Instant
import no.vaccsca.amandman.model.aircraft.AircraftPosition
import no.vaccsca.amandman.model.airport.Airport
import no.vaccsca.amandman.model.airport.AirportArea
import no.vaccsca.amandman.model.airport.RunwayArrivalProfile
import no.vaccsca.amandman.model.airport.RunwayThreshold
import no.vaccsca.amandman.model.atc.AtcClientArrivalData
import no.vaccsca.amandman.model.navigation.LatLng
import no.vaccsca.amandman.model.navigation.Waypoint
import no.vaccsca.amandman.model.planning.SequencingLockEvaluator
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class SequencingLockEvaluatorTest {

    private val lockedArea = AirportArea.fromBoundary(
        id = "commonLockedArea",
        boundary = listOf(
            LatLng(60.0, 11.0),
            LatLng(60.0, 11.1),
            LatLng(60.1, 11.1),
            LatLng(60.1, 11.0),
        ),
    )

    private val runway = RunwayThreshold(
        id = "19L",
        latLng = LatLng(60.2, 11.2),
        elevation = 681f,
        trueHeading = 194f,
        arrivalProfiles = listOf(
            RunwayArrivalProfile(
                arrivalNamePattern = "*",
                frozenSequenceAreaId = "commonLockedArea",
                fixExpectations = emptyList(),
            )
        ),
    )

    private val airportWithArea = Airport(
        icao = "ENGM",
        location = LatLng(60.0, 11.0),
        runways = mapOf("19L" to runway),
        areas = mapOf(lockedArea.id to lockedArea),
        independentRunwaySystems = listOf(setOf("19L")),
        sequencingHorizon = 30.minutes,
        lockedHorizon = 10.minutes,
    )

    private val airportWithoutArea = airportWithArea.copy(areas = emptyMap())

    @Test
    fun `inside polygon without ceiling is locked`() {
        val result = SequencingLockEvaluator.isLockedForSequencing(
            airport = airportWithArea,
            arrival = arrival(position = LatLng(60.05, 11.05)),
            estimatedTime = Instant.parse("2026-04-12T12:20:00Z"),
            now = Instant.parse("2026-04-12T12:00:00Z"),
        )

        assertTrue(result)
    }

    @Test
    fun `outside polygon is not locked when area is configured`() {
        val result = SequencingLockEvaluator.isLockedForSequencing(
            airport = airportWithArea,
            arrival = arrival(position = LatLng(60.2, 11.2)),
            estimatedTime = Instant.parse("2026-04-12T12:05:00Z"),
            now = Instant.parse("2026-04-12T12:00:00Z"),
        )

        assertFalse(result)
    }

    @Test
    fun `inside polygon above ceiling is not locked`() {
        val airport = airportWithArea.copy(
            areas = mapOf(
                "commonLockedArea" to AirportArea.fromBoundary(
                    id = "commonLockedArea",
                    boundary = lockedArea.boundary,
                    ceilingFt = 12000,
                )
            )
        )

        val result = SequencingLockEvaluator.isLockedForSequencing(
            airport = airport,
            arrival = arrival(position = LatLng(60.05, 11.05), altitudeFt = 13000),
            estimatedTime = Instant.parse("2026-04-12T12:05:00Z"),
            now = Instant.parse("2026-04-12T12:00:00Z"),
        )

        assertFalse(result)
    }

    @Test
    fun `inside polygon at or below ceiling is locked`() {
        val airport = airportWithArea.copy(
            areas = mapOf(
                "commonLockedArea" to AirportArea.fromBoundary(
                    id = "commonLockedArea",
                    boundary = lockedArea.boundary,
                    ceilingFt = 12000,
                )
            )
        )

        val result = SequencingLockEvaluator.isLockedForSequencing(
            airport = airport,
            arrival = arrival(position = LatLng(60.05, 11.05), altitudeFt = 12000),
            estimatedTime = Instant.parse("2026-04-12T12:20:00Z"),
            now = Instant.parse("2026-04-12T12:00:00Z"),
        )

        assertTrue(result)
    }

    @Test
    fun `falls back to locked horizon when no area is resolved`() {
        val result = SequencingLockEvaluator.isLockedForSequencing(
            airport = airportWithoutArea,
            arrival = arrival(position = LatLng(60.2, 11.2)),
            estimatedTime = Instant.parse("2026-04-12T12:05:00Z"),
            now = Instant.parse("2026-04-12T12:00:00Z"),
        )

        assertTrue(result)
    }

    private fun arrival(
        position: LatLng,
        altitudeFt: Int = 10000,
    ) = AtcClientArrivalData(
        callsign = "SAS123",
        icaoType = "B738",
        assignedStar = "INREX4M",
        assignedDirect = null,
        trackingController = "APP",
        scratchPad = "SCR",
        currentPosition = AircraftPosition(
            latLng = position,
            altitudeFt = altitudeFt,
            flightLevel = altitudeFt,
            groundspeedKts = 250,
            trackDeg = 180,
        ),
        remainingWaypoints = listOf(Waypoint("TITLA", LatLng(60.1, 11.1))),
        assignedRunway = "19L",
        arrivalAirportIcao = "ENGM",
        flightPlanTas = 430,
        recvTimestamp = Instant.parse("2026-04-12T12:00:00Z"),
    )
}
