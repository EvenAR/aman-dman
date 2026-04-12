package no.vaccsca.amandman.model.config.mapper

import no.vaccsca.amandman.model.airport.ArrivalFixExpectation
import no.vaccsca.amandman.model.airport.ArrivalFixRole
import no.vaccsca.amandman.model.airport.RunwayArrivalProfile
import no.vaccsca.amandman.model.config.yaml.ArrivalFixRoleYaml
import no.vaccsca.amandman.model.config.yaml.ArrivalProfileFixYaml
import no.vaccsca.amandman.model.config.yaml.ArrivalProfileYaml

private val FIX_NAME_REGEX = Regex("^[A-Z0-9]{1,5}$")
private val ARRIVAL_NAME_PATTERN_REGEX = Regex("^[A-Z0-9*]+$")
private val RUNWAY_PATTERN_REGEX = Regex("^[A-Z0-9*]+$")

internal fun Map<String, List<ArrivalProfileYaml>>.toRunwayProfilesByRunway(
    airportIcao: String,
    availableRunways: Set<String>,
    availableAreaIds: Set<String>,
): Map<String, List<RunwayArrivalProfile>> {
    val profilesByRunway = availableRunways.associateWith { mutableListOf<RunwayArrivalProfile>() }

    entries.forEach { (rawRunwayPattern, profiles) ->
        val runwayPattern = rawRunwayPattern.trim().uppercase()
        require(runwayPattern.isNotBlank()) {
            "Airport $airportIcao arrivalProfiles contains a blank runway pattern."
        }
        require(RUNWAY_PATTERN_REGEX.matches(runwayPattern)) {
            "Airport $airportIcao arrivalProfiles has invalid runway pattern '$rawRunwayPattern'. " +
                "Expected uppercase alphanumeric characters and '*'."
        }
        val matchingRunways = availableRunways.filter { runwayIdentifier ->
            runwayPattern.matchesGlob(runwayIdentifier)
        }
        require(matchingRunways.isNotEmpty()) {
            "Airport $airportIcao arrivalProfiles runway pattern $runwayPattern matches no configured runways."
        }

        val domainProfiles = profiles.toDomainProfiles(
            airportIcao = airportIcao,
            runwayPattern = runwayPattern,
            availableAreaIds = availableAreaIds,
        )

        matchingRunways.forEach { runwayIdentifier ->
            profilesByRunway.getValue(runwayIdentifier).addAll(domainProfiles)
        }
    }

    return profilesByRunway
        .filterValues { it.isNotEmpty() }
        .mapValues { (_, profiles) -> profiles.toList() }
}

private fun List<ArrivalProfileYaml>.toDomainProfiles(
    airportIcao: String,
    runwayPattern: String,
    availableAreaIds: Set<String>,
): List<RunwayArrivalProfile> {
        val normalizedArrivalNames = mutableSetOf<String>()

    return mapIndexed { index, profile ->
        val rowPrefix = "Airport $airportIcao runway pattern $runwayPattern arrival profile ${index + 1}"
        val normalizedArrivalName = profile.arrivalName.trim().uppercase()
        require(normalizedArrivalName.isNotBlank()) {
            "$rowPrefix has a blank arrivalName pattern."
        }
        require(ARRIVAL_NAME_PATTERN_REGEX.matches(normalizedArrivalName)) {
            "$rowPrefix has invalid arrivalName pattern '${profile.arrivalName}'. Expected uppercase alphanumeric characters and '*'."
        }
        require(normalizedArrivalNames.add(normalizedArrivalName)) {
            "$rowPrefix duplicates arrivalName pattern $normalizedArrivalName."
        }
        require(profile.fixes.isNotEmpty()) {
            "$rowPrefix must define at least one fix."
        }
        val frozenSequenceAreaId = profile.frozenSequenceArea?.trim()
        frozenSequenceAreaId?.let { frozenSequenceArea ->
            require(frozenSequenceArea.isNotBlank()) {
                "$rowPrefix has a blank frozenSequenceArea."
            }
            require(frozenSequenceArea in availableAreaIds) {
                "$rowPrefix references unknown frozenSequenceArea '$frozenSequenceArea'."
            }
        }

        RunwayArrivalProfile(
            arrivalNamePattern = normalizedArrivalName,
            frozenSequenceAreaId = frozenSequenceAreaId,
            fixExpectations = profile.fixes.toDomainFixExpectations(
                airportIcao = airportIcao,
                runwayPattern = runwayPattern,
                arrivalNamePattern = normalizedArrivalName,
            ),
        )
    }
}

private fun List<ArrivalProfileFixYaml>.toDomainFixExpectations(
    airportIcao: String,
    runwayPattern: String,
    arrivalNamePattern: String,
): List<ArrivalFixExpectation> {
    val seenFixes = mutableSetOf<String>()

    return mapIndexed { index, fixRow ->
        val rowPrefix =
            "Airport $airportIcao runway pattern $runwayPattern arrival profile $arrivalNamePattern fix row ${index + 1}"
        val normalizedFixName = fixRow.fix.trim().uppercase()
        require(FIX_NAME_REGEX.matches(normalizedFixName)) {
            "$rowPrefix has invalid fix '${fixRow.fix}'. Expected 1-5 uppercase alphanumeric characters."
        }
        require(seenFixes.add(normalizedFixName)) {
            "$rowPrefix duplicates fix $normalizedFixName within the same arrival profile."
        }

        fixRow.altitude?.let {
            require(it > 0) { "$rowPrefix has invalid altitude=$it. Value must be > 0." }
        }
        fixRow.speed?.let {
            require(it > 0) { "$rowPrefix has invalid speed=$it. Value must be > 0." }
        }
        require(fixRow.role != null || fixRow.altitude != null || fixRow.speed != null) {
            "$rowPrefix must define at least one of role, altitude, or speed."
        }

        ArrivalFixExpectation(
            fixName = normalizedFixName,
            role = fixRow.role?.toDomain(),
            typicalAltitude = fixRow.altitude,
            typicalSpeedIas = fixRow.speed,
        )
    }
}

private fun String.matchesGlob(value: String): Boolean {
    val patternRegex = Regex(buildString {
        append("^")
        this@matchesGlob.forEach { character ->
            if (character == '*') {
                append(".*")
            } else {
                append(Regex.escape(character.toString()))
            }
        }
        append("$")
    })
    return patternRegex.matches(value.uppercase())
}

private fun ArrivalFixRoleYaml.toDomain(): ArrivalFixRole =
    when (this) {
        ArrivalFixRoleYaml.IF -> ArrivalFixRole.IF
        ArrivalFixRoleYaml.IAF -> ArrivalFixRole.IAF
    }
