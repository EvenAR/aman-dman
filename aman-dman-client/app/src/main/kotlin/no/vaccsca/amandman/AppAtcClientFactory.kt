package no.vaccsca.amandman

import no.vaccsca.amandman.model.atc.AtcClient
import no.vaccsca.amandman.model.atc.AtcClientEuroScope
import no.vaccsca.amandman.model.atc.AtcClientFactory
import no.vaccsca.amandman.model.atc.ControllerInfoData
import no.vaccsca.amandman.model.config.SettingsProvider

class AppAtcClientFactory(
    private val settingsProvider: SettingsProvider
) : AtcClientFactory {
    override fun create(
        controllerInfoCallback: (ControllerInfoData) -> Unit,
        onVersionMismatch: ((clientVersion: String, pluginVersion: String) -> Unit)?,
        onAircraftSelectionChanged: ((String) -> Unit)?
    ): AtcClient {
        return AtcClientEuroScope(
            controllerInfoCallback = controllerInfoCallback,
            onVersionMismatch = onVersionMismatch,
            onAircraftSelectionChanged = onAircraftSelectionChanged,
            settingsProvider = settingsProvider
        )
    }
}
