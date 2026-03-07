package no.vaccsca.amandman.view.airport

import no.vaccsca.amandman.presenter.AirportPresenterInterface
import no.vaccsca.amandman.view.entity.AirportViewState
import java.awt.Color
import java.awt.FlowLayout
import javax.swing.*

class TopBar(
    private val airportViewState: AirportViewState,
    private val presenterProvider: () -> AirportPresenterInterface,
) : JPanel() {

    private val presenter: AirportPresenterInterface get() = presenterProvider()

    private val departuresCheckbox = JCheckBox("Departures")

    private val nonSequencedButton = JButton("NonSeq")
        .apply { preferredSize = this.preferredSize.apply { width = 100 } }

    private val landingRatesButton = JButton("TLM")
        .apply { preferredSize = this.preferredSize.apply { width = 100 } }

    private val initialBorder = BorderFactory.createEmptyBorder(0, 0, 0, 0)

    /** Row 1 container */
    private val topRow = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0))

    /** Runway modes */
    private val runwayModePanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.X_AXIS)
        border = BorderFactory.createEmptyBorder(0, 0, 0, 0)
    }

    /** Buttons */
    private val buttonsPanel = JPanel().apply {
        layout = FlowLayout(FlowLayout.LEFT, 5, 0)
        border = BorderFactory.createEmptyBorder(3, -2, 2, 0)
    }

    /** Row 2 container */
    private val bottomRow = JPanel().apply {
        layout = FlowLayout(FlowLayout.LEFT, 5, 0)
        border = BorderFactory.createEmptyBorder(6, 3, 6, -3)
    }

    private val rowSeparator = JPanel().apply {
        background = Color.DARK_GRAY
        preferredSize = java.awt.Dimension(1, 1)
        maximumSize = java.awt.Dimension(Int.MAX_VALUE, 1)
    }

    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        initActions()
        initStateListeners()
        initLayout()
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

        topRow.add(buttonsPanel)
        topRow.alignmentX = LEFT_ALIGNMENT
        bottomRow.add(runwayModePanel)
        bottomRow.alignmentX = LEFT_ALIGNMENT
        rowSeparator.alignmentX = LEFT_ALIGNMENT

        add(topRow)
        add(rowSeparator)
        add(bottomRow)
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
