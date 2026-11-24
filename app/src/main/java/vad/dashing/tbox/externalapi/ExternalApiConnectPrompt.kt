package vad.dashing.tbox.externalapi

/**
 * On-screen Allow/Deny prompt for External API connect flows.
 * Local companion apps (MQTT / Voice / DCL) take priority over HTTP pairing.
 */
sealed class ExternalApiConnectPrompt {
    data class Local(val consent: ExternalApiLocalConsent) : ExternalApiConnectPrompt()
    data class HttpPair(val request: ExternalApiPairRequest) : ExternalApiConnectPrompt()
}

/**
 * Pure policy: overlay (and Activity fallback) only while the API server toggle is on.
 */
object ExternalApiConnectPromptSelector {
    fun select(
        apiEnabled: Boolean,
        localConsent: ExternalApiLocalConsent?,
        pendingPairs: List<ExternalApiPairRequest>,
    ): ExternalApiConnectPrompt? {
        if (!apiEnabled) return null
        if (localConsent != null) return ExternalApiConnectPrompt.Local(localConsent)
        val oldest = pendingPairs.firstOrNull() ?: return null
        return ExternalApiConnectPrompt.HttpPair(oldest)
    }
}
