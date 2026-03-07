package no.vaccsca.amandman.view.airport.timeline.utils

import java.awt.Color
import java.awt.Graphics

object GraphicUtils {
    fun Graphics.drawStringAdvanced(
        text: String,
        x: Int,
        y: Int,
        backgroundColor: Color? = null,
        borderColor: Color? = null,
        hPadding: Int = 0,
        vPadding: Int = 0,
        hCenter: Boolean = true,
        vCenter: Boolean = true
    ) {
        val metrics = this.getFontMetrics(font)
        val textWidth = metrics.stringWidth(text)
        val textHeight = metrics.height

        // Total rectangle size including padding
        val rectWidth = textWidth + 2 * hPadding
        val rectHeight = textHeight + 2 * vPadding

        // Rectangle top-left corner
        val rectX = if (hCenter) x - rectWidth / 2 else x
        val rectY = if (vCenter) y - rectHeight / 2 else y

        // Draw background rectangle if needed
        if (backgroundColor != null) {
            val originalColor = this.color
            this.color = backgroundColor
            this.fillRect(rectX, rectY, rectWidth, rectHeight)
            this.color = originalColor
        }

        if (borderColor != null) {
            val originalColor = this.color
            this.color = borderColor
            this.drawRect(rectX, rectY, rectWidth - 1, rectHeight - 1)
            this.color = originalColor
        }

        // Draw text centered inside the rectangle
        val textX = rectX + (rectWidth - textWidth) / 2
        val textY = rectY + (rectHeight - textHeight) / 2 + metrics.ascent

        this.font = font
        this.drawString(text, textX, textY)
    }
}
