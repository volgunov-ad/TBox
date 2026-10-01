package vad.dashing.voice

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity

/**
 * Exported entry for Monitor automations / hard-key forward.
 * For now shows a stub toast and opens [MainActivity]; STT session comes later.
 */
class ListenActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val source = intent?.getStringExtra(VoiceIntents.EXTRA_SOURCE)
            ?: VoiceIntents.SOURCE_AUTOMATION
        Toast.makeText(
            this,
            getString(R.string.listen_stub, source),
            Toast.LENGTH_LONG,
        ).show()
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                action = VoiceIntents.ACTION_LISTEN
                putExtra(VoiceIntents.EXTRA_SOURCE, source)
            },
        )
        finish()
    }
}
