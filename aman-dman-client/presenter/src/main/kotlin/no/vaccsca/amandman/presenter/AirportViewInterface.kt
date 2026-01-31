package no.vaccsca.amandman.presenter

import kotlinx.datetime.Instant
import no.vaccsca.amandman.common.TimelineConfig
import no.vaccsca.amandman.model.domain.valueobjects.NonSequencedEvent
import no.vaccsca.amandman.model.domain.valueobjects.timelineEvent.RunwayEvent
import no.vaccsca.amandman.model.domain.valueobjects.timelineEvent.TimelineEvent
import no.vaccsca.amandman.model.domain.valueobjects.weather.VerticalWeatherProfile
import java.awt.Point

/**
 * Interface for an Airport-specific View in the MVP architecture.
 * Defines methods for updating the UI of a single airport tab.
 *
 * AirportPresenter -> AirportView communication
 */
interface AirportViewInterface {
    var airportPresenterInterface: AirportPresenterInterface

    fun updateTab(timelineEvents: List<TimelineEvent>, nonSequencedList: List<NonSequencedEvent>)
    fun updateWeatherData(weather: VerticalWeatherProfile?)
    fun updateRunwayModes(runwayModes: List<Pair<String, Boolean>>)
    fun updateMinimumSpacing(minimumSpacingNm: Double)
    fun updateDraggedLabel(timelineEvent: TimelineEvent, newInstant: Instant, isAvailable: Boolean)

    fun showAirportContextMenu(availableTimelines: List<TimelineConfig>, screenPos: Point)
    fun openMetWindow()
    fun openLandingRatesWindow()
    fun openNonSequencedWindow()
    fun showMinimumSpacingDialog(default: Double)
    fun openSelectRunwayDialog(
        runwayEvent: RunwayEvent,
        runwayOptions: Set<String>,
        onSubmit: (String?) -> Unit,
        onCancel: () -> Unit
    )

    fun openTimelineConfigForm(
        availableTagLayoutsDep: Set<String>,
        availableTagLayoutsArr: Set<String>,
        existingConfig: TimelineConfig? = null
    )
    fun closeTimelineForm()

    fun addNewTimeline(timelineConfig: TimelineConfig)
    fun removeTimeline(timelineConfig: TimelineConfig)
}
