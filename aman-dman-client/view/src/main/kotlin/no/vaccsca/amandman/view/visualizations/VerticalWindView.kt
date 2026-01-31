package no.vaccsca.amandman.view.visualizations

import no.vaccsca.amandman.common.util.NumberUtils.format
import no.vaccsca.amandman.model.domain.valueobjects.weather.VerticalWeatherProfile
import no.vaccsca.amandman.presenter.AirportPresenterInterface
import no.vaccsca.amandman.view.components.ReloadButton
import no.vaccsca.amandman.view.entity.AirportViewState
import java.awt.BorderLayout
import java.awt.Color
import java.awt.FlowLayout
import java.awt.Graphics
import javax.swing.JPanel
import kotlin.math.roundToInt

class VerticalWindView(
    private val viewState: AirportViewState,
    private val presenterProvider: () -> AirportPresenterInterface
): JPanel(BorderLayout()) {

    private val presenter: AirportPresenterInterface get() = presenterProvider()
    private var weatherProfile: VerticalWeatherProfile? = null

    private val contentPanel = ContentPanel()
    private val reloadButton = ReloadButton("Reload winds for airport") {
        presenter.onReloadWindsClicked()
    }

    init {
        val bottomPanel = JPanel(FlowLayout(FlowLayout.LEFT, 5, 5))
        bottomPanel.add(reloadButton)

        add(contentPanel, BorderLayout.CENTER)
        add(bottomPanel, BorderLayout.SOUTH)

        viewState.weatherProfile.addListener { newWeatherProfile ->
            this.weatherProfile = newWeatherProfile
            contentPanel.repaint()
        }
    }

    private inner class ContentPanel : JPanel() {
        override fun paintComponent(g: Graphics) {
            super.paintComponent(g)

            val profile = weatherProfile

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
