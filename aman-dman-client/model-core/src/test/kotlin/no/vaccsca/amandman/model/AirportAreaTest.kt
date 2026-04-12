package no.vaccsca.amandman.model

import no.vaccsca.amandman.model.airport.AirportArea
import no.vaccsca.amandman.model.navigation.LatLng
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AirportAreaTest {

    private val squareArea = AirportArea.fromBoundary(
        id = "square",
        boundary = listOf(
            LatLng(60.0, 11.0),
            LatLng(60.0, 11.1),
            LatLng(60.1, 11.1),
            LatLng(60.1, 11.0),
        ),
    )

    @Test
    fun `covers returns true for point inside polygon`() {
        assertTrue(squareArea.covers(LatLng(60.05, 11.05), altitudeFt = 5000))
    }

    @Test
    fun `covers returns false for point outside polygon`() {
        assertFalse(squareArea.covers(LatLng(60.2, 11.2), altitudeFt = 5000))
    }

    @Test
    fun `covers treats boundary point as inside`() {
        assertTrue(squareArea.covers(LatLng(60.0, 11.05), altitudeFt = 5000))
    }

    @Test
    fun `covers respects optional ceiling`() {
        val areaWithCeiling = AirportArea.fromBoundary(
            id = "ceiling",
            boundary = squareArea.boundary,
            ceilingFt = 12000,
        )

        assertTrue(areaWithCeiling.covers(LatLng(60.05, 11.05), altitudeFt = 12000))
        assertFalse(areaWithCeiling.covers(LatLng(60.05, 11.05), altitudeFt = 12001))
    }
}
