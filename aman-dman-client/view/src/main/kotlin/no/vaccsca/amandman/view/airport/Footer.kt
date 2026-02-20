package no.vaccsca.amandman.view.airport

import no.vaccsca.amandman.common.NtpClock
import no.vaccsca.amandman.model.airport.AirportStatus
import no.vaccsca.amandman.view.entity.MainViewState
import java.awt.FlowLayout
import javax.swing.JLabel
import javax.swing.JPanel


class Footer(
    mainViewState: MainViewState,
) : JPanel(FlowLayout(FlowLayout.RIGHT)) {
    private val timeLabel = JLabel("--:--:--")
    private val statuses = mutableListOf<AirportStatus>()

    init {
        add(timeLabel)

        mainViewState.currentClock.addListener {
            timeLabel.text = NtpClock.now().toString().substring(11, 19)
        }
    }
}