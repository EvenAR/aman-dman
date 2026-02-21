package no.vaccsca.amandman

import no.vaccsca.amandman.presenter.RepeatingUiTask
import no.vaccsca.amandman.presenter.UiDispatcher
import javax.swing.SwingUtilities
import javax.swing.Timer

class SwingUiDispatcher : UiDispatcher {
    override fun isUiThread(): Boolean = SwingUtilities.isEventDispatchThread()

    override fun dispatch(action: () -> Unit) {
        SwingUtilities.invokeLater(action)
    }

    override fun scheduleRepeating(intervalMs: Int, action: () -> Unit): RepeatingUiTask {
        val timer = Timer(intervalMs) { action() }
        timer.start()
        return RepeatingUiTask { timer.stop() }
    }
}
