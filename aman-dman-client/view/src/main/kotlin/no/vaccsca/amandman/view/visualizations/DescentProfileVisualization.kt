package no.vaccsca.amandman.view.visualizations

import no.vaccsca.amandman.model.planning.TrajectoryPoint
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Graphics
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionAdapter
import javax.swing.JPanel
import kotlin.math.abs
import kotlin.math.roundToInt

class DescentProfileVisualization : JPanel(BorderLayout()) {

    private var trajectoryPoints: List<TrajectoryPoint> = emptyList()
    private var hoveredPoint: TrajectoryPoint? = null

    private var pxPerFt: Float = 0.0f // Y-axis
    private var pxPerKts: Float = 0.0f // Y-axis
    private var pxPerNm: Float = 0.0f // X-axis

    private val BARB_SPACING = 40

    init {
        addMouseMotionListener(object : MouseMotionAdapter() {
            override fun mouseMoved(e: MouseEvent) {
                val point = findClosestTrajectoryPointAlongXAxis(e.x)
                if (point != hoveredPoint) {
                    hoveredPoint = point
                    repaint()
                }
            }
        })
    }

    override fun doLayout() {
        super.doLayout()
        if (trajectoryPoints.isNotEmpty()) {
            val altitudeRange = IntRange(0, trajectoryPoints.maxOf { it.altitude })
            val speedRange = IntRange(0, 600)
            pxPerNm = (width - diagramMargin - diagramMargin).toFloat() / trajectoryPoints.first().remainingDistance
            pxPerFt = (height - diagramMargin - diagramMarginTop).toFloat() / (altitudeRange.max() - altitudeRange.min()).toFloat()
            pxPerKts = (height - diagramMargin - diagramMarginTop).toFloat() / (speedRange.max() - speedRange.min()).toFloat()
        }
    }

    fun setDescentSegments(segments: List<TrajectoryPoint>) {
        this.trajectoryPoints = segments
        doLayout()
        repaint()
    }

    private val diagramMargin = 40
    private val diagramMarginTop = 90

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)

        if (trajectoryPoints.isEmpty()) {
            return
        }

        val maxAlt = trajectoryPoints.maxOf { it.altitude }

        // Illustrate the flight levels
        for (i in 0..maxAlt step 1000) {
            val yPos = calculateAltitudeCoordinates(altitude = i)
            g.color = Color(60, 60, 60)
            g.drawLine(50, yPos, width - diagramMargin, yPos)
            g.drawString("FL" + (i / 100).toString().padStart(3, '0'), 5, yPos + 5)
        }

        var prevX = diagramMargin
        var prevY = diagramMarginTop
        var prevGsY: Int? = null
        var prevTasY: Int? = null

        var distFromPreviousBarb = 0

        trajectoryPoints.forEach { tp ->
            val x = calculateXcoordinate(tp.remainingDistance)
            val yPosAlt = calculateAltitudeCoordinates(tp.altitude)
            val yPosGs = calculateSpeedCoordinates(tp.groundSpeed)
            val yPosTas = calculateSpeedCoordinates(tp.tas)

            // Visualize true airspeed
            g.color = Color.YELLOW
            prevTasY?.let { y -> g.drawLine(prevX, y, x, yPosTas) }

            // Visualize ground speed
            g.color = Color.GREEN
            prevGsY?.let { y -> g.drawLine(prevX, y, x, yPosGs) }

            // Visualize remaining distance wrt height
            g.color = Color.MAGENTA
            g.drawLine(prevX, prevY, x, yPosAlt)

            g.color = Color.WHITE
            tp.fixId?.let { fixId ->
                g.drawOval(x - 3, yPosAlt - 3, 6, 6)
                g.drawString(fixId, x, yPosAlt - 5)
            }


            if (distFromPreviousBarb > BARB_SPACING) {
                distFromPreviousBarb = 0
                WindBarbs.drawWindBarb(g, x, 40, tp.windVector.directionDeg, tp.windVector.speedKts, relativeToHeading = tp.heading + 90 )
            } else {
                distFromPreviousBarb += (x - prevX)
            }

            prevX = x
            prevY = yPosAlt
            prevGsY = yPosGs
            prevTasY = yPosTas
        }

        hoveredPoint?.let {
            val x = calculateXcoordinate(it.remainingDistance)

            val tailwindComponent = it.groundSpeed - it.tas
            g.drawString(
                if (tailwindComponent > 0) "+${tailwindComponent} kt" else "${tailwindComponent} kt",
                x + 5,
                diagramMargin + 20
            )

            drawLegend(g, it)

            g.color = Color.RED
            g.drawLine(x, diagramMarginTop, x, height - diagramMargin)
        }
    }

    private fun drawLegend(g: Graphics, it: TrajectoryPoint) {
        var x = 5

        // Altitude
        g.color = Color.MAGENTA
        val alt = "FL${(it.altitude / 100).toString().padStart(3, '0')}"
        g.drawString(alt, x, height - 5)
        x += g.fontMetrics.stringWidth("$alt | ")

        // Ground speed
        g.color = Color.GREEN
        val gs = "GS: ${it.groundSpeed} "
        g.drawString(gs, x, height - 5)
        x += g.fontMetrics.stringWidth("$gs | ")

        // True airspeed
        g.color = Color.YELLOW
        val tas = "TAS: ${it.tas}"
        g.drawString(tas, x, height - 5)
        x += g.fontMetrics.stringWidth("$tas | ")

        g.color = Color.WHITE

        // Indicated airspeed
        val ias = "IAS: ${it.ias}"
        g.drawString(ias, x, height - 5)
        x += g.fontMetrics.stringWidth("$ias | ")

        // ETA
        val eta = "ETA: ${it.remainingTime}"
        g.drawString(eta, x, height - 5)
        x += g.fontMetrics.stringWidth("$eta | ")

        // Remaining distance
        val dist = "${it.remainingDistance.roundToInt()} NM"
        g.drawString(dist, x, height - 5)
        x += g.fontMetrics.stringWidth("$dist | ")

        // Tailwind component
        val tailwindComponent = it.groundSpeed - it.tas
        g.color = if (tailwindComponent > 0) Color.GREEN.brighter() else Color.PINK
        val twc = if (tailwindComponent > 0) "+${tailwindComponent} kt" else "${tailwindComponent} kt"
        g.drawString(twc, x, height - 5)
    }

    private fun calculateSpeedCoordinates(speed: Int): Int {
        return height - diagramMargin - (pxPerKts * speed).toInt()
    }

    private fun calculateAltitudeCoordinates(altitude: Int): Int {
        return height - diagramMargin - (pxPerFt * altitude).toInt()
    }

    private fun calculateXcoordinate(remainingDistance: Float): Int {
        return width - diagramMargin - (pxPerNm * remainingDistance).toInt()
    }

    private fun findClosestTrajectoryPointAlongXAxis(x: Int): TrajectoryPoint? {
        val closestPoint = trajectoryPoints.minByOrNull { point ->
            val xPos = calculateXcoordinate(point.remainingDistance)
            abs(x - xPos)
        }
        return closestPoint
    }

}