package no.vaccsca.amandman.presenter

import no.vaccsca.amandman.common.domain.valueobjects.RunwayStatus

/**
 * Manages runway mode state and automatically updates the view when any component changes.
 * This follows the Single Responsibility Principle and Observer Pattern.
 */
class RunwayModeStateManager(private val view: AirportViewInterface) {

    private val airportStates = mutableMapOf<String, RunwayModeState>()

    /**
     * Updates runway statuses for an airport and triggers view update
     */
    fun updateRunwayStatuses(airportIcao: String, runwayStatuses: Map<String, RunwayStatus>, minimumSpacingNm: Double) {
        val possibleRunwayModes =  inferPossibleRunwayModes(runwayStatuses)
        val newState = RunwayModeState(airportIcao, runwayStatuses, minimumSpacingNm, possibleRunwayModes)

        airportStates[airportIcao] = newState
        updateView(airportIcao, newState)
    }

    /**
     * Updates minimum spacing for all airports and triggers view updates
     */
    fun updateMinimumSpacing(minimumSpacingNm: Double) {
        airportStates.forEach { (airportIcao, currentState) ->
            val updatedState = currentState.copy(minimumSpacingNm = minimumSpacingNm)
            airportStates[airportIcao] = updatedState
            updateView(airportIcao, updatedState)
        }
    }

    /**
     * Refreshes all airport states (useful when settings change)
     */
    fun refreshAllStates() {
        airportStates.forEach { (airportIcao, currentState) ->
            val possibleRunwayModes =  inferPossibleRunwayModes(currentState.runwayStatuses)
            val updatedState = currentState.copy(runwayModes = possibleRunwayModes)
            airportStates[airportIcao] = updatedState
            updateView(airportIcao, updatedState)
        }
    }

    /**
     * Cleans up state for a specific airport when the tab is removed
     */
    fun cleanupAirportState(airportIcao: String) {
        airportStates.remove(airportIcao)
    }

    private fun updateView(airportIcao: String, state: RunwayModeState) {
        val displayLabels = state.generateDisplayLabels()
        //view.updateRunwayModes(airportIcao, displayLabels)
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
