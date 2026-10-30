package vad.dashing.tbox.ui

import android.content.Context
import android.hardware.display.DisplayManager
import android.util.DisplayMetrics
import android.view.Display
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.FLOATING_PANEL_BEYOND_SCREEN_MIN_ORIGIN_PX
import vad.dashing.tbox.MIN_FLOATING_PANEL_SIZE_PX
import vad.dashing.tbox.MIN_MAIN_SCREEN_PANEL_REL_PERCENT
import vad.dashing.tbox.R
import vad.dashing.tbox.SettingsViewModel
import vad.dashing.tbox.ui.theme.tboxCaption
import kotlin.math.hypot

private const val FLOATING_PANEL_MAX_SIZE_BEYOND_SCREEN_PX = 100_000

/** «Размер и место»: panel frame on the screen, applied on Save together with the rest of the panel. */
@Composable
internal fun PanelPlacementTab(
    state: WidgetSelectionDialogState,
    surface: WidgetEditorSurface,
    settingsViewModel: SettingsViewModel,
) {
    val placement = state.wholePanelPlacement ?: return
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val allowBeyondScreen by settingsViewModel.floatingPanelsAllowBeyondScreen.collectAsStateWithLifecycle()
    val floatingScreenPx = remember(context) { defaultDisplaySizePx(context) }
    val units: PanelPlacementUnits = if (surface == WidgetEditorSurface.FLOATING_PANEL) {
        PanelPlacementUnits.Pixels(floatingScreenPx.first, floatingScreenPx.second, allowBeyondScreen)
    } else {
        PanelPlacementUnits.MainScreenPercent
    }
    val screenAspect = when (units) {
        is PanelPlacementUnits.Pixels -> units.screenWidth.toFloat() / units.screenHeight.coerceAtLeast(1)
        PanelPlacementUnits.MainScreenPercent ->
            configuration.screenWidthDp.toFloat() / configuration.screenHeightDp.coerceAtLeast(1)
    }.coerceIn(0.5f, 4f)
    val update: (PanelPlacement) -> Unit = { state.wholePanelPlacement = it }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        EditorCard(
            title = stringResource(R.string.widget_editor_card_placement),
            description = stringResource(R.string.widget_editor_placement_hint),
        ) {
            PlacementDiagram(
                placement = placement,
                units = units,
                screenAspect = screenAspect,
                rows = state.wholePanelRows,
                cols = state.wholePanelCols,
                onPlacementChange = update,
            )
            if (units is PanelPlacementUnits.Pixels) {
                Text(
                    text = stringResource(R.string.widget_editor_placement_screen_px, units.screenWidth, units.screenHeight),
                    style = MaterialTheme.typography.tboxCaption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                EditorToggleButton(
                    text = stringResource(R.string.widget_editor_center_horizontal),
                    selected = false,
                    onClick = { update(centerPanelPlacement(placement, units, horizontal = true)) },
                    compact = true,
                )
                EditorToggleButton(
                    text = stringResource(R.string.widget_editor_center_vertical),
                    selected = false,
                    onClick = { update(centerPanelPlacement(placement, units, horizontal = false)) },
                    compact = true,
                )
            }
        }
        EditorCard(title = stringResource(R.string.widget_editor_card_placement_values)) {
            when (units) {
                PanelPlacementUnits.MainScreenPercent -> MainScreenPlacementSteppers(placement, update)
                is PanelPlacementUnits.Pixels -> FloatingPlacementSteppers(placement, units, update)
            }
        }
    }
}

@Composable
private fun MainScreenPlacementSteppers(placement: PanelPlacement, onChange: (PanelPlacement) -> Unit) {
    val presets = listOf(25, 33, 50, 100).map { it to "$it" }
    EditorIntStepper(
        label = stringResource(R.string.main_screen_panel_rel_width_pct),
        value = placement.width,
        onValueChange = { onChange(placement.copy(width = it)) },
        minValue = MIN_MAIN_SCREEN_PANEL_REL_PERCENT,
        maxValue = 100,
        suffix = " %",
        presets = presets,
    )
    EditorIntStepper(
        label = stringResource(R.string.main_screen_panel_rel_height_pct),
        value = placement.height,
        onValueChange = { onChange(placement.copy(height = it)) },
        minValue = MIN_MAIN_SCREEN_PANEL_REL_PERCENT,
        maxValue = 100,
        suffix = " %",
        presets = presets,
    )
    EditorIntStepper(
        label = stringResource(R.string.main_screen_panel_rel_x_pct),
        value = placement.x,
        onValueChange = { onChange(placement.copy(x = it)) },
        minValue = 0,
        maxValue = 100,
        suffix = " %",
        presets = listOf(0, 50, 100).map { it to "$it" },
    )
    EditorIntStepper(
        label = stringResource(R.string.main_screen_panel_rel_y_pct),
        value = placement.y,
        onValueChange = { onChange(placement.copy(y = it)) },
        minValue = 0,
        maxValue = 100,
        suffix = " %",
        presets = listOf(0, 50, 100).map { it to "$it" },
    )
}

@Composable
private fun FloatingPlacementSteppers(
    placement: PanelPlacement,
    units: PanelPlacementUnits.Pixels,
    onChange: (PanelPlacement) -> Unit,
) {
    val beyond = units.allowBeyondScreen
    val originMin = if (beyond) FLOATING_PANEL_BEYOND_SCREEN_MIN_ORIGIN_PX else 0
    val maxWidth = if (beyond) FLOATING_PANEL_MAX_SIZE_BEYOND_SCREEN_PX else units.screenWidth
    val maxHeight = if (beyond) FLOATING_PANEL_MAX_SIZE_BEYOND_SCREEN_PX else units.screenHeight
    val maxX = if (beyond) FLOATING_PANEL_MAX_SIZE_BEYOND_SCREEN_PX else units.screenWidth - MIN_FLOATING_PANEL_SIZE_PX
    val maxY = if (beyond) FLOATING_PANEL_MAX_SIZE_BEYOND_SCREEN_PX else units.screenHeight - MIN_FLOATING_PANEL_SIZE_PX
    EditorIntStepper(
        label = stringResource(R.string.floating_panel_width_px),
        value = placement.width,
        onValueChange = { onChange(placement.copy(width = it)) },
        minValue = MIN_FLOATING_PANEL_SIZE_PX,
        maxValue = maxWidth.coerceAtLeast(MIN_FLOATING_PANEL_SIZE_PX),
        step = 10,
    )
    EditorIntStepper(
        label = stringResource(R.string.floating_panel_height_px),
        value = placement.height,
        onValueChange = { onChange(placement.copy(height = it)) },
        minValue = MIN_FLOATING_PANEL_SIZE_PX,
        maxValue = maxHeight.coerceAtLeast(MIN_FLOATING_PANEL_SIZE_PX),
        step = 10,
    )
    EditorIntStepper(
        label = stringResource(R.string.floating_panel_pos_x_px),
        value = placement.x,
        onValueChange = { onChange(placement.copy(x = it)) },
        minValue = originMin,
        maxValue = maxX.coerceAtLeast(originMin),
        step = 10,
    )
    EditorIntStepper(
        label = stringResource(R.string.floating_panel_pos_y_px),
        value = placement.y,
        onValueChange = { onChange(placement.copy(y = it)) },
        minValue = originMin,
        maxValue = maxY.coerceAtLeast(originMin),
        step = 10,
    )
}

private enum class PlacementDragMode { MOVE, RESIZE }

/** Screen schematic: drag the panel to move it, drag the bottom-right handle to resize. */
@Composable
private fun PlacementDiagram(
    placement: PanelPlacement,
    units: PanelPlacementUnits,
    screenAspect: Float,
    rows: Int,
    cols: Int,
    onPlacementChange: (PanelPlacement) -> Unit,
) {
    val screenColor = MaterialTheme.colorScheme.surfaceVariant
    val outlineColor = MaterialTheme.colorScheme.outline
    val panelColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.onPrimary
    val currentPlacement by rememberUpdatedState(placement)
    val currentOnChange by rememberUpdatedState(onPlacementChange)
    var dragMode by remember { mutableStateOf<PlacementDragMode?>(null) }
    val rect = panelPlacementToScreenRect(placement, units)
    Canvas(
        modifier = Modifier
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .aspectRatio(screenAspect)
            .padding(vertical = 8.dp)
            .clipToBounds()
            .pointerInput(units) {
                var startRect = rect
                var totalX = 0f
                var totalY = 0f
                detectDragGestures(
                    onDragStart = { offset ->
                        startRect = panelPlacementToScreenRect(currentPlacement, units)
                        totalX = 0f
                        totalY = 0f
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        val right = (startRect.left + startRect.width) * w
                        val bottom = (startRect.top + startRect.height) * h
                        dragMode = when {
                            hypot(offset.x - right, offset.y - bottom) <= 36.dp.toPx() -> PlacementDragMode.RESIZE
                            offset.x in startRect.left * w..right && offset.y in startRect.top * h..bottom ->
                                PlacementDragMode.MOVE
                            else -> null
                        }
                    },
                    onDragEnd = { dragMode = null },
                    onDragCancel = { dragMode = null },
                ) { change, amount ->
                    val mode = dragMode ?: return@detectDragGestures
                    change.consume()
                    totalX += amount.x / size.width
                    totalY += amount.y / size.height
                    val next = when (mode) {
                        PlacementDragMode.MOVE -> movePanelScreenRect(startRect, totalX, totalY, units)
                        PlacementDragMode.RESIZE -> resizePanelScreenRect(startRect, totalX, totalY, units)
                    }
                    val nextPlacement = panelPlacementFromScreenRect(next, units)
                    if (nextPlacement != currentPlacement) currentOnChange(nextPlacement)
                }
            },
    ) {
        drawRect(color = screenColor)
        drawRect(color = outlineColor, style = Stroke(width = 1.dp.toPx()))
        val left = rect.left * size.width
        val top = rect.top * size.height
        val panelW = rect.width * size.width
        val panelH = rect.height * size.height
        val corner = CornerRadius(6.dp.toPx())
        drawRoundRect(
            color = panelColor,
            topLeft = Offset(left, top),
            size = Size(panelW, panelH),
            cornerRadius = corner,
            alpha = if (dragMode == PlacementDragMode.MOVE) 0.55f else 0.35f,
        )
        val safeCols = cols.coerceAtLeast(1)
        val safeRows = rows.coerceAtLeast(1)
        for (c in 1 until safeCols) {
            val x = left + panelW * c / safeCols
            drawLine(gridColor, Offset(x, top), Offset(x, top + panelH), strokeWidth = 1.dp.toPx(), alpha = 0.6f)
        }
        for (r in 1 until safeRows) {
            val y = top + panelH * r / safeRows
            drawLine(gridColor, Offset(left, y), Offset(left + panelW, y), strokeWidth = 1.dp.toPx(), alpha = 0.6f)
        }
        drawRoundRect(
            color = panelColor,
            topLeft = Offset(left, top),
            size = Size(panelW, panelH),
            cornerRadius = corner,
            style = Stroke(width = 2.dp.toPx()),
        )
        val handle = (if (dragMode == PlacementDragMode.RESIZE) 18.dp else 14.dp).toPx()
        drawRoundRect(
            color = panelColor,
            topLeft = Offset(left + panelW - handle / 2, top + panelH - handle / 2),
            size = Size(handle, handle),
            cornerRadius = CornerRadius(3.dp.toPx()),
        )
    }
}

/** Physical size of the default display: floating panel coordinates are px of that display. */
@Suppress("DEPRECATION")
private fun defaultDisplaySizePx(context: Context): Pair<Int, Int> {
    val display = context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)
    if (display == null) {
        val metrics = context.resources.displayMetrics
        return metrics.widthPixels to metrics.heightPixels
    }
    val metrics = DisplayMetrics()
    display.getRealMetrics(metrics)
    return metrics.widthPixels to metrics.heightPixels
}
