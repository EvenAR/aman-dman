package no.vaccsca.amandman.view.visualizations

import no.vaccsca.amandman.model.timeline.event.NonSequencedEvent
import no.vaccsca.amandman.view.entity.AirportViewState
import java.awt.BorderLayout
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTable
import javax.swing.SwingUtilities
import javax.swing.border.TitledBorder
import javax.swing.table.DefaultTableModel

class NonSeqView(
    airportViewState: AirportViewState,
) : JPanel() {
    private val columnNames = arrayOf("Call Sign", "A/C", "WVC", "Reason")
    private val tableModel = object : DefaultTableModel(columnNames, 0) {
        override fun isCellEditable(row: Int, column: Int): Boolean = false
    }
    private val table = JTable(tableModel)

    // Keep last known data for any future diffing needs (optional but useful)
    private val currentData = mutableListOf<NonSequencedEvent>()

    init {
        layout = BorderLayout()

        // Table setup
        table.autoCreateRowSorter = true
        table.tableHeader.reorderingAllowed = true
        table.tableHeader.resizingAllowed = true

        val scrollPane = JScrollPane(table)

        val groupPanel = JPanel(BorderLayout()).apply {
            border = TitledBorder("Arrivals")
            add(scrollPane, BorderLayout.CENTER)
        }

        add(groupPanel, BorderLayout.CENTER)

        // Set all columns except last to preferred width 75
        for (i in 0 until columnNames.size - 1) {
            val column = table.columnModel.getColumn(i)
            column.preferredWidth = 80
            column.maxWidth = 80
        }

        airportViewState.nonSequencedList.addListener {
            updateNonSeqData(it)
        }
    }

    // Helper to ensure EDT usage
    private fun runOnUiThread(block: () -> Unit) {
        if (SwingUtilities.isEventDispatchThread()) block() else SwingUtilities.invokeLater(block)
    }

    // Helper to find a row by callsign (column 0)
    private fun findRowByCallsign(callsign: String): Int {
        for (r in 0 until tableModel.rowCount) {
            val value = tableModel.getValueAt(r, 0)
            if (value is String && value == callsign) return r
        }
        return -1
    }

    private fun updateNonSeqData(data: List<NonSequencedEvent>) {
        runOnUiThread {
            // Process items in the new order, inserting/moving/updating as needed
            for (i in data.indices) {
                val item = data[i]
                val existingRow = findRowByCallsign(item.callsign)

                if (existingRow == -1) {
                    // Insert new row at the desired position
                    tableModel.insertRow(i, arrayOf(
                        item.callsign,
                        item.aircraftType,
                        item.wakeCategory ?: '?',
                        item.reason.name
                    ))
                } else {
                    // Move row if index differs
                    if (existingRow != i) {
                        tableModel.moveRow(existingRow, existingRow, i)
                    }
                    // Update cells that changed
                    if (tableModel.getValueAt(i, 1) != item.aircraftType) {
                        tableModel.setValueAt(item.aircraftType, i, 1)
                    }
                    if (tableModel.getValueAt(i, 2) != item.wakeCategory) {
                        tableModel.setValueAt(item.wakeCategory, i, 2)
                    }
                    if (tableModel.getValueAt(i, 3) != item.reason.name) {
                        tableModel.setValueAt(item.reason.name, i, 3)
                    }
                }
            }

            // Remove any remaining rows that are no longer present
            for (r in tableModel.rowCount - 1 downTo data.size) {
                tableModel.removeRow(r)
            }

            // Update local cache
            currentData.clear()
            currentData.addAll(data)
        }
    }
}
