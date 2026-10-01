package vad.dashing.voice

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity

/**
 * Exported entry for Monitor automations / hard-key forward.
 * Opens [MainActivity] and starts a listening session (PTT).
 */
class ListenActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val source = intent?.getStringExtra(VoiceIntents.EXTRA_SOURCE)
            ?: VoiceIntents.SOURCE_AUTOMATION
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                action = VoiceIntents.ACTION_LISTEN
                putExtra(VoiceIntents.EXTRA_SOURCE, source)
            },
        )
        finish()
    }
}
