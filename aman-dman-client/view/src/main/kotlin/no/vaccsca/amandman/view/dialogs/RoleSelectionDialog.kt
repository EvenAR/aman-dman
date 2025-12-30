package no.vaccsca.amandman.view.dialogs

import no.vaccsca.amandman.common.domain.UserRole
import java.awt.Frame
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel

object RoleSelectionDialog {
    fun open(parent: Frame, availableAirportIcaos: Set<String>, onSubmit: (icao: String, role: UserRole) -> Unit) {
        // Get available ICAOs and sort them alphabetically
        val icaoComboBox = JComboBox(availableAirportIcaos.toTypedArray())

        val roles = UserRole.entries.toTypedArray()
        val roleComboBox = JComboBox(roles)

        val panel = JPanel(GridBagLayout()).apply {
            val gbc = GridBagConstraints().apply {
                fill = GridBagConstraints.HORIZONTAL
                insets = Insets(5, 5, 5, 5)
                anchor = GridBagConstraints.WEST
            }

            // --- ICAO ComboBox ---
            gbc.gridx = 0
            gbc.gridy = 0
            add(JLabel("Airport"), gbc)

            gbc.gridx = 1
            gbc.gridy = 0
            add(icaoComboBox, gbc)

            // --- Role ComboBox ---
            gbc.gridx = 0
            gbc.gridy = 1
            add(JLabel("User Role"), gbc)

            gbc.gridx = 1
            gbc.gridy = 1
            add(roleComboBox, gbc)
        }

        val result = JOptionPane.showConfirmDialog(
            parent,
            panel,
            "New Timeline Group",
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE
        )

        if (result == JOptionPane.OK_OPTION) {
            val icao = icaoComboBox.selectedItem as? String
            val role = roleComboBox.selectedItem as? UserRole
            if (!icao.isNullOrBlank() && role != null) {
                onSubmit(icao, role)
            }
        }
    }
}