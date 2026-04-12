package no.vaccsca.amandman.model.airport

import no.vaccsca.amandman.model.navigation.LatLng
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.Point
import org.locationtech.jts.geom.Polygon
import org.locationtech.jts.geom.PrecisionModel
import org.locationtech.jts.geom.prep.PreparedGeometryFactory

class AirportArea private constructor(
    val id: String,
    val boundary: List<LatLng>,
    val ceilingFt: Int?,
) {
    private val polygon: Polygon by lazy {
        geometryFactory.createPolygon(
            (boundary + boundary.first()).map { it.toCoordinate() }.toTypedArray()
        )
    }
    private val preparedPolygon by lazy { PreparedGeometryFactory.prepare(polygon) }

    fun covers(position: LatLng, altitudeFt: Int): Boolean {
        if (ceilingFt != null && altitudeFt > ceilingFt) {
            return false
        }

        val point: Point = geometryFactory.createPoint(position.toCoordinate())
        return preparedPolygon.covers(point)
    }

    companion object {
        private val geometryFactory = GeometryFactory(PrecisionModel(), 4326)

        fun fromBoundary(
            id: String,
            boundary: List<LatLng>,
            ceilingFt: Int? = null,
        ): AirportArea {
            require(id.isNotBlank()) { "Airport area id must not be blank." }
            require(boundary.size >= 3) { "Airport area $id must contain at least three boundary points." }
            ceilingFt?.let {
                require(it > 0) { "Airport area $id has invalid ceilingFt=$it. Value must be > 0." }
            }

            val polygon = geometryFactory.createPolygon(
                (boundary + boundary.first()).map { it.toCoordinate() }.toTypedArray()
            )
            require(polygon.isValid) { "Airport area $id is not a valid polygon." }
            require(polygon.area > 0.0) { "Airport area $id must enclose a non-zero area." }

            return AirportArea(
                id = id,
                boundary = boundary,
                ceilingFt = ceilingFt,
            )
        }
    }
}

private fun LatLng.toCoordinate(): Coordinate = Coordinate(lon, lat)
