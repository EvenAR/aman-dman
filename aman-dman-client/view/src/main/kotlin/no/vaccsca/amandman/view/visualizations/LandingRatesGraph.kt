package no.vaccsca.amandman.view.visualizations

import kotlinx.datetime.Instant
import no.vaccsca.amandman.common.NtpClock
import no.vaccsca.amandman.model.timeline.event.timeline.RunwayArrivalEvent
import no.vaccsca.amandman.model.timeline.event.timeline.TimelineEvent
import no.vaccsca.amandman.view.airport.TimeRangeScrollBarHorizontal
import no.vaccsca.amandman.view.entity.AirportViewState
import no.vaccsca.amandman.view.entity.MainViewState
import no.vaccsca.amandman.view.entity.SharedValue
import no.vaccsca.amandman.view.entity.TimeRange
import org.jfree.chart.ChartFactory
import org.jfree.chart.ChartPanel
import org.jfree.chart.JFreeChart
import org.jfree.chart.axis.DateAxis
import org.jfree.chart.axis.DateTickUnit
import org.jfree.chart.axis.DateTickUnitType
import org.jfree.chart.axis.NumberAxis
import org.jfree.chart.labels.ItemLabelAnchor
import org.jfree.chart.labels.ItemLabelPosition
import org.jfree.chart.labels.StandardXYItemLabelGenerator
import org.jfree.chart.plot.XYPlot
import org.jfree.chart.renderer.xy.StandardXYBarPainter
import org.jfree.chart.renderer.xy.XYBarRenderer
import org.jfree.chart.ui.GradientPaintTransformType
import org.jfree.chart.ui.StandardGradientPaintTransformer
import org.jfree.chart.ui.TextAnchor
import org.jfree.data.RangeType
import org.jfree.data.time.Minute
import org.jfree.data.time.TimeSeries
import org.jfree.data.time.TimeSeriesCollection
import org.jfree.data.xy.XYBarDataset
import java.awt.*
import java.text.SimpleDateFormat
import java.util.*
import javax.swing.JComboBox
import javax.swing.JPanel
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes


class LandingRatesGraph(
    val airportViewState: AirportViewState,
    mainViewState: MainViewState
) : JPanel() {

    private val timeSeries = TimeSeries("Landings")
    private val dataset = TimeSeriesCollection(timeSeries)
    private var chart: JFreeChart? = null
    private var plot: XYPlot? = null
    private var barDataset: XYBarDataset? = null
    private var currentBucketSize = 10.minutes
    private var currentTime = NtpClock.now()

    private val bucketSelector = JComboBox(arrayOf("10 min", "30 min", "60 min"))

    private val availableTimeRange = SharedValue(
        initialValue = TimeRange(
            NtpClock.now() - 1.hours,
            NtpClock.now() + 3.hours,
        )
    )

    private val selectedTimeRange = SharedValue(
        initialValue = TimeRange(
            NtpClock.now() - 10.minutes,
            NtpClock.now() + 60.minutes,
        )
    )

    private val timeWindowScrollbar = TimeRangeScrollBarHorizontal(selectedTimeRange, availableTimeRange)

    private var currentEvents: List<TimelineEvent> = emptyList()

    init {
        layout = BorderLayout()

        chart = createChart()
        val chartPanel = ChartPanel(chart)

        val controlPanel = JPanel().apply {
            add(bucketSelector)
        }

        bucketSelector.addActionListener {
            currentBucketSize = when (bucketSelector.selectedItem as String) {
                "10 min" -> 10.minutes
                "30 min" -> 30.minutes
                "60 min" -> 60.minutes
                else -> 10.minutes
            }
            updateChartBarWidth()
            showEvents(currentEvents)
        }

        selectedTimeRange.addListener {
            updateXAxisRange()
        }

        mainViewState.currentClock.addListener { newTime ->
            val delta = newTime - this.currentTime
            this.currentTime = newTime

            selectedTimeRange.value = TimeRange(
                selectedTimeRange.value.start + delta,
                selectedTimeRange.value.end + delta,
            )
            availableTimeRange.value = TimeRange(
                newTime - 30.minutes,
                newTime + 2.hours,
            )
        }

        airportViewState.events.addListener { updatedEvents ->
            currentEvents = updatedEvents.filterIsInstance<RunwayArrivalEvent>()
            showEvents(updatedEvents)
        }

        add(controlPanel, BorderLayout.NORTH)
        add(chartPanel, BorderLayout.CENTER)
        add(timeWindowScrollbar, BorderLayout.SOUTH)
        updateXAxisRange()
    }

    private fun createChart(): JFreeChart {
        val baseChart = ChartFactory.createXYBarChart(
            "",
            null,
            true,
            null,
            dataset
        )

        plot = baseChart.plot as XYPlot
        barDataset = XYBarDataset(dataset, currentBucketSize.inWholeMilliseconds.toDouble())
        plot!!.dataset = barDataset
        plot!!.isOutlineVisible = false

        plot!!.backgroundAlpha = 0.0f
        plot!!.domainGridlinePaint = background
        plot!!.rangeGridlinePaint = background
        plot!!.isRangeGridlinesVisible = false
        plot!!.isDomainGridlinesVisible = false

        baseChart.backgroundPaint = background
        baseChart.title.paint = foreground
        baseChart.removeLegend()

        val domainAxis = plot!!.domainAxis as DateAxis
        domainAxis.timeZone = TimeZone.getTimeZone("UTC") // <- Add this line
        domainAxis.labelPaint = foreground
        domainAxis.tickLabelPaint = foreground
        domainAxis.tickUnit = DateTickUnit(DateTickUnitType.MINUTE, 15)
        domainAxis.dateFormatOverride = SimpleDateFormat("HH:mm").apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        val rangeAxis = plot!!.rangeAxis as NumberAxis
        rangeAxis.labelPaint = foreground
        rangeAxis.tickLabelPaint = foreground
        rangeAxis.standardTickUnits = NumberAxis.createIntegerTickUnits()
        rangeAxis.isAutoRange = true
        rangeAxis.autoRangeMinimumSize = 10.0
        rangeAxis.rangeType = RangeType.POSITIVE

        val renderer = XYBarRenderer()
        renderer.margin = 0.0
        renderer.barAlignmentFactor = 0.0
        renderer.setShadowVisible(false)
        renderer.setSeriesPaint(0, Color(0, 178, 0))
        renderer.barPainter = StandardXYBarPainter()

        renderer.isDrawBarOutline = true
        renderer.setSeriesOutlinePaint(0, background)
        renderer.setSeriesOutlineStroke(0, BasicStroke(1.0f))
        val baseGradient = GradientPaint(
            0f, 100f, Color(75, 243, 98),
            0f, 0f, Color(45, 123, 56),

        )
        renderer.setSeriesPaint(0, baseGradient)
        renderer.gradientPaintTransformer = StandardGradientPaintTransformer(GradientPaintTransformType.VERTICAL)


        renderer.defaultItemLabelsVisible = true
        renderer.defaultItemLabelGenerator = StandardXYItemLabelGenerator()
        renderer.defaultItemLabelPaint = foreground
        renderer.defaultItemLabelFont = Font("SansSerif", Font.BOLD, 12)
        renderer.defaultPositiveItemLabelPosition = ItemLabelPosition(ItemLabelAnchor.INSIDE12, TextAnchor.TOP_CENTER)

        plot!!.renderer = renderer

        return baseChart
    }

    private fun updateChartBarWidth() {
        barDataset = XYBarDataset(dataset, currentBucketSize.inWholeMilliseconds.toDouble())
        plot?.dataset = barDataset
    }

    private fun showEvents(events: List<TimelineEvent>) {
        if (events.isEmpty()) {
            timeSeries.clear()
            updateChartBarWidth()
            return
        }

        val grouped = events.groupBy {
            val timeUtc = it.scheduledTime.epochSeconds
            val bucketSizeSeconds = currentBucketSize.inWholeSeconds
            Instant.fromEpochSeconds(timeUtc / bucketSizeSeconds * bucketSizeSeconds)
        }.mapValues { (_, occurrences) -> occurrences.size }

        timeSeries.clear()

        for ((instant, count) in grouped.entries.sortedBy { it.key }) {
            val date = Date(instant.toEpochMilliseconds())
            val calendar = Calendar.getInstance()
            calendar.time = date
            val minute = Minute(date, calendar)
            timeSeries.addOrUpdate(minute, count)
        }

        updateChartBarWidth()
    }

    private fun updateXAxisRange() {
        val domainAxis = plot?.domainAxis as? DateAxis ?: return
        domainAxis.setRange(
            Date(selectedTimeRange.value.start.toEpochMilliseconds()),
            Date(selectedTimeRange.value.end.toEpochMilliseconds())
        )
    }
}
