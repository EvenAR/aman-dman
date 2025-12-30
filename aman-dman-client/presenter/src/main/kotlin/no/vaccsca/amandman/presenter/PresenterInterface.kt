package no.vaccsca.amandman.presenter

import no.vaccsca.amandman.common.domain.UserRole

/**
 * Interface defining the contract for the Presenter in the MVP architecture.
 * It handles user interactions and communicates with the View and Model layers.
 *
 * View -> Presenter communication
 */
interface PresenterInterface {
    fun onOpenMetWindowClicked(airportIcao: String)
    fun onOpenVerticalProfileWindowClicked(callsign: String)
    fun onStartButtonClicked()
    fun onNewTimelineGroup(airportIcao: String, userRole: UserRole)
    fun onReloadWindsClicked(airportIcao: String)
}
