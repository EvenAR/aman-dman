package no.vaccsca.amandman.model.airport

import no.vaccsca.amandman.model.navigation.LatLng
import kotlin.time.Duration

data class Airport(
    val icao: String,
    val location: LatLng,
    val runways: Map<String, RunwayThreshold>,
    val areas: Map<String, AirportArea> = emptyMap(),
    val independentRunwaySystems: List<Set<String>>,
    val sequencingHorizon: Duration,
    val lockedHorizon: Duration,
    val weatherFetchRadiusNm: Double = 200.0,
    val feederFixes: List<String> = emptyList(),
    val feederFixTimelineArrivalLabelLayoutId: String? = null,
    // Reserved for future fixed-transit feeder fix timing strategy.
    val feederFixTransitTimesMinutes: Map<String, Map<String, Int>> = emptyMap(),
)

data class RunwayThreshold(
    val id: String,
    val latLng: LatLng,
    val elevation: Float,
    val trueHeading: Float,
    val arrivalProfiles: List<RunwayArrivalProfile> = emptyList(),
) {
    val arrivalFixExpectations: List<ArrivalFixExpectation>
        get() = arrivalFixExpectationsFor(assignedArrivalName = null)

    fun arrivalFixExpectationsFor(assignedArrivalName: String?): List<ArrivalFixExpectation> {
        val mergedExpectations = mutableListOf<ArrivalFixExpectation>()
        val indexByFixName = mutableMapOf<String, Int>()

        arrivalProfiles
            .filter { it.matches(assignedArrivalName) }
            .forEach { profile ->
                profile.fixExpectations.forEach { fixExpectation ->
                    val fixKey = fixExpectation.fixName.uppercase()
                    val existingIndex = indexByFixName[fixKey]
                    if (existingIndex == null) {
                        indexByFixName[fixKey] = mergedExpectations.size
                        mergedExpectations += fixExpectation
                    } else {
                        mergedExpectations[existingIndex] = fixExpectation
                    }
                }
            }

        return mergedExpectations
    }

    fun frozenSequenceAreaIdFor(assignedArrivalName: String?): String? =
        arrivalProfiles
            .filter { it.matches(assignedArrivalName) }
            .mapNotNull { profile -> profile.frozenSequenceAreaId }
            .lastOrNull()
}

data class RunwayArrivalProfile(
    val arrivalNamePattern: String,
    val fixExpectations: List<ArrivalFixExpectation>,
    val frozenSequenceAreaId: String? = null,
) {
    fun matches(assignedArrivalName: String?): Boolean {
        val normalizedPattern = arrivalNamePattern.trim().uppercase()
        val normalizedArrivalName = assignedArrivalName?.trim()?.uppercase().orEmpty()
        val patternRegex = Regex(buildString {
            append("^")
            normalizedPattern.forEach { character ->
                if (character == '*') {
                    append(".*")
                } else {
                    append(Regex.escape(character.toString()))
                }
            }
            append("$")
        })
        return patternRegex.matches(normalizedArrivalName)
    }
}
