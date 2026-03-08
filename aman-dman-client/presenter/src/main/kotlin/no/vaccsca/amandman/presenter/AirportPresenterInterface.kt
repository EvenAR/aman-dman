package no.vaccsca.amandman.presenter

import kotlinx.datetime.Instant
import no.vaccsca.amandman.common.TimelineConfig
import no.vaccsca.amandman.model.timeline.CreateOrUpdateTimelineDto
import no.vaccsca.amandman.model.timeline.event.timeline.RunwayEvent
import no.vaccsca.amandman.model.timeline.event.timeline.TimelineEvent
import java.awt.Point

/**
 * Interface defining the contract for an Airport-specific Presenter in the MVP architecture.
 * Handles user interactions for a single airport tab.
 *
 * AirportView -> AirportPresenter communication
 */
interface AirportPresenterInterface {
    val airportIcao: String

    fun onLabelDrag(timelineEvent: TimelineEvent, newInstant: Instant)
    fun onLabelDragEnd(timelineEvent: TimelineEvent, newScheduledTime: Instant, newRunway: String? = null)

    fun onRecalculateSequenceClicked(callSign: String? = null)
    fun onMinimumSpacingDistanceSet(minimumSpacingDistanceNm: Double)
    fun onSetMinSpacingSelectionClicked(minSpacingSelectionNm: Double?)

    fun onOpenMetWindowClicked()
    fun onOpenLandingRatesWindow()
    fun onOpenNonSequencedWindow()
    fun onOpenVerticalProfileWindowClicked(callsign: String)
    fun onAircraftSelected(callsign: String)

    fun beginRunwaySelection(runwayEvent: RunwayEvent, onSubmit: (runway: String?) -> Unit, onCancel: () -> Unit)

    fun onToggleShowDepartures(selected: Boolean)
    fun onReloadWindsClicked()

    fun onTabMenu(screenPos: Point)
    fun onCreateNewTimelineClicked()
    fun onAddTimelineButtonClicked(timelineConfig: TimelineConfig)
    fun onRemoveTimelineClicked(timelineConfig: TimelineConfig)
    fun onEditTimelineRequested(timelineConfig: TimelineConfig)
    fun onMoveTimelineLeftRequested(timelineConfig: TimelineConfig)
    fun onMoveTimelineRightRequested(timelineConfig: TimelineConfig)
    fun onCreateNewTimeline(config: CreateOrUpdateTimelineDto)
    fun onDeleteEditedTimeline()

    fun onRemoveTab()
}
