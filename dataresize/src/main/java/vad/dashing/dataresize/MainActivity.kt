package vad.dashing.dataresize

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
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
                    onInventory = viewModel::inventory,
                    onCopyInventorySummary = { copyInventorySummary(state.inventorySummary) },
                )
            }
        }
    }

    private fun copyInventorySummary(summary: String?) {
        val text = summary?.trim().orEmpty()
        if (text.isEmpty()) {
            Toast.makeText(this, "Сводка ещё не собрана", Toast.LENGTH_SHORT).show()
            return
        }
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("partition-inventory", text))
        Toast.makeText(this, "Сводка скопирована", Toast.LENGTH_SHORT).show()
    }
}
