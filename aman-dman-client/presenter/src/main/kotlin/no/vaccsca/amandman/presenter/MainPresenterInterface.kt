package no.vaccsca.amandman.presenter

import no.vaccsca.amandman.model.UserRole

/**
 * Interface defining the contract for the Main Presenter in the MVP architecture.
 * Handles global (non-airport-specific) user interactions.
 *
 * View -> MainPresenter communication
 */
interface MainPresenterInterface {
    fun onReloadSettingsRequested()
    fun onNewTimelineGroup(airportIcao: String, userRole: UserRole)
    fun onOpenLogsWindowClicked()
    fun onOpenVerticalProfileWindowClicked(callsign: String)
    fun onAircraftSelected(callsign: String)
}
