package vad.dashing.voice

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import vad.dashing.voice.ui.VoiceHomeScreen
import vad.dashing.voice.ui.VoiceHomeViewModel
import vad.dashing.voice.ui.VoiceHomeViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val listenSource = if (intent?.action == VoiceIntents.ACTION_LISTEN) {
            intent.getStringExtra(VoiceIntents.EXTRA_SOURCE) ?: VoiceIntents.SOURCE_UI
        } else {
            null
        }
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val vm: VoiceHomeViewModel = viewModel(
                        factory = VoiceHomeViewModelFactory(application),
                    )
                    var pendingListen by rememberSaveable { mutableStateOf(listenSource) }
                    VoiceHomeScreen(
                        viewModel = vm,
                        autoListenSource = pendingListen,
                        onAutoListenConsumed = { pendingListen = null },
                    )
                }
            }
        }
    }
}
