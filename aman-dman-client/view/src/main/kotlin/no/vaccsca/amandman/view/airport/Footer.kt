package no.vaccsca.amandman.view.airport

import no.vaccsca.amandman.common.NtpClock
import no.vaccsca.amandman.model.integration.IntegrationKind
import no.vaccsca.amandman.model.integration.IntegrationStatusState
import no.vaccsca.amandman.view.entity.AirportViewState
import no.vaccsca.amandman.view.entity.MainViewState
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import javax.swing.BoxLayout
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JSeparator
import javax.swing.SwingConstants
import javax.swing.Timer

class Footer(
    mainViewState: MainViewState,
    airportViewState: AirportViewState,
) : JPanel(FlowLayout(FlowLayout.RIGHT)) {
    private val timeLabel = JLabel("--:--:--")
    private val statusContainer = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0)).apply {
        isOpaque = false
    }
    private val statusItems = IntegrationKind.entries.associateWith { kind -> StatusItem(kind.name) }
    private var flashOn = true
    private var currentStatuses = airportViewState.integrationStatuses.peek()

    private val flashTimer = Timer(350) {
        flashOn = !flashOn
        renderStatuses()
    }

    init {
        border = javax.swing.BorderFactory.createEmptyBorder(0, 0, 2, 4)
        val spacer = JSeparator(SwingConstants.VERTICAL).apply {
            preferredSize = Dimension(2, 16)
        }
        statusItems.values.forEach { statusContainer.add(it) }
        add(statusContainer)
        add(spacer)
        add(timeLabel)

        airportViewState.integrationStatuses.addListener {
            currentStatuses = it
            renderStatuses()
        }

        mainViewState.currentClock.addListener {
            timeLabel.text = NtpClock.now().toString().substring(11, 19)
            renderStatuses()
        }

        flashTimer.start()
        renderStatuses()
    }

    private fun renderStatuses() {
        IntegrationKind.entries.forEach { kind ->
            val display = currentStatuses[kind]
            val item = statusItems[kind] ?: return@forEach
            if (display == null) {
                item.isVisible = false
                return@forEach
            }
            item.isVisible = true

            val state = display.status.state
            val shouldFlash = display.status.shouldFlash
            item.setAbbreviation(display.label)

            val activeColor = when (state) {
                IntegrationStatusState.OK -> Color(45, 185, 45)
                IntegrationStatusState.LOADING -> Color(220, 180, 0)
                IntegrationStatusState.ERROR -> Color(190, 60, 60)
            }
            val inactiveColor = Color(80, 80, 80)
            item.setCircleColor(if (shouldFlash && !flashOn) inactiveColor else activeColor)
        }
        statusContainer.revalidate()
        repaint()
    }

    private class StatusItem(
        abbreviation: String,
    ) : JPanel() {
        private val abbreviationLabel = JLabel(abbreviation).apply {
            foreground = Color.WHITE
            font = Font(font.name, Font.BOLD, 11)
        }
        private val circleLabel = JLabel("●").apply {
            font = Font(font.name, Font.BOLD, 12)
            foreground = Color(190, 60, 60)
        }

        init {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            add(abbreviationLabel)
            add(JLabel(" "))
            add(circleLabel)
        }

        fun setAbbreviation(text: String) {
            abbreviationLabel.text = text
        }

        fun setCircleColor(color: Color) {
            circleLabel.foreground = color
        }
    }
}
