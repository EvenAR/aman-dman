package no.vaccsca.amandman.view.airport.timeline

import kotlinx.datetime.Instant
import no.vaccsca.amandman.common.NtpClock
import no.vaccsca.amandman.common.TimelineConfig
import no.vaccsca.amandman.model.domain.valueobjects.LabelItem
import no.vaccsca.amandman.model.domain.valueobjects.TimelineData
import no.vaccsca.amandman.model.domain.valueobjects.sequence.SequenceStatus
import no.vaccsca.amandman.model.domain.valueobjects.timelineEvent.*
import no.vaccsca.amandman.presenter.AirportPresenterInterface
import no.vaccsca.amandman.view.airport.timeline.labels.ArrivalLabel
import no.vaccsca.amandman.view.airport.timeline.labels.DepartureLabel
import no.vaccsca.amandman.view.airport.timeline.labels.TimelineLabel
import no.vaccsca.amandman.view.airport.timeline.utils.GraphicUtils.drawStringAdvanced
import no.vaccsca.amandman.view.entity.AirportViewState
import no.vaccsca.amandman.view.entity.DraggedLabelState
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import java.text.SimpleDateFormat
import java.util.*
import javax.swing.JPanel
import javax.swing.SwingUtilities

class TimelineOverlay(
    val timelineConfig: TimelineConfig,
    val timelineView: TimelineView,
    private val presenterProvider: () -> AirportPresenterInterface,
    val airportViewState: AirportViewState,
    val arrivalLabelLayout: List<LabelItem>,
    val departureLabelLayout: List<LabelItem>,
) : JPanel(null) {

    private val presenter: AirportPresenterInterface get() = presenterProvider()
    private val baseFont = Font(Font.MONOSPACED, Font.PLAIN, 12)

    // --- Constants ---
    private val labelHBorder = 3         // Horizontal padding inside labels
    private val labelVBorder = 0         // Vertical padding inside labels
    private val pointDiameter = 6       // Diameter of the dot on the timescale
    private val scaleMargin = 30        // Distance between timescale and labels
    private val timelinePadding = 10   // Padding between timeline edge and labels
    private val timeFormat = SimpleDateFormat("HH:mm")

    // --- State ---
    private val labels = hashMapOf<String, TimelineLabel>()
    private var leftEvents: List<TimelineEvent>? = null
    private var rightEvents: List<TimelineEvent>? = null
    private var isDraggingLabel = false
    private var draggedLabelCopy: TimelineLabel? = null
    private var draggedLabelOriginalX = 0
    private var draggedLabelState: DraggedLabelState? = null

    // --- UI ---
    init {
        isOpaque = false

        airportViewState.draggedLabelState.addListener { newDraggedLabelState ->
            updateDraggedLabel(newDraggedLabelState)
        }
    }

    fun updateTimelineData(timelineData: TimelineData) {
        leftEvents = timelineData.left
        rightEvents = timelineData.right
        val allEvents = (leftEvents ?: emptyList()) + (rightEvents ?: emptyList())
        syncLabelsWithEvents(allEvents)
        revalidate()
        repaint()
    }

    override fun doLayout() {
        super.doLayout()
        rearrangeLabels()
    }

    private fun updateDraggedLabel(newState: DraggedLabelState?) {
        draggedLabelState = newState

        if (newState == null) {
            // Clear any existing dragged label copy
            draggedLabelCopy?.let { remove(it) }
            draggedLabelCopy = null
            repaint()
            return
        }

        val sourceEvent = newState.timelineEvent

        fun reposition(copy: TimelineLabel) {
            val yOnTimeline = timelineView.calculateYPositionForInstant(newState.proposedTime)
            val pointInOverlay = SwingUtilities.convertPoint(timelineView, 0, yOnTimeline, this)
            val targetY = pointInOverlay.y - copy.preferredSize.height / 2
            copy.setLocation(copy.x, targetY)
            copy.timelineEvent = sourceEvent
            copy.updateText()
            copy.updateColors()
            copy.repaint()
        }

        if (draggedLabelCopy != null) {
            reposition(draggedLabelCopy!!)
        } else {
            val original = labels.values.firstOrNull { it.timelineEvent == sourceEvent }
            if (original != null) {
                val copy = createLabelCopy(original)
                if (copy != null) {
                    add(copy)
                    setComponentZOrder(copy, 0)
                    copy.onDragStart()
                    reposition(copy)
                    draggedLabelCopy = copy
                }
            }
        }

        repaint()
    }

    private fun containsEventLabel(timelineEvent: TimelineEvent) =
        labels.values.any { it.timelineEvent.getFlight()?.callsign == timelineEvent.getFlight()?.callsign }

    private fun isDualTimeline() = timelineConfig.runwaysLeft.isNotEmpty() && timelineConfig.runwaysRight.isNotEmpty()

    private fun computedLabelWidth(): Int {
        val maxLabelLength = maxOf(
            arrivalLabelLayout.sumOf { it.width },
            departureLabelLayout.sumOf { it.width }
        )
        val dummyLabelContent = "-".repeat(maxLabelLength)
        val fm = getFontMetrics(baseFont)
        val typicalSize = fm.stringWidth(dummyLabelContent)
        return typicalSize + labelHBorder * 2
    }

    override fun getPreferredSize(): Dimension {
        val scaleW = timelineView.getScaleWidth()
        val labelWidth = computedLabelWidth()
        val width = if (isDualTimeline()) {
            scaleW + 2 * (labelWidth + scaleMargin) + timelinePadding * 2
        } else {
            scaleW + labelWidth + scaleMargin + timelinePadding
        }
        return Dimension(width, super.getPreferredSize().height)
    }

    // --- Layout ---
    private fun rearrangeLabels() {
        var previousTopLeft: Int? = null
        var previousTopRight: Int? = null
        val labelWidth = computedLabelWidth()

        val leftSet = (leftEvents ?: emptyList()).toSet()
        val rightSet = (rightEvents ?: emptyList()).toSet()

        val leftLabels = labels.values.filter { it.timelineEvent in leftSet }
        val rightLabels = labels.values.filter { it.timelineEvent in rightSet }

        leftLabels.sortedBy { it.getTimelinePlacement() }.forEach { label ->
            val dotY = timelineView.calculateYPositionForInstant(label.getTimelinePlacement())
            val centerY = dotY - label.preferredSize.height / 2
            val labelX = timelineView.getScaleBounds().x - labelWidth - scaleMargin
            val labelY = previousTopLeft?.let { minOf(it - 3, centerY) } ?: centerY
            label.setBounds(labelX, labelY, labelWidth, label.preferredSize.height)
            previousTopLeft = label.y - label.preferredSize.height
        }

        rightLabels.sortedBy { it.getTimelinePlacement() }.forEach { label ->
            val dotY = timelineView.calculateYPositionForInstant(label.getTimelinePlacement())
            val centerY = dotY - label.preferredSize.height / 2
            val labelX = timelineView.getScaleBounds().x + timelineView.getScaleBounds().width + scaleMargin
            val labelY = previousTopRight?.let { minOf(it - 3, centerY) } ?: centerY
            label.setBounds(labelX, labelY, labelWidth, label.preferredSize.height)
            previousTopRight = label.y - label.preferredSize.height
        }
    }

    // --- Painting ---
    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        doLayout()
        drawLinesFromLabelsToTimeScale(g)
        drawDraggedLabelLine(g)
        drawHourglasses(g)
        drawTimelineTitle(g)
        drawProposedTime(g)
    }

    private fun drawTimelineTitle(g: Graphics) {
        val scaleBounds = timelineView.getScaleBounds()
        g.color = Color.BLACK
        val vPadding = 2
        val hPadding = 4
        g.drawStringAdvanced(
            text = timelineConfig.title,
            x = if (isDualTimeline()) scaleBounds.x + scaleBounds.width / 2 else scaleBounds.x,
            y = scaleBounds.y + scaleBounds.height - g.fontMetrics.height - vPadding * 2,
            backgroundColor = Color.LIGHT_GRAY,
            hPadding = hPadding,
            vPadding = vPadding,
            hCenter = isDualTimeline(),
            vCenter = false,
        )
    }

    private fun drawLinesFromLabelsToTimeScale(g: Graphics) {
        val scaleBounds = timelineView.getScaleBounds()
        labels.values.forEach { label ->
            val isOnRightSide = label.x > scaleBounds.x
            val labelX = if (isOnRightSide) label.x else label.x + label.width
            val dotX = if (isOnRightSide) scaleBounds.x + scaleBounds.width else scaleBounds.x
            val dotY = timelineView.calculateYPositionForInstant(label.getTimelinePlacement())
            val event = label.timelineEvent
            g.color = if (event is RunwayArrivalEvent && event.sequenceStatus == SequenceStatus.OK) Color.WHITE else Color.GRAY
            g.drawLine(labelX, label.y + label.preferredSize.height / 2, dotX, dotY)
            g.fillOval(dotX - pointDiameter / 2, dotY - pointDiameter / 2, pointDiameter, pointDiameter)
        }
    }

    private fun drawDraggedLabelLine(g: Graphics) {
        draggedLabelCopy?.let { copy ->
            val scaleBounds = timelineView.getScaleBounds()
            val isOnRightSide = copy.x > scaleBounds.x
            val labelX = if (isOnRightSide) copy.x else copy.x + copy.width
            val dotX = if (isOnRightSide) scaleBounds.x + scaleBounds.width else scaleBounds.x
            val labelCenterY = copy.y + copy.preferredSize.height / 2

            val availableTime = draggedLabelState?.takeIf { it.isAvailable }?.proposedTime
            if (availableTime != null) {
                g.color = Color.WHITE
                paintHourglass(g, dotX, availableTime)
                g.drawLine(labelX, labelCenterY, dotX, labelCenterY)
            }
        }
    }

    private fun drawHourglasses(g: Graphics) {
        val scaleBounds = timelineView.getScaleBounds()
        val now = NtpClock.now()
        g.color = Color.decode("#ff4800")
        if (timelineConfig.runwaysLeft.isNotEmpty()) paintHourglass(g, scaleBounds.x, now)
        if (timelineConfig.runwaysRight.isNotEmpty()) paintHourglass(g, scaleBounds.x + scaleBounds.width, now)
    }

    private fun drawProposedTime(g: Graphics) {
        // If there's a backend-provided draggedLabelState and the event is present, use it.
        // Otherwise, if we have a dragged label copy, derive the proposed time locally and show it.
        val state = draggedLabelState
        val shouldBeVisible = state?.isAvailable == true && containsEventLabel(state.timelineEvent)

        if (shouldBeVisible) {
            val scaleBounds = timelineView.getScaleBounds()
            val proposedY = timelineView.calculateYPositionForInstant(state.proposedTime)
            val text = timeFormat.format(Date(state.proposedTime.toEpochMilliseconds()))
            g.color = Color.YELLOW
            g.drawStringAdvanced(
                text = text,
                x = scaleBounds.x + scaleBounds.width / 2,
                y = proposedY,
                backgroundColor = Color(80, 80, 80),
                hCenter = true,
                vCenter = true,
            )
        }
    }

    // --- Label/Event Sync ---
    private fun syncLabelsWithEvents(events: List<TimelineEvent>?) {
        val flights = events?.mapNotNull { it.getFlight() } ?: emptyList()
        val validCallsigns = flights.map { it.callsign }.toSet()

        val toRemove = labels.keys - validCallsigns
        toRemove.forEach { cs ->
            labels.remove(cs)?.let { remove(it) }
        }

        val eventsByCallsign: Map<String, TimelineEvent> = events
            ?.mapNotNull { ev -> ev.getFlight()?.callsign?.let { it to ev } }
            ?.toMap()
            ?: emptyMap()

        flights.forEach { flight ->
            val callsign = flight.callsign
            val event = eventsByCallsign[callsign] ?: return@forEach
            val existing = labels[callsign]
            if (existing == null) {
                val newLabel = event.createLabel()
                newLabel.font = baseFont
                newLabel.addMouseListener(labelMouseAdapter(newLabel))
                newLabel.addMouseMotionListener(labelMouseMotionAdapter(newLabel))
                newLabel.updateText()
                newLabel.updateColors()
                labels[callsign] = newLabel
                add(newLabel)
            } else {
                existing.timelineEvent = event
                existing.updateText()
                existing.updateColors()
            }
        }
    }

    private fun TimelineEvent.getFlight(): RunwayFlightEvent? = when (this) {
        is DepartureEvent -> this
        is RunwayArrivalEvent -> this
        is RunwayDelayEvent -> null
        else -> null
    }

    private fun TimelineEvent.createLabel(): TimelineLabel {
        val label = when (this) {
            is DepartureEvent -> DepartureLabel(departureLabelLayout, this, hBorder = labelHBorder, vBorder = labelVBorder)
            is RunwayArrivalEvent -> ArrivalLabel(arrivalLabelLayout, this, presenter, hBorder = labelHBorder, vBorder = labelVBorder)
            else -> throw IllegalArgumentException("Unsupported occurrence type")
        }
        label.font = baseFont
        return label
    }

    private fun labelMouseAdapter(label: TimelineLabel) = object : MouseAdapter() {
        override fun mousePressed(e: MouseEvent) {
            draggedLabelOriginalX = label.x
        }
        override fun mouseClicked(e: MouseEvent?) {
            if (e != null && e.isLeftButton()) {
                label.onDragEnd()
                handleLabelClick(label)
            }
        }
        override fun mouseReleased(e: MouseEvent) {
            if (isDraggingLabel && e.isLeftButton()) {
                val pointInView = SwingUtilities.convertPoint(e.component, e.point, timelineView)
                val newInstant = timelineView.calculateInstantForYPosition(pointInView.y)
                onLabelDropped(label.timelineEvent, newInstant)
                label.onDragEnd()
            }
        }
    }

    private fun labelMouseMotionAdapter(label: TimelineLabel) = object : MouseMotionAdapter() {
        override fun mouseDragged(e: MouseEvent) {
            if (!e.isLeftButtonDown()) return
            isDraggingLabel = true
            val pointInView = SwingUtilities.convertPoint(e.component, e.point, timelineView)
            val newInstant = timelineView.calculateInstantForYPosition(pointInView.y)
            presenter.onLabelDrag(label.timelineEvent, newInstant)
        }
    }

    private fun createLabelCopy(label: TimelineLabel): TimelineLabel? {
        val copy = when (label.timelineEvent) {
            is DepartureEvent -> DepartureLabel(departureLabelLayout, label.timelineEvent as DepartureEvent, hBorder = labelHBorder, vBorder = labelVBorder)
            is RunwayArrivalEvent -> ArrivalLabel(arrivalLabelLayout, label.timelineEvent as RunwayArrivalEvent, presenter, hBorder = labelHBorder, vBorder = labelVBorder)
            else -> return null
        }
        copy.font = label.font
        copy.bounds = label.bounds
        return copy
    }

    private fun handleLabelClick(label: TimelineLabel) {
        label.timelineEvent.getFlight()?.let { presenter.onAircraftSelected(it.callsign) }
    }

    private fun onLabelDropped(timelineEvent: TimelineEvent, newTime: Instant) {
        if (timelineEvent is RunwayArrivalEvent) {
            isDraggingLabel = false
            presenter.beginRunwaySelection(
                runwayEvent = timelineEvent,
                onCancel = { airportViewState.draggedLabelState.value = null },
                onSubmit = { selectedRunway ->
                    presenter.onLabelDragEnd(timelineEvent, newTime, selectedRunway)
                    airportViewState.draggedLabelState.value = null
                }
            )
        }
    }

    // --- Drawing Helpers ---
    private fun paintHourglass(g: Graphics, xPosition: Int, atInstant: Instant) {
        val nowY = timelineView.calculateYPositionForInstant(atInstant)
        val hourglassSize = 6
        g.fillPolygon(Polygon(
            intArrayOf(xPosition, xPosition - hourglassSize, xPosition - hourglassSize),
            intArrayOf(nowY, nowY - hourglassSize, nowY + hourglassSize),
            3
        ))
        g.fillPolygon(Polygon(
            intArrayOf(xPosition, xPosition + hourglassSize, xPosition + hourglassSize),
            intArrayOf(nowY, nowY + hourglassSize, nowY - hourglassSize),
            3
        ))
    }

    private fun MouseEvent.isLeftButton(): Boolean = this.button == MouseEvent.BUTTON1

    private fun MouseEvent.isLeftButtonDown(): Boolean = (this.modifiersEx and MouseEvent.BUTTON1_DOWN_MASK) != 0

}
