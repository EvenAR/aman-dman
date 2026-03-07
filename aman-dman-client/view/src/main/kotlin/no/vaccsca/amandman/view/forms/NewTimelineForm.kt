package no.vaccsca.amandman.view.forms

import no.vaccsca.amandman.common.TimelineConfig
import no.vaccsca.amandman.common.TimelineSideConfig
import no.vaccsca.amandman.model.timeline.CreateOrUpdateTimelineDto
import no.vaccsca.amandman.presenter.AirportPresenterInterface
import java.awt.CardLayout
import java.awt.Dialog
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.GraphicsEnvironment
import java.awt.Insets
import java.awt.Window
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JDialog
import javax.swing.JList
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.ListSelectionModel
import javax.swing.JTextField

class NewTimelineForm(
    private val presenterInterface: AirportPresenterInterface,
    private val airportIcao: String,
    existingConfig: TimelineConfig?,
    availableRunwaysInitial: Set<String>,
    availableMeteringPointsInitial: Set<String>,
) : JPanel() {

    private enum class AnchorType(val label: String) {
        RUNWAYS("Runways"),
        METERING_POINTS("Metering points");

        override fun toString(): String = label
    }

    private data class SideTargetSelector(
        val targetCards: JPanel,
        val runwayList: JList<String>,
        val meteringPointList: JList<String>,
    )

    private val initialDepLayoutSelection = existingConfig?.depLabelLayout
    private val initialArrLayoutSelection = existingConfig?.arrLabelLayout

    private val titleInput = JTextField(20)
    private val anchorTypeCombo = JComboBox(AnchorType.entries.toTypedArray())
    private val leftSelector = createSideTargetSelector("left")
    private val rightSelector = createSideTargetSelector("right")

    private val depLayoutCombo = JComboBox<String>()
    private val arrLayoutCombo = JComboBox<String>()

    private var availableRunways: Set<String> = availableRunwaysInitial.map { it.uppercase() }.toSet()
    private var availableMeteringPoints: Set<String> = availableMeteringPointsInitial.map { it.uppercase() }.toSet()
    private var parentDialog: JDialog? = null
    private var syncingListState = false

    init {
        border = BorderFactory.createEmptyBorder(15, 15, 15, 15)
        layout = GridBagLayout()
        minimumSize = Dimension(460, 430)

        val gbc = GridBagConstraints().apply {
            fill = GridBagConstraints.HORIZONTAL
            anchor = GridBagConstraints.WEST
            insets = Insets(5, 5, 5, 5)
        }

        titleInput.name = "titleInput"
        FormUtils.enforceUppercase(titleInput)

        anchorTypeCombo.name = "timelineAnchorTypeCombo"
        anchorTypeCombo.addActionListener {
            applyAnchorType(anchorTypeCombo.selectedItem as AnchorType)
        }

        addLabeledField("Timeline Title*", titleInput, gbc, row = 0)
        addLabeledField("Timeline anchor type*", anchorTypeCombo, gbc, row = 1)
        addLabeledField("Right side targets*", rightSelector.targetCards, gbc, row = 2, fill = GridBagConstraints.BOTH, weightY = 0.5)
        addLabeledField("Left side targets", leftSelector.targetCards, gbc, row = 3, fill = GridBagConstraints.BOTH, weightY = 0.5)

        addLabeledField("Arrival Layout*", arrLayoutCombo, gbc, row = 4)
        addLabeledField("Departure Layout*", depLayoutCombo, gbc, row = 5)

        val submitButton = JButton("Submit").apply {
            name = "submitButton"
            addActionListener { handleSubmit() }
        }

        gbc.gridx = 0
        gbc.gridy = 6
        gbc.gridwidth = 2
        gbc.weightx = 1.0
        gbc.weighty = 0.0
        gbc.fill = GridBagConstraints.NONE
        gbc.anchor = GridBagConstraints.CENTER
        add(submitButton, gbc)

        attachListSyncListeners()
        refreshSideOptionLists(existingConfig)
        existingConfig?.let { applyExistingConfig(it) } ?: applyAnchorType(AnchorType.RUNWAYS)
        syncCrossSideAvailability()
    }

    private fun createSideTargetSelector(prefix: String): SideTargetSelector {
        val runwayList = createSelectionList("${prefix}RunwayList")
        val meteringPointList = createSelectionList("${prefix}MeteringPointList")

        val runwayCard = JScrollPane(runwayList).apply {
            name = "${prefix}RunwayCard"
            minimumSize = Dimension(140, 70)
            preferredSize = Dimension(240, 110)
        }
        val meteringCard = JScrollPane(meteringPointList).apply {
            name = "${prefix}MeteringPointCard"
            minimumSize = Dimension(140, 70)
            preferredSize = Dimension(240, 110)
        }

        val targetCards = JPanel(CardLayout()).apply {
            name = "${prefix}TargetCards"
            add(runwayCard, AnchorType.RUNWAYS.name)
            add(meteringCard, AnchorType.METERING_POINTS.name)
        }

        return SideTargetSelector(targetCards, runwayList, meteringPointList)
    }

    private fun createSelectionList(name: String): JList<String> = JList<String>().apply {
        this.name = name
        selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
        visibleRowCount = 6
    }

    private fun addLabeledField(
        labelText: String,
        component: JComponent,
        gbc: GridBagConstraints,
        row: Int,
        fill: Int = GridBagConstraints.HORIZONTAL,
        weightY: Double = 0.0,
    ) {
        gbc.gridx = 0
        gbc.gridy = row
        gbc.weightx = 0.0
        gbc.weighty = 0.0
        gbc.gridwidth = 1
        gbc.fill = GridBagConstraints.HORIZONTAL
        add(javax.swing.JLabel(labelText), gbc)

        gbc.gridx = 1
        gbc.weightx = 1.0
        gbc.weighty = weightY
        gbc.fill = fill
        add(component, gbc)

        gbc.weighty = 0.0
        gbc.fill = GridBagConstraints.HORIZONTAL
    }

    fun update(
        arrLayouts: Set<String>,
        depLayouts: Set<String>,
        availableRunways: Set<String>,
        availableMeteringPoints: Set<String>,
    ) {
        updateLayoutCombo(depLayoutCombo, depLayouts, initialDepLayoutSelection)
        updateLayoutCombo(arrLayoutCombo, arrLayouts, initialArrLayoutSelection)

        this.availableRunways = availableRunways.map { it.uppercase() }.toSet()
        this.availableMeteringPoints = availableMeteringPoints.map { it.uppercase() }.toSet()

        refreshSideOptionLists()
        applyAnchorType(anchorTypeCombo.selectedItem as AnchorType)
        syncCrossSideAvailability()
    }

    private fun updateLayoutCombo(combo: JComboBox<String>, options: Set<String>, initialSelection: String?) {
        val currentSelection = combo.selectedItem as? String
        combo.removeAllItems()
        options.sorted().forEach { combo.addItem(it) }

        val preferredSelection = currentSelection ?: initialSelection
        if (preferredSelection != null && options.contains(preferredSelection)) {
            combo.selectedItem = preferredSelection
        } else if (combo.itemCount > 0) {
            combo.selectedIndex = 0
        }
    }

    fun open(owner: Window) {
        parentDialog = JDialog(owner, "Timeline Configuration", Dialog.ModalityType.APPLICATION_MODAL).apply {
            contentPane = this@NewTimelineForm
            pack()
            setLocationRelativeTo(owner)
            isResizable = true
            defaultCloseOperation = JDialog.DISPOSE_ON_CLOSE
            isVisible = true
        }
    }

    private fun handleSubmit() {
        val titleText = titleInput.text.trim()
        if (titleText.isEmpty()) {
            error("Title is required.")
            return
        }

        val anchorType = anchorTypeCombo.selectedItem as AnchorType
        val rightTargets = getSelectedTargets(rightSelector, anchorType)
        if (rightTargets.isEmpty()) {
            error("Right side targets are required.")
            return
        }

        val leftTargets = getSelectedTargets(leftSelector, anchorType)

        presenterInterface.onCreateNewTimeline(
            CreateOrUpdateTimelineDto(
                airportIcao = airportIcao,
                title = titleText,
                left = buildSide(anchorType, leftTargets),
                right = buildSide(anchorType, rightTargets),
                depLabelLayout = depLayoutCombo.selectedItem as? String ?: "",
                arrLabelLayout = arrLayoutCombo.selectedItem as? String ?: ""
            )
        )

        parentDialog?.dispose()
    }

    private fun getSelectedTargets(selector: SideTargetSelector, type: AnchorType): List<String> = when (type) {
        AnchorType.RUNWAYS -> selector.runwayList.selectedValuesList.map { it.uppercase() }
        AnchorType.METERING_POINTS -> selector.meteringPointList.selectedValuesList.map { it.uppercase() }
    }

    private fun buildSide(type: AnchorType, targets: List<String>): CreateOrUpdateTimelineDto.TimeLineSide = when (type) {
        AnchorType.RUNWAYS -> CreateOrUpdateTimelineDto.TimeLineSide.Runways(targetRunways = targets)
        AnchorType.METERING_POINTS -> CreateOrUpdateTimelineDto.TimeLineSide.MeteringPoints(targetMeteringPoints = targets)
    }

    private fun applyExistingConfig(config: TimelineConfig) {
        titleInput.text = config.title

        applyExistingSideSelections(leftSelector, config.left)
        applyExistingSideSelections(rightSelector, config.right)

        val anchorType = anchorTypeFor(config.right)
        anchorTypeCombo.selectedItem = anchorType
        applyAnchorType(anchorType)

        depLayoutCombo.selectedItem = config.depLabelLayout
        arrLayoutCombo.selectedItem = config.arrLabelLayout
    }

    private fun applyExistingSideSelections(selector: SideTargetSelector, side: TimelineSideConfig) {
        when (side) {
            is TimelineSideConfig.Runways -> {
                selectListValues(selector.runwayList, side.runways.map { it.uppercase() }.toSet())
                selector.meteringPointList.clearSelection()
            }

            is TimelineSideConfig.MeteringPoints -> {
                selectListValues(selector.meteringPointList, side.meteringPoints.map { it.uppercase() }.toSet())
                selector.runwayList.clearSelection()
            }
        }
    }

    private fun anchorTypeFor(side: TimelineSideConfig): AnchorType = when (side) {
        is TimelineSideConfig.Runways -> AnchorType.RUNWAYS
        is TimelineSideConfig.MeteringPoints -> AnchorType.METERING_POINTS
    }

    private fun applyAnchorType(anchorType: AnchorType) {
        showCardForSide(leftSelector, anchorType)
        showCardForSide(rightSelector, anchorType)
    }

    private fun showCardForSide(selector: SideTargetSelector, anchorType: AnchorType) {
        (selector.targetCards.layout as CardLayout).show(selector.targetCards, anchorType.name)
    }

    private fun refreshSideOptionLists(preservedConfig: TimelineConfig? = null) {
        refreshSideOptions(
            selector = leftSelector,
            preservedRunways = (preservedConfig?.left as? TimelineSideConfig.Runways)
                ?.runways
                ?.map { it.uppercase() }
                ?.toSet()
                ?: emptySet(),
            preservedMeteringPoints = (preservedConfig?.left as? TimelineSideConfig.MeteringPoints)
                ?.meteringPoints
                ?.map { it.uppercase() }
                ?.toSet()
                ?: emptySet()
        )

        refreshSideOptions(
            selector = rightSelector,
            preservedRunways = (preservedConfig?.right as? TimelineSideConfig.Runways)
                ?.runways
                ?.map { it.uppercase() }
                ?.toSet()
                ?: emptySet(),
            preservedMeteringPoints = (preservedConfig?.right as? TimelineSideConfig.MeteringPoints)
                ?.meteringPoints
                ?.map { it.uppercase() }
                ?.toSet()
                ?: emptySet()
        )
    }

    private fun refreshSideOptions(
        selector: SideTargetSelector,
        preservedRunways: Set<String>,
        preservedMeteringPoints: Set<String>,
    ) {
        val selectedRunways = selector.runwayList.selectedValuesList.map { it.uppercase() }.toSet()
        val selectedMeteringPoints = selector.meteringPointList.selectedValuesList.map { it.uppercase() }.toSet()

        val runwayOptions = (availableRunways + selectedRunways + preservedRunways).sorted()
        val meteringPointOptions = (availableMeteringPoints + selectedMeteringPoints + preservedMeteringPoints).sorted()

        selector.runwayList.setListData(runwayOptions.toTypedArray())
        selector.meteringPointList.setListData(meteringPointOptions.toTypedArray())

        selectListValues(selector.runwayList, selectedRunways + preservedRunways)
        selectListValues(selector.meteringPointList, selectedMeteringPoints + preservedMeteringPoints)
    }

    private fun attachListSyncListeners() {
        listOf(
            leftSelector.runwayList,
            rightSelector.runwayList,
            leftSelector.meteringPointList,
            rightSelector.meteringPointList,
        ).forEach { list ->
            list.addListSelectionListener {
                if (!it.valueIsAdjusting) {
                    syncCrossSideAvailability()
                }
            }
        }
    }

    private fun syncCrossSideAvailability() {
        if (syncingListState) return
        syncingListState = true
        try {
            syncOneTypeAcrossSides(
                leftList = leftSelector.runwayList,
                rightList = rightSelector.runwayList,
                configuredOptions = availableRunways,
            )
            syncOneTypeAcrossSides(
                leftList = leftSelector.meteringPointList,
                rightList = rightSelector.meteringPointList,
                configuredOptions = availableMeteringPoints,
            )
        } finally {
            syncingListState = false
        }
    }

    private fun syncOneTypeAcrossSides(
        leftList: JList<String>,
        rightList: JList<String>,
        configuredOptions: Set<String>,
    ) {
        val leftModelValues = listModelValues(leftList)
        val rightModelValues = listModelValues(rightList)

        val rightSelected = rightList.selectedValuesList.map { it.uppercase() }.toSet()
        val leftSelectedRaw = leftList.selectedValuesList.map { it.uppercase() }.toSet()
        val leftSelected = leftSelectedRaw - rightSelected

        val allOptions = (configuredOptions + leftModelValues + rightModelValues + leftSelected + rightSelected).sorted()

        val leftOptions = (allOptions - rightSelected).sorted()
        val rightOptions = (allOptions - leftSelected).sorted()

        leftList.setListData(leftOptions.toTypedArray())
        rightList.setListData(rightOptions.toTypedArray())

        selectListValues(leftList, leftSelected)
        selectListValues(rightList, rightSelected)
    }

    private fun listModelValues(list: JList<String>): Set<String> {
        val values = mutableSetOf<String>()
        for (index in 0 until list.model.size) {
            values += list.model.getElementAt(index).uppercase()
        }
        return values
    }

    private fun selectListValues(list: JList<String>, values: Set<String>) {
        if (values.isEmpty()) {
            list.clearSelection()
            return
        }

        val selectedIndexes = mutableListOf<Int>()
        for (index in 0 until list.model.size) {
            val value = list.model.getElementAt(index)
            if (value in values) {
                selectedIndexes += index
            }
        }

        if (selectedIndexes.isNotEmpty()) {
            list.selectedIndices = selectedIndexes.toIntArray()
        } else {
            list.clearSelection()
        }
    }

    private fun error(msg: String) {
        if (GraphicsEnvironment.isHeadless()) return
        JOptionPane.showMessageDialog(this, msg, "Validation Error", JOptionPane.ERROR_MESSAGE)
    }
}
