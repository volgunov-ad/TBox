package vad.dashing.dataresize

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.dataresize.ui.ResizeScreen
import vad.dashing.dataresize.ui.theme.DataResizeTheme

class MainActivity : ComponentActivity() {
    private val viewModel: ResizeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            DataResizeTheme(dark = isSystemInDarkTheme()) {
                ResizeScreen(
                    state = state,
                    onRefresh = viewModel::refresh,
                    onMove = viewModel::move,
                    onStage2 = viewModel::stage2,
                    onRollback = viewModel::rollback,
                    onFixSecondary = viewModel::fixSecondary,
                )
            }
        }
    }
}
