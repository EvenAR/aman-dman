package no.vaccsca.amandman.view.airport.timeline.labels

import kotlinx.datetime.Instant
import no.vaccsca.amandman.model.domain.valueobjects.LabelItem
import no.vaccsca.amandman.model.domain.valueobjects.LabelItemAlignment
import no.vaccsca.amandman.model.domain.valueobjects.timelineEvent.TimelineEvent
import no.vaccsca.amandman.view.entity.SharedValue
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.JLabel
import javax.swing.border.EmptyBorder

abstract class TimelineLabel(
    var timelineEvent: TimelineEvent,
    open val labelItems: List<LabelItem>,
    val defaultBackgroundColor: Color? = null,
    var defaultForegroundColor: Color = Color.WHITE,
    val hoverBackgroundColor: Color? = Color.GRAY,
    val hoverForegroundColor: Color = Color.BLACK,
    hBorder: Int,
    vBorder: Int,
    protected val selectedAircraftCallsign: SharedValue<String>? = null,
) : JLabel() {

    private var isHovered: Boolean = false
    private var isDragging: Boolean = false
    private val labels = mutableListOf<JLabel>()
    private val baseFont = Font(Font.MONOSPACED, Font.PLAIN, 12)

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
        selectedAircraftCallsign?.addListener {
            repaint()
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
        return selectedAircraftCallsign?.value == timelineEvent.callsign
    }

    override fun paintBorder(g: java.awt.Graphics) {
        super.paintBorder(g)
        
        // Draw white border if this aircraft is selected
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