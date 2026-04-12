package no.vaccsca.amandman.model

import kotlinx.datetime.Instant
import no.vaccsca.amandman.model.atc.euroscope.ArrivalJson
import no.vaccsca.amandman.model.atc.euroscope.FixPointJson
import no.vaccsca.amandman.model.atc.toDomain
import kotlin.test.*

class AtcClientEuroScopeMappingTest {

    @Test
    fun `arrival mapping should preserve extracted route while keeping remaining waypoints filtered`() {
        val mapped = ArrivalJson(
            callsign = "SAS123",
            icaoType = "B738",
            assignedRunway = "19L",
            assignedStar = "INREX4M",
            assignedDirect = "NEXT",
            trackingController = "ENGM_APP",
            scratchPad = "A1",
            latitude = 60.1,
            longitude = 11.1,
            flightLevel = 150,
            pressureAltitude = 14500,
            groundSpeed = 280,
            track = 175,
            route = listOf(
                FixPointJson(name = "BYPASSED", latitude = 60.0, longitude = 10.0, isActive = false),
                FixPointJson(name = "F1", latitude = 60.5, longitude = 11.0, isActive = false),
                FixPointJson(name = "NEXT", latitude = 60.7, longitude = 11.5, isActive = true),
                FixPointJson(name = "OSPAD", latitude = 60.4, longitude = 11.2, isActive = true),
            ),
            arrivalAirportIcao = "ENGM",
            flightPlanTas = 450,
        ).toDomain(receivedAt = Instant.parse("2026-04-08T18:00:00Z"))

        assertNotNull(mapped)
        assertEquals(4, mapped.extractedRoute.size)
        assertEquals(listOf("BYPASSED", "F1", "NEXT", "OSPAD"), mapped.extractedRoute.map { it.id })
        assertFalse(mapped.extractedRoute[0].isActive)
        assertTrue(mapped.extractedRoute[2].isActive)

        assertEquals(listOf("NEXT", "OSPAD"), mapped.remainingWaypoints.map { it.id })
        assertEquals(60.7, mapped.remainingWaypoints[0].latLng.lat)
        assertEquals(11.5, mapped.remainingWaypoints[0].latLng.lon)
        assertEquals("NEXT", mapped.assignedDirect)
    }
}
