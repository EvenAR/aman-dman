package no.vaccsca.amandman.presenter

import no.vaccsca.amandman.common.domain.valueobjects.RunwayStatus

/**
 * Holds the complete state needed to generate runway mode labels.
 * This decouples the timing of when runway statuses vs minimum spacing updates arrive.
 */
data class RunwayModeState(
    val airportIcao: String,
    val runwayStatuses: Map<String, RunwayStatus>,
    val minimumSpacingNm: Double,
    val runwayModes: List<String> // From settings
) {
    /**
     * Generates the display labels by combining mode strings with spacing and active status
     */
    fun generateDisplayLabels(): List<Pair<String, Boolean>> {
        val activeArrivalRunways = runwayStatuses.filter { it.value.arrivals }.keys.toList().sorted()

        val airportLabel = Pair("[$airportIcao]", true)
        val modeLabel =
            when (activeArrivalRunways.size) {
                0 -> Pair("NO ACT RWY", false)
                1 -> Pair("S${activeArrivalRunways.first()}", true) // Single mode
                else -> Pair("M${activeArrivalRunways.joinToString("/")}", true) // Mixed mode (multiple runways)
            }

        val runwayModeLabels = runwayModes.map { mode -> formatLabel(mode, activeArrivalRunways) }

        return listOf(airportLabel, modeLabel) + runwayModeLabels
    }

    private fun formatLabel(modeString: String, activeArrivalRunways: List<String>): Pair<String, Boolean> {
        val isActive = activeArrivalRunways.any { runway ->
            modeString.contains(runway)
        }

        val displayLabel =
            if (modeString.startsWith("S"))
                modeString
            else
                "$modeString:$minimumSpacingNm"

        return Pair(displayLabel, isActive)
    }
}
