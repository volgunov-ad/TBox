package vad.dashing.tbox.externalapi

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import vad.dashing.tbox.localaccess.LocalAccessContract
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory pending local-access consent (MQTT / Voice / DCL).
 * Survives accidental dialog dismiss; only [takeForApprove] / [takeForDeny] clear it.
 */
class ExternalApiLocalConsentSession {
    private val _pending = MutableStateFlow<ExternalApiLocalConsent?>(null)
    val pending: StateFlow<ExternalApiLocalConsent?> = _pending.asStateFlow()

    private val deniedPackages: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** Interactive ask: clear a prior deny for this package, keep/replace pending, return pending. */
    fun requestInteractive(consent: ExternalApiLocalConsent): String {
        deniedPackages.remove(consent.packageName)
        _pending.value = consent
        return LocalAccessContract.STATUS_PENDING
    }

    fun pollStatus(consent: ExternalApiLocalConsent): String = when {
        _pending.value == consent -> LocalAccessContract.STATUS_PENDING
        consent.packageName in deniedPackages -> LocalAccessContract.STATUS_DENIED
        else -> LocalAccessContract.STATUS_CONSENT_REQUIRED
    }

    fun takeForApprove(): ExternalApiLocalConsent? {
        val consent = _pending.value ?: return null
        _pending.value = null
        deniedPackages.remove(consent.packageName)
        return consent
    }

    fun takeForDeny(): ExternalApiLocalConsent? {
        val consent = _pending.value ?: return null
        _pending.value = null
        deniedPackages.add(consent.packageName)
        return consent
    }

    /**
     * Accidental UI dismiss must not clear pending. Explicit no-op so call sites stay honest
     * if something still invokes dismiss after the dialog was made non-cancelable.
     */
    @Suppress("Unused")
    fun dismissWithoutAnswer() {
        // Intentionally empty: pending stays until Allow / Deny.
    }
}
