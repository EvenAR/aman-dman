package no.vaccsca.amandman.presenter

import kotlinx.datetime.Instant
import no.vaccsca.amandman.common.TimelineConfig
import no.vaccsca.amandman.model.UserRole
import no.vaccsca.amandman.model.data.dto.CreateOrUpdateTimelineDto
import no.vaccsca.amandman.model.domain.valueobjects.timelineEvent.RunwayEvent
import no.vaccsca.amandman.model.domain.valueobjects.timelineEvent.TimelineEvent
import java.awt.Point

/**
 * Interface defining the contract for the Presenter in the MVP architecture.
 * It handles user interactions and communicates with the View and Model layers.
 *
 * View -> Presenter communication
 */
interface PresenterInterface {
    fun onReloadSettingsRequested()
    fun onOpenMetWindowClicked(airportIcao: String)
    fun onOpenVerticalProfileWindowClicked(callsign: String)
    fun onAircraftSelected(callsign: String)
    fun onEditTimelineRequested(groupId: String, timelineTitle: String)
    fun onNewTimelineGroup(airportIcao: String, userRole: UserRole)
    fun onOpenLandingRatesWindow(airportIcao: String)
    fun onOpenNonSequencedWindow(airportIcao: String)
    fun onLabelDragEnd(airportIcao: String, timelineEvent: TimelineEvent, newScheduledTime: Instant, newRunway: String? = null)
    fun onRecalculateSequenceClicked(airportIcao: String, callSign: String? = null)
    fun onRemoveTimelineClicked(timelineConfig: TimelineConfig)
    fun onLabelDrag(airportIcao: String, timelineEvent: TimelineEvent, newInstant: Instant)
    fun onMinimumSpacingDistanceSet(airportIcao: String, minimumSpacingDistanceNm: Double)
    fun beginRunwaySelection(runwayEvent: RunwayEvent, onSubmit: (runway: String?) -> Unit, onCancel: () -> Unit)
    fun onToggleShowDepartures(airportIcao: String, selected: Boolean)
    
    // Tab context menu actions
    fun onTabMenu(airportIcao: String, screenPos: Point)
    fun onCreateNewTimelineClicked(groupId: String)
    fun onRemoveTab(airportIcao: String)

    // New timeline
    fun onAddTimelineButtonClicked(airportIcao: String, timelineConfig: TimelineConfig)
    fun onCreateNewTimeline(config: CreateOrUpdateTimelineDto)
    fun onReloadWindsClicked(airportIcao: String)
    fun onSetMinSpacingSelectionClicked(icao: String, minSpacingSelectionNm: Double?)
    fun onOpenLogsWindowClicked()
}
