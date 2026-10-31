package vad.dashing.tbox.ui

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.FloatingDashboardWidgetConfig
import vad.dashing.tbox.MAX_PANEL_COLLAPSE_ON_TILE_TAP_DELAY_SEC
import vad.dashing.tbox.MAX_PANEL_COLLAPSE_STRIP_LENGTH_PERCENT
import vad.dashing.tbox.MAX_PANEL_COLLAPSE_STRIP_THICKNESS_DP
import vad.dashing.tbox.MAX_PANEL_COLLAPSE_TOUCH_ZONE_THICKNESS_DP
import vad.dashing.tbox.MAX_PANEL_GRID_SPACING_DP
import vad.dashing.tbox.MIN_PANEL_COLLAPSE_ON_TILE_TAP_DELAY_SEC
import vad.dashing.tbox.MIN_PANEL_COLLAPSE_STRIP_LENGTH_PERCENT
import vad.dashing.tbox.MIN_PANEL_COLLAPSE_STRIP_THICKNESS_DP
import vad.dashing.tbox.MIN_PANEL_GRID_SPACING_DP
import vad.dashing.tbox.PanelCollapseEdge
import vad.dashing.tbox.R
import vad.dashing.tbox.SettingsManager
import vad.dashing.tbox.SettingsViewModel
import vad.dashing.tbox.normalizePanelCollapseOnTileTapDelaySec
import vad.dashing.tbox.normalizePanelCollapseStripLengthPercent
import vad.dashing.tbox.normalizePanelCollapseStripPositionPercent
import vad.dashing.tbox.normalizePanelCollapseStripThicknessDp
import vad.dashing.tbox.normalizePanelCollapseTouchZoneThicknessDp
import vad.dashing.tbox.normalizePanelGridSpacingDp
import vad.dashing.tbox.ui.theme.tboxBody
import vad.dashing.tbox.ui.theme.tboxCaption
import vad.dashing.tbox.ui.theme.tboxTitle
import kotlin.math.abs

/** Where the editor was opened from; decides which panel options exist. */
internal enum class WidgetEditorSurface { MAIN_TAB, MAIN_SCREEN_PANEL, FLOATING_PANEL }

@Composable
internal fun PanelGeneralTab(
    state: WidgetSelectionDialogState,
    surface: WidgetEditorSurface,
    settingsViewModel: SettingsViewModel,
) {
    val pageCount by settingsViewModel.mainScreenPageCount.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        EditorCard(title = stringResource(R.string.widget_editor_card_panel_name)) {
            OutlinedTextField(
                value = state.wholePanelNameDraft,
                onValueChange = { state.wholePanelNameDraft = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                singleLine = true,
                label = {
                    Text(
                        text = stringResource(R.string.floating_panel_name_label),
                        style = MaterialTheme.typography.tboxBody,
                    )
                },
                textStyle = MaterialTheme.typography.tboxTitle,
            )
            if (surface == WidgetEditorSurface.MAIN_SCREEN_PANEL) {
                SettingDropdownGeneric(
                    state.wholePanelPageNumber,
                    { state.wholePanelPageNumber = it },
                    stringResource(R.string.settings_main_screen_panel_page_title),
                    stringResource(R.string.settings_main_screen_panel_page_desc),
                    true,
                    (1..pageCount.coerceAtLeast(1)).toList(),
                )
            }
        }
        EditorCard(title = stringResource(R.string.widget_editor_card_panel_behavior)) {
            SettingSwitch(
                state.wholePanelClickAction,
                { state.wholePanelClickAction = it },
                stringResource(
                    if (surface == WidgetEditorSurface.MAIN_SCREEN_PANEL) {
                        R.string.settings_open_app_on_main_screen_panel_click_title
                    } else {
                        R.string.settings_open_app_on_panel_click_title
                    }
                ),
                "",
                true,
            )
            SettingSwitch(
                state.wholePanelShowTboxDisconnect,
                { state.wholePanelShowTboxDisconnect = it },
                stringResource(R.string.settings_floating_tbox_disconnect_indicator_title),
                "",
                true,
            )
        }
    }
}

@Composable
internal fun PanelGridTab(
    state: WidgetSelectionDialogState,
    surface: WidgetEditorSurface,
    workingConfigs: List<FloatingDashboardWidgetConfig>,
    currentWidgetConfigs: List<FloatingDashboardWidgetConfig>,
    tileCount: Int,
) {
    val context = LocalContext.current
    val maxDim = SettingsManager.DASHBOARD_PANEL_MAX_GRID_DIMENSION
    val droppedTiles = countConfiguredTilesOutsideGrid(
        workingConfigs,
        state.wholePanelRows,
        state.wholePanelCols,
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        EditorCard(
            title = stringResource(
                R.string.widget_editor_grid_size_value,
                state.wholePanelRows,
                state.wholePanelCols,
            ),
            description = stringResource(R.string.widget_editor_grid_hint),
        ) {
            GridSizePicker(
                rows = state.wholePanelRows,
                cols = state.wholePanelCols,
                maxDimension = maxDim,
                onSizeChange = { rows, cols ->
                    state.wholePanelRows = rows
                    state.wholePanelCols = cols
                },
            )
            EditorIntStepper(
                label = stringResource(
                    if (surface == WidgetEditorSurface.FLOATING_PANEL) {
                        R.string.settings_floating_rows_title
                    } else {
                        R.string.settings_main_screen_panel_rows_title
                    }
                ),
                value = state.wholePanelRows,
                onValueChange = { state.wholePanelRows = it },
                minValue = SettingsManager.DASHBOARD_PANEL_GRID_OPTIONS.first(),
                maxValue = SettingsManager.DASHBOARD_PANEL_GRID_OPTIONS.last(),
            )
            EditorIntStepper(
                label = stringResource(
                    if (surface == WidgetEditorSurface.FLOATING_PANEL) {
                        R.string.settings_floating_cols_title
                    } else {
                        R.string.settings_main_screen_panel_cols_title
                    }
                ),
                value = state.wholePanelCols,
                onValueChange = { state.wholePanelCols = it },
                minValue = SettingsManager.DASHBOARD_PANEL_GRID_OPTIONS.first(),
                maxValue = SettingsManager.DASHBOARD_PANEL_GRID_OPTIONS.last(),
            )
            if (droppedTiles > 0) {
                Text(
                    text = stringResource(R.string.widget_editor_grid_overflow, droppedTiles),
                    style = MaterialTheme.typography.tboxBody,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        EditorCard(
            title = stringResource(R.string.widget_editor_spacing),
            description = stringResource(R.string.settings_panel_grid_spacing_desc),
        ) {
            EditorIntStepper(
                label = stringResource(R.string.widget_editor_spacing),
                value = state.wholePanelGridSpacingDp,
                onValueChange = { state.wholePanelGridSpacingDp = normalizePanelGridSpacingDp(it) },
                minValue = MIN_PANEL_GRID_SPACING_DP,
                maxValue = MAX_PANEL_GRID_SPACING_DP,
                suffix = " dp",
                presets = listOf(0, 2, 4, 8, 12, 16).map { it to "$it" },
            )
        }
        val doneMessage = stringResource(R.string.widget_editor_apply_look_done)
        EditorCard(
            title = stringResource(R.string.widget_editor_card_apply_look),
            description = stringResource(R.string.widget_editor_apply_look_desc, state.editingIndex + 1),
        ) {
            val selectable = (0 until tileCount).filter {
                it != state.editingIndex && workingConfigs.getOrNull(it)?.dataKey?.isNotEmpty() == true
            }.toSet()
            val selected = state.gridSelection.intersect(selectable)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EditorToggleButton(
                    text = stringResource(R.string.widget_editor_select_all),
                    selected = false,
                    onClick = { state.gridSelection = selectable },
                    enabled = selectable.isNotEmpty(),
                    compact = true,
                )
                EditorToggleButton(
                    text = stringResource(R.string.widget_editor_select_none),
                    selected = false,
                    onClick = { state.gridSelection = emptySet() },
                    enabled = selected.isNotEmpty(),
                    compact = true,
                )
                EditorToggleButton(
                    text = stringResource(R.string.widget_editor_apply_look_button, selected.size),
                    selected = selected.isNotEmpty(),
                    onClick = {
                        state.gridSelection = selected
                        val changed = state.applyAppearanceToSelection(currentWidgetConfigs, tileCount)
                        state.gridSelection = emptySet()
                        Toast.makeText(context, String.format(doneMessage, changed), Toast.LENGTH_SHORT).show()
                    },
                    enabled = selected.isNotEmpty() && state.togglesEnabled,
                    compact = true,
                )
            }
        }
    }
}

/** 15×15 cell picker: tap or drag to the bottom-right cell of the wanted grid. */
@Composable
private fun GridSizePicker(
    rows: Int,
    cols: Int,
    maxDimension: Int,
    onSizeChange: (rows: Int, cols: Int) -> Unit,
) {
    val selectedColor = MaterialTheme.colorScheme.primary
    val idleColor = MaterialTheme.colorScheme.outlineVariant
    fun cellAt(offset: Offset, width: Float, height: Float): Pair<Int, Int> {
        val c = ((offset.x / width) * maxDimension).toInt().coerceIn(0, maxDimension - 1) + 1
        val r = ((offset.y / height) * maxDimension).toInt().coerceIn(0, maxDimension - 1) + 1
        return r to c
    }
    Canvas(
        modifier = Modifier
            .widthIn(max = 360.dp)
            .fillMaxWidth()
            .aspectRatio(1f)
            .padding(vertical = 8.dp)
            .pointerInput(maxDimension) {
                detectTapGestures { offset ->
                    val (r, c) = cellAt(offset, size.width.toFloat(), size.height.toFloat())
                    onSizeChange(r, c)
                }
            }
            .pointerInput(maxDimension) {
                detectDragGestures { change, _ ->
                    val (r, c) = cellAt(change.position, size.width.toFloat(), size.height.toFloat())
                    onSizeChange(r, c)
                }
            },
    ) {
        val gap = 3.dp.toPx()
        val cell = (size.width - gap * (maxDimension - 1)) / maxDimension
        for (r in 0 until maxDimension) {
            for (c in 0 until maxDimension) {
                val inside = r < rows && c < cols
                drawRoundRect(
                    color = if (inside) selectedColor else idleColor,
                    topLeft = Offset(c * (cell + gap), r * (cell + gap)),
                    size = Size(cell, cell),
                    cornerRadius = CornerRadius(3.dp.toPx()),
                    alpha = if (inside) 0.85f else 0.5f,
                )
            }
        }
    }
}

@Composable
internal fun PanelBackgroundTab(
    state: WidgetSelectionDialogState,
    settingsViewModel: SettingsViewModel,
    panelStorageId: String,
) {
    val presetSlots by settingsViewModel.widgetColorPresetSlots.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        EditorCard(title = null) {
            PanelBackgroundAppearanceSettingsSection(
                panelStorageId = panelStorageId,
                enabled = true,
                colorThemeSegment = state.wholePanelBackgroundColorThemeSegment,
                onColorThemeSegmentChange = { state.wholePanelBackgroundColorThemeSegment = it },
                backgroundColorLight = state.wholePanelBackgroundColorLight,
                backgroundColorDark = state.wholePanelBackgroundColorDark,
                onBackgroundColorLightChange = { state.wholePanelBackgroundColorLight = it },
                onBackgroundColorDarkChange = { state.wholePanelBackgroundColorDark = it },
                backgroundImageRelPathLight = state.wholePanelBackgroundImageRelPathLight,
                backgroundImageRelPathDark = state.wholePanelBackgroundImageRelPathDark,
                onBackgroundImageRelPathLightChange = { state.wholePanelBackgroundImageRelPathLight = it },
                onBackgroundImageRelPathDarkChange = { state.wholePanelBackgroundImageRelPathDark = it },
                panelShape = state.wholePanelShape,
                onPanelShapeChange = { state.wholePanelShape = it },
                settingsViewModel = settingsViewModel,
                presetSlots = presetSlots,
            )
        }
    }
}

@Composable
internal fun PanelCollapseTab(
    state: WidgetSelectionDialogState,
    settingsViewModel: SettingsViewModel,
) {
    val presetSlots by settingsViewModel.widgetColorPresetSlots.collectAsStateWithLifecycle()
    val edge = PanelCollapseEdge.fromStorage(state.wholePanelCollapseEdge)
    val collapseEnabled = edge != PanelCollapseEdge.NONE
    /** 0 = strip settings while the panel is expanded, 1 = collapsed. */
    var stripStateSegment by remember { mutableIntStateOf(1) }
    val editingCollapsed = stripStateSegment == 1
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        EditorCard(
            title = stringResource(R.string.settings_panel_collapse_edge_title),
            description = stringResource(R.string.settings_panel_collapse_edge_desc),
        ) {
            EditorChoiceChips(
                options = listOf(
                    PanelCollapseEdge.NONE to stringResource(R.string.settings_panel_collapse_edge_none),
                    PanelCollapseEdge.LEFT to stringResource(R.string.settings_panel_collapse_edge_left),
                    PanelCollapseEdge.RIGHT to stringResource(R.string.settings_panel_collapse_edge_right),
                    PanelCollapseEdge.TOP to stringResource(R.string.settings_panel_collapse_edge_top),
                    PanelCollapseEdge.BOTTOM to stringResource(R.string.settings_panel_collapse_edge_bottom),
                ),
                selected = edge,
                onSelect = { state.wholePanelCollapseEdge = it.storageValue },
            )
            CollapseDiagram(
                state = state,
                edge = edge,
                editingCollapsed = editingCollapsed,
            )
            Text(
                text = stringResource(R.string.widget_editor_collapse_diagram_hint),
                style = MaterialTheme.typography.tboxCaption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        EditorCard(title = stringResource(R.string.widget_editor_card_strip)) {
            EditorSegmented(
                options = listOf(
                    stringResource(R.string.widget_editor_strip_expanded),
                    stringResource(R.string.widget_editor_strip_collapsed),
                ),
                selectedIndex = stripStateSegment,
                onSelect = { stripStateSegment = it },
                modifier = Modifier.padding(bottom = 8.dp),
            )
            if (editingCollapsed) {
                StripLengthAndPositionSliders(
                    lengthPercent = state.wholePanelCollapseStripLengthPercentCollapsed,
                    positionPercent = state.wholePanelCollapseStripPositionPercentCollapsed,
                    onLengthChange = { length ->
                        state.wholePanelCollapseStripLengthPercentCollapsed = length
                        state.wholePanelCollapseStripPositionPercentCollapsed =
                            normalizePanelCollapseStripPositionPercent(
                                state.wholePanelCollapseStripPositionPercentCollapsed,
                                length,
                            )
                    },
                    onPositionChange = { state.wholePanelCollapseStripPositionPercentCollapsed = it },
                    lengthTitleRes = R.string.settings_panel_collapse_strip_length_collapsed_title,
                    positionTitleRes = R.string.settings_panel_collapse_strip_position_collapsed_title,
                    enabled = collapseEnabled,
                )
            } else {
                StripLengthAndPositionSliders(
                    lengthPercent = state.wholePanelCollapseStripLengthPercentExpanded,
                    positionPercent = state.wholePanelCollapseStripPositionPercentExpanded,
                    onLengthChange = { length ->
                        state.wholePanelCollapseStripLengthPercentExpanded = length
                        state.wholePanelCollapseStripPositionPercentExpanded =
                            normalizePanelCollapseStripPositionPercent(
                                state.wholePanelCollapseStripPositionPercentExpanded,
                                length,
                            )
                    },
                    onPositionChange = { state.wholePanelCollapseStripPositionPercentExpanded = it },
                    lengthTitleRes = R.string.settings_panel_collapse_strip_length_expanded_title,
                    positionTitleRes = R.string.settings_panel_collapse_strip_position_expanded_title,
                    enabled = collapseEnabled,
                )
            }
            WidgetColorThemeSegmentRow(
                selectedSegment = state.wholePanelCollapseColorThemeSegment,
                onSegmentSelected = { state.wholePanelCollapseColorThemeSegment = it },
                enabled = true,
            )
            val editingLight = state.wholePanelCollapseColorThemeSegment == 0
            if (editingCollapsed) {
                key("collapsed", editingLight) {
                    WidgetColorSetting(
                        title = stringResource(R.string.settings_panel_collapse_strip_color_collapsed_title),
                        colorValue = if (editingLight) {
                            state.wholePanelCollapseStripColorLight
                        } else {
                            state.wholePanelCollapseStripColorDark
                        },
                        enabled = true,
                        onColorChange = {
                            if (editingLight) state.wholePanelCollapseStripColorLight = it
                            else state.wholePanelCollapseStripColorDark = it
                        },
                        presetSlots = presetSlots,
                        onPresetSlotColorSave = settingsViewModel::saveWidgetColorPresetSlot,
                        valueTextStyle = MaterialTheme.typography.tboxTitle,
                        valueLabelStyle = MaterialTheme.typography.tboxBody,
                    )
                }
                CaptionText(stringResource(R.string.settings_panel_collapse_strip_color_collapsed_desc))
            } else {
                key("expanded", editingLight) {
                    WidgetColorSetting(
                        title = stringResource(R.string.settings_panel_collapse_strip_color_expanded_title),
                        colorValue = if (editingLight) {
                            state.wholePanelCollapseStripExpandedColorLight
                        } else {
                            state.wholePanelCollapseStripExpandedColorDark
                        },
                        enabled = true,
                        onColorChange = {
                            if (editingLight) state.wholePanelCollapseStripExpandedColorLight = it
                            else state.wholePanelCollapseStripExpandedColorDark = it
                        },
                        presetSlots = presetSlots,
                        onPresetSlotColorSave = settingsViewModel::saveWidgetColorPresetSlot,
                        valueTextStyle = MaterialTheme.typography.tboxTitle,
                        valueLabelStyle = MaterialTheme.typography.tboxBody,
                    )
                }
                CaptionText(stringResource(R.string.settings_panel_collapse_strip_color_expanded_desc))
            }
        }
        EditorCard(title = stringResource(R.string.widget_editor_card_strip_size)) {
            SettingSliderInt(
                value = state.wholePanelCollapseStripThicknessDp,
                onValueChange = {
                    val strip = normalizePanelCollapseStripThicknessDp(it)
                    state.wholePanelCollapseStripThicknessDp = strip
                    state.wholePanelCollapseTouchZoneThicknessDp =
                        normalizePanelCollapseTouchZoneThicknessDp(
                            state.wholePanelCollapseTouchZoneThicknessDp,
                            strip,
                        )
                },
                text = stringResource(
                    R.string.settings_panel_collapse_thickness_title,
                    state.wholePanelCollapseStripThicknessDp,
                ),
                description = stringResource(R.string.settings_panel_collapse_thickness_desc),
                minValue = MIN_PANEL_COLLAPSE_STRIP_THICKNESS_DP,
                maxValue = MAX_PANEL_COLLAPSE_STRIP_THICKNESS_DP,
                enabled = true,
            )
            val touchZoneMin = maxOf(
                MIN_PANEL_COLLAPSE_STRIP_THICKNESS_DP,
                normalizePanelCollapseStripThicknessDp(state.wholePanelCollapseStripThicknessDp),
            )
            SettingSliderInt(
                value = state.wholePanelCollapseTouchZoneThicknessDp,
                onValueChange = {
                    state.wholePanelCollapseTouchZoneThicknessDp =
                        normalizePanelCollapseTouchZoneThicknessDp(
                            it,
                            state.wholePanelCollapseStripThicknessDp,
                        )
                },
                text = stringResource(
                    R.string.settings_panel_collapse_touch_zone_thickness_title,
                    state.wholePanelCollapseTouchZoneThicknessDp,
                ),
                description = stringResource(R.string.settings_panel_collapse_touch_zone_thickness_desc),
                minValue = touchZoneMin,
                maxValue = MAX_PANEL_COLLAPSE_TOUCH_ZONE_THICKNESS_DP,
                enabled = collapseEnabled,
            )
            SettingSwitch(
                state.wholePanelCollapseTouchZoneMatchStrip,
                { state.wholePanelCollapseTouchZoneMatchStrip = it },
                stringResource(R.string.settings_panel_collapse_touch_zone_match_strip_title),
                stringResource(R.string.settings_panel_collapse_touch_zone_match_strip_desc),
                collapseEnabled,
            )
        }
        EditorCard(title = stringResource(R.string.widget_editor_card_gestures)) {
            SettingSwitch(
                state.wholePanelCollapseOnStripTap,
                { state.wholePanelCollapseOnStripTap = it },
                stringResource(R.string.settings_panel_collapse_on_strip_tap_title),
                stringResource(R.string.settings_panel_collapse_on_strip_tap_desc),
                collapseEnabled,
            )
            SettingSwitch(
                state.wholePanelCollapseOnStripDoubleTap,
                { state.wholePanelCollapseOnStripDoubleTap = it },
                stringResource(R.string.settings_panel_collapse_on_strip_double_tap_title),
                stringResource(R.string.settings_panel_collapse_on_strip_double_tap_desc),
                collapseEnabled,
            )
            SettingSwitch(
                state.wholePanelCollapseOnTileTap,
                { state.wholePanelCollapseOnTileTap = it },
                stringResource(R.string.settings_panel_collapse_on_tile_tap_title),
                stringResource(R.string.settings_panel_collapse_on_tile_tap_desc),
                collapseEnabled,
            )
            SettingSliderInt(
                value = state.wholePanelCollapseOnTileTapDelaySec,
                onValueChange = {
                    state.wholePanelCollapseOnTileTapDelaySec = normalizePanelCollapseOnTileTapDelaySec(it)
                },
                text = stringResource(
                    R.string.settings_panel_collapse_on_tile_tap_delay_title,
                    state.wholePanelCollapseOnTileTapDelaySec,
                ),
                description = stringResource(R.string.settings_panel_collapse_on_tile_tap_delay_desc),
                minValue = MIN_PANEL_COLLAPSE_ON_TILE_TAP_DELAY_SEC,
                maxValue = MAX_PANEL_COLLAPSE_ON_TILE_TAP_DELAY_SEC,
                enabled = collapseEnabled && state.wholePanelCollapseOnTileTap,
            )
        }
    }
}

@Composable
private fun CaptionText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.tboxCaption,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun StripLengthAndPositionSliders(
    lengthPercent: Int,
    positionPercent: Int,
    onLengthChange: (Int) -> Unit,
    onPositionChange: (Int) -> Unit,
    lengthTitleRes: Int,
    positionTitleRes: Int,
    enabled: Boolean,
) {
    val length = normalizePanelCollapseStripLengthPercent(lengthPercent)
    SettingSliderInt(
        value = lengthPercent,
        onValueChange = { onLengthChange(normalizePanelCollapseStripLengthPercent(it)) },
        text = stringResource(lengthTitleRes, lengthPercent),
        description = stringResource(R.string.settings_panel_collapse_strip_length_desc),
        minValue = MIN_PANEL_COLLAPSE_STRIP_LENGTH_PERCENT,
        maxValue = MAX_PANEL_COLLAPSE_STRIP_LENGTH_PERCENT,
        enabled = enabled,
    )
    SettingSliderInt(
        value = positionPercent,
        onValueChange = { onPositionChange(normalizePanelCollapseStripPositionPercent(it, lengthPercent)) },
        text = stringResource(positionTitleRes, positionPercent),
        description = stringResource(R.string.settings_panel_collapse_strip_position_desc),
        minValue = 0,
        maxValue = (100 - length).coerceAtLeast(0),
        enabled = enabled && length < MAX_PANEL_COLLAPSE_STRIP_LENGTH_PERCENT,
    )
}

/**
 * Schematic panel: tap near an edge to choose it, drag along the chosen edge to move
 * the strip of the edited state ([editingCollapsed]).
 */
@Composable
private fun CollapseDiagram(
    state: WidgetSelectionDialogState,
    edge: PanelCollapseEdge,
    editingCollapsed: Boolean,
) {
    val panelColor = MaterialTheme.colorScheme.surfaceVariant
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val outlineColor = MaterialTheme.colorScheme.outline
    val stripColor = MaterialTheme.colorScheme.primary
    val rows = state.wholePanelRows.coerceAtLeast(1)
    val cols = state.wholePanelCols.coerceAtLeast(1)
    val length = if (editingCollapsed) {
        state.wholePanelCollapseStripLengthPercentCollapsed
    } else {
        state.wholePanelCollapseStripLengthPercentExpanded
    }
    val position = if (editingCollapsed) {
        state.wholePanelCollapseStripPositionPercentCollapsed
    } else {
        state.wholePanelCollapseStripPositionPercentExpanded
    }
    val span = collapseStripSpan(length, position)
    var dragging by remember { mutableStateOf(false) }
    fun setPosition(fraction: Float) {
        val newPosition = collapseStripPositionForCenter(fraction, length)
        if (editingCollapsed) {
            state.wholePanelCollapseStripPositionPercentCollapsed = newPosition
        } else {
            state.wholePanelCollapseStripPositionPercentExpanded = newPosition
        }
    }
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .padding(vertical = 8.dp)
            .pointerInput(rows, cols) {
                detectTapGestures { offset ->
                    val inset = 28.dp.toPx()
                    val left = inset
                    val top = inset
                    val right = size.width - inset
                    val bottom = size.height - inset
                    val distances = listOf(
                        PanelCollapseEdge.LEFT to abs(offset.x - left),
                        PanelCollapseEdge.RIGHT to abs(offset.x - right),
                        PanelCollapseEdge.TOP to abs(offset.y - top),
                        PanelCollapseEdge.BOTTOM to abs(offset.y - bottom),
                    )
                    state.wholePanelCollapseEdge = distances.minBy { it.second }.first.storageValue
                }
            }
            .pointerInput(edge, length, editingCollapsed) {
                if (edge == PanelCollapseEdge.NONE) return@pointerInput
                detectDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false },
                ) { change, _ ->
                    val inset = 28.dp.toPx()
                    val fraction = when (edge) {
                        PanelCollapseEdge.LEFT, PanelCollapseEdge.RIGHT ->
                            (change.position.y - inset) / (size.height - 2 * inset)
                        else -> (change.position.x - inset) / (size.width - 2 * inset)
                    }
                    setPosition(fraction)
                }
            },
    ) {
        val inset = 28.dp.toPx()
        val panelW = size.width - 2 * inset
        val panelH = size.height - 2 * inset
        drawRoundRect(
            color = panelColor,
            topLeft = Offset(inset, inset),
            size = Size(panelW, panelH),
            cornerRadius = CornerRadius(10.dp.toPx()),
        )
        for (c in 1 until cols) {
            val x = inset + panelW * c / cols
            drawLine(gridColor, Offset(x, inset), Offset(x, inset + panelH), strokeWidth = 1.dp.toPx())
        }
        for (r in 1 until rows) {
            val y = inset + panelH * r / rows
            drawLine(gridColor, Offset(inset, y), Offset(inset + panelW, y), strokeWidth = 1.dp.toPx())
        }
        drawRoundRect(
            color = outlineColor,
            topLeft = Offset(inset, inset),
            size = Size(panelW, panelH),
            cornerRadius = CornerRadius(10.dp.toPx()),
            style = Stroke(width = 1.dp.toPx()),
        )
        if (edge != PanelCollapseEdge.NONE) {
            val thickness = (if (dragging) 14.dp else 10.dp).toPx()
            val (start, end) = when (edge) {
                PanelCollapseEdge.LEFT -> Offset(inset - thickness, inset + panelH * span.start) to
                    Offset(inset - thickness, inset + panelH * span.end)
                PanelCollapseEdge.RIGHT -> Offset(inset + panelW + thickness, inset + panelH * span.start) to
                    Offset(inset + panelW + thickness, inset + panelH * span.end)
                PanelCollapseEdge.TOP -> Offset(inset + panelW * span.start, inset - thickness) to
                    Offset(inset + panelW * span.end, inset - thickness)
                else -> Offset(inset + panelW * span.start, inset + panelH + thickness) to
                    Offset(inset + panelW * span.end, inset + panelH + thickness)
            }
            drawLine(
                color = stripColor,
                start = start,
                end = end,
                strokeWidth = thickness,
                cap = StrokeCap.Round,
            )
        }
    }
}

