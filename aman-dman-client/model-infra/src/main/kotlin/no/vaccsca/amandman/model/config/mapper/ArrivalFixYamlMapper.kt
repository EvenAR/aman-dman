package no.vaccsca.amandman.model.config.mapper

import no.vaccsca.amandman.model.airport.ArrivalFixExpectation
import no.vaccsca.amandman.model.airport.ArrivalFixRole
import no.vaccsca.amandman.model.config.yaml.ArrivalFixRoleYaml
import no.vaccsca.amandman.model.config.yaml.ArrivalFixYamlEntry
import org.slf4j.LoggerFactory

private val FIX_NAME_REGEX = Regex("^[A-Z0-9]{1,5}$")
private const val ARRIVAL_FIX_YAML_MAPPER_LOGGER_NAME = "no.vaccsca.amandman.model.config.mapper.ArrivalFixYamlMapper"
internal var arrivalFixWarningLogger: (String) -> Unit = { message ->
    LoggerFactory.getLogger(ARRIVAL_FIX_YAML_MAPPER_LOGGER_NAME).warn(message)
}

internal fun List<ArrivalFixYamlEntry>.toRunwayExpectationsByRunway(
    airportIcao: String,
    availableRunways: Set<String>,
): Map<String, List<ArrivalFixExpectation>> {
    val normalizedRows = mapIndexed { index, entry ->
        entry.toNormalizedRow(airportIcao, index + 1)
    }

    normalizedRows.forEach { row ->
        row.runwayIdentifiers.forEach { runwayIdentifier ->
            require(runwayIdentifier in availableRunways) {
                "Airport $airportIcao arrival fix ${row.fixName} references unknown runway $runwayIdentifier."
            }
        }
    }

    val seenFixesByRunway = mutableSetOf<Pair<String, String>>()
    val seenRolesByRunway = mutableMapOf<Pair<String, ArrivalFixRole>, String>()

    normalizedRows.forEach { row ->
        row.runwayIdentifiers.forEach { runwayIdentifier ->
            require(seenFixesByRunway.add(runwayIdentifier to row.fixName)) {
                "Airport $airportIcao has duplicate arrival fix ${row.fixName} for runway $runwayIdentifier."
            }

            row.role?.let { role ->
                val previousFix = seenRolesByRunway.putIfAbsent(runwayIdentifier to role, row.fixName)
                if (previousFix != null && role == ArrivalFixRole.IF) {
                    arrivalFixWarningLogger(
                        "Airport $airportIcao runway $runwayIdentifier has multiple IF fixes ($previousFix, ${row.fixName})."
                    )
                }
            }
        }
    }

    return normalizedRows
        .flatMap { row ->
            row.runwayIdentifiers.map { runwayIdentifier ->
                runwayIdentifier to row.toDomain()
            }
        }
        .groupBy(keySelector = { it.first }, valueTransform = { it.second })
}

private fun ArrivalFixYamlEntry.toNormalizedRow(
    airportIcao: String,
    rowNumber: Int,
): NormalizedArrivalFixRow {
    val rowPrefix = "Airport $airportIcao arrival fix row $rowNumber"
    val normalizedFixName = name.trim().uppercase()
    require(FIX_NAME_REGEX.matches(normalizedFixName)) {
        "$rowPrefix has invalid name '$name'. Expected 1-5 uppercase alphanumeric characters."
    }

    val normalizedRunways = runways.map { it.trim().uppercase() }
    require(normalizedRunways.isNotEmpty()) {
        "$rowPrefix must define at least one runway."
    }
    require(normalizedRunways.none { it.isBlank() }) {
        "$rowPrefix contains a blank runway."
    }
    require(normalizedRunways.distinct().size == normalizedRunways.size) {
        "$rowPrefix contains duplicate runways."
    }

    typicalAltitude?.let {
        require(it > 0) { "$rowPrefix has invalid typicalAltitude=$it. Value must be > 0." }
    }
    typicalAirspeed?.let {
        require(it > 0) { "$rowPrefix has invalid typicalAirspeed=$it. Value must be > 0." }
    }
    require(role != null || typicalAltitude != null || typicalAirspeed != null) {
        "$rowPrefix must define at least one of role, typicalAltitude, or typicalAirspeed."
    }

    return NormalizedArrivalFixRow(
        fixName = normalizedFixName,
        runwayIdentifiers = normalizedRunways,
        role = role?.toDomain(),
        typicalAltitude = typicalAltitude,
        typicalAirspeed = typicalAirspeed,
    )
}

private data class NormalizedArrivalFixRow(
    val fixName: String,
    val runwayIdentifiers: List<String>,
    val role: ArrivalFixRole?,
    val typicalAltitude: Int?,
    val typicalAirspeed: Int?,
) {
    fun toDomain() = ArrivalFixExpectation(
        fixName = fixName,
        role = role,
        typicalAltitude = typicalAltitude,
        typicalSpeedIas = typicalAirspeed,
    )
}

private fun ArrivalFixRoleYaml.toDomain(): ArrivalFixRole =
    when (this) {
        ArrivalFixRoleYaml.IF -> ArrivalFixRole.IF
        ArrivalFixRoleYaml.IAF -> ArrivalFixRole.IAF
    }
