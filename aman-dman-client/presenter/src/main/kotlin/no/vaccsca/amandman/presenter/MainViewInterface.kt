package no.vaccsca.amandman.presenter

import kotlinx.datetime.Instant
import no.vaccsca.amandman.model.domain.TimelineGroup
import no.vaccsca.amandman.model.domain.valueobjects.TrajectoryPoint
import no.vaccsca.amandman.model.domain.valueobjects.atcClient.ControllerInfoData

/**
 * Interface for the Main View in the MVP architecture.
 * Defines methods for global (non-airport-specific) UI updates.
 *
 * MainPresenter -> View communication
 */
interface MainViewInterface {
    var mainPresenterInterface: MainPresenterInterface

    fun openWindow()
    fun showErrorMessage(message: String)
    fun updateTime(currentTime: Instant)
    fun updateControllerInfo(controllerInfoData: ControllerInfoData)
    fun updateTimelineGroups(timelineGroups: List<TimelineGroup>)
    fun showTimelineGroup(airportIcao: String)

    fun openDescentProfileWindow(callsign: String)
    fun updateDescentTrajectory(callsign: String, trajectory: List<TrajectoryPoint>)
    fun openLogsWindow()

    fun createAirportViewDelegate(airportIcao: String, timelineGroup: TimelineGroup): AirportViewInterface
    fun removeAirportViewDelegate(airportIcao: String)
}
