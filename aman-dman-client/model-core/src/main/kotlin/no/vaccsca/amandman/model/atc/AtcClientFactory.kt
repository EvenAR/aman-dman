package no.vaccsca.amandman.model.atc

interface AtcClientFactory {
    fun create(
        controllerInfoCallback: (ControllerInfoData) -> Unit,
        onVersionMismatch: ((clientVersion: String, pluginVersion: String) -> Unit)? = null,
        onAircraftSelectionChanged: ((String) -> Unit)? = null
    ): AtcClient
}
