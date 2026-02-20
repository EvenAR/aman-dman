package no.vaccsca.amandman.presenter

import no.vaccsca.amandman.model.airport.RunwayStatus

/**
 * Manages runway mode state for a single airport and automatically updates the view when any component changes.
 * Each AirportPresenter has its own instance.
 */
class AirportRunwayModeStateManager(
    private val airportIcao: String,
    private val view: AirportViewInterface
) {

    private var currentState: RunwayModeState? = null

    fun updateRunwayStatuses(runwayStatuses: Map<String, RunwayStatus>, minimumSpacingNm: Double) {
        val possibleRunwayModes = inferPossibleRunwayModes(runwayStatuses)
        val newState = RunwayModeState(airportIcao, runwayStatuses, minimumSpacingNm, possibleRunwayModes)
        currentState = newState
        updateView(newState)
    }

    fun updateMinimumSpacing(minimumSpacingNm: Double) {
        currentState?.let { state ->
            val updatedState = state.copy(minimumSpacingNm = minimumSpacingNm)
            currentState = updatedState
            updateView(updatedState)
        }
    }

    private fun updateView(state: RunwayModeState) {
        val displayLabels = state.generateDisplayLabels()
        view.updateRunwayModes(displayLabels)
    }

    private fun inferPossibleRunwayModes(runwayStatuses: Map<String, RunwayStatus>): List<String> {
        val allRunwayIds = runwayStatuses.keys.sorted()
        val runwaysWithSameDirection = allRunwayIds
            .groupBy { it.take(2) } // Assumes first two characters denote direction
            .filter { it.value.size >= 2 } // Two or more runways in same direction
            .map { it.value.joinToString("/") }

        return allRunwayIds + runwaysWithSameDirection
    }
}
