package no.vaccsca.amandman.view.airport

import no.vaccsca.amandman.common.domain.valueobjects.RunwayStatus
import no.vaccsca.amandman.presenter.AirportPresenterInterface
import no.vaccsca.amandman.view.components.WrapLayout
import java.awt.*
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.*

class TopBar(
    private val presenter: AirportPresenterInterface,
) : JPanel() {
    private val showDepartures = JCheckBox("Departures")
    private val nonSequencedButton = JButton("NonSeq")
    private val landingRatesButton = JButton("Landing Rates")
    // Use WrapLayout so the labels wrap based on available width
    private val runwayModeList = JPanel(WrapLayout(FlowLayout.LEFT, 10, 5))

    init {
        layout = BorderLayout()

        showDepartures.addActionListener {
            presenter.onToggleShowDepartures(showDepartures.isSelected)
        }

        landingRatesButton.addActionListener {
            presenter.onOpenLandingRatesWindow()
        }

        nonSequencedButton.addActionListener {
            presenter.onOpenNonSequencedWindow()
        }

        // Right-aligned controls
        val rightPanel = JPanel(FlowLayout(FlowLayout.RIGHT, 10, 5))
        rightPanel.add(showDepartures)
        rightPanel.add(nonSequencedButton)
        rightPanel.add(landingRatesButton)

        // Place runwayModeList in CENTER so it can expand vertically/horizontally
        add(runwayModeList, BorderLayout.CENTER)
        add(rightPanel, BorderLayout.EAST)

        // Revalidate/repaint the runwayModeList on resize to force WrapLayout recalculation
        addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent?) {
                runwayModeList.revalidate()
                runwayModeList.repaint()
            }
        })
    }

    fun updateNonSeqNumbers(numberOfNonSeq: Int) {
        this.nonSequencedButton.apply {
            background = if (numberOfNonSeq > 0) Color.YELLOW else Color.GRAY
            text = "NonSeq ($numberOfNonSeq)"
            foreground = if (numberOfNonSeq > 0) Color.BLACK else Color.WHITE
        }
    }

    fun setRunwayModes(runwayModes: Map<String, RunwayStatus>) {
        val possibleRunwayModes = inferPossibleRunwayModes(runwayModes)
        val runwayModesWithStatus = possibleRunwayModes.associateWith { mode ->
            runwayModes.containsKey(mode)
        }
        runwayModeList.removeAll()
        runwayModesWithStatus.forEach { (modeName, isActive) ->
            val label = JLabel(modeName)
            label.foreground = if (isActive) Color.WHITE else Color.GRAY
            runwayModeList.add(label)
        }
        runwayModeList.revalidate()
        runwayModeList.repaint()
    }

    private fun inferPossibleRunwayModes(runwayStatuses: Map<String, RunwayStatus>): List<String> {
        val allRunwayIds = runwayStatuses.keys.sorted()
        val runwaysWithSameDirection = allRunwayIds
            .groupBy { it.take(2) } // Assumes first two characters denote direction
            .filter { it.value.size >= 2 } // Two or more runways in same direction
            .map { it.value.joinToString("/") }

        return allRunwayIds + runwaysWithSameDirection
    }
}
