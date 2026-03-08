import kotlinx.datetime.Instant
import no.vaccsca.amandman.common.TimelineConfig
import no.vaccsca.amandman.common.TimelineSideConfig
import no.vaccsca.amandman.model.airport.Airport
import no.vaccsca.amandman.model.airport.RunwayStatus
import no.vaccsca.amandman.model.airport.RunwayThreshold
import no.vaccsca.amandman.model.config.AmanDmanSettings
import no.vaccsca.amandman.model.config.AtcClientConnectionParameters
import no.vaccsca.amandman.model.config.ConnectionConfig
import no.vaccsca.amandman.model.config.LabelItem
import no.vaccsca.amandman.model.config.SettingsProvider
import no.vaccsca.amandman.model.config.SharedStateConnectionParameters
import no.vaccsca.amandman.model.config.Side
import no.vaccsca.amandman.model.config.Theme
import no.vaccsca.amandman.model.config.Timeline
import no.vaccsca.amandman.model.integration.AirportIntegrationStatuses
import no.vaccsca.amandman.model.integration.IntegrationDisplayStatus
import no.vaccsca.amandman.model.integration.IntegrationKind
import no.vaccsca.amandman.model.navigation.LatLng
import no.vaccsca.amandman.model.planning.AirportDataSource
import no.vaccsca.amandman.model.timeline.MeteringPointState
import no.vaccsca.amandman.model.timeline.event.NonSequencedEvent
import no.vaccsca.amandman.model.timeline.event.timeline.RunwayEvent
import no.vaccsca.amandman.model.timeline.event.timeline.TimelineEvent
import no.vaccsca.amandman.model.user.UserRole
import no.vaccsca.amandman.model.weather.VerticalWeatherProfile
import no.vaccsca.amandman.presenter.AirportPresenter
import no.vaccsca.amandman.presenter.AirportPresenterInterface
import no.vaccsca.amandman.presenter.AirportViewInterface
import no.vaccsca.amandman.presenter.RepeatingUiTask
import no.vaccsca.amandman.presenter.UiDispatcher
import java.awt.Point
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.minutes

class AirportPresenterTimelineFormTest {

    @Test
    fun `onCreateNewTimelineClicked passes live runways and metering points`() {
        val settingsProvider = FakeSettingsProvider(
            settings = baseSettings(),
            airports = listOf(airport("TEST", runways = setOf("01L", "01R"), meteringPoints = setOf("M1")))
        )
        val view = CapturingAirportView()

        val presenter = createPresenter(settingsProvider, view)

        presenter.onRunwayModesUpdated(
            "TEST",
            mapOf(
                "19L" to RunwayStatus(arrivals = true, departures = false),
                "19R" to RunwayStatus(arrivals = true, departures = true)
            )
        )
        presenter.onMeteringPointStateUpdated(
            "TEST",
            MeteringPointState(availableMeteringPoints = listOf("M2", "M3"))
        )

        presenter.onCreateNewTimelineClicked()

        val args = assertNotNull(view.lastOpenTimelineConfigFormArgs)
        assertEquals(setOf("19L", "19R"), args.availableRunways)
        assertEquals(setOf("M2", "M3"), args.availableMeteringPoints)
    }

    @Test
    fun `onCreateNewTimelineClicked falls back to airport-config runways when no live runway state`() {
        val settingsProvider = FakeSettingsProvider(
            settings = baseSettings(),
            airports = listOf(airport("TEST", runways = setOf("01L", "01R"), meteringPoints = setOf("MPA")))
        )
        val view = CapturingAirportView()
        val presenter = createPresenter(settingsProvider, view)

        presenter.onCreateNewTimelineClicked()

        val args = assertNotNull(view.lastOpenTimelineConfigFormArgs)
        assertEquals(setOf("01L", "01R"), args.availableRunways)
        assertEquals(setOf("MPA"), args.availableMeteringPoints)
    }

    @Test
    fun `onEditTimelineRequested passes existing config and available runway list`() {
        val timeline = Timeline(
            title = "FLOW",
            left = Side.MeteringPoints(listOf("MPX")),
            right = Side.Runways(listOf("19L")),
            arrivalLabelLayoutId = "ARR",
            departureLabelLayoutId = "DEP"
        )

        val settingsProvider = FakeSettingsProvider(
            settings = baseSettings(timelines = mapOf("TEST" to listOf(timeline))),
            airports = listOf(airport("TEST", runways = setOf("19L", "19R"), meteringPoints = setOf("MPX")))
        )
        val view = CapturingAirportView()
        val presenter = createPresenter(settingsProvider, view)

        presenter.onEditTimelineRequested("FLOW")

        val args = assertNotNull(view.lastOpenTimelineConfigFormArgs)
        assertNotNull(args.existingConfig)
        assertEquals("FLOW", args.existingConfig.title)
        assertEquals(setOf("19L", "19R"), args.availableRunways)
        assertEquals(setOf("MPX"), args.availableMeteringPoints)
    }

    @Test
    fun `onTabMenu generates metering point timelines using airport default layout and live fixes`() {
        val settingsProvider = FakeSettingsProvider(
            settings = baseSettings(timelines = mapOf("TEST" to listOf(
                Timeline(
                    title = "M2",
                    right = Side.MeteringPoints(listOf("M2")),
                    arrivalLabelLayoutId = "ARR",
                    departureLabelLayoutId = null
                )
            ))),
            airports = listOf(
                airport(
                    "TEST",
                    runways = setOf("19L"),
                    meteringPoints = setOf("M1"),
                    meteringTimelineArrivalLabelLayoutId = "ARR"
                )
            )
        )
        val view = CapturingAirportView()
        val presenter = createPresenter(settingsProvider, view)

        presenter.onMeteringPointStateUpdated("TEST", MeteringPointState(availableMeteringPoints = listOf("M2", "M3")))
        presenter.onTabMenu(Point(5, 5))

        assertEquals(1, view.lastContextMenuConfiguredTimelines.size)
        assertEquals("M2", view.lastContextMenuConfiguredTimelines.single().title)
        assertEquals(listOf("M2", "M3"), view.lastContextMenuGeneratedTimelines.map { it.title })
        assertTrue(view.lastContextMenuGeneratedTimelines.all { it.depLabelLayout == null })
        assertTrue(view.lastContextMenuGeneratedTimelines.all { it.arrLabelLayout == "ARR" })
        assertTrue(view.lastContextMenuGeneratedTimelines.all { it.right is TimelineSideConfig.MeteringPoints })
    }

    @Test
    fun `onTabMenu falls back to airport metering points when live fixes unavailable`() {
        val settingsProvider = FakeSettingsProvider(
            settings = baseSettings(),
            airports = listOf(
                airport(
                    "TEST",
                    runways = setOf("19L"),
                    meteringPoints = setOf("M9", "M1"),
                    meteringTimelineArrivalLabelLayoutId = "ARR"
                )
            )
        )
        val view = CapturingAirportView()
        val presenter = createPresenter(settingsProvider, view)

        presenter.onTabMenu(Point(1, 1))

        assertEquals(listOf("M1", "M9"), view.lastContextMenuGeneratedTimelines.map { it.title })
    }

    @Test
    fun `onTabMenu generates no metering timelines when airport default layout is missing`() {
        val settingsProvider = FakeSettingsProvider(
            settings = baseSettings(),
            airports = listOf(
                airport(
                    "TEST",
                    runways = setOf("19L"),
                    meteringPoints = setOf("M1"),
                    meteringTimelineArrivalLabelLayoutId = null
                )
            )
        )
        val view = CapturingAirportView()
        val presenter = createPresenter(settingsProvider, view)

        presenter.onTabMenu(Point(1, 1))

        assertTrue(view.lastContextMenuGeneratedTimelines.isEmpty())
    }

    private fun createPresenter(
        settingsProvider: SettingsProvider,
        view: CapturingAirportView,
    ): AirportPresenter {
        return AirportPresenter(
            airportIcao = "TEST",
            dataSource = FakeAirportDataSource("TEST"),
            userRole = UserRole.LOCAL,
            view = view,
            settingsProvider = settingsProvider,
            controllerInfoProvider = { null },
            showErrorMessage = {},
            onAircraftSelectedCallback = {},
            onOpenVerticalProfileCallback = {},
            onRemove = {},
            uiDispatcher = ImmediateUiDispatcher,
        )
    }

    private fun baseSettings(timelines: Map<String, List<Timeline>> = emptyMap()): AmanDmanSettings {
        return AmanDmanSettings(
            timelines = timelines,
            connectionConfig = ConnectionConfig(
                atcClient = AtcClientConnectionParameters(host = "127.0.0.1", port = 6809),
                api = SharedStateConnectionParameters(host = "http://localhost:3000")
            ),
            arrivalLabelLayouts = mapOf("ARR" to emptyList<LabelItem>()),
            departureLabelLayouts = mapOf("DEP" to emptyList<LabelItem>()),
            theme = Theme.FLATLAF_DARK
        )
    }

    private fun airport(
        icao: String,
        runways: Set<String>,
        meteringPoints: Set<String>,
        meteringTimelineArrivalLabelLayoutId: String? = null
    ): Airport {
        val runwayMap = runways.associateWith { id ->
            RunwayThreshold(
                id = id,
                latLng = LatLng(60.0, 11.0),
                elevation = 200f,
                trueHeading = 190f,
                stars = emptyList()
            )
        }
        return Airport(
            icao = icao,
            location = LatLng(60.0, 11.0),
            runways = runwayMap,
            independentRunwaySystems = emptyList(),
            sequencingHorizon = 30.minutes,
            lockedHorizon = 10.minutes,
            meteringPoints = meteringPoints.toList(),
            meteringTimelineArrivalLabelLayoutId = meteringTimelineArrivalLabelLayoutId
        )
    }

    private object ImmediateUiDispatcher : UiDispatcher {
        override fun isUiThread(): Boolean = true
        override fun dispatch(action: () -> Unit) = action()
        override fun scheduleRepeating(intervalMs: Int, action: () -> Unit): RepeatingUiTask = RepeatingUiTask {}
    }

    private class FakeAirportDataSource(
        override val airportIcao: String,
    ) : AirportDataSource {
        override val isReadOnly: Boolean = true
        override fun start() {}
        override fun stop() {}
        override fun startDataCollection() {}
        override fun getIntegrationStatuses(): AirportIntegrationStatuses = AirportIntegrationStatuses.errorAll("test")
    }

    private class FakeSettingsProvider(
        private val settings: AmanDmanSettings,
        private val airports: List<Airport>,
    ) : SettingsProvider {
        override fun getSettings(reload: Boolean): AmanDmanSettings = settings
        override fun getAirportData(reload: Boolean): List<Airport> = airports
    }

    private data class OpenTimelineConfigFormArgs(
        val availableTagLayoutsDep: Set<String>,
        val availableTagLayoutsArr: Set<String>,
        val availableRunways: Set<String>,
        val availableMeteringPoints: Set<String>,
        val existingConfig: TimelineConfig?,
    )

    private class CapturingAirportView : AirportViewInterface {
        override lateinit var airportPresenterInterface: AirportPresenterInterface

        var lastOpenTimelineConfigFormArgs: OpenTimelineConfigFormArgs? = null
        var lastContextMenuConfiguredTimelines: List<TimelineConfig> = emptyList()
        var lastContextMenuGeneratedTimelines: List<TimelineConfig> = emptyList()

        override fun updateTab(timelineEvents: List<TimelineEvent>, nonSequencedList: List<NonSequencedEvent>) {}
        override fun updateWeatherData(weather: VerticalWeatherProfile?) {}
        override fun updateIntegrationStatuses(statuses: Map<IntegrationKind, IntegrationDisplayStatus>) {}
        override fun updateRunwayModes(runwayModes: List<Pair<String, Boolean>>) {}
        override fun updateMinimumSpacing(minimumSpacingNm: Double) {}
        override fun updateDraggedLabel(timelineEvent: TimelineEvent, newInstant: Instant, isAvailable: Boolean) {}
        override fun updateMeteringPointState(meteringPointState: MeteringPointState) {}
        override fun showAirportContextMenu(
            cusomizedTimelines: List<TimelineConfig>,
            generatedMeteringPointTimelines: List<TimelineConfig>,
            screenPos: Point
        ) {
            lastContextMenuConfiguredTimelines = cusomizedTimelines
            lastContextMenuGeneratedTimelines = generatedMeteringPointTimelines
        }
        override fun openMetWindow() {}
        override fun openLandingRatesWindow() {}
        override fun openNonSequencedWindow() {}
        override fun showMinimumSpacingDialog(default: Double) {}
        override fun openSelectRunwayDialog(
            runwayEvent: RunwayEvent,
            runwayOptions: Set<String>,
            onSubmit: (String?) -> Unit,
            onCancel: () -> Unit,
        ) {}

        override fun openTimelineConfigForm(
            availableTagLayoutsDep: Set<String>,
            availableTagLayoutsArr: Set<String>,
            availableRunways: Set<String>,
            availableMeteringPoints: Set<String>,
            existingConfig: TimelineConfig?,
        ) {
            lastOpenTimelineConfigFormArgs = OpenTimelineConfigFormArgs(
                availableTagLayoutsDep = availableTagLayoutsDep,
                availableTagLayoutsArr = availableTagLayoutsArr,
                availableRunways = availableRunways,
                availableMeteringPoints = availableMeteringPoints,
                existingConfig = existingConfig,
            )
        }

        override fun closeTimelineForm() {}
        override fun addNewTimeline(timelineConfig: TimelineConfig) {}
        override fun removeTimeline(timelineConfig: TimelineConfig) {}
        override fun setSelectedAircraftCallsign(callsign: String) {}
    }
}
