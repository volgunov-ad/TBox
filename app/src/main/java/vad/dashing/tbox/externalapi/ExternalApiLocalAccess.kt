package vad.dashing.tbox.externalapi

import android.os.Bundle
import vad.dashing.tbox.localaccess.LocalAccessContract

/** Companion apps allowed to ask for access through the local provider. */
object ExternalApiLocalClients {
    private val names = mapOf(
        "vad.dashing.mqtt" to "TBox MQTT",
        "vad.dashing.voice" to "VAD Voice",
    )

    fun nameOf(packageName: String): String? = names[packageName]

    fun clientId(packageName: String): String = "local:$packageName"
}

data class ExternalApiLocalConsent(
    val packageName: String,
    val clientName: String,
    val certSha256: String,
)

data class ExternalApiLocalAnswer(
    val status: String,
    val accessToken: String? = null,
    val port: Int? = null,
    val serverEnabled: Boolean? = null,
) {
    fun toBundle(): Bundle = Bundle().apply {
        putString(LocalAccessContract.KEY_STATUS, status)
        accessToken?.let { putString(LocalAccessContract.KEY_ACCESS_TOKEN, it) }
        port?.let { putInt(LocalAccessContract.KEY_PORT, it) }
        serverEnabled?.let { putBoolean(LocalAccessContract.KEY_SERVER_ENABLED, it) }
    }
}
