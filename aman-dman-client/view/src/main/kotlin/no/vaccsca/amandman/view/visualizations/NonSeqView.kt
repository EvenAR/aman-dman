package no.vaccsca.amandman.view.visualizations

import no.vaccsca.amandman.common.domain.valueobjects.SequenceStatus
import no.vaccsca.amandman.common.domain.valueobjects.timelineEvent.RunwayArrivalEvent
import no.vaccsca.amandman.common.domain.valueobjects.timelineEvent.TimelineEvent
import java.awt.BorderLayout
import javax.swing.*
import javax.swing.border.TitledBorder
import javax.swing.table.DefaultTableModel

class NonSeqView : JPanel() {
    private val columnNames = arrayOf("No.", "Call Sign", "A/C", "WVC")
    private val tableModel = object : DefaultTableModel(columnNames, 0) {
        override fun isCellEditable(row: Int, column: Int): Boolean = false
    }
    private val table = JTable(tableModel)

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
    }

    fun updateNonSeqData(data: List<TimelineEvent>) {
        val nonSequenced = data
            .filterIsInstance<RunwayArrivalEvent>()
            .filter { it.sequenceStatus == SequenceStatus.FOR_MANUAL_REINSERTION }

        tableModel.setRowCount(0) // Efficiently clear existing rows

        nonSequenced.forEach {
            tableModel.addRow(arrayOf(
                0,
                it.callsign,
                it.icaoType,
                it.wakeCategory
            ))
        }
    }
}
