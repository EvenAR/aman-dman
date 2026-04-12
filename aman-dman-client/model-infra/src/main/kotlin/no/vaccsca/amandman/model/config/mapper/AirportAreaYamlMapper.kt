package no.vaccsca.amandman.model.config.mapper

import com.fasterxml.jackson.databind.JsonNode
import no.vaccsca.amandman.model.airport.AirportArea
import no.vaccsca.amandman.model.navigation.LatLng
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.LineString
import org.locationtech.jts.geom.Polygon
import org.locationtech.jts.geom.PrecisionModel
import org.locationtech.jts.operation.polygonize.Polygonizer

private val geometryFactory = GeometryFactory(PrecisionModel(), 4326)
private val latitudeTokenRegex = Regex("""^([NS])(\d{2,3})\.(\d{2})\.(\d{2}(?:\.\d+)?)$""")
private val longitudeTokenRegex = Regex("""^([EW])(\d{2,3})\.(\d{2})\.(\d{2}(?:\.\d+)?)$""")

private data class ParsedAirportAreaYaml(
    val boundary: List<String>,
    val ceilingFt: Int?,
)

internal fun Map<String, JsonNode>.toAirportAreas(
    airportIcao: String,
): Map<String, AirportArea> = entries.associate { (rawAreaId, rawAreaNode) ->
    val areaId = rawAreaId.trim()
    require(areaId.isNotBlank()) {
        "Airport $airportIcao contains an area with a blank id."
    }

    val areaYaml = rawAreaNode.toParsedAirportAreaYaml(
        airportIcao = airportIcao,
        areaId = areaId,
    )
    val polygon = polygonizeArea(
        airportIcao = airportIcao,
        areaId = areaId,
        boundaryRows = areaYaml.boundary,
    )

    areaId to AirportArea.fromBoundary(
        id = areaId,
        boundary = polygon.exteriorRing.coordinates
            .dropLast(1)
            .map { coordinate -> LatLng(coordinate.y, coordinate.x) },
        ceilingFt = areaYaml.ceilingFt,
    )
}

private fun JsonNode.toParsedAirportAreaYaml(
    airportIcao: String,
    areaId: String,
): ParsedAirportAreaYaml {
    require(isObject) {
        "Airport $airportIcao area $areaId must be an object with boundary and optional ceilingFt."
    }

    val boundaryNode = get("boundary")
    require(boundaryNode != null && boundaryNode.isArray) {
        "Airport $airportIcao area $areaId must define a boundary array."
    }

    val ceilingFt = get("ceilingFt")?.let { ceilingNode ->
        require(ceilingNode.canConvertToInt()) {
            "Airport $airportIcao area $areaId has invalid ceilingFt value."
        }
        ceilingNode.intValue().also {
            require(it > 0) {
                "Airport $airportIcao area $areaId has invalid ceilingFt=$it. Value must be > 0."
            }
        }
    }

    return ParsedAirportAreaYaml(
        boundary = boundaryNode.mapIndexed { index, child ->
            require(child.isTextual) {
                "Airport $airportIcao area $areaId boundary entry ${index + 1} must be a string."
            }
            child.asText()
        },
        ceilingFt = ceilingFt,
    )
}


private fun polygonizeArea(
    airportIcao: String,
    areaId: String,
    boundaryRows: List<String>,
): Polygon {
    require(boundaryRows.isNotEmpty()) {
        "Airport $airportIcao area $areaId must define at least one boundary row."
    }

    val polygonizer = Polygonizer()
    boundaryRows.forEachIndexed { index, row ->
        polygonizer.add(row.toBoundarySegment(
            airportIcao = airportIcao,
            areaId = areaId,
            rowIndex = index + 1,
        ))
    }

    require(polygonizer.dangles.isEmpty()) {
        "Airport $airportIcao area $areaId contains dangling boundary segments."
    }
    require(polygonizer.cutEdges.isEmpty()) {
        "Airport $airportIcao area $areaId contains cut edges and does not form a closed polygon."
    }
    require(polygonizer.invalidRingLines.isEmpty()) {
        "Airport $airportIcao area $areaId contains invalid polygon ring lines."
    }

    val polygons = polygonizer.polygons.filterIsInstance<Polygon>()
    require(polygons.size == 1) {
        "Airport $airportIcao area $areaId must resolve to exactly one polygon, but resolved to ${polygons.size}."
    }

    val polygon = polygons.single()
    require(polygon.isValid) {
        "Airport $airportIcao area $areaId resolved to an invalid polygon."
    }

    return polygon
}

private fun String.toBoundarySegment(
    airportIcao: String,
    areaId: String,
    rowIndex: Int,
): LineString {
    val tokens = trim().split(Regex("""\s+"""))
    require(tokens.size == 4) {
        "Airport $airportIcao area $areaId boundary row $rowIndex must contain exactly four coordinate tokens."
    }

    val start = parseLatLng(
        latitudeToken = tokens[0],
        longitudeToken = tokens[1],
        airportIcao = airportIcao,
        areaId = areaId,
        rowIndex = rowIndex,
    )
    val end = parseLatLng(
        latitudeToken = tokens[2],
        longitudeToken = tokens[3],
        airportIcao = airportIcao,
        areaId = areaId,
        rowIndex = rowIndex,
    )

    return geometryFactory.createLineString(
        arrayOf(
            start.toCoordinate(),
            end.toCoordinate(),
        )
    )
}

private fun parseLatLng(
    latitudeToken: String,
    longitudeToken: String,
    airportIcao: String,
    areaId: String,
    rowIndex: Int,
): LatLng {
    val latitude = parseAngle(
        token = latitudeToken,
        regex = latitudeTokenRegex,
        airportIcao = airportIcao,
        areaId = areaId,
        rowIndex = rowIndex,
    )
    val longitude = parseAngle(
        token = longitudeToken,
        regex = longitudeTokenRegex,
        airportIcao = airportIcao,
        areaId = areaId,
        rowIndex = rowIndex,
    )

    return LatLng(latitude, longitude)
}

private fun parseAngle(
    token: String,
    regex: Regex,
    airportIcao: String,
    areaId: String,
    rowIndex: Int,
): Double {
    val match = regex.matchEntire(token)
        ?: throw IllegalArgumentException(
            "Airport $airportIcao area $areaId boundary row $rowIndex contains invalid coordinate token '$token'."
        )

    val (hemisphere, degrees, minutes, seconds) = match.destructured
    val value = degrees.toDouble() + (minutes.toDouble() / 60.0) + (seconds.toDouble() / 3600.0)

    return when (hemisphere) {
        "S", "W" -> -value
        else -> value
    }
}

private fun LatLng.toCoordinate(): Coordinate = Coordinate(lon, lat)
