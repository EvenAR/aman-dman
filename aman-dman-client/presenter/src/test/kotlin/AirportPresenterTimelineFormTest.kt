import kotlinx.datetime.Instant
import no.vaccsca.amandman.common.MeteringPointTimelineConfig
import no.vaccsca.amandman.common.RunwayTimelineConfig
import no.vaccsca.amandman.common.TimelineConfig
import no.vaccsca.amandman.model.airport.Airport
import no.vaccsca.amandman.model.airport.RunwayStatus
import no.vaccsca.amandman.model.airport.RunwayThreshold
import no.vaccsca.amandman.model.config.AirportTimelines
import no.vaccsca.amandman.model.config.AmanDmanSettings
import no.vaccsca.amandman.model.config.AtcClientConnectionParameters
import no.vaccsca.amandman.model.config.ConnectionConfig
import no.vaccsca.amandman.model.config.LabelItem
import no.vaccsca.amandman.model.config.MeteringPointTimeline
import no.vaccsca.amandman.model.config.RunwayTimeline
import no.vaccsca.amandman.model.config.SettingsProvider
import no.vaccsca.amandman.model.config.SharedStateConnectionParameters
import no.vaccsca.amandman.model.config.Theme
import no.vaccsca.amandman.model.config.TimelineDefaults
import no.vaccsca.amandman.model.integration.AirportIntegrationStatuses
import no.vaccsca.amandman.model.integration.IntegrationDisplayStatus
import no.vaccsca.amandman.model.integration.IntegrationKind
import no.vaccsca.amandman.model.navigation.LatLng
import no.vaccsca.amandman.model.planning.AirportDataSource
import no.vaccsca.amandman.model.timeline.CreateOrUpdateTimelineDto
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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
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
    fun `onEditTimelineRequested passes exact config and available runway list`() {
        val settingsProvider = FakeSettingsProvider(
            settings = baseSettings(
                timelines = mapOf(
                    "TEST" to AirportTimelines(
                        defaults = TimelineDefaults(defaultArrivalLabelLayoutId = "ARR", defaultDepartureLabelLayoutId = "DEP"),
                        meteringPointBased = listOf(
                            MeteringPointTimeline(
                                title = "FLOW",
                                left = listOf("MPX"),
                                right = listOf("MPY"),
                                arrivalLabelLayoutId = "ARR",
                            )
                        )
                    )
                )
            ),
            airports = listOf(airport("TEST", runways = setOf("19L", "19R"), meteringPoints = setOf("MPX")))
        )
        val view = CapturingAirportView()
        val presenter = createPresenter(settingsProvider, view)

        val generatedTimeline: TimelineConfig = MeteringPointTimelineConfig(
            title = "FLOW-GEN",
            airportIcao = "TEST",
            leftMeteringPoints = emptyList(),
            rightMeteringPoints = listOf("MPX"),
            arrLabelLayout = "ARR"
        )

        presenter.onEditTimelineRequested(generatedTimeline)

        val args = assertNotNull(view.lastOpenTimelineConfigFormArgs)
        assertNotNull(args.existingConfig)
        assertEquals(generatedTimeline, args.existingConfig)
        assertEquals(setOf("19L", "19R"), args.availableRunways)
        assertEquals(setOf("MPX"), args.availableMeteringPoints)
    }

    @Test
    fun `onCreateNewTimeline replaces original when edit mode is active`() {
        val settingsProvider = FakeSettingsProvider(
            settings = baseSettings(),
            airports = listOf(airport("TEST", runways = setOf("19L"), meteringPoints = setOf("M1")))
        )
        val view = CapturingAirportView()
        val presenter = createPresenter(settingsProvider, view)

        val existing: TimelineConfig = RunwayTimelineConfig(
            title = "OLD",
            airportIcao = "TEST",
            leftRunways = emptyList(),
            rightRunways = listOf("19L"),
            depLabelLayout = "DEP",
            arrLabelLayout = "ARR"
        )
        presenter.onEditTimelineRequested(existing)

        presenter.onCreateNewTimeline(
            CreateOrUpdateTimelineDto.Runway(
                airportIcao = "TEST",
                title = "NEW",
                left = emptyList(),
                right = listOf("19L"),
                depLabelLayout = "DEP",
                arrLabelLayout = "ARR"
            )
        )

        assertTrue(view.removedTimelines.isEmpty())
        assertTrue(view.addedTimelines.isEmpty())
        assertEquals(1, view.replacedTimelines.size)
        assertEquals(existing, view.replacedTimelines.single().first)
        assertEquals("NEW", view.replacedTimelines.single().second.title)
    }

    @Test
    fun `onCreateNewTimeline adds without removal when not editing`() {
        val settingsProvider = FakeSettingsProvider(
            settings = baseSettings(),
            airports = listOf(airport("TEST", runways = setOf("19L"), meteringPoints = setOf("M1")))
        )
        val view = CapturingAirportView()
        val presenter = createPresenter(settingsProvider, view)

        presenter.onCreateNewTimeline(
            CreateOrUpdateTimelineDto.MeteringPoint(
                airportIcao = "TEST",
                title = "NEW",
                left = emptyList(),
                right = listOf("M1"),
                arrLabelLayout = "ARR"
            )
        )

        assertTrue(view.removedTimelines.isEmpty())
        assertEquals(1, view.addedTimelines.size)
        assertEquals("NEW", view.addedTimelines.single().title)
        assertTrue(view.addedTimelines.single() is MeteringPointTimelineConfig)
    }

    @Test
    fun `move timeline requests are forwarded to view with expected direction`() {
        val settingsProvider = FakeSettingsProvider(
            settings = baseSettings(),
            airports = listOf(airport("TEST", runways = setOf("19L"), meteringPoints = setOf("M1")))
        )
        val view = CapturingAirportView()
        val presenter = createPresenter(settingsProvider, view)

        val timeline: TimelineConfig = RunwayTimelineConfig(
            title = "FLOW",
            airportIcao = "TEST",
            leftRunways = emptyList(),
            rightRunways = listOf("19L"),
            depLabelLayout = "DEP",
            arrLabelLayout = "ARR"
        )

        presenter.onMoveTimelineLeftRequested(timeline)
        presenter.onMoveTimelineRightRequested(timeline)

        assertEquals(listOf(timeline to -1, timeline to 1), view.moveTimelineCalls)
    }

    @Test
    fun `onTabMenu generates metering point timelines using airport default layout and live fixes`() {
        val settingsProvider = FakeSettingsProvider(
            settings = baseSettings(
                timelines = mapOf(
                    "TEST" to AirportTimelines(
                        defaults = TimelineDefaults(defaultArrivalLabelLayoutId = "ARR", defaultDepartureLabelLayoutId = "DEP"),
                        meteringPointBased = listOf(
                            MeteringPointTimeline(
                                title = "M2",
                                right = listOf("M2"),
                                arrivalLabelLayoutId = "ARR",
                            )
                        )
                    )
                )
            ),
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
        assertTrue(view.lastContextMenuGeneratedTimelines.all { it is MeteringPointTimelineConfig })
        assertTrue(view.lastContextMenuGeneratedTimelines.all { (it as MeteringPointTimelineConfig).arrLabelLayout == "ARR" })
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

    private fun baseSettings(timelines: Map<String, AirportTimelines> = emptyMap()): AmanDmanSettings {
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
        val addedTimelines = mutableListOf<TimelineConfig>()
        val removedTimelines = mutableListOf<TimelineConfig>()
        val replacedTimelines = mutableListOf<Pair<TimelineConfig, TimelineConfig>>()
        val moveTimelineCalls = mutableListOf<Pair<TimelineConfig, Int>>()

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
        override fun addNewTimeline(timelineConfig: TimelineConfig) {
            addedTimelines += timelineConfig
        }
        override fun removeTimeline(timelineConfig: TimelineConfig) {
            removedTimelines += timelineConfig
        }
        override fun replaceTimeline(previous: TimelineConfig, updated: TimelineConfig) {
            replacedTimelines += previous to updated
        }
        override fun moveTimeline(timelineConfig: TimelineConfig, positions: Int) {
            moveTimelineCalls += timelineConfig to positions
        }
        override fun setSelectedAircraftCallsign(callsign: String) {}
    }
}
