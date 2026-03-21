package vad.dashing.tbox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt
import vad.dashing.tbox.MainScreenSettingsButtonPosition
import vad.dashing.tbox.R
import vad.dashing.tbox.SettingsViewModel

private val MainScreenSettingsIconSize = 32.dp

/**
 * Root home window. Gear opens [vad.dashing.tbox.ui.TboxScreen]; long-press and drag to move the button.
 */
@Composable
fun MainScreen(
    settingsViewModel: SettingsViewModel,
    onOpenConsole: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val saved by settingsViewModel.mainScreenSettingsButtonPosition.collectAsStateWithLifecycle()
    val savedState by rememberUpdatedState(saved)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val density = LocalDensity.current
        val btnPx = with(density) { MainScreenSettingsIconSize.toPx() }
        val maxW = constraints.maxWidth.toFloat()
        val maxH = constraints.maxHeight.toFloat()

        var offsetPx by remember { mutableStateOf(Offset.Zero) }

        // Sync from storage / layout; does not run during drag because [saved] is unchanged until persist completes.
        LaunchedEffect(saved, maxW, maxH) {
            if (maxW <= 0f || maxH <= 0f) return@LaunchedEffect
            val rangeW = (maxW - btnPx).coerceAtLeast(0f)
            val rangeH = (maxH - btnPx).coerceAtLeast(0f)
            offsetPx = Offset(
                x = (saved.x * rangeW).coerceIn(0f, rangeW),
                y = (saved.y * rangeH).coerceIn(0f, rangeH)
            )
        }

        Icon(
            imageVector = ImageVector.vectorResource(R.drawable.ic_main_open_console),
            contentDescription = stringResource(R.string.main_open_console_cd),
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .offset {
                    IntOffset(offsetPx.x.roundToInt(), offsetPx.y.roundToInt())
                }
                .size(MainScreenSettingsIconSize)
                .clickable(onClick = onOpenConsole)
                .pointerInput(maxW, maxH, btnPx) {
                    detectDragGesturesAfterLongPress(
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val rangeW = (maxW - btnPx).coerceAtLeast(0f)
                            val rangeH = (maxH - btnPx).coerceAtLeast(0f)
                            offsetPx = Offset(
                                x = (offsetPx.x + dragAmount.x).coerceIn(0f, rangeW),
                                y = (offsetPx.y + dragAmount.y).coerceIn(0f, rangeH)
                            )
                        },
                        onDragEnd = {
                            val rangeW = (maxW - btnPx).coerceAtLeast(1f)
                            val rangeH = (maxH - btnPx).coerceAtLeast(1f)
                            settingsViewModel.saveMainScreenSettingsButton(
                                MainScreenSettingsButtonPosition(
                                    x = (offsetPx.x / rangeW).coerceIn(0f, 1f),
                                    y = (offsetPx.y / rangeH).coerceIn(0f, 1f)
                                )
                            )
                        },
                        onDragCancel = {
                            val s = savedState
                            val rangeW = (maxW - btnPx).coerceAtLeast(0f)
                            val rangeH = (maxH - btnPx).coerceAtLeast(0f)
                            offsetPx = Offset(
                                x = (s.x * rangeW).coerceIn(0f, rangeW),
                                y = (s.y * rangeH).coerceIn(0f, rangeH)
                            )
                        }
                    )
                }
        )
    }
}
