package no.vaccsca.amandman.presenter

interface UiDispatcher {
    fun isUiThread(): Boolean
    fun dispatch(action: () -> Unit)
    fun scheduleRepeating(intervalMs: Int, action: () -> Unit): RepeatingUiTask
}

fun interface RepeatingUiTask {
    fun cancel()
}
