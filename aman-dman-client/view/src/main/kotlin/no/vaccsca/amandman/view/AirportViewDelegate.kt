package no.vaccsca.amandman.view

import kotlinx.datetime.Instant
import no.vaccsca.amandman.common.TimelineConfig
import no.vaccsca.amandman.model.domain.valueobjects.NonSequencedEvent
import no.vaccsca.amandman.model.domain.valueobjects.timelineEvent.RunwayEvent
import no.vaccsca.amandman.model.domain.valueobjects.timelineEvent.TimelineEvent
import no.vaccsca.amandman.model.domain.valueobjects.weather.VerticalWeatherProfile
import no.vaccsca.amandman.presenter.AirportPresenterInterface
import no.vaccsca.amandman.presenter.AirportViewInterface
import no.vaccsca.amandman.view.dialogs.RunwayDialog
import no.vaccsca.amandman.view.dialogs.SpacingDialog
import no.vaccsca.amandman.view.entity.AirportViewState
import no.vaccsca.amandman.view.entity.DraggedLabelState
import no.vaccsca.amandman.view.forms.NewTimelineForm
import java.awt.Point
import javax.swing.JDialog
import javax.swing.JFrame
import javax.swing.SwingUtilities

/**
 * Delegate that implements AirportViewInterface by wrapping an AirportView and its state.
 * Handles all airport-specific view operations.
 */
class AirportViewDelegate(
    private val parentFrame: JFrame,
    private val airportView: AirportView,
    private val airportViewState: AirportViewState,
) : AirportViewInterface {

    override var airportPresenterInterface: AirportPresenterInterface
        get() = airportView.presenter
        set(value) {
            airportView.presenter = value
        }

    private var newTimelineForm: JDialog? = null

    override fun updateTab(timelineEvents: List<TimelineEvent>, nonSequencedList: List<NonSequencedEvent>) = runOnUiThread {
        airportViewState.events.value = timelineEvents
        airportViewState.nonSequencedList.value = nonSequencedList
    }

    override fun updateWeatherData(weather: VerticalWeatherProfile?) = runOnUiThread {
        airportViewState.weatherProfile.value = weather
    }

    override fun updateRunwayModes(runwayModes: List<Pair<String, Boolean>>) = runOnUiThread {
        airportViewState.runwayModes.value = runwayModes
    }

    override fun updateMinimumSpacing(minimumSpacingNm: Double) = runOnUiThread {
        airportViewState.minimumSpacingNm.value = minimumSpacingNm
    }

    override fun updateDraggedLabel(timelineEvent: TimelineEvent, newInstant: Instant, isAvailable: Boolean) = runOnUiThread {
        airportViewState.draggedLabelState.value = DraggedLabelState(
            timelineEvent = timelineEvent,
            proposedTime = newInstant,
            isAvailable = isAvailable
        )
    }

    override fun showAirportContextMenu(availableTimelines: List<TimelineConfig>, screenPos: Point) = runOnUiThread {
        airportView.openPopupMenu(availableTimelines, screenPos)
    }

    override fun openMetWindow() = runOnUiThread {
        airportView.openMetWindow()
    }

    override fun openLandingRatesWindow() = runOnUiThread {
        airportView.openLandingRatesWindow()
    }

    override fun openNonSequencedWindow() = runOnUiThread {
        airportView.openNonSequencedWindow()
    }

    override fun showMinimumSpacingDialog(default: Double) = runOnUiThread {
        SpacingDialog.open(parentFrame, airportViewState.airportIcao, default) { newValue ->
            airportPresenterInterface.onMinimumSpacingDistanceSet(newValue)
        }
    }

    override fun openSelectRunwayDialog(
        runwayEvent: RunwayEvent,
        runwayOptions: Set<String>,
        onSubmit: (String?) -> Unit,
        onCancel: () -> Unit
    ) = runOnUiThread {
        RunwayDialog.open(
            parent = parentFrame,
            runwayEvent = runwayEvent,
            runwayOptions = runwayOptions,
            onSubmit = onSubmit,
            onCancel = onCancel
        )
    }

    override fun openTimelineConfigForm(
        availableTagLayoutsDep: Set<String>,
        availableTagLayoutsArr: Set<String>,
        existingConfig: TimelineConfig?
    ) = runOnUiThread {
        val groupId = airportViewState.airportIcao
        if (newTimelineForm != null) {
            newTimelineForm?.isVisible = true
        } else {
            newTimelineForm = JDialog(parentFrame, "New timeline for $groupId").apply {
                defaultCloseOperation = JDialog.DISPOSE_ON_CLOSE
                contentPane = NewTimelineForm(airportPresenterInterface, groupId, existingConfig)
                pack()
                setLocationRelativeTo(null)
                isVisible = true
            }
        }
        val timelineForm = newTimelineForm?.contentPane as? NewTimelineForm
        timelineForm?.update(
            arrLayouts = availableTagLayoutsArr,
            depLayouts = availableTagLayoutsDep
        )
    }

    override fun closeTimelineForm() = runOnUiThread {
        newTimelineForm?.isVisible = false
        newTimelineForm?.dispose()
        newTimelineForm = null
    }

    override fun addNewTimeline(timelineConfig: TimelineConfig) {
        airportViewState.openTimelines.value += timelineConfig
    }

    override fun removeTimeline(timelineConfig: TimelineConfig) {
        airportViewState.openTimelines.value -= timelineConfig
    }

    override fun setSelectedAircraftCallsign(callsign: String) {
        airportViewState.selectedAircraftCallsign.value = callsign
        // Set timestamp when selection changes (even if cleared)
        airportViewState.selectedAircraftTimestamp.value = if (callsign.isNotEmpty()) {
            no.vaccsca.amandman.common.NtpClock.now()
        } else {
            null
        }
    }

    private fun runOnUiThread(block: () -> Unit) {
        if (SwingUtilities.isEventDispatchThread()) {
            block()
        } else {
            SwingUtilities.invokeLater(block)
        }
    }
}
