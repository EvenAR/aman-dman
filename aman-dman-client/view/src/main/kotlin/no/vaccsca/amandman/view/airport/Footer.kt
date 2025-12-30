package no.vaccsca.amandman.view.airport

import no.vaccsca.amandman.common.NtpClock
import no.vaccsca.amandman.presenter.PresenterInterface
import no.vaccsca.amandman.view.dialogs.RoleSelectionDialog
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Graphics
import javax.swing.*

class Footer(
    private val presenterInterface: PresenterInterface,
    private val mainWindow: JFrame
) : JPanel(FlowLayout(FlowLayout.RIGHT)) {
    private val timeLabel = JLabel("--:--:--")
    private val startButton = JButton("Start")

    init {
        add(startButton)
        add(JSeparator(SwingConstants.VERTICAL).apply {
            preferredSize = Dimension(2, 20)
        })
        add(timeLabel)

        // Every second, repaint the component
        Timer(1000) {
            repaint()
        }.start()

        startButton.addActionListener {
            presenterInterface.onStartButtonClicked()
        }
    }

    override fun paintComponent(g: Graphics?) {
        super.paintComponent(g)
        timeLabel.text = NtpClock.now().toString().substring(11, 19)
    }
}