package vad.dashing.usbaudio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.usbaudio.ui.UsbAudioScreen
import vad.dashing.usbaudio.ui.theme.UsbAudioTheme

class MainActivity : ComponentActivity() {
    private val viewModel: UsbAudioViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            UsbAudioTheme(dark = isSystemInDarkTheme()) {
                UsbAudioScreen(
                    state = state,
                    onRefresh = viewModel::refresh,
                    onApply = viewModel::apply,
                    onRestore = viewModel::restore,
                )
            }
        }
    }
}
