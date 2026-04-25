package no.vaccsca.amandman.view

import kotlinx.datetime.Instant
import no.vaccsca.amandman.common.FeederFixTimelineConfig
import no.vaccsca.amandman.common.RunwayTimelineConfig
import no.vaccsca.amandman.common.TimelineConfig
import no.vaccsca.amandman.model.timeline.CreateOrUpdateTimelineDto
import no.vaccsca.amandman.model.timeline.event.timeline.RunwayEvent
import no.vaccsca.amandman.model.timeline.event.timeline.TimelineEvent
import no.vaccsca.amandman.presenter.AirportPresenterInterface
import no.vaccsca.amandman.view.forms.NewTimelineForm
import java.awt.Component
import java.awt.Container
import java.awt.Point
import javax.swing.*
import kotlin.test.*

class NewTimelineFormTest {

    @BeforeTest
    fun setupHeadless() {
        System.setProperty("java.awt.headless", "true")
    }

    @Test
    fun `anchor type switch toggles both side cards and departure layout enabled state`() {
        onEdt {
            val presenter = CapturingPresenter()
            val form = NewTimelineForm(
                presenterInterface = presenter,
                airportIcao = "TEST",
                existingConfig = null,
                availableRunwaysInitial = setOf("19L", "19R"),
                availableFixesInitial = setOf("M1", "M2")
            )

            val anchorTypeCombo = findByName<JComboBox<*>>(form, "timelineAnchorTypeCombo")
            val rightRunwayCard = findByName<Component>(form, "rightRunwayCard")
            val rightMeteringCard = findByName<Component>(form, "rightFeederFixCard")
            val leftRunwayCard = findByName<Component>(form, "leftRunwayCard")
            val leftMeteringCard = findByName<Component>(form, "leftFeederFixCard")
            val depLayoutCombo = findByName<JComboBox<*>>(form, "departureLayoutCombo")

            assertTrue(rightRunwayCard.isVisible)
            assertFalse(rightMeteringCard.isVisible)
            assertTrue(leftRunwayCard.isVisible)
            assertFalse(leftMeteringCard.isVisible)
            assertTrue(depLayoutCombo.isEnabled)

            anchorTypeCombo.selectedIndex = 1

            assertFalse(rightRunwayCard.isVisible)
            assertTrue(rightMeteringCard.isVisible)
            assertFalse(leftRunwayCard.isVisible)
            assertTrue(leftMeteringCard.isVisible)
            assertFalse(depLayoutCombo.isEnabled)
        }
    }

    @Test
    fun `selected target is removed from opposite side list`() {
        onEdt {
            val presenter = CapturingPresenter()
            val form = createForm(presenter = presenter)
            form.update(
                arrLayouts = setOf("ARR"),
                depLayouts = setOf("DEP"),
                availableRunways = setOf("19L", "19R"),
                availableFixes = setOf("M1", "M2")
            )

            val rightRunwayList = findByName<JList<String>>(form, "rightRunwayList")
            val leftRunwayList = findByName<JList<String>>(form, "leftRunwayList")

            selectValues(rightRunwayList, setOf("19L"))

            val leftOptions = (0 until leftRunwayList.model.size).map { index -> leftRunwayList.model.getElementAt(index) }.toSet()
            assertTrue("19L" !in leftOptions)
            assertTrue("19R" in leftOptions)
        }
    }

    @Test
    fun `existing runway config preselects runway side targets`() {
        onEdt {
            val presenter = CapturingPresenter()
            val existingConfig: TimelineConfig = RunwayTimelineConfig(
                title = "FLOW",
                airportIcao = "TEST",
                leftRunways = listOf("19R"),
                rightRunways = listOf("19L", "19R"),
                depLabelLayout = "DEP",
                arrLabelLayout = "ARR"
            )

            val form = createForm(presenter = presenter, existingConfig = existingConfig)
            form.update(
                arrLayouts = setOf("ARR"),
                depLayouts = setOf("DEP"),
                availableRunways = setOf("19L", "19R"),
                availableFixes = setOf("M1", "M2")
            )

            val rightRunwayList = findByName<JList<String>>(form, "rightRunwayList")
            val leftRunwayList = findByName<JList<String>>(form, "leftRunwayList")

            assertEquals(setOf("19L"), rightRunwayList.selectedValuesList.toSet())
            assertEquals(setOf("19R"), leftRunwayList.selectedValuesList.toSet())
        }
    }

    @Test
    fun `unknown legacy targets are preserved in selectable options`() {
        onEdt {
            val presenter = CapturingPresenter()
            val existingConfig: TimelineConfig = RunwayTimelineConfig(
                title = "LEGACY",
                airportIcao = "TEST",
                leftRunways = emptyList(),
                rightRunways = listOf("XX1"),
                depLabelLayout = "DEP",
                arrLabelLayout = "ARR"
            )

            val form = createForm(
                presenter = presenter,
                existingConfig = existingConfig,
                availableRunwaysInitial = setOf("19L"),
                availableFixesInitial = emptySet()
            )
            form.update(
                arrLayouts = setOf("ARR"),
                depLayouts = setOf("DEP"),
                availableRunways = setOf("19L"),
                availableFixes = emptySet()
            )

            val rightRunwayList = findByName<JList<String>>(form, "rightRunwayList")
            val options = (0 until rightRunwayList.model.size).map { idx -> rightRunwayList.model.getElementAt(idx) }.toSet()

            assertTrue("XX1" in options)
            assertEquals(setOf("XX1"), rightRunwayList.selectedValuesList.toSet())
        }
    }

    @Test
    fun `right side requires at least one selected target`() {
        onEdt {
            val presenter = CapturingPresenter()
            val form = createForm(
                presenter = presenter,
                availableRunwaysInitial = setOf("19L"),
                availableFixesInitial = setOf("M1")
            )
            form.update(
                arrLayouts = setOf("ARR"),
                depLayouts = setOf("DEP"),
                availableRunways = setOf("19L"),
                availableFixes = setOf("M1")
            )

            findByName<JTextField>(form, "titleInput").text = "NEW"
            findByName<JButton>(form, "saveButton").doClick()

            assertTrue(presenter.createdTimelines.isEmpty())
        }
    }

    @Test
    fun `submit maps feeder fix timeline to feeder fix dto without departure layout`() {
        onEdt {
            val presenter = CapturingPresenter()
            val form = createForm(presenter = presenter)
            form.update(
                arrLayouts = setOf("ARR"),
                depLayouts = setOf("DEP"),
                availableRunways = setOf("19L", "19R"),
                availableFixes = setOf("M1", "M2")
            )

            populateMeteringForm(form)
            findByName<JButton>(form, "saveButton").doClick()

            assertEquals(1, presenter.createdTimelines.size)
            val created = presenter.createdTimelines.single()
            assertTrue(created is CreateOrUpdateTimelineDto.FeederFix)
            assertEquals(setOf("M2"), created.left.toSet())
            assertEquals(setOf("M1"), created.right.toSet())
        }
    }

    @Test
    fun `delete button is hidden in create mode`() {
        onEdt {
            val presenter = CapturingPresenter()
            val form = createForm(presenter = presenter)

            val deleteButton = findByName<JButton>(form, "deleteButton")
            assertFalse(deleteButton.isVisible)
        }
    }

    @Test
    fun `delete button is shown for saved edit mode and requires confirmation`() {
        onEdt {
            val presenter = CapturingPresenter()
            var confirmCalls = 0
            val form = createForm(
                presenter = presenter,
                existingConfig = RunwayTimelineConfig(
                    title = "FLOW",
                    airportIcao = "TEST",
                    leftRunways = emptyList(),
                    rightRunways = listOf("19L"),
                    depLabelLayout = "DEP",
                    arrLabelLayout = "ARR"
                ),
                canDeleteExistingConfig = true,
                confirmDeleteAction = {
                    confirmCalls += 1
                    true
                }
            )

            val deleteButton = findByName<JButton>(form, "deleteButton")
            assertTrue(deleteButton.isVisible)

            deleteButton.doClick()

            assertEquals(1, confirmCalls)
            assertEquals(1, presenter.deleteRequests)
        }
    }
    @Test
    fun existingConfigTitleCanBeChangedBeforeSave() {
        onEdt {
            val presenter = CapturingPresenter()
            val form = createForm(
                presenter = presenter,
                existingConfig = FeederFixTimelineConfig(
                    title = "FLOW",
                    airportIcao = "TEST",
                    leftFixes = listOf("M2"),
                    rightFixes = listOf("M1"),
                    arrLabelLayout = "ARR",
                    timelineId = "saved-mp",
                )
            )
            form.update(
                arrLayouts = setOf("ARR"),
                depLayouts = setOf("DEP"),
                availableRunways = setOf("19L", "19R"),
                availableFixes = setOf("M1", "M2")
            )

            val titleInput = findByName<JTextField>(form, "titleInput")
            assertTrue(titleInput.isEnabled)
            titleInput.text = "RENAMED"
            findByName<JButton>(form, "saveButton").doClick()

            val created = presenter.createdTimelines.single() as CreateOrUpdateTimelineDto.FeederFix
            assertEquals("RENAMED", created.title)
            assertEquals("saved-mp", created.timelineId)
        }
    }

    private fun createForm(
        presenter: CapturingPresenter,
        existingConfig: TimelineConfig? = null,
        availableRunwaysInitial: Set<String> = setOf("19L", "19R"),
        availableFixesInitial: Set<String> = setOf("M1", "M2"),
        canDeleteExistingConfig: Boolean = false,
        confirmDeleteAction: (() -> Boolean)? = null,
    ): NewTimelineForm = NewTimelineForm(
        presenterInterface = presenter,
        airportIcao = "TEST",
        existingConfig = existingConfig,
        availableRunwaysInitial = availableRunwaysInitial,
        availableFixesInitial = availableFixesInitial,
        canDeleteExistingConfig = canDeleteExistingConfig,
        confirmDeleteAction = confirmDeleteAction,
    )

    private fun populateMeteringForm(form: NewTimelineForm) {
        findByName<JTextField>(form, "titleInput").text = "FLOW"
        findByName<JComboBox<*>>(form, "timelineAnchorTypeCombo").selectedIndex = 1

        val leftMeteringList = findByName<JList<String>>(form, "leftFeederFixList")
        val rightMeteringList = findByName<JList<String>>(form, "rightFeederFixList")
        selectValues(leftMeteringList, setOf("M2"))
        selectValues(rightMeteringList, setOf("M1", "M2"))
    }

    private fun selectValues(list: JList<String>, values: Set<String>) {
        val indices = (0 until list.model.size)
            .filter { index -> list.model.getElementAt(index) in values }
            .toIntArray()
        list.selectedIndices = indices
    }

    private fun <T : Component> findByName(root: Container, name: String): T {
        findByNameOrNull<T>(root, name)?.let { return it }
        error("Component with name '$name' not found")
    }

    private fun <T : Component> findByNameOrNull(root: Container, name: String): T? {
        if (root.name == name) {
            @Suppress("UNCHECKED_CAST")
            return root as T
        }

        for (component in root.components) {
            if (component.name == name) {
                @Suppress("UNCHECKED_CAST")
                return component as T
            }
            if (component is Container) {
                val nested = findByNameOrNull<T>(component, name)
                if (nested != null) return nested
            }
        }

        return null
    }

    private fun <T> onEdt(block: () -> T): T {
        if (SwingUtilities.isEventDispatchThread()) {
            return block()
        }

        var result: T? = null
        var failure: Throwable? = null

        SwingUtilities.invokeAndWait {
            try {
                result = block()
            } catch (t: Throwable) {
                failure = t
            }
        }

        failure?.let { throw it }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    private class CapturingPresenter : AirportPresenterInterface {
        override val airportIcao: String = "TEST"
        val createdTimelines = mutableListOf<CreateOrUpdateTimelineDto>()
        var deleteRequests = 0

        override fun onLabelDrag(timelineEvent: TimelineEvent, newInstant: Instant) {}
        override fun onLabelDragEnd(timelineEvent: TimelineEvent, newScheduledTime: Instant, newRunway: String?) {}
        override fun onRecalculateSequenceClicked(callSign: String?) {}
        override fun onMinimumSpacingDistanceSet(minimumSpacingDistanceNm: Double) {}
        override fun onSetMinSpacingSelectionClicked(minSpacingSelectionNm: Double?) {}
        override fun onOpenMetWindowClicked() {}
        override fun onOpenLandingRatesWindow() {}
        override fun onOpenNonSequencedWindow() {}
        override fun onOpenVerticalProfileWindowClicked(callsign: String) {}
        override fun onAircraftSelected(callsign: String) {}
        override fun beginRunwaySelection(runwayEvent: RunwayEvent, onSubmit: (runway: String?) -> Unit, onCancel: () -> Unit) {}
        override fun onToggleShowDepartures(selected: Boolean) {}
        override fun onReloadWindsClicked() {}
        override fun onHighlightAreasOnRadarClicked() {}
        override fun onTabMenu(screenPos: Point) {}
        override fun onCreateNewTimelineClicked() {}
        override fun onAddTimelineButtonClicked(timelineConfig: TimelineConfig) {}
        override fun onRemoveTimelineClicked(timelineConfig: TimelineConfig) {}
        override fun onEditTimelineRequested(timelineConfig: TimelineConfig) {}
        override fun onMoveTimelineLeftRequested(timelineConfig: TimelineConfig) {}
        override fun onMoveTimelineRightRequested(timelineConfig: TimelineConfig) {}
        override fun onCreateNewTimeline(config: CreateOrUpdateTimelineDto) {
            createdTimelines += config
        }

        override fun onDeleteEditedTimeline() {
            deleteRequests += 1
        }
        override fun onRemoveTab() {}
    }
}
