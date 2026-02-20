package no.vaccsca.amandman.view.airport.timeline.labels

import kotlinx.datetime.Instant
import no.vaccsca.amandman.common.NtpClock
import no.vaccsca.amandman.model.config.LabelItem
import no.vaccsca.amandman.model.config.LabelItemAlignment
import no.vaccsca.amandman.model.timeline.event.timeline.RunwayFlightEvent
import no.vaccsca.amandman.model.timeline.event.timeline.TimelineEvent
import no.vaccsca.amandman.view.entity.AircraftSelection
import no.vaccsca.amandman.view.entity.SharedValue
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JLabel
import javax.swing.Timer
import javax.swing.border.EmptyBorder
import kotlin.time.Duration.Companion.seconds

abstract class TimelineLabel(
    var timelineEvent: TimelineEvent,
    open val labelItems: List<LabelItem>,
    val defaultBackgroundColor: Color? = null,
    var defaultForegroundColor: Color = Color.WHITE,
    val hoverBackgroundColor: Color? = Color.GRAY,
    val hoverForegroundColor: Color = Color.BLACK,
    hBorder: Int,
    vBorder: Int,
    protected val aircraftSelection: SharedValue<AircraftSelection?>,
) : JLabel() {

    private var isHovered: Boolean = false
    private var isDragging: Boolean = false
    private val labels = mutableListOf<JLabel>()
    private val baseFont = Font(Font.MONOSPACED, Font.PLAIN, 12)
    private var repaintTimer: Timer? = null

    private val highlightDuration = 2.seconds

    protected abstract fun decideLabelItemStyle(item: LabelItem, event: TimelineEvent): LabelStyleOptions
    abstract fun getTimelinePlacement(): Instant

    init {
        layout = BoxLayout(this, BoxLayout.X_AXIS)
        background = defaultBackgroundColor
        foreground = defaultForegroundColor
        isOpaque = defaultBackgroundColor != null
        border = EmptyBorder(vBorder, hBorder, vBorder, hBorder)

        addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseEntered(e: java.awt.event.MouseEvent?) {
                isHovered = true
                updateColors()
            }

            override fun mouseExited(e: java.awt.event.MouseEvent?) {
                isHovered = false
                updateColors()
            }
        })
        
        // Listen for selection changes
        aircraftSelection.addListener {
            repaint()
            scheduleRepaintAfterTimeout()
        }
    }
    
    private fun scheduleRepaintAfterTimeout() {
        // Cancel existing timer
        repaintTimer?.stop()

        val flight = timelineEvent as? RunwayFlightEvent ?: return

        // Only schedule repaint if this aircraft is currently selected
        if (aircraftSelection.value?.callsign == flight.callsign) {
            // Schedule repaint after 2 seconds to hide the border
            repaintTimer = Timer(highlightDuration.inWholeMilliseconds.toInt()) {
                repaint()
            }
            repaintTimer?.isRepeats = false
            repaintTimer?.start()
        }
    }

    fun updateColors() {
        if (isHovered || isDragging) {
            background = hoverBackgroundColor
            foreground = hoverForegroundColor
            isOpaque = hoverBackgroundColor != null
        } else {
            background = defaultBackgroundColor
            foreground = defaultForegroundColor
            isOpaque = defaultBackgroundColor != null
        }
        repaint()
    }

    fun onDragStart() {
        isDragging = true
        updateColors()
    }

    fun onDragEnd() {
        isDragging = false
        updateColors()
    }

    fun updateText() {
        // Create labels only once
        if (labels.isEmpty()) {
            labelItems.forEach { item ->
                val lbl = JLabel()
                lbl.font = baseFont
                lbl.border = BorderFactory.createEmptyBorder(-1, 0, -1, 0)
                add(lbl)
                labels += lbl
            }
        }

        // Update existing labels
        labelItems.forEachIndexed { index, item ->
            val labelStyle = decideLabelItemStyle(item, timelineEvent)
            val lbl = labels[index]
            lbl.text = item.formatText(labelStyle.text)
            lbl.foreground = labelStyle.textColor
            lbl.border = BorderFactory.createEmptyBorder(-1, 0, -1, 0)
        }

        repaint()
    }

    fun LabelItem.formatText(value: String?): String {
        val originalValueAsString = value ?: this.defaultValue ?: ""
        val maxCharacters = this.width.coerceAtMost(this.maxLength ?: Int.MAX_VALUE)
        val truncatedValue =
            if (originalValueAsString.length > maxCharacters) {
                originalValueAsString.substring(0, maxCharacters)
            } else {
                originalValueAsString
            }

        val paddedValue = when (this.alignment) {
            null, LabelItemAlignment.LEFT -> truncatedValue.padEnd(width)
            LabelItemAlignment.CENTER -> truncatedValue.padStart(((width - truncatedValue.length) / 2) + truncatedValue.length).padEnd(width)
            LabelItemAlignment.RIGHT -> truncatedValue.padStart(width)
        }

        return paddedValue
    }

    protected open fun isSelected(): Boolean {
        val flight = timelineEvent as? RunwayFlightEvent ?: return false

        if (aircraftSelection.value?.callsign != flight.callsign) {
            return false
        }
        
        // Check if selection is within 3 seconds
        val timestamp = aircraftSelection.value?.timestamp ?: return false
        val now = NtpClock.now()
        val elapsed = now - timestamp
        
        return elapsed < highlightDuration
    }

    override fun paintBorder(g: java.awt.Graphics) {
        super.paintBorder(g)
        
        // Draw white border if this aircraft is selected and within 3 seconds
        if (isSelected()) {
            g.color = Color.WHITE
            g.drawRect(0, 0, width - 1, height - 1)
        }
    }

    override fun getPreferredSize(): Dimension {
        var totalWidth = 0
        var maxHeight = 0
        for (comp in components) {
            val size = comp.preferredSize
            totalWidth += size.width
            if (size.height > maxHeight) maxHeight = size.height
        }
        return Dimension(totalWidth, maxHeight)
    }

    protected data class LabelStyleOptions(
        val text: String,
        val textColor: Color? = null,
    )
}