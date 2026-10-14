package vad.dashing.tbox

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import vad.dashing.tbox.adb.WriteSecureSettingsAutoGrant

/**
 * Adds / removes [OverlayAccessibilityService] in `Settings.Secure.enabled_accessibility_services`
 * without touching other services. Requires WRITE_SECURE_SETTINGS (auto-granted via localhost ADB).
 */
object OverlayAccessibilityEnabler {
    private const val TAG = "OverlayA11y"

    fun componentFor(context: Context): String =
        ComponentName(context, OverlayAccessibilityService::class.java).flattenToString()

    suspend fun apply(context: Context, enabled: Boolean): Boolean {
        val appContext = context.applicationContext
        val component = componentFor(appContext)
        val resolver = appContext.contentResolver
        val current = Settings.Secure.getString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        val updated = if (enabled) {
            EnabledAccessibilityServices.with(current, component)
        } else {
            EnabledAccessibilityServices.without(current, component)
        }
        if (updated == EnabledAccessibilityServices.normalize(current) &&
            (!enabled || isAccessibilityOn(appContext))
        ) {
            return true
        }
        if (!AppPermissions.hasWriteSecureSettings(appContext)) {
            if (!enabled) return false
            val grant = WriteSecureSettingsAutoGrant.grant(appContext)
            if (grant is WriteSecureSettingsAutoGrant.Outcome.Failed) {
                TboxRepository.addLog("ERROR", TAG, "WRITE_SECURE_SETTINGS unavailable: ${grant.reason}")
                return false
            }
        }
        return try {
            Settings.Secure.putString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, updated)
            Settings.Secure.putInt(
                resolver,
                Settings.Secure.ACCESSIBILITY_ENABLED,
                if (updated.isEmpty()) 0 else 1,
            )
            TboxRepository.addLog("INFO", TAG, "enabled=$enabled services=$updated")
            true
        } catch (e: SecurityException) {
            TboxRepository.addLog("ERROR", TAG, "write failed: ${e.message}")
            false
        }
    }

    private fun isAccessibilityOn(context: Context): Boolean =
        Settings.Secure.getInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1
}

/** Colon-separated `enabled_accessibility_services` editing (entries may use `pkg/.Cls` short form). */
internal object EnabledAccessibilityServices {
    fun normalize(raw: String?): String = entries(raw).joinToString(":")

    fun with(raw: String?, component: String): String {
        val list = entries(raw)
        return if (list.any { sameComponent(it, component) }) {
            list.joinToString(":")
        } else {
            (list + component).joinToString(":")
        }
    }

    fun without(raw: String?, component: String): String =
        entries(raw).filterNot { sameComponent(it, component) }.joinToString(":")

    private fun entries(raw: String?): List<String> =
        raw.orEmpty().split(':').map { it.trim() }.filter { it.isNotEmpty() && it != "null" }

    private fun sameComponent(a: String, b: String): Boolean = expand(a) == expand(b)

    private fun expand(entry: String): String {
        val slash = entry.indexOf('/')
        if (slash <= 0) return entry
        val pkg = entry.substring(0, slash)
        val cls = entry.substring(slash + 1)
        return if (cls.startsWith(".")) "$pkg/$pkg$cls" else entry
    }
}
