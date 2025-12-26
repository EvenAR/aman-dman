package no.vaccsca.amandman.view.airport

import no.vaccsca.amandman.common.NtpClock
import no.vaccsca.amandman.presenter.PresenterInterface
import no.vaccsca.amandman.view.dialogs.RoleSelectionDialog
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Graphics
import javax.swing.*

class Footer(
    private val presenterInterface: PresenterInterface,
    private val mainWindow: JFrame
) : JPanel(FlowLayout(FlowLayout.RIGHT)) {
    private val timeLabel = JLabel("--:--:--")
    private val startButton = JButton("+")

    // Replace separate label+light fields with reusable StatusIndicator
    private val atcClientStatus = StatusIndicator("ES", TrafficLightPanel.Status.GREY)
    private val serverStatus = StatusIndicator("SERVER", TrafficLightPanel.Status.GREY)
    private val metStatus = StatusIndicator("MET", TrafficLightPanel.Status.GREY)
    private val cdmStatus = StatusIndicator("CDM", TrafficLightPanel.Status.GREY)

    init {
        add(startButton)

        // ES status
        add(atcClientStatus)

        // SERVER status
        add(serverStatus)

        // MET status
        add(metStatus)

        add(cdmStatus)

        add(JSeparator(SwingConstants.VERTICAL).apply {
            preferredSize = Dimension(2, 20)
        })
        add(timeLabel)

        // Every second, repaint the component
        Timer(1000) {
            repaint()
        }.start()

        startButton.addActionListener {
            RoleSelectionDialog.open(mainWindow) { icao, role ->
                presenterInterface.onNewTimelineGroup(icao, role)
            }
        }
    }

    override fun paintComponent(g: Graphics?) {
        super.paintComponent(g)
        timeLabel.text = NtpClock.now().toString().substring(11, 19)
    }

    /**
     * Set ES status light.
     */
    fun setAtcClientStatus(status: TrafficLightPanel.Status) {
        runOnUiThread {
            atcClientStatus.setStatus(status)
        }
    }

    /**
     * Set SERVER status light.
     */
    fun setServerStatus(status: TrafficLightPanel.Status) {
        runOnUiThread {
            serverStatus.setStatus(status)
        }
    }

    /**
     * Set MET status light.
     */
    fun setMetStatus(status: TrafficLightPanel.Status) {
        runOnUiThread {
            metStatus.setStatus(status)
            // MET is always present in layout; no special separator logic needed
        }
    }

    // helper to ensure EDT updates
    private fun runOnUiThread(block: () -> Unit) {
        if (SwingUtilities.isEventDispatchThread()) block() else SwingUtilities.invokeLater(block)
    }

    // Simple traffic-light indicator component
    class TrafficLightPanel(initial: Status) : JPanel() {
        enum class Status { RED, YELLOW, GREEN, GREY }
        @Volatile
        private var status: Status = initial

        init {
            preferredSize = Dimension(16, 16)
            minimumSize = Dimension(16, 16)
            maximumSize = Dimension(16, 16)
            isOpaque = false
        }

        fun setStatus(s: Status) {
            status = s
            // ensure Swing update happens on EDT
            if (SwingUtilities.isEventDispatchThread()) {
                repaint()
            } else {
                SwingUtilities.invokeLater { repaint() }
            }
        }

        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)
            val color = when (status) {
                Status.RED -> Color(0xFF0000) // red
                Status.YELLOW -> Color(0xFFFF00) // yellow
                Status.GREEN -> Color(0x00FF00) // green
                Status.GREY -> Color(0x9E9E9E) // grey
            }
            g.color = color
            val inset = 2
            val diameter = Math.min(width, height) - inset * 2
            g.fillOval(inset, inset, diameter, diameter)
            // optional border
            g.color = Color.BLACK
            g.drawOval(inset, inset, diameter, diameter)
        }
    }

    // New reusable status component (label + light + setter)
    private class StatusIndicator(labelText: String, initial: TrafficLightPanel.Status) : JPanel(FlowLayout(FlowLayout.CENTER, 4, 0)) {
        private val label = JLabel(labelText)
        private val light = TrafficLightPanel(initial)
        private val sep = JSeparator(SwingConstants.VERTICAL).apply { preferredSize = Dimension(2, 20) }
        private var currentStatus: TrafficLightPanel.Status = initial

        init {
            isOpaque = false
            add(sep)
            add(light)
            add(label)
            // initial visibility
            isVisible = currentStatus != TrafficLightPanel.Status.GREY
        }

        fun setStatus(status: TrafficLightPanel.Status) {
            currentStatus = status
            light.setStatus(status)
            // hide the whole indicator when GREY
            isVisible = status != TrafficLightPanel.Status.GREY
        }

        fun isGrey(): Boolean = currentStatus == TrafficLightPanel.Status.GREY
    }
}