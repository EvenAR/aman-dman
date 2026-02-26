package no.vaccsca.amandman.view.airport

import no.vaccsca.amandman.presenter.AirportPresenterInterface
import no.vaccsca.amandman.view.entity.AirportViewState
import java.awt.BorderLayout
import java.awt.Color
import java.awt.FlowLayout
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.*

class TopBar(
    private val airportViewState: AirportViewState,
    private val presenterProvider: () -> AirportPresenterInterface,
) : JPanel(BorderLayout()) {

    private val presenter: AirportPresenterInterface get() = presenterProvider()

    private val departuresCheckbox = JCheckBox("Departures")

    private val nonSequencedButton = JButton("NonSeq")
        .apply { preferredSize = this.preferredSize.apply { width = 100 } }

    private val landingRatesButton = JButton("TLM")
        .apply { preferredSize = this.preferredSize.apply { width = 100 } }

    private val initialBorder = BorderFactory.createEmptyBorder(0, 5, 0, 0)

    /** Row 1 container */
    private val topRow = JPanel(BorderLayout())

    /** Runway modes */
    private val runwayModePanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.X_AXIS)
        border = BorderFactory.createEmptyBorder(0, 0, 0, 0)
    }

    /** Buttons – can move to second row */
    private val buttonsPanel = JPanel().apply {
        layout = FlowLayout(FlowLayout.LEFT, 5, 0)
        border = BorderFactory.createEmptyBorder(0, 0, 0, 0)
    }

    /** Row 2 container (buttons overflow) */
    private val bottomRow = JPanel().apply {
        layout = FlowLayout(FlowLayout.LEFT, 5, 0)
        border = BorderFactory.createEmptyBorder(5, 0, 5, -5)
    }

    init {
        initActions()
        initStateListeners()
        initLayout()
        initResizeHandling()
        border = initialBorder
    }

    private fun initActions() {
        departuresCheckbox.addActionListener {
            presenter.onToggleShowDepartures(departuresCheckbox.isSelected)
        }

        landingRatesButton.addActionListener {
            presenter.onOpenLandingRatesWindow()
        }

        nonSequencedButton.addActionListener {
            presenter.onOpenNonSequencedWindow()
        }
    }

    private fun initStateListeners() {
        airportViewState.runwayModes.addListener {
            setRunwayModes(it)
        }

        airportViewState.nonSequencedList.addListener {
            updateNonSeqNumbers(it.size)
        }

        airportViewState.showDepartures.addListener {
            departuresCheckbox.isSelected = it
        }
    }

    private fun initLayout() {
        buttonsPanel.add(nonSequencedButton)
        buttonsPanel.add(landingRatesButton)
        buttonsPanel.add(departuresCheckbox)

        bottomRow.add(runwayModePanel)
        bottomRow.add(buttonsPanel)

        add(topRow, BorderLayout.NORTH)
        add(bottomRow, BorderLayout.SOUTH)
    }

    private fun initResizeHandling() {
        addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent?) {
                SwingUtilities.invokeLater {
                    updateButtonsRowPlacement()
                }
            }
        })
    }

    private fun updateButtonsRowPlacement() {
        val availableWidth = topRow.width
        val requiredWidth = runwayModePanel.preferredSize.width + buttonsPanel.preferredSize.width

        val shouldWrap = requiredWidth > availableWidth

        if (shouldWrap && buttonsPanel.parent !== topRow) {
            bottomRow.remove(buttonsPanel)
            topRow.add(buttonsPanel)
            border = BorderFactory.createEmptyBorder(5, 0, 0, 0)
        } else if (!shouldWrap && buttonsPanel.parent !== bottomRow) {
            topRow.remove(buttonsPanel)
            bottomRow.add(buttonsPanel)
            border = initialBorder
        }

        revalidate()
        repaint()
    }

    private fun updateNonSeqNumbers(numberOfNonSeq: Int) {
        nonSequencedButton.apply {
            background = if (numberOfNonSeq > 0) Color.YELLOW else null
            foreground = if (numberOfNonSeq > 0) Color.BLACK else Color.WHITE
            text = "NonSeq ($numberOfNonSeq)"
        }
    }

    private fun setRunwayModes(runwayModes: List<Pair<String, Boolean>>) {
        runwayModePanel.removeAll()

        runwayModes.forEach { (modeName, isActive) ->
            val label = JLabel(modeName).apply {
                foreground = if (isActive) Color.WHITE else Color.GRAY
                border = BorderFactory.createEmptyBorder(0, 0, 0, 6)
            }
            runwayModePanel.add(label)
        }

        runwayModePanel.revalidate()
        runwayModePanel.repaint()
    }
}
