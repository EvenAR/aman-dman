import kotlinx.datetime.Instant
import no.vaccsca.amandman.common.FeederFixTimelineConfig
import no.vaccsca.amandman.common.RunwayTimelineConfig
import no.vaccsca.amandman.common.TimelineConfig
import no.vaccsca.amandman.model.airport.Airport
import no.vaccsca.amandman.model.airport.RunwayArrivalProfile
import no.vaccsca.amandman.model.airport.RunwayStatus
import no.vaccsca.amandman.model.airport.RunwayThreshold
import no.vaccsca.amandman.model.config.*
import no.vaccsca.amandman.model.integration.AirportDataSource
import no.vaccsca.amandman.model.integration.AirportIntegrationStatuses
import no.vaccsca.amandman.model.integration.IntegrationDisplayStatus
import no.vaccsca.amandman.model.integration.IntegrationKind
import no.vaccsca.amandman.model.navigation.LatLng
import no.vaccsca.amandman.model.timeline.CreateOrUpdateTimelineDto
import no.vaccsca.amandman.model.timeline.FeederFixState
import no.vaccsca.amandman.model.timeline.event.NonSequencedEvent
import no.vaccsca.amandman.model.timeline.event.timeline.RunwayEvent
import no.vaccsca.amandman.model.timeline.event.timeline.TimelineEvent
import no.vaccsca.amandman.model.user.UserRole
import no.vaccsca.amandman.model.weather.VerticalWeatherProfile
import no.vaccsca.amandman.presenter.*
import java.awt.Point
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class AirportPresenterTimelineFormTest {

    @Test
    fun `onCreateNewTimelineClicked passes live runways and fixes`() {
        val settingsProvider = FakeSettingsProvider(
            settings = baseSettings(),
            airports = listOf(airport("TEST", runways = setOf("01L", "01R"), fixes = setOf("M1")))
        )
        val timelineStore = InMemoryTimelineSettingsStore()
        val view = CapturingAirportView()

        val presenter = createPresenter(settingsProvider, timelineStore, view)

        presenter.onRunwayModesUpdated(
            "TEST",
            mapOf(
                "19L" to RunwayStatus(arrivals = true, departures = false),
                "19R" to RunwayStatus(arrivals = true, departures = true)
            )
        )
        presenter.onFeederFixStateUpdated(
            "TEST",
            FeederFixState(availableFixes = listOf("M2", "M3"))
        )

        presenter.onCreateNewTimelineClicked()

        val args = assertNotNull(view.lastOpenTimelineConfigFormArgs)
        assertEquals(setOf("19L", "19R"), args.availableRunways)
        assertEquals(setOf("M2", "M3"), args.availableFixes)
        assertTrue(!args.canDeleteExistingConfig)
    }

    @Test
    fun `onCreateNewTimelineClicked falls back to airport-config runways when no live runway state`() {
        val settingsProvider = FakeSettingsProvider(
            settings = baseSettings(),
            airports = listOf(airport("TEST", runways = setOf("01L", "01R"), fixes = setOf("MPA")))
        )
        val timelineStore = InMemoryTimelineSettingsStore()
        val view = CapturingAirportView()
        val presenter = createPresenter(settingsProvider, timelineStore, view)

        presenter.onCreateNewTimelineClicked()

        val args = assertNotNull(view.lastOpenTimelineConfigFormArgs)
        assertEquals(setOf("01L", "01R"), args.availableRunways)
        assertEquals(setOf("MPA"), args.availableFixes)
    }

    @Test
    fun `onEditTimelineRequested passes delete-enabled flag for saved timelines`() {
        val settingsProvider = FakeSettingsProvider(
            settings = baseSettings(),
            airports = listOf(airport("TEST", runways = setOf("19L", "19R"), fixes = setOf("MPX")))
        )
        val timelineStore = InMemoryTimelineSettingsStore(
            timelines = linkedMapOf(
                "TEST" to AirportTimelines(
                    defaults = TimelineDefaults(defaultArrivalLabelLayoutId = "ARR", defaultDepartureLabelLayoutId = "DEP"),
                    feederFixBased = listOf(
                        FeederFixTimeline(
                            title = "FLOW",
                            left = listOf("MPX"),
                            right = listOf("MPY"),
                            arrivalLabelLayoutId = "ARR",
                        )
                    )
                )
            )
        )
        val view = CapturingAirportView()
        val presenter = createPresenter(settingsProvider, timelineStore, view)

        val generatedTimeline: TimelineConfig = FeederFixTimelineConfig(
            title = "FLOW",
            airportIcao = "TEST",
            leftFixes = listOf("MPX"),
            rightFixes = listOf("MPY"),
            arrLabelLayout = "ARR"
        )

        presenter.onEditTimelineRequested(generatedTimeline)

        val args = assertNotNull(view.lastOpenTimelineConfigFormArgs)
        assertNotNull(args.existingConfig)
        assertEquals(generatedTimeline, args.existingConfig)
        assertEquals(setOf("19L", "19R"), args.availableRunways)
        assertEquals(setOf("MPX"), args.availableFixes)
        assertTrue(args.canDeleteExistingConfig)
    }

    @Test
    fun `onEditTimelineRequested keeps delete hidden for unsaved timelines`() {
        val settingsProvider = FakeSettingsProvider(
            settings = baseSettings(),
            airports = listOf(airport("TEST", runways = setOf("19L"), fixes = setOf("M1")))
        )
        val timelineStore = InMemoryTimelineSettingsStore()
        val view = CapturingAirportView()
        val presenter = createPresenter(settingsProvider, timelineStore, view)

        val unsaved: TimelineConfig = FeederFixTimelineConfig(
            title = "M1",
            airportIcao = "TEST",
            leftFixes = emptyList(),
            rightFixes = listOf("M1"),
            arrLabelLayout = "ARR"
        )

        presenter.onEditTimelineRequested(unsaved)

        val args = assertNotNull(view.lastOpenTimelineConfigFormArgs)
        assertTrue(!args.canDeleteExistingConfig)
    }

    @Test
    fun `onCreateNewTimeline replaces original when edit mode is active and persists update`() {
        val settingsProvider = FakeSettingsProvider(
            settings = baseSettings(),
            airports = listOf(airport("TEST", runways = setOf("19L"), fixes = setOf("M1")))
        )
        val timelineStore = InMemoryTimelineSettingsStore(
            timelines = linkedMapOf(
                "TEST" to AirportTimelines(
                    defaults = TimelineDefaults(defaultArrivalLabelLayoutId = "ARR", defaultDepartureLabelLayoutId = "DEP"),
                    runwayBased = listOf(
                        RunwayTimeline(
                            title = "OLD",
                            left = emptyList(),
                            right = listOf("19L"),
                            arrivalLabelLayoutId = "ARR",
                            departureLabelLayoutId = "DEP"
                        )
                    )
                )
            )
        )
        val view = CapturingAirportView()
        val presenter = createPresenter(settingsProvider, timelineStore, view)

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
        assertEquals(1, view.closeTimelineFormCalls)
        assertEquals("NEW", timelineStore.timelines.getValue("TEST").runwayBased.single().title)
    }

    @Test
    fun `onCreateNewTimeline adds and persists when not editing`() {
        val settingsProvider = FakeSettingsProvider(
            settings = baseSettings(),
            airports = listOf(airport("TEST", runways = setOf("19L"), fixes = setOf("M1")))
        )
        val timelineStore = InMemoryTimelineSettingsStore()
        val view = CapturingAirportView()
        val presenter = createPresenter(settingsProvider, timelineStore, view)

        presenter.onCreateNewTimeline(
            CreateOrUpdateTimelineDto.FeederFix(
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
        assertTrue(view.addedTimelines.single() is FeederFixTimelineConfig)
        assertEquals(1, view.closeTimelineFormCalls)
        assertEquals("NEW", timelineStore.timelines.getValue("TEST").feederFixBased.single().title)
    }

    @Test
    fun `editing unsaved timeline creates saved entry and replaces strip`() {
        val settingsProvider = FakeSettingsProvider(
            settings = baseSettings(),
            airports = listOf(airport("TEST", runways = setOf("19L"), fixes = setOf("M1")))
        )
        val timelineStore = InMemoryTimelineSettingsStore()
        val view = CapturingAirportView()
        val presenter = createPresenter(settingsProvider, timelineStore, view)

        val unsaved: TimelineConfig = FeederFixTimelineConfig(
            title = "M1",
            airportIcao = "TEST",
            leftFixes = emptyList(),
            rightFixes = listOf("M1"),
            arrLabelLayout = "ARR"
        )
        presenter.onEditTimelineRequested(unsaved)

        presenter.onCreateNewTimeline(
            CreateOrUpdateTimelineDto.FeederFix(
                airportIcao = "TEST",
                title = "CUSTOM",
                left = emptyList(),
                right = listOf("M1"),
                arrLabelLayout = "ARR"
            )
        )

        assertEquals(1, view.replacedTimelines.size)
        assertEquals(unsaved, view.replacedTimelines.single().first)
        assertEquals("CUSTOM", timelineStore.timelines.getValue("TEST").feederFixBased.single().title)
    }
    @Test
    fun `duplicate title can be overwritten when confirmed`() {
        val settingsProvider = FakeSettingsProvider(
            settings = baseSettings(),
            airports = listOf(airport("TEST", runways = setOf("19L"), fixes = setOf("M1")))
        )
        val timelineStore = InMemoryTimelineSettingsStore(
            timelines = linkedMapOf(
                "TEST" to AirportTimelines(
                    defaults = TimelineDefaults(defaultArrivalLabelLayoutId = "ARR", defaultDepartureLabelLayoutId = "DEP"),
                    runwayBased = listOf(
                        RunwayTimeline(
                            title = "FLOW",
                            left = emptyList(),
                            right = listOf("19L"),
                            arrivalLabelLayoutId = "ARR",
                            departureLabelLayoutId = "DEP",
                            timelineId = "saved-flow",
                        )
                    )
                )
            )
        )
        val view = CapturingAirportView().apply {
            overwriteConfirmationResult = true
        }
        val presenter = createPresenter(settingsProvider, timelineStore, view)

        presenter.onCreateNewTimeline(
            CreateOrUpdateTimelineDto.Runway(
                airportIcao = "TEST",
                title = "FLOW",
                left = listOf("19L"),
                right = emptyList(),
                depLabelLayout = "DEP",
                arrLabelLayout = "ARR",
            )
        )

        val savedRunwayTimelines = timelineStore.timelines.getValue("TEST").runwayBased
        assertEquals(listOf("FLOW"), view.overwritePrompts)
        assertEquals(1, savedRunwayTimelines.size)
        assertEquals(listOf("19L"), savedRunwayTimelines.single().left)
        assertEquals(emptyList(), savedRunwayTimelines.single().right)
        assertEquals(1, view.closeTimelineFormCalls)
    }

    @Test
    fun `overwrite removes conflicting open saved timeline when editing another timeline`() {
        val settingsProvider = FakeSettingsProvider(
            settings = baseSettings(),
            airports = listOf(airport("TEST", runways = setOf("19L"), fixes = setOf("M1")))
        )
        val timelineStore = InMemoryTimelineSettingsStore(
            timelines = linkedMapOf(
                "TEST" to AirportTimelines(
                    defaults = TimelineDefaults(defaultArrivalLabelLayoutId = "ARR", defaultDepartureLabelLayoutId = "DEP"),
                    runwayBased = listOf(
                        RunwayTimeline(
                            title = "FLOW",
                            left = emptyList(),
                            right = listOf("19L"),
                            arrivalLabelLayoutId = "ARR",
                            departureLabelLayoutId = "DEP",
                            timelineId = "saved-flow",
                        )
                    )
                )
            )
        )
        val view = CapturingAirportView().apply {
            overwriteConfirmationResult = true
        }
        val presenter = createPresenter(settingsProvider, timelineStore, view)

        val saved: TimelineConfig = RunwayTimelineConfig(
            title = "FLOW",
            airportIcao = "TEST",
            leftRunways = emptyList(),
            rightRunways = listOf("19L"),
            depLabelLayout = "DEP",
            arrLabelLayout = "ARR",
            timelineId = "saved-flow",
        )
        val unsaved: TimelineConfig = FeederFixTimelineConfig(
            title = "TEMP",
            airportIcao = "TEST",
            leftFixes = emptyList(),
            rightFixes = listOf("M1"),
            arrLabelLayout = "ARR",
        )

        presenter.onAddTimelineButtonClicked(saved)
        presenter.onAddTimelineButtonClicked(unsaved)
        presenter.onEditTimelineRequested(unsaved)
        view.removedTimelines.clear()
        view.replacedTimelines.clear()
        view.closeTimelineFormCalls = 0

        presenter.onCreateNewTimeline(
            CreateOrUpdateTimelineDto.Runway(
                airportIcao = "TEST",
                title = "FLOW",
                left = listOf("19L"),
                right = emptyList(),
                depLabelLayout = "DEP",
                arrLabelLayout = "ARR",
            )
        )

        assertEquals(listOf(saved), view.removedTimelines)
        assertEquals(1, view.replacedTimelines.size)
        assertEquals(unsaved, view.replacedTimelines.single().first)
        assertEquals("FLOW", view.replacedTimelines.single().second.title)
        assertEquals(1, timelineStore.timelines.getValue("TEST").runwayBased.size)
        assertEquals(listOf("19L"), timelineStore.timelines.getValue("TEST").runwayBased.single().left)
        assertEquals(emptyList(), timelineStore.timelines.getValue("TEST").runwayBased.single().right)
    }

    @Test
    fun `duplicate title stays unchanged when overwrite is declined`() {
        val settingsProvider = FakeSettingsProvider(
            settings = baseSettings(),
            airports = listOf(airport("TEST", runways = setOf("19L"), fixes = setOf("M1")))
        )
        val timelineStore = InMemoryTimelineSettingsStore(
            timelines = linkedMapOf(
                "TEST" to AirportTimelines(
                    defaults = TimelineDefaults(defaultArrivalLabelLayoutId = "ARR", defaultDepartureLabelLayoutId = "DEP"),
                    runwayBased = listOf(
                        RunwayTimeline(
                            title = "FLOW",
                            left = emptyList(),
                            right = listOf("19L"),
                            arrivalLabelLayoutId = "ARR",
                            departureLabelLayoutId = "DEP",
                            timelineId = "saved-flow",
                        )
                    )
                )
            )
        )
        val view = CapturingAirportView().apply {
            overwriteConfirmationResult = false
        }
        val presenter = createPresenter(settingsProvider, timelineStore, view)

        presenter.onCreateNewTimeline(
            CreateOrUpdateTimelineDto.Runway(
                airportIcao = "TEST",
                title = "FLOW",
                left = listOf("19L"),
                right = emptyList(),
                depLabelLayout = "DEP",
                arrLabelLayout = "ARR",
            )
        )

        val savedRunwayTimeline = timelineStore.timelines.getValue("TEST").runwayBased.single()
        assertEquals(listOf("FLOW"), view.overwritePrompts)
        assertEquals(emptyList<String>(), savedRunwayTimeline.left)
        assertEquals(listOf("19L"), savedRunwayTimeline.right)
        assertTrue(view.addedTimelines.isEmpty())
        assertTrue(view.replacedTimelines.isEmpty())
        assertEquals(0, view.closeTimelineFormCalls)
    }

    @Test
    fun `onDeleteEditedTimeline deletes saved config and removes live strip`() {
        val settingsProvider = FakeSettingsProvider(
            settings = baseSettings(),
            airports = listOf(airport("TEST", runways = setOf("19L"), fixes = setOf("M1")))
        )
        val timelineStore = InMemoryTimelineSettingsStore(
            timelines = linkedMapOf(
                "TEST" to AirportTimelines(
                    defaults = TimelineDefaults(defaultArrivalLabelLayoutId = "ARR", defaultDepartureLabelLayoutId = "DEP"),
                    runwayBased = listOf(
                        RunwayTimeline(
                            title = "FLOW",
                            left = emptyList(),
                            right = listOf("19L"),
                            arrivalLabelLayoutId = "ARR",
                            departureLabelLayoutId = "DEP"
                        )
                    )
                )
            )
        )
        val view = CapturingAirportView()
        val presenter = createPresenter(settingsProvider, timelineStore, view)

        val saved: TimelineConfig = RunwayTimelineConfig(
            title = "FLOW",
            airportIcao = "TEST",
            leftRunways = emptyList(),
            rightRunways = listOf("19L"),
            depLabelLayout = "DEP",
            arrLabelLayout = "ARR"
        )
        presenter.onEditTimelineRequested(saved)

        presenter.onDeleteEditedTimeline()

        assertEquals(listOf(saved), view.removedTimelines)
        assertEquals(1, view.closeTimelineFormCalls)
        assertTrue("TEST" !in timelineStore.timelines)
    }

    @Test
    fun `failed persistence shows error and does not mutate strip`() {
        val settingsProvider = FakeSettingsProvider(
            settings = baseSettings(),
            airports = listOf(airport("TEST", runways = setOf("19L"), fixes = setOf("M1")))
        )
        val timelineStore = InMemoryTimelineSettingsStore(failure = IllegalStateException("disk full"))
        val view = CapturingAirportView()
        val errors = mutableListOf<String>()
        val presenter = createPresenter(settingsProvider, timelineStore, view, errors)

        presenter.onCreateNewTimeline(
            CreateOrUpdateTimelineDto.FeederFix(
                airportIcao = "TEST",
                title = "NEW",
                left = emptyList(),
                right = listOf("M1"),
                arrLabelLayout = "ARR"
            )
        )

        assertTrue(view.addedTimelines.isEmpty())
        assertTrue(view.replacedTimelines.isEmpty())
        assertEquals(0, view.closeTimelineFormCalls)
        assertEquals(listOf("Unable to save timeline: disk full"), errors)
    }

    @Test
    fun `move timeline requests are forwarded to view with expected direction`() {
        val settingsProvider = FakeSettingsProvider(
            settings = baseSettings(),
            airports = listOf(airport("TEST", runways = setOf("19L"), fixes = setOf("M1")))
        )
        val timelineStore = InMemoryTimelineSettingsStore()
        val view = CapturingAirportView()
        val presenter = createPresenter(settingsProvider, timelineStore, view)

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
    fun `onTabMenu generates feeder fix timelines using airport default layout and live fixes`() {
        val settingsProvider = FakeSettingsProvider(
            settings = baseSettings(),
            airports = listOf(
                airport(
                    "TEST",
                    runways = setOf("19L"),
                    fixes = setOf("M1"),
                    feederFixTimelineArrivalLabelLayoutId = "ARR"
                )
            )
        )
        val timelineStore = InMemoryTimelineSettingsStore(
            timelines = linkedMapOf(
                "TEST" to AirportTimelines(
                    defaults = TimelineDefaults(defaultArrivalLabelLayoutId = "ARR", defaultDepartureLabelLayoutId = "DEP"),
                    feederFixBased = listOf(
                        FeederFixTimeline(
                            title = "M2",
                            right = listOf("M2"),
                            arrivalLabelLayoutId = "ARR",
                        )
                    )
                )
            )
        )
        val view = CapturingAirportView()
        val presenter = createPresenter(settingsProvider, timelineStore, view)

        presenter.onFeederFixStateUpdated("TEST", FeederFixState(availableFixes = listOf("M2", "M3")))
        presenter.onTabMenu(Point(5, 5))

        assertEquals(1, view.lastContextMenuConfiguredTimelines.size)
        assertEquals("M2", view.lastContextMenuConfiguredTimelines.single().title)
        assertEquals(listOf("M2", "M3"), view.lastContextMenuGeneratedTimelines.map { it.title })
        assertTrue(view.lastContextMenuGeneratedTimelines.all { it is FeederFixTimelineConfig })
        assertTrue(view.lastContextMenuGeneratedTimelines.all { (it as FeederFixTimelineConfig).arrLabelLayout == "ARR" })
    }

    @Test
    fun `onTabMenu falls back to airport fixes when live fixes unavailable`() {
        val settingsProvider = FakeSettingsProvider(
            settings = baseSettings(),
            airports = listOf(
                airport(
                    "TEST",
                    runways = setOf("19L"),
                    fixes = setOf("M9", "M1"),
                    feederFixTimelineArrivalLabelLayoutId = "ARR"
                )
            )
        )
        val timelineStore = InMemoryTimelineSettingsStore()
        val view = CapturingAirportView()
        val presenter = createPresenter(settingsProvider, timelineStore, view)

        presenter.onTabMenu(Point(1, 1))

        assertEquals(listOf("M1", "M9"), view.lastContextMenuGeneratedTimelines.map { it.title })
    }

    @Test
    fun `onTabMenu generates no feeder fix timelines when airport default layout is missing`() {
        val settingsProvider = FakeSettingsProvider(
            settings = baseSettings(),
            airports = listOf(
                airport(
                    "TEST",
                    runways = setOf("19L"),
                    fixes = setOf("M1"),
                    feederFixTimelineArrivalLabelLayoutId = null
                )
            )
        )
        val timelineStore = InMemoryTimelineSettingsStore()
        val view = CapturingAirportView()
        val presenter = createPresenter(settingsProvider, timelineStore, view)

        presenter.onTabMenu(Point(1, 1))

        assertTrue(view.lastContextMenuGeneratedTimelines.isEmpty())
    }
    @Test
    fun savingEditedSavedTimelineRenamesAndOverwritesOriginalConfig() {
        val settingsProvider = FakeSettingsProvider(
            settings = baseSettings(),
            airports = listOf(airport("TEST", runways = setOf("19L"), fixes = setOf("M1")))
        )
        val timelineStore = InMemoryTimelineSettingsStore(
            timelines = linkedMapOf(
                "TEST" to AirportTimelines(
                    defaults = TimelineDefaults(defaultArrivalLabelLayoutId = "ARR", defaultDepartureLabelLayoutId = "DEP"),
                    runwayBased = listOf(
                        RunwayTimeline(
                            title = "FLOW",
                            left = emptyList(),
                            right = listOf("19L"),
                            arrivalLabelLayoutId = "ARR",
                            departureLabelLayoutId = "DEP",
                            timelineId = "saved-flow",
                        )
                    )
                )
            )
        )
        val view = CapturingAirportView()
        val presenter = createPresenter(settingsProvider, timelineStore, view)

        val saved: TimelineConfig = RunwayTimelineConfig(
            title = "FLOW",
            airportIcao = "TEST",
            leftRunways = emptyList(),
            rightRunways = listOf("19L"),
            depLabelLayout = "DEP",
            arrLabelLayout = "ARR",
            timelineId = "saved-flow",
        )

        presenter.onAddTimelineButtonClicked(saved)
        presenter.onEditTimelineRequested(saved)
        presenter.onCreateNewTimeline(
            CreateOrUpdateTimelineDto.Runway(
                airportIcao = "TEST",
                title = "FLOW-RENAMED",
                left = listOf("19L"),
                right = emptyList(),
                depLabelLayout = "DEP",
                arrLabelLayout = "ARR",
                timelineId = "saved-flow",
            )
        )

        val savedRunwayTimelines = timelineStore.timelines.getValue("TEST").runwayBased
        assertEquals(1, savedRunwayTimelines.size)
        assertEquals("saved-flow", savedRunwayTimelines.single().timelineId)
        assertEquals("FLOW-RENAMED", savedRunwayTimelines.single().title)
        assertEquals(listOf("19L"), savedRunwayTimelines.single().left)
        assertEquals(emptyList(), savedRunwayTimelines.single().right)
    }
    private fun createPresenter(
        settingsProvider: SettingsProvider,
        timelineSettingsStore: TimelineSettingsStore,
        view: CapturingAirportView,
        errors: MutableList<String> = mutableListOf(),
    ): AirportPresenter {
        return AirportPresenter(
            airportIcao = "TEST",
            dataSource = FakeAirportDataSource("TEST"),
            userRole = UserRole.LOCAL,
            view = view,
            settingsProvider = settingsProvider,
            timelineSettingsStore = timelineSettingsStore,
            controllerInfoProvider = { null },
            showErrorMessage = { errors += it },
            onAircraftSelectedCallback = {},
            onOpenVerticalProfileCallback = {},
            onRemove = {},
            uiDispatcher = ImmediateUiDispatcher,
        )
    }

    private fun baseSettings(): AmanDmanSettings {
        return AmanDmanSettings(
            connectionConfig = ConnectionConfig(
                atcClient = AtcClientConnectionParameters(host = "127.0.0.1", port = 6809),
                api = SharedStateConnectionParameters(host = "http://localhost:3000")
            ),
            arrivalLabelLayouts = mapOf("ARR" to emptyList()),
            departureLabelLayouts = mapOf("DEP" to emptyList()),
            theme = Theme.FLATLAF_DARK,
            planningSettings = PlanningSettings(true, 15.0)
        )
    }

    private fun airport(
        icao: String,
        runways: Set<String>,
        fixes: Set<String>,
        feederFixTimelineArrivalLabelLayoutId: String? = null
    ): Airport {
        val runwayMap = runways.associateWith { id ->
            RunwayThreshold(
                id = id,
                latLng = LatLng(60.0, 11.0),
                elevation = 200f,
                trueHeading = 190f,
                arrivalProfiles = listOf(
                    RunwayArrivalProfile(
                        arrivalNamePattern = "*",
                        fixExpectations = emptyList(),
                    )
                )
            )
        }
        return Airport(
            icao = icao,
            location = LatLng(60.0, 11.0),
            runways = runwayMap,
            independentRunwaySystems = emptyList(),
            sequencingHorizon = 30.minutes,
            lockedHorizon = 10.minutes,
            feederFixes = fixes.toList(),
            feederFixTimelineArrivalLabelLayoutId = feederFixTimelineArrivalLabelLayoutId
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

    private class InMemoryTimelineSettingsStore(
        initialTimelines: Map<String, AirportTimelines> = emptyMap(),
        private val failure: Exception? = null,
    ) : TimelineSettingsStore {
        var timelines: LinkedHashMap<String, AirportTimelines> = LinkedHashMap(initialTimelines)
            private set
        var saveCalls: Int = 0
            private set

        constructor(
            timelines: LinkedHashMap<String, AirportTimelines>,
            failure: Exception? = null,
        ) : this(initialTimelines = timelines, failure = failure)

        override fun getTimelines(reload: Boolean): Map<String, AirportTimelines> = timelines

        override fun saveAirportTimelines(airportIcao: String, timelines: AirportTimelines?) {
            failure?.let { throw it }
            saveCalls += 1
            if (timelines == null) {
                this.timelines.remove(airportIcao)
            } else {
                this.timelines[airportIcao] = timelines
            }
        }
    }

    private data class OpenTimelineConfigFormArgs(
        val availableTagLayoutsDep: Set<String>,
        val availableTagLayoutsArr: Set<String>,
        val availableRunways: Set<String>,
        val availableFixes: Set<String>,
        val existingConfig: TimelineConfig?,
        val canDeleteExistingConfig: Boolean,
    )

    private class CapturingAirportView : AirportViewInterface {
        override lateinit var airportPresenterInterface: AirportPresenterInterface

        var lastOpenTimelineConfigFormArgs: OpenTimelineConfigFormArgs? = null
        var overwriteConfirmationResult: Boolean = true
        val overwritePrompts = mutableListOf<String>()
        var lastContextMenuConfiguredTimelines: List<TimelineConfig> = emptyList()
        var lastContextMenuGeneratedTimelines: List<TimelineConfig> = emptyList()
        val addedTimelines = mutableListOf<TimelineConfig>()
        val removedTimelines = mutableListOf<TimelineConfig>()
        val replacedTimelines = mutableListOf<Pair<TimelineConfig, TimelineConfig>>()
        val moveTimelineCalls = mutableListOf<Pair<TimelineConfig, Int>>()
        var closeTimelineFormCalls = 0

        override fun updateTab(timelineEvents: List<TimelineEvent>, nonSequencedList: List<NonSequencedEvent>) {}
        override fun updateWeatherData(weather: VerticalWeatherProfile?) {}
        override fun updateIntegrationStatuses(statuses: Map<IntegrationKind, IntegrationDisplayStatus>) {}
        override fun updateRunwayModes(runwayModes: List<Pair<String, Boolean>>) {}
        override fun updateMinimumSpacing(minimumSpacingNm: Double) {}
        override fun updateDraggedLabel(timelineEvent: TimelineEvent, newInstant: Instant, isAvailable: Boolean) {}
        override fun updateFeederFixState(feederFixState: FeederFixState) {}
        override fun showAirportContextMenu(
            customizedTimelines: List<TimelineConfig>,
            generatedFixTimelines: List<TimelineConfig>,
            screenPos: Point
        ) {
            lastContextMenuConfiguredTimelines = customizedTimelines
            lastContextMenuGeneratedTimelines = generatedFixTimelines
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
            availableFixes: Set<String>,
            existingConfig: TimelineConfig?,
            canDeleteExistingConfig: Boolean,
        ) {
            lastOpenTimelineConfigFormArgs = OpenTimelineConfigFormArgs(
                availableTagLayoutsDep = availableTagLayoutsDep,
                availableTagLayoutsArr = availableTagLayoutsArr,
                availableRunways = availableRunways,
                availableFixes = availableFixes,
                existingConfig = existingConfig,
                canDeleteExistingConfig = canDeleteExistingConfig,
            )
        }

        override fun confirmTimelineOverwrite(title: String): Boolean {
            overwritePrompts += title
            return overwriteConfirmationResult
        }

        override fun closeTimelineForm() {
            closeTimelineFormCalls += 1
        }
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
