package no.vaccsca.amandman.model.config.yaml

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import java.time.Duration

@JsonIgnoreProperties(ignoreUnknown = true)
private data class AirportDataJsonRaw(
    val location: LocationJson,
    val runwayThresholds: Map<String, RunwayThresholdJson>,
    val independentRunwaySystems: List<List<String>>? = null,
    val sequencingHorizon: Duration? = null,
    val lockedHorizon: Duration? = null,
    val weatherFetchRadiusNm: Double? = null,
    val feederFixes: List<String> = emptyList(),
    val feederFixTimelineArrivalLabelLayoutId: String? = null,
    val feederFixTransitTimesMinutes: Map<String, Map<String, Int>>? = null,
    val arrivalProfiles: Map<String, List<ArrivalProfileYaml>> = emptyMap(),
)

class AirportDataJsonDeserializer : JsonDeserializer<AirportDataJson>() {
    override fun deserialize(parser: JsonParser, ctxt: DeserializationContext): AirportDataJson {
        val node = parser.codec.readTree<JsonNode>(parser)
        require(node is ObjectNode) {
            "Airport config must be a YAML object."
        }
        val raw = parser.codec.treeToValue(node, AirportDataJsonRaw::class.java)

        return AirportDataJson(
            location = raw.location,
            runwayThresholds = raw.runwayThresholds,
            independentRunwaySystems = raw.independentRunwaySystems,
            sequencingHorizon = raw.sequencingHorizon,
            lockedHorizon = raw.lockedHorizon,
            weatherFetchRadiusNm = raw.weatherFetchRadiusNm,
            feederFixes = raw.feederFixes,
            feederFixTimelineArrivalLabelLayoutId = raw.feederFixTimelineArrivalLabelLayoutId,
            feederFixTransitTimesMinutes = raw.feederFixTransitTimesMinutes,
            areas = node.get("areas").toAreasMap(),
            arrivalProfiles = raw.arrivalProfiles,
        )
    }
}

private fun JsonNode?.toAreasMap(): Map<String, JsonNode> {
    if (this == null || isNull) {
        return emptyMap()
    }

    require(this is ObjectNode) {
        "Airport areas must be a map of area ids to boundary definitions."
    }

    return fields().asSequence().associate { (areaId, areaNode) ->
        areaId to areaNode
    }
}
