package vad.dashing.tbox.externalapi

import android.os.Bundle
import vad.dashing.tbox.dcl.DclLauncherApiContract
import vad.dashing.tbox.localaccess.LocalAccessContract

/** Companion apps allowed to ask for access through the local provider. */
object ExternalApiLocalClients {
    private val names = mapOf(
        "vad.dashing.mqtt" to "TBox MQTT",
        "vad.dashing.voice" to "VAD Voice",
        DclLauncherApiContract.LAUNCHER_PACKAGE to "Dashing Club Launcher",
    )

    fun nameOf(packageName: String): String? = names[packageName]

    fun clientId(packageName: String): String = "local:$packageName"

    /**
     * DCL local-access Allow should also turn on «Работа в среде лаунчера DCL».
     * MQTT / Voice approve must not flip that preference.
     */
    fun shouldEnableDclEnvironmentOnApprove(packageName: String): Boolean =
        packageName == DclLauncherApiContract.LAUNCHER_PACKAGE
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
