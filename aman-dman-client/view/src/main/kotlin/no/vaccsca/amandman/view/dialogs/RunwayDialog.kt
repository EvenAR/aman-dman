package no.vaccsca.amandman.view.dialogs

import no.vaccsca.amandman.common.domain.valueobjects.timelineEvent.RunwayEvent
import java.awt.Frame
import javax.swing.JComboBox
import javax.swing.JOptionPane
import javax.swing.JPanel

object RunwayDialog {
    fun open(
        parent: Frame,
        runwayEvent: RunwayEvent,
        runwayOptions: Set<String>,
        onSubmit: (String) -> Unit
    ) {
        val comboBox = JComboBox(runwayOptions.toTypedArray()).apply {
            selectedItem = runwayEvent.runway
        }

        val panel = JPanel().apply {
            add(comboBox)
        }

        val result = JOptionPane.showConfirmDialog(
            parent,
            panel,
            "Select Runway",
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE
        )

        if (result == JOptionPane.OK_OPTION) {
            val selected = comboBox.selectedItem as String
            onSubmit(selected)
        }
    }
}