package vad.dashing.winpatch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.winpatch.ui.WinPatchScreen
import vad.dashing.winpatch.ui.theme.WinPatchTheme

class MainActivity : ComponentActivity() {
    private val viewModel: WinPatchViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            WinPatchTheme(dark = isSystemInDarkTheme()) {
                WinPatchScreen(
                    state = state,
                    onRefresh = viewModel::refresh,
                    onApply = viewModel::apply,
                    onRestore = viewModel::restore,
                    onReboot = viewModel::reboot,
                )
            }
        }
    }
}
