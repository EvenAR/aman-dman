package no.vaccsca.amandman.view.airport

import no.vaccsca.amandman.common.TimelineConfig
import no.vaccsca.amandman.common.domain.valueobjects.TimelineData
import no.vaccsca.amandman.presenter.AirportPresenterInterface
import no.vaccsca.amandman.view.AmanPopupMenu
import no.vaccsca.amandman.view.airport.timeline.TimelineView
import no.vaccsca.amandman.view.entity.TimeRange
import no.vaccsca.amandman.view.entity.SharedValue
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Point
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import kotlin.math.pow
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds


class TimelineScrollPane(
    val selectedTimeRange: SharedValue<TimeRange>,
    val availableTimeRange: SharedValue<TimeRange>,
    val presenter: AirportPresenterInterface,
) : JScrollPane(VERTICAL_SCROLLBAR_NEVER, HORIZONTAL_SCROLLBAR_AS_NEEDED) {

    private var minSpacingSelectionNm: Double? = null

    init {
        val items = JPanel(GridBagLayout())
        val gbc = GridBagConstraints()
        gbc.weightx = 1.0
        gbc.weighty = 1.0
        gbc.anchor = GridBagConstraints.WEST
        gbc.fill = GridBagConstraints.VERTICAL
        viewport.add(items)

        viewport.view.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mousePressed(e: java.awt.event.MouseEvent) = maybeShowPopup(e)
            override fun mouseReleased(e: java.awt.event.MouseEvent) = maybeShowPopup(e)

            private fun maybeShowPopup(e: java.awt.event.MouseEvent) {
                if (e.isPopupTrigger) {
                    val converted = javax.swing.SwingUtilities.convertPoint(e.component, e.point, viewport)
                    presenter.onTabMenu(converted)
                }
            }
        })
    }

    fun insertTimeline(timelineConfig: TimelineConfig) {
        val tl = TimelineView(timelineConfig, selectedTimeRange, presenter)
        val items = viewport.view as JPanel

        // Remove the previous glue (assumes it’s always the last component and a JLabel)
        if (items.componentCount > 0) {
            val last = items.getComponent(items.componentCount - 1)
            if (last is JLabel) {
                items.remove(last)
            }
        }

        val gbc = GridBagConstraints().apply {
            gridx = items.componentCount
            weightx = 0.0
            weighty = 1.0
            anchor = GridBagConstraints.WEST
            fill = GridBagConstraints.VERTICAL
        }
        items.add(tl, gbc)

        // Add new glue at the end
        val glue = JLabel()
        val glueConstraints = GridBagConstraints().apply {
            gridx = items.componentCount
            weightx = 1.0
            weighty = 0.0
            fill = GridBagConstraints.BOTH
        }
        items.add(glue, glueConstraints)

        items.revalidate()
        items.repaint()
    }


    fun updateTimelineEvents(timelineData: List<TimelineData>) {
        val items = viewport.view as JPanel
        timelineData.forEach {
           items.components.filterIsInstance<TimelineView>().forEach { timelineView ->
                if (timelineView.timelineConfig.title == it.timelineId) {
                    timelineView.updateTimelineData(it)
                }
            }
        }
    }

    // Zoom when using scrollwheel
    override fun processMouseWheelEvent(e: java.awt.event.MouseWheelEvent) {
        // Check if Shift is down -> horizontal scroll
        if (e.isShiftDown) {
            // Horizontal scroll
            val hBar = horizontalScrollBar
            val increment = hBar.unitIncrement * e.wheelRotation
            hBar.value += increment
        } else {
            // Vertical scroll -> zoom
            val currentRange = selectedTimeRange.value
            val rangeDuration = currentRange.end - currentRange.start
            val zoomFactor = 1.1.pow(e.wheelRotation.toDouble())
            val newDuration = (rangeDuration * zoomFactor).coerceAtLeast(1.seconds)
            val centerTime = currentRange.start + rangeDuration / 2
            val newEnd = centerTime + newDuration / 2

            if (newEnd > availableTimeRange.value.end || newEnd < currentRange.start + 1.seconds || newDuration < 10.minutes) {
                return
            }

            selectedTimeRange.value = TimeRange(currentRange.start, newEnd)
        }

        e.consume()
    }

    fun updateMinimumSpacingSelection(distanceNm: Double) {
        minSpacingSelectionNm = distanceNm
    }

    fun openPopupMenu(availableTimelines: List<TimelineConfig>, screenPos: Point) {
        val sorted = availableTimelines.sortedBy { it.title }

        val popup = AmanPopupMenu("Airport Actions") {
            item("Add timeline") {
                sorted.forEach { timeline ->
                    item(timeline.title, action = {
                        presenter.onAddTimelineButtonClicked(timeline)
                    })
                }
                separator()
                item("Custom ...", action = {
                    presenter.onCreateNewTimelineClicked()
                })
            }

            item("Final approach spacing", action = {
                presenter.onSetMinSpacingSelectionClicked(minSpacingSelectionNm)
            })

            item("Show winds", action = {
                presenter.onOpenMetWindowClicked()
            })

            separator()

            item("Close airport view", action = {
                presenter.onRemoveTab()
            })
        }

        popup.show(this, screenPos.x, screenPos.y)
    }
}
