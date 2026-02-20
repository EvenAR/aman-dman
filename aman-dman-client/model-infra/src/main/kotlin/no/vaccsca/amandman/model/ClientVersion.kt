package no.vaccsca.amandman.model

import java.util.Properties

internal object ClientVersion {
    val value: String by lazy {
        runCatching {
            ClientVersion::class.java.classLoader.getResourceAsStream("build-info.properties")?.use { input ->
                Properties().apply { load(input) }.getProperty("app.version")
            }
        }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: "unknown"
    }
}
