package vad.dashing.tbox.ui.launcher

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/**
 * Scales launcher layout via [Density] so dp/sp reflow and fill the screen,
 * instead of zooming the whole tree like a bitmap ([graphicsLayer] scale).
 */
@Composable
fun LauncherDevScaleProvider(content: @Composable () -> Unit) {
    val base = LocalDensity.current
    val scale = LauncherDevScaleState.scale
    CompositionLocalProvider(
        LocalDensity provides Density(
            density = base.density * scale,
            fontScale = base.fontScale * scale,
        ),
    ) {
        content()
    }
}
