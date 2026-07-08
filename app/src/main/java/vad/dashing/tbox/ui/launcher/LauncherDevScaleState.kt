package vad.dashing.tbox.ui.launcher

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue

/** Default launcher UI scale tuned for Jetour Dashing head unit. */
object LauncherDevScaleState {
    var scale by mutableFloatStateOf(1.3f)

    fun increase() {
        scale = (scale + 0.05f).coerceAtMost(1.4f)
    }

    fun decrease() {
        scale = (scale - 0.05f).coerceAtLeast(0.7f)
    }

    fun reset() {
        scale = 1.3f
    }
}
