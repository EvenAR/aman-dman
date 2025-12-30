package no.vaccsca.amandman.view.visualizations

import no.vaccsca.amandman.common.domain.valueobjects.weather.VerticalWeatherProfile
import no.vaccsca.amandman.common.util.NumberUtils.format
import no.vaccsca.amandman.presenter.PresenterInterface
import no.vaccsca.amandman.view.components.ReloadButton
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Graphics
import java.awt.FlowLayout
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.math.roundToInt

class VerticalWindView(
    private val presenter: PresenterInterface,
): JPanel(BorderLayout()) {

    private val profiles = mutableMapOf<String, VerticalWeatherProfile>()
    private var selectedAirport: String? = null

    private val airportSelector = JComboBox<String>()
    private val contentPanel = ContentPanel()
    private val reloadButton = ReloadButton("Reload winds for airport") {
        selectedAirport?.let {
            presenter.onReloadWindsClicked(airportIcao = it)
        }
    }

    init {
        airportSelector.addActionListener {
            selectedAirport = airportSelector.selectedItem as? String
            contentPanel.repaint()
        }

        // Create bottom panel with label and dropdown
        val bottomPanel = JPanel(FlowLayout(FlowLayout.LEFT, 5, 5))
        val airportLabel = JLabel("Airport:")

        bottomPanel.add(airportLabel)
        bottomPanel.add(airportSelector)
        bottomPanel.add(reloadButton)

        add(contentPanel, BorderLayout.CENTER)
        add(bottomPanel, BorderLayout.SOUTH)
    }

    fun update(airportIcao: String, weatherProfile: VerticalWeatherProfile?) {
        if (weatherProfile != null) {
            profiles[airportIcao] = weatherProfile
        } else {
            profiles.remove(airportIcao)
        }

        updateSelector()
        contentPanel.repaint()
    }

    private fun updateSelector() {
        val currentSelection = airportSelector.selectedItem as? String
        airportSelector.removeAllItems()

        profiles.keys.sorted().forEach { icao ->
            airportSelector.addItem(icao)
        }

        // Restore selection if it still exists, otherwise select first
        if (currentSelection != null && profiles.containsKey(currentSelection)) {
            airportSelector.selectedItem = currentSelection
            selectedAirport = currentSelection
        } else if (profiles.isNotEmpty()) {
            airportSelector.selectedIndex = 0
            selectedAirport = airportSelector.selectedItem as? String
        } else {
            selectedAirport = null
        }
    }

    fun showAirport(airportIcao: String) {
        airportSelector.selectedItem = airportIcao
        updateSelector()
    }

    private inner class ContentPanel : JPanel() {
        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)

            val profile = selectedAirport?.let { profiles[it] }

            profile?.let { currentProfile ->
                val maxFl = currentProfile.weatherLayers.maxOf { it.flightLevelFt }
                val diagramMargin = 30

                val pxPerFl = (height - diagramMargin * 2).toFloat() / maxFl.toFloat()

                currentProfile.weatherLayers.forEach {
                    val yPos = (height - pxPerFl * it.flightLevelFt).roundToInt() - diagramMargin
                    g.color = foreground
                    g.drawLine(0, yPos, 10, yPos)

                    WindBarbs.drawWindBarb(
                        g,
                        width - diagramMargin - 20,
                        yPos,
                        it.windVector.directionDeg,
                        it.windVector.speedKts,
                        length = 40,
                        barbMaxLength = 12,
                    )

                    g.drawString("FL${(it.flightLevelFt / 100.0).roundToInt().toString().padStart(3, '0')}: ${it.windVector.directionDeg.toString().padStart(3, '0')} / ${it.windVector.speedKts} kt / ${it.temperatureC} C", 30, yPos)
                }

                g.drawString("Valid " + currentProfile.time.format("yyyy/MM/dd HH:mm") + "Z", 5, height - 5)
            } ?: run {
                g.color = Color.RED
                g.drawString("No data available", 5, height - 5)
            }
        }
    }
}
