package no.vaccsca.amandman.presenter

import kotlinx.datetime.Instant
import no.vaccsca.amandman.common.TimelineConfig
import no.vaccsca.amandman.common.domain.valueobjects.atcClient.ControllerInfoData
import no.vaccsca.amandman.common.domain.valueobjects.timelineEvent.RunwayEvent

/**
 * Interface for the View in the MVP architecture.
 * Defines methods that the Presenter can call to update the UI.
 *
 * Presenter -> View communication
 */
interface ViewInterface {
    var presenterInterface: PresenterInterface

    fun showTimelineGroup(airportIcao: String)
    fun updateControllerInfo(controllerInfoData: ControllerInfoData)
    fun updateTimelineGroups(timelineGroups: List<TimelineGroup>)
    fun openMetWindow(airportIcao: String)
    fun openLandingRatesWindow()
    fun openNonSequencedWindow()
    fun openDescentProfileWindow(callsign: String)
    fun showErrorMessage(message: String)
    fun openWindow()
    fun openSelectRunwayDialog(runwayEvent: RunwayEvent, runwayOptions: Set<String>, onClose: (String) -> Unit)
    fun updateTime(currentTime: Instant)

    // Timeline creation and editing
    fun openTimelineConfigForm(groupId: String, availableTagLayoutsDep: Set<String>, availableTagLayoutsArr: Set<String>, existingConfig: TimelineConfig? = null)
    fun closeTimelineForm()
    fun createAirportView(airportIcao: String, presenter: AirportPresenter): AirportViewInterface
    fun openStartWindow(availableAirports: Set<String>)
}