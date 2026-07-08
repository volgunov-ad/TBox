package vad.dashing.tbox.ui.launcher

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** OEM WT_Launcher3 / Tesla reference: left column ~33%. */
private const val SIDEBAR_WIDTH_FRACTION = 0.33f
private val SIDEBAR_MIN_WIDTH = 200.dp
private val SIDEBAR_MAX_WIDTH = 340.dp

@Composable
fun rememberLauncherSidebarWidth(): Dp {
    val screenWidthDp = LocalConfiguration.current.screenWidthDp.dp
    return remember(screenWidthDp) {
        (screenWidthDp * SIDEBAR_WIDTH_FRACTION).coerceIn(SIDEBAR_MIN_WIDTH, SIDEBAR_MAX_WIDTH)
    }
}

@Composable
fun rememberLauncherSafePadding(): PaddingValues =
    WindowInsets.safeDrawing.asPaddingValues()
