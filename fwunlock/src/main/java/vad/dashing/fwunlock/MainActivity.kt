package vad.dashing.fwunlock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.fwunlock.ui.FwUnlockScreen
import vad.dashing.fwunlock.ui.theme.FwUnlockTheme

class MainActivity : ComponentActivity() {
    private val viewModel: FwUnlockViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            FwUnlockTheme(dark = isSystemInDarkTheme()) {
                FwUnlockScreen(
                    state = state,
                    onRefresh = viewModel::refresh,
                    onApply = viewModel::apply,
                    onRestore = viewModel::restore,
                )
            }
        }
    }
}
