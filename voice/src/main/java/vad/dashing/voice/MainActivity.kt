package vad.dashing.voice

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import vad.dashing.voice.ui.VoiceHomeScreen
import vad.dashing.voice.ui.VoiceHomeViewModel
import vad.dashing.voice.ui.VoiceHomeViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        maybeHandleListenExtra()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val vm: VoiceHomeViewModel = viewModel(
                        factory = VoiceHomeViewModelFactory(application),
                    )
                    VoiceHomeScreen(viewModel = vm)
                }
            }
        }
    }

    private fun maybeHandleListenExtra() {
        if (intent?.action == VoiceIntents.ACTION_LISTEN) {
            val source = intent.getStringExtra(VoiceIntents.EXTRA_SOURCE) ?: VoiceIntents.SOURCE_UI
            Toast.makeText(
                this,
                getString(R.string.listen_stub, source),
                Toast.LENGTH_LONG,
            ).show()
        }
    }
}
