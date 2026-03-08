package no.vaccsca.amandman.view

import kotlinx.datetime.Instant
import no.vaccsca.amandman.common.NtpClock
import no.vaccsca.amandman.common.TimelineConfig
import no.vaccsca.amandman.model.integration.IntegrationDisplayStatus
import no.vaccsca.amandman.model.integration.IntegrationKind
import no.vaccsca.amandman.model.timeline.FeederFixState
import no.vaccsca.amandman.model.timeline.event.NonSequencedEvent
import no.vaccsca.amandman.model.timeline.event.timeline.RunwayEvent
import no.vaccsca.amandman.model.timeline.event.timeline.TimelineEvent
import no.vaccsca.amandman.model.weather.VerticalWeatherProfile
import no.vaccsca.amandman.presenter.AirportPresenterInterface
import no.vaccsca.amandman.presenter.AirportViewInterface
import no.vaccsca.amandman.view.dialogs.RunwayDialog
import no.vaccsca.amandman.view.entity.AircraftSelection
import no.vaccsca.amandman.view.entity.AirportViewState
import no.vaccsca.amandman.view.entity.DraggedLabelState
import no.vaccsca.amandman.view.forms.NewTimelineForm
import java.awt.Dimension
import java.awt.Point
import javax.swing.JDialog
import javax.swing.JFrame
import javax.swing.JOptionPane
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

    override fun updateIntegrationStatuses(statuses: Map<IntegrationKind, IntegrationDisplayStatus>) = runOnUiThread {
        airportViewState.integrationStatuses.value = statuses
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

    override fun updateFeederFixState(feederFixState: FeederFixState) = runOnUiThread {
        airportViewState.feederFixState.value = feederFixState
    }

    override fun showAirportContextMenu(
        customizedTimelines: List<TimelineConfig>,
        generatedFixTimelines: List<TimelineConfig>,
        screenPos: Point
    ) = runOnUiThread {
        airportView.openPopupMenu(customizedTimelines, generatedFixTimelines, screenPos)
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
        airportView.openMinimumSpacingWindow(default) { newValue ->
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
        availableRunways: Set<String>,
        availableFixes: Set<String>,
        existingConfig: TimelineConfig?,
        canDeleteExistingConfig: Boolean,
    ) = runOnUiThread {
        val groupId = airportViewState.airportIcao
        // Always recreate the form when opening so edit/create requests never reuse stale prefilled values.
        newTimelineForm?.dispose()
        newTimelineForm = JDialog(parentFrame, "New timeline for $groupId").apply {
            defaultCloseOperation = JDialog.DISPOSE_ON_CLOSE
            contentPane = NewTimelineForm(airportPresenterInterface, groupId, existingConfig, availableRunways, availableFixes, canDeleteExistingConfig)
            pack()
            minimumSize = Dimension(520, 460)
            isResizable = true
            setLocationRelativeTo(null)
            isVisible = true
        }

        val timelineForm = newTimelineForm?.contentPane as? NewTimelineForm
        timelineForm?.update(
            arrLayouts = availableTagLayoutsArr,
            depLayouts = availableTagLayoutsDep,
            availableRunways = availableRunways,
            availableFixes = availableFixes,
        )
    }


    override fun confirmTimelineOverwrite(title: String): Boolean = runOnUiThreadAndWait {
        JOptionPane.showConfirmDialog(
            parentFrame,
            "A saved timeline named $title already exists. Overwrite it?",
            "Overwrite Timeline",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE,
        ) == JOptionPane.YES_OPTION
    }
    override fun closeTimelineForm() = runOnUiThread {
        newTimelineForm?.isVisible = false
        newTimelineForm?.dispose()
        newTimelineForm = null
    }

    override fun addNewTimeline(timelineConfig: TimelineConfig) {
        airportViewState.openTimelines.update { current ->
            if (timelineConfig in current) current else current + timelineConfig
        }
    }

    override fun removeTimeline(timelineConfig: TimelineConfig) {
        airportViewState.openTimelines.update { current ->
            current.filterNot { it == timelineConfig }
        }
    }

    override fun replaceTimeline(previous: TimelineConfig, updated: TimelineConfig) {
        airportViewState.openTimelines.update { current ->
            val ordered = current.toMutableList()
            val index = ordered.indexOf(previous)
            if (index == -1) {
                if (updated in ordered) current else current + updated
            } else if (updated in ordered && updated != previous) {
                ordered.removeAt(index)
                ordered
            } else {
                ordered.also { it[index] = updated }
            }
        }
    }

    override fun moveTimeline(timelineConfig: TimelineConfig, positions: Int) {
        if (positions == 0) return

        airportViewState.openTimelines.update { current ->
            val ordered = current.toMutableList()
            val index = ordered.indexOf(timelineConfig)
            if (index == -1) return@update current

            val targetIndex = (index + positions).coerceIn(0, ordered.lastIndex)
            if (targetIndex == index) return@update current

            ordered.also { list ->
                val timeline = list.removeAt(index)
                list.add(targetIndex, timeline)
            }
        }
    }

    override fun setSelectedAircraftCallsign(callsign: String) {
        airportViewState.aircraftSelection.value = if (callsign.isNotEmpty()) {
            AircraftSelection(callsign, NtpClock.now())
        } else {
            null
        }
    }


    private fun <T> runOnUiThreadAndWait(block: () -> T): T {
        if (SwingUtilities.isEventDispatchThread()) {
            return block()
        }

        var result: T? = null
        SwingUtilities.invokeAndWait {
            result = block()
        }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }
    private fun runOnUiThread(block: () -> Unit) {
        if (SwingUtilities.isEventDispatchThread()) {
            block()
        } else {
            SwingUtilities.invokeLater(block)
        }
    }
}
