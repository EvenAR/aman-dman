package no.vaccsca.amandman.presenter

import kotlinx.datetime.Instant
import no.vaccsca.amandman.common.TimelineConfig
import no.vaccsca.amandman.common.domain.UserRole
import no.vaccsca.amandman.common.domain.valueobjects.timelineEvent.RunwayEvent
import no.vaccsca.amandman.common.domain.valueobjects.timelineEvent.TimelineEvent
import no.vaccsca.amandman.common.dto.CreateOrUpdateTimelineDto
import java.awt.Point

/**
 * Interface defining the contract for the Presenter in the MVP architecture.
 * It handles user interactions and communicates with the View and Model layers.
 *
 * View -> Presenter communication
 */
interface AirportPresenterInterface {
    fun onAircraftSelected(callsign: String)
    fun onOpenLandingRatesWindow()
    fun onOpenNonSequencedWindow()
    fun onLabelDragEnd(timelineEvent: TimelineEvent, newScheduledTime: Instant, newRunway: String? = null)
    fun onRecalculateSequenceClicked(callSign: String? = null)
    fun onRemoveTimelineClicked(timelineConfig: TimelineConfig)
    fun onLabelDrag(timelineEvent: TimelineEvent, newInstant: Instant)
    fun onMinimumSpacingDistanceSet(minimumSpacingDistanceNm: Double)
    fun beginRunwaySelection(runwayEvent: RunwayEvent, onClose: (runway: String?) -> Unit)
    fun onToggleShowDepartures(selected: Boolean)
    
    // Tab context menu actions
    fun onTabMenu(screenPos: Point)
    fun onCreateNewTimelineClicked()
    fun onRemoveTab()

    // New timeline
    fun onAddTimelineButtonClicked(timelineConfig: TimelineConfig)
    fun onCreateNewTimeline(config: CreateOrUpdateTimelineDto)
    fun onReloadWindsClicked()
    fun onSetMinSpacingSelectionClicked(minSpacingSelectionNm: Double?)
    fun onOpenMetWindowClicked()
    fun onOpenVerticalProfileWindowClicked(callsign: String)
}
