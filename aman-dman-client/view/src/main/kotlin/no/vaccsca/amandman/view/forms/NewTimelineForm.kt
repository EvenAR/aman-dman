package no.vaccsca.amandman.view.forms

import no.vaccsca.amandman.common.TimelineConfig
import no.vaccsca.amandman.model.data.dto.CreateOrUpdateTimelineDto
import no.vaccsca.amandman.presenter.AirportPresenterInterface
import java.awt.*
import javax.swing.*

class NewTimelineForm(
    private val presenterInterface: AirportPresenterInterface,
    private val airportIcao: String,
    existingConfig: TimelineConfig?
) : JPanel() {

    private val titleInput = JTextField(20)
    private val leftRunwaysInput = JTextField(20)
    private val rightRunwaysInput = JTextField(20)

    private val depLayoutCombo = JComboBox<String>()
    private val arrLayoutCombo = JComboBox<String>()

    private var parentDialog: JDialog? = null

    init {
        border = BorderFactory.createEmptyBorder(15, 15, 15, 15)
        layout = GridBagLayout()
        val gbc = GridBagConstraints().apply {
            fill = GridBagConstraints.HORIZONTAL
            anchor = GridBagConstraints.WEST
            insets = Insets(5, 5, 5, 5)
        }

        // --- Title ---
        addLabeledField("Timeline Title*", titleInput, gbc, 0)
        FormUtils.enforceUppercase(titleInput)

        // --- Right side runways ---
        addLabeledField("Right side runways* (comma separated)", rightRunwaysInput, gbc, 1)
        FormUtils.enforceUppercase(rightRunwaysInput)

        // --- Left side runways ---
        addLabeledField("Left side runways (comma separated)", leftRunwaysInput, gbc, 2)
        FormUtils.enforceUppercase(leftRunwaysInput)

        // --- Arrival layout ---
        addLabeledField("Arrival Layout*", arrLayoutCombo, gbc, 3)

        // --- Departure layout ---
        addLabeledField("Departure Layout*", depLayoutCombo, gbc, 4)

        // --- Submit button ---
        val submitButton = JButton("Submit").apply {
            addActionListener { handleSubmit() }
        }

        gbc.gridx = 0
        gbc.gridy = 5
        gbc.gridwidth = 2
        gbc.anchor = GridBagConstraints.CENTER
        add(submitButton, gbc)

        // --- Pre-fill existing config ---
        existingConfig?.let { config ->
            titleInput.text = config.title
            leftRunwaysInput.text = config.runwaysLeft.joinToString(",")
            rightRunwaysInput.text = config.runwaysRight.joinToString(",")
            depLayoutCombo.selectedItem = config.depLabelLayout
            arrLayoutCombo.selectedItem = config.arrLabelLayout
        }
    }

    /**
     * Utility function to add a label + field in one go.
     */
    private fun addLabeledField(labelText: String, component: JComponent, gbc: GridBagConstraints, row: Int) {
        gbc.gridx = 0
        gbc.gridy = row
        gbc.weightx = 0.0
        add(JLabel(labelText), gbc)

        gbc.gridx = 1
        gbc.weightx = 1.0
        add(component, gbc)
    }

    /**
     * Populate layout dropdowns after creation.
     */
    fun update(arrLayouts: Set<String>, depLayouts: Set<String>) {
        depLayoutCombo.removeAllItems()
        depLayouts.forEach { depLayoutCombo.addItem(it) }

        arrLayoutCombo.removeAllItems()
        arrLayouts.forEach { arrLayoutCombo.addItem(it) }

        if (depLayoutCombo.itemCount > 0) depLayoutCombo.selectedIndex = 0
        if (arrLayoutCombo.itemCount > 0) arrLayoutCombo.selectedIndex = 0
    }

    /**
     * Opens the form inside a modal dialog.
     */
    fun open(owner: Window) {
        parentDialog = JDialog(owner, "Timeline Configuration", Dialog.ModalityType.APPLICATION_MODAL).apply {
            contentPane = this@NewTimelineForm
            pack()
            setLocationRelativeTo(owner)
            isResizable = false
            defaultCloseOperation = JDialog.DISPOSE_ON_CLOSE
            isVisible = true
        }
    }

    /**
     * Parse runway list safely
     */
    private fun parseRunwayList(input: String): List<String> =
        input.split(',')
            .map { it.trim().uppercase() }
            .filter { it.isNotEmpty() }

    /**
     * Validation + callback + dialog closing
     */
    private fun handleSubmit() {
        val titleText = titleInput.text.trim()
        val rightText = rightRunwaysInput.text.trim()

        if (titleText.isEmpty()) {
            error("Title is required.")
            return
        }
        if (rightText.isEmpty()) {
            error("Right side runways are required.")
            return
        }

        presenterInterface.onCreateNewTimeline(
            CreateOrUpdateTimelineDto(
                airportIcao = airportIcao,
                title = titleText,
                left = CreateOrUpdateTimelineDto.TimeLineSide(
                    targetRunways = parseRunwayList(leftRunwaysInput.text)
                ),
                right = CreateOrUpdateTimelineDto.TimeLineSide(
                    targetRunways = parseRunwayList(rightRunwaysInput.text)
                ),
                depLabelLayout = depLayoutCombo.selectedItem as? String ?: "",
                arrLabelLayout = arrLayoutCombo.selectedItem as? String ?: ""
            )
        )

        parentDialog?.dispose()
    }

    private fun error(msg: String) {
        JOptionPane.showMessageDialog(this, msg, "Validation Error", JOptionPane.ERROR_MESSAGE)
    }
}