package no.vaccsca.amandman.presenter

import kotlinx.datetime.Instant
import no.vaccsca.amandman.common.TimelineConfig
import no.vaccsca.amandman.model.domain.TimelineGroup
import no.vaccsca.amandman.model.domain.valueobjects.TrajectoryPoint
import no.vaccsca.amandman.model.domain.valueobjects.weather.VerticalWeatherProfile
import no.vaccsca.amandman.model.data.dto.TabData
import no.vaccsca.amandman.model.domain.valueobjects.atcClient.ControllerInfoData
import no.vaccsca.amandman.model.domain.valueobjects.timelineEvent.RunwayEvent
import no.vaccsca.amandman.model.domain.valueobjects.timelineEvent.TimelineEvent
import java.awt.Point

/**
 * Interface for the View in the MVP architecture.
 * Defines methods that the Presenter can call to update the UI.
 *
 * Presenter -> View communication
 */
interface ViewInterface {
    var presenterInterface: PresenterInterface

    fun updateControllerInfo(controllerInfoData: ControllerInfoData)
    fun updateTimelineGroups(timelineGroups: List<TimelineGroup>)
    fun openMetWindow(airportIcao: String)
    fun openLandingRatesWindow()
    fun openNonSequencedWindow()
    fun updateWeatherData(airportIcao: String, weather: VerticalWeatherProfile?)
    fun openDescentProfileWindow(callsign: String)
    fun updateDescentTrajectory(callsign: String, trajectory: List<TrajectoryPoint>)
    fun updateTab(airportIcao: String, tabData: TabData)
    fun showAirportContextMenu(airportIcao: String, availableTimelines: List<TimelineConfig>, screenPos: Point)
    fun updateDraggedLabel(timelineEvent: TimelineEvent, newInstant: Instant, isAvailable: Boolean)
    fun updateRunwayModes(airportIcao: String, runwayModes: List<Pair<String, Boolean>>)
    fun showErrorMessage(message: String)
    fun openWindow()
    fun updateMinimumSpacing(airportIcao: String, minimumSpacingNm: Double)
    fun openSelectRunwayDialog(runwayEvent: RunwayEvent, runwayOptions: Set<String>, onClose: (String) -> Unit)
    fun showTimelineGroup(airportIcao: String)
    fun updateTime(currentTime: Instant)

    // Timeline creation and editing
    fun openTimelineConfigForm(groupId: String, availableTagLayoutsDep: Set<String>, availableTagLayoutsArr: Set<String>, existingConfig: TimelineConfig? = null)
    fun closeTimelineForm()
    fun showMinimumSpacingDialog(icao: String, d: Double)
    fun updateAtcClientConnectionStatus(isConnected: Boolean, hasStarted: Boolean, lastReceivedTimestamp: Instant?)
    fun updateMetStatus(airportIcao: String, status: MetStatus)

    enum class MetStatus {
        GREY,    // Initial state - not started
        YELLOW,  // Fetching data
        GREEN,   // Data fetched successfully
        RED      // Failed to fetch data
    }
}