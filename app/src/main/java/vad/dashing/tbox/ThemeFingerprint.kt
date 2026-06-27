package vad.dashing.tbox

import android.content.Context
import kotlinx.coroutines.flow.first
import java.security.MessageDigest

object ThemeFingerprint {

    suspend fun compute(context: Context, settingsManager: SettingsManager, sections: Set<ThemeSection>): String {
        if (sections.isEmpty()) return ""
        val json = ThemeLayoutExport.exportJson(context, settingsManager, sections)
        return sha256(json)
    }

    fun sha256(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(text.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
