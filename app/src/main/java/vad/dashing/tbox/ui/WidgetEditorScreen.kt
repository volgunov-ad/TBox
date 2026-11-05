package vad.dashing.tbox.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.FloatingDashboardWidgetConfig
import vad.dashing.tbox.HTTP_REQUEST_WIDGET_DATA_KEY
import vad.dashing.tbox.PHONE_DIAL_WIDGET_DATA_KEY
import vad.dashing.tbox.R
import vad.dashing.tbox.SettingsViewModel
import vad.dashing.tbox.TileBackgroundImageStorage
import vad.dashing.tbox.WidgetsRepository
import vad.dashing.tbox.normalizePanelShape
import vad.dashing.tbox.normalizeWidgetFontWeight
import vad.dashing.tbox.normalizeWidgetScale
import vad.dashing.tbox.normalizeWidgetShape
import vad.dashing.tbox.normalizeWidgetTextAlign
import vad.dashing.tbox.normalizeWidgetTitlePosition
import vad.dashing.tbox.resolvePanelBackgroundColorArgb
import vad.dashing.tbox.ui.theme.tboxBody
import vad.dashing.tbox.ui.theme.tboxButton
import vad.dashing.tbox.ui.theme.tboxCaption
import vad.dashing.tbox.ui.theme.tboxHeadline
import vad.dashing.tbox.ui.theme.tboxTitle
import kotlinx.coroutines.delay

/**
 * Full-screen tile / panel editor shared by the Tiles tab, main-screen panels and floating panels.
 * [inWindow] draws it inside the host composition instead of a dialog window (floating overlay
 * edit on A10, where a separate window may not get a valid token).
 */
@Composable
internal fun WidgetEditorScreen(
    state: WidgetSelectionDialogState,
    settingsViewModel: SettingsViewModel,
    surface: WidgetEditorSurface,
    inWindow: Boolean,
    tileStorageId: String,
    currentWidgetConfigs: List<FloatingDashboardWidgetConfig>,
    baseGrid: WidgetEditorGrid,
    baseTileCount: Int,
    panelName: String,
    dataKeyFilter: (String) -> Boolean,
    onWholePanelOpened: () -> Unit,
    onPickExternalWidget: () -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onDeletePanel: (() -> Unit)?,
) {
    var confirmDiscard by remember { mutableStateOf(false) }
    val requestClose: () -> Unit = {
        if (state.hasUnsavedChanges) confirmDiscard = true else onDismiss()
    }
    val content: @Composable () -> Unit = {
        Box(modifier = Modifier.fillMaxSize()) {
            WidgetEditorContent(
                state = state,
                settingsViewModel = settingsViewModel,
                surface = surface,
                tileStorageId = tileStorageId,
                currentWidgetConfigs = currentWidgetConfigs,
                baseGrid = baseGrid,
                baseTileCount = baseTileCount,
                panelName = panelName,
                dataKeyFilter = dataKeyFilter,
                onWholePanelOpened = onWholePanelOpened,
                onPickExternalWidget = onPickExternalWidget,
                onClose = requestClose,
                onSave = onSave,
                onDeletePanel = onDeletePanel,
            )
            if (confirmDiscard) {
                DiscardChangesOverlay(
                    onKeepEditing = { confirmDiscard = false },
                    onDiscard = {
                        confirmDiscard = false
                        onDismiss()
                    },
                )
            }
        }
    }
    if (inWindow) {
        BackHandler(onBack = { if (confirmDiscard) confirmDiscard = false else requestClose() })
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
            color = MaterialTheme.colorScheme.surface,
        ) {
            content()
        }
    } else {
        Dialog(
            onDismissRequest = { if (confirmDiscard) confirmDiscard = false else requestClose() },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnClickOutside = false,
                decorFitsSystemWindows = false,
            ),
        ) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                content()
            }
        }
    }
}

@Composable
private fun WidgetEditorContent(
    state: WidgetSelectionDialogState,
    settingsViewModel: SettingsViewModel,
    surface: WidgetEditorSurface,
    tileStorageId: String,
    currentWidgetConfigs: List<FloatingDashboardWidgetConfig>,
    baseGrid: WidgetEditorGrid,
    baseTileCount: Int,
    panelName: String,
    dataKeyFilter: (String) -> Boolean,
    onWholePanelOpened: () -> Unit,
    onPickExternalWidget: () -> Unit,
    onClose: () -> Unit,
    onSave: () -> Unit,
    onDeletePanel: (() -> Unit)?,
) {
    val noTboxConnect by settingsViewModel.noTboxConnect.collectAsStateWithLifecycle()
    LaunchedEffect(noTboxConnect) {
        state.preferUseMbCanVhalDefault = noTboxConnect
    }
    val hasPanelLevel = surface != WidgetEditorSurface.MAIN_TAB
    val grid = if (state.wholePanelDraftSeeded) {
        WidgetEditorGrid(
            rows = state.wholePanelRows,
            cols = state.wholePanelCols,
            gridSpacingDp = state.wholePanelGridSpacingDp,
            panelBackgroundColorLight = state.wholePanelBackgroundColorLight,
            panelBackgroundColorDark = state.wholePanelBackgroundColorDark,
            panelShape = state.wholePanelShape,
        )
    } else {
        baseGrid
    }
    val tileCount = if (state.wholePanelDraftSeeded) grid.tileCount else baseTileCount.coerceAtLeast(1)
    val workingConfigs = state.workingTileConfigs(currentWidgetConfigs, tileCount)
    val externalAppWidgetId = externalAppWidgetIdForApply(state, currentWidgetConfigs, state.editingIndex)
    val tileBackgroundSlot = resolveTileBackgroundStorageSlot(
        panelStorageId = tileStorageId,
        configs = workingConfigs,
        editingIndex = state.editingIndex,
        ownPathLight = state.tileBackgroundImageRelPathLight,
        ownPathDark = state.tileBackgroundImageRelPathDark,
    )
    val context = LocalContext.current
    val fixTileFirstMessage = stringResource(R.string.widget_editor_fix_tile_first)
    val goToTile: (Int) -> Boolean = { index ->
        state.goToTile(index, currentWidgetConfigs, tileCount).also { moved ->
            if (!moved) Toast.makeText(context, fixTileFirstMessage, Toast.LENGTH_SHORT).show()
        }
    }
    val openLevel: (WidgetEditorLevel) -> Unit = { level ->
        if (level == WidgetEditorLevel.PANEL && !state.wholePanelDraftSeeded) {
            onWholePanelOpened()
        }
        state.level = level
    }

    Column(modifier = Modifier.fillMaxSize()) {
        EditorHeader(
            state = state,
            surface = surface,
            panelName = panelName,
            hasPanelLevel = hasPanelLevel,
            onLevelChange = openLevel,
            onClose = onClose,
        )
        HorizontalDivider()
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(0.38f)
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
            ) {
                PreviewCard(
                    state = state,
                    settingsViewModel = settingsViewModel,
                    grid = grid,
                    tileCount = tileCount,
                    workingConfigs = workingConfigs,
                    currentWidgetConfigs = currentWidgetConfigs,
                    onTileTap = { index ->
                        val gridSelectMode = state.level == WidgetEditorLevel.PANEL &&
                            state.panelTab == PanelEditorTab.GRID
                        when {
                            gridSelectMode -> {
                                if (index != state.editingIndex &&
                                    workingConfigs.getOrNull(index)?.dataKey?.isNotEmpty() == true
                                ) {
                                    state.gridSelection = if (index in state.gridSelection) {
                                        state.gridSelection - index
                                    } else {
                                        state.gridSelection + index
                                    }
                                }
                            }
                            else -> {
                                if (goToTile(index)) state.level = WidgetEditorLevel.TILE
                            }
                        }
                    },
                )
                TileNavigationRow(state = state, tileCount = tileCount, onGoTo = { goToTile(it) })
                ClipboardCard(state = state, currentWidgetConfigs = currentWidgetConfigs)
                if (state.level == WidgetEditorLevel.TILE) {
                    TypeDescriptionCard(state.selectedDataKey)
                }
            }
            VerticalDivider()
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(0.62f)
                    .padding(start = 12.dp, end = 12.dp, top = 4.dp),
            ) {
                if (state.level == WidgetEditorLevel.TILE) {
                    val tabs = TileEditorTab.entries
                    HorizontalSectionTabRow(
                        tabs = tabs.map { stringResource(it.titleRes()) },
                        selectedIndex = tabs.indexOf(state.tileTab),
                        onTabSelected = { state.tileTab = tabs[it] },
                    )
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        when (state.tileTab) {
                            TileEditorTab.TYPE -> TileTypeTab(
                                state = state,
                                settingsViewModel = settingsViewModel,
                                dataKeyFilter = dataKeyFilter,
                                panelTileConfigs = workingConfigs,
                                externalAppWidgetId = externalAppWidgetId,
                                onPickExternalWidget = onPickExternalWidget,
                            )
                            TileEditorTab.VIEW -> TileViewTab(state)
                            TileEditorTab.COLORS -> TileColorsTab(
                                state = state,
                                settingsViewModel = settingsViewModel,
                                tileBackgroundPanelStorageId = tileStorageId,
                                tileBackgroundSlot = tileBackgroundSlot,
                            )
                            TileEditorTab.BEHAVIOR -> TileBehaviorTab(
                                state = state,
                                settingsViewModel = settingsViewModel,
                                tileBackgroundPanelStorageId = tileStorageId,
                                externalAppWidgetId = externalAppWidgetId,
                                onPickExternalWidget = onPickExternalWidget,
                            )
                        }
                    }
                } else {
                    val tabs = PanelEditorTab.entries
                    HorizontalSectionTabRow(
                        tabs = tabs.map { stringResource(it.titleRes()) },
                        selectedIndex = tabs.indexOf(state.panelTab),
                        onTabSelected = { state.panelTab = tabs[it] },
                    )
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        when (state.panelTab) {
                            PanelEditorTab.GENERAL -> PanelGeneralTab(state, surface, settingsViewModel)
                            PanelEditorTab.PLACEMENT -> PanelPlacementTab(state, surface, settingsViewModel)
                            PanelEditorTab.GRID -> PanelGridTab(
                                state = state,
                                surface = surface,
                                workingConfigs = workingConfigs,
                                currentWidgetConfigs = currentWidgetConfigs,
                                tileCount = tileCount,
                            )
                            PanelEditorTab.BACKGROUND -> PanelBackgroundTab(state, settingsViewModel, tileStorageId)
                            PanelEditorTab.COLLAPSE -> PanelCollapseTab(state, settingsViewModel)
                        }
                    }
                }
            }
        }
        HorizontalDivider()
        EditorFooter(
            state = state,
            onClose = onClose,
            onSave = onSave,
            onDeletePanel = onDeletePanel,
        )
    }
}

private fun TileEditorTab.titleRes(): Int = when (this) {
    TileEditorTab.TYPE -> R.string.widget_editor_tab_type
    TileEditorTab.VIEW -> R.string.widget_editor_tab_view
    TileEditorTab.COLORS -> R.string.widget_editor_tab_colors
    TileEditorTab.BEHAVIOR -> R.string.widget_editor_tab_behavior
}

private fun PanelEditorTab.titleRes(): Int = when (this) {
    PanelEditorTab.GENERAL -> R.string.widget_editor_tab_general
    PanelEditorTab.PLACEMENT -> R.string.widget_editor_tab_placement
    PanelEditorTab.GRID -> R.string.widget_editor_tab_grid
    PanelEditorTab.BACKGROUND -> R.string.widget_editor_tab_background
    PanelEditorTab.COLLAPSE -> R.string.widget_editor_tab_collapse
}

@Composable
private fun EditorHeader(
    state: WidgetSelectionDialogState,
    surface: WidgetEditorSurface,
    panelName: String,
    hasPanelLevel: Boolean,
    onLevelChange: (WidgetEditorLevel) -> Unit,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (state.level == WidgetEditorLevel.PANEL) {
                    stringResource(R.string.widget_panel_settings_title)
                } else {
                    stringResource(R.string.widget_editor_title_tile, state.editingIndex + 1)
                },
                style = MaterialTheme.typography.tboxHeadline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (surface != WidgetEditorSurface.MAIN_TAB && panelName.isNotBlank()) {
                Text(
                    text = panelName,
                    style = MaterialTheme.typography.tboxCaption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (hasPanelLevel) {
            EditorSegmented(
                options = listOf(
                    stringResource(R.string.widget_editor_level_tile),
                    stringResource(R.string.widget_toggle_whole_panel),
                ),
                selectedIndex = if (state.level == WidgetEditorLevel.TILE) 0 else 1,
                onSelect = { onLevelChange(if (it == 0) WidgetEditorLevel.TILE else WidgetEditorLevel.PANEL) },
                modifier = Modifier.widthIn(max = 420.dp).padding(horizontal = 16.dp),
            )
        }
        TextButton(onClick = rememberWrappedOnClick(onClose)) {
            Text(text = "✕", style = MaterialTheme.typography.tboxHeadline)
        }
    }
}

private const val DELETE_CONFIRM_TIMEOUT_MS = 4_000L

@Composable
private fun EditorFooter(
    state: WidgetSelectionDialogState,
    onClose: () -> Unit,
    onSave: () -> Unit,
    onDeletePanel: (() -> Unit)?,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(confirmDelete) {
        if (confirmDelete) {
            delay(DELETE_CONFIRM_TIMEOUT_MS)
            confirmDelete = false
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (onDeletePanel != null) {
            OutlinedButton(
                onClick = rememberWrappedOnClick {
                    if (confirmDelete) onDeletePanel() else confirmDelete = true
                },
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(
                    text = stringResource(
                        if (confirmDelete) R.string.widget_editor_delete_panel_confirm else R.string.widget_editor_delete_panel
                    ),
                    style = MaterialTheme.typography.tboxButton,
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            if (state.hasUnsavedChanges) {
                Text(
                    text = "● " + stringResource(R.string.widget_editor_unsaved),
                    style = MaterialTheme.typography.tboxBody,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (!state.canSaveSelection) {
                Text(
                    text = stringResource(R.string.widget_editor_cannot_save),
                    style = MaterialTheme.typography.tboxCaption,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        OutlinedButton(onClick = rememberWrappedOnClick(onClose)) {
            Text(text = stringResource(R.string.action_cancel), style = MaterialTheme.typography.tboxButton)
        }
        Button(enabled = state.canSaveSelection, onClick = rememberWrappedOnClick(onSave)) {
            Text(text = stringResource(R.string.action_save), style = MaterialTheme.typography.tboxButton)
        }
    }
}

@Composable
private fun DiscardChangesOverlay(onKeepEditing: () -> Unit, onDiscard: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onKeepEditing,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .widthIn(max = 560.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = stringResource(R.string.widget_editor_discard_title),
                    style = MaterialTheme.typography.tboxHeadline,
                )
                Text(
                    text = stringResource(R.string.widget_editor_discard_text),
                    style = MaterialTheme.typography.tboxBody,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                ) {
                    OutlinedButton(onClick = rememberWrappedOnClick(onDiscard)) {
                        Text(
                            text = stringResource(R.string.widget_editor_discard_confirm),
                            style = MaterialTheme.typography.tboxButton,
                        )
                    }
                    Button(onClick = rememberWrappedOnClick(onKeepEditing)) {
                        Text(
                            text = stringResource(R.string.widget_editor_keep_editing),
                            style = MaterialTheme.typography.tboxButton,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewCard(
    state: WidgetSelectionDialogState,
    settingsViewModel: SettingsViewModel,
    grid: WidgetEditorGrid,
    tileCount: Int,
    workingConfigs: List<FloatingDashboardWidgetConfig>,
    currentWidgetConfigs: List<FloatingDashboardWidgetConfig>,
    onTileTap: (Int) -> Unit,
) {
    val context = LocalContext.current
    val gridSelectMode = state.level == WidgetEditorLevel.PANEL && state.panelTab == PanelEditorTab.GRID
    val httpBlockedMessage = stringResource(R.string.widget_editor_swap_http_blocked)
    val fixTileFirstMessage = stringResource(R.string.widget_editor_fix_tile_first)
    EditorCard(title = null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.widget_editor_preview),
                style = MaterialTheme.typography.tboxTitle,
                modifier = Modifier.weight(1f),
            )
            EditorSegmented(
                options = listOf(
                    stringResource(R.string.widget_editor_day),
                    stringResource(R.string.widget_editor_night),
                ),
                selectedIndex = if (state.previewDark) 1 else 0,
                onSelect = { state.previewDark = it == 1 },
                modifier = Modifier.width(220.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        PanelPreviewGrid(
            state = state,
            settingsViewModel = settingsViewModel,
            grid = grid,
            tileCount = tileCount,
            configs = workingConfigs,
            gridSelectMode = gridSelectMode,
            onTileTap = onTileTap,
            onSwap = { from, to ->
                val list = state.workingTileConfigs(currentWidgetConfigs, tileCount)
                if (list.getOrNull(from)?.dataKey == HTTP_REQUEST_WIDGET_DATA_KEY ||
                    list.getOrNull(to)?.dataKey == HTTP_REQUEST_WIDGET_DATA_KEY
                ) {
                    Toast.makeText(context, httpBlockedMessage, Toast.LENGTH_LONG).show()
                } else if (!state.canLeaveEditingTile) {
                    Toast.makeText(context, fixTileFirstMessage, Toast.LENGTH_SHORT).show()
                } else {
                    state.swapTiles(from, to, currentWidgetConfigs, tileCount)
                }
            },
        )
        Text(
            text = stringResource(
                if (gridSelectMode) R.string.widget_editor_preview_hint_select else R.string.widget_editor_preview_hint_tile
            ),
            style = MaterialTheme.typography.tboxCaption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun PanelPreviewGrid(
    state: WidgetSelectionDialogState,
    settingsViewModel: SettingsViewModel,
    grid: WidgetEditorGrid,
    tileCount: Int,
    configs: List<FloatingDashboardWidgetConfig>,
    gridSelectMode: Boolean,
    onTileTap: (Int) -> Unit,
    onSwap: (Int, Int) -> Unit,
) {
    val density = LocalDensity.current
    val rows = grid.rows.coerceAtLeast(1)
    val cols = grid.cols.coerceAtLeast(1)
    val dark = state.previewDark
    val spacingDp = grid.gridSpacingDp.coerceIn(0, 8).dp
    val panelColor = Color(
        resolvePanelBackgroundColorArgb(
            if (dark) grid.panelBackgroundColorDark else grid.panelBackgroundColorLight,
        ),
    )
    var dragFrom by remember { mutableStateOf<Int?>(null) }
    var dragOver by remember { mutableStateOf<Int?>(null) }
    val currentOnTileTap by rememberUpdatedState(onTileTap)
    val currentOnSwap by rememberUpdatedState(onSwap)
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val maxHeight = 300.dp
        val aspect = ((cols * 1.5f) / rows).coerceIn(0.4f, 4f)
        val width = minOf(maxWidth, maxHeight * aspect)
        val height = width / aspect
        val spacingPx = with(density) { spacingDp.toPx() }
        fun indexAt(offset: Offset, sizeW: Float, sizeH: Float): Int? {
            val cellW = (sizeW - spacingPx * (cols - 1)) / cols
            val cellH = (sizeH - spacingPx * (rows - 1)) / rows
            val c = (offset.x / (cellW + spacingPx)).toInt()
            val r = (offset.y / (cellH + spacingPx)).toInt()
            if (r !in 0 until rows || c !in 0 until cols) return null
            return r * cols + c
        }
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(width, height)
                .clip(RoundedCornerShape(normalizePanelShape(grid.panelShape).dp))
                .background(panelColor)
                .pointerInput(rows, cols, tileCount, gridSelectMode, spacingPx) {
                    detectTapGestures { offset ->
                        val index = indexAt(offset, size.width.toFloat(), size.height.toFloat())
                        if (index != null && index < tileCount) currentOnTileTap(index)
                    }
                }
                .pointerInput(rows, cols, tileCount, gridSelectMode, spacingPx) {
                    if (gridSelectMode) return@pointerInput
                    detectDragGesturesAfterLongPress(
                        onDragStart = { offset ->
                            dragFrom = indexAt(offset, size.width.toFloat(), size.height.toFloat())
                                ?.takeIf { it < tileCount }
                            dragOver = dragFrom
                        },
                        onDragEnd = {
                            val from = dragFrom
                            val to = dragOver
                            dragFrom = null
                            dragOver = null
                            if (from != null && to != null && from != to) currentOnSwap(from, to)
                        },
                        onDragCancel = {
                            dragFrom = null
                            dragOver = null
                        },
                    ) { change, _ ->
                        change.consume()
                        dragOver = indexAt(change.position, size.width.toFloat(), size.height.toFloat())
                            ?.takeIf { it < tileCount }
                    }
                },
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(spacingDp),
            ) {
                for (r in 0 until rows) {
                    Row(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacingDp),
                    ) {
                        for (c in 0 until cols) {
                            val index = r * cols + c
                            PreviewTile(
                                config = configs.getOrNull(index),
                                exists = index < tileCount,
                                dark = dark,
                                defaultBackgroundLight = state.panelDefaultBackgroundLight,
                                defaultBackgroundDark = state.panelDefaultBackgroundDark,
                                editing = index == state.editingIndex,
                                selected = gridSelectMode && index in state.gridSelection,
                                dragSource = index == dragFrom,
                                dragTarget = index == dragOver && dragFrom != null && dragOver != dragFrom,
                                settingsViewModel = settingsViewModel,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewTile(
    config: FloatingDashboardWidgetConfig?,
    exists: Boolean,
    dark: Boolean,
    defaultBackgroundLight: Int,
    defaultBackgroundDark: Int,
    editing: Boolean,
    selected: Boolean,
    dragSource: Boolean,
    dragTarget: Boolean,
    settingsViewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val key = config?.dataKey.orEmpty()
    val shapeDp = normalizeWidgetShape(config?.shape ?: 0).dp
    val shape = RoundedCornerShape(shapeDp)
    val borderColor = when {
        dragTarget -> MaterialTheme.colorScheme.tertiary
        editing -> MaterialTheme.colorScheme.primary
        selected -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    val borderWidth = if (editing || selected || dragTarget) 3.dp else 1.dp
    if (!exists) {
        Box(modifier = modifier.clip(shape).background(Color.Transparent))
        return
    }
    val backgroundArgb = if (key.isEmpty()) {
        null
    } else {
        if (dark) config?.backgroundColorDark ?: defaultBackgroundDark
        else config?.backgroundColorLight ?: defaultBackgroundLight
    }
    val background = backgroundArgb?.let { Color(it) } ?: Color.Transparent
    val textColor = if (key.isEmpty()) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        Color(if (dark) config!!.textColorDark else config!!.textColorLight)
    }
    val themeCode = if (dark) 2 else 1
    val tileBgRelPath = (
        if (dark) config?.tileBackgroundImageRelPathDark else config?.tileBackgroundImageRelPathLight
        )?.takeIf { TileBackgroundImageStorage.isAllowedStoredRelPath(it) }
    val useTileBackgroundUnderlay = key.isNotEmpty() && tileBgRelPath != null
    val contentBackground = when {
        key.isEmpty() -> Color.Transparent
        useTileBackgroundUnderlay -> Color.Transparent
        else -> background.copy(alpha = if (dragSource) background.alpha * 0.4f else background.alpha)
    }
    Box(
        modifier = modifier
            .clip(shape)
            .border(borderWidth, borderColor, shape),
    ) {
        if (key.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Transparent),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "+",
                    color = textColor,
                    style = MaterialTheme.typography.tboxHeadline,
                )
            }
            return
        }
        val cfg = config!!
        WidgetCellContentPadding(widgetConfig = cfg) {
            if (useTileBackgroundUnderlay) {
                DashboardTileBackgroundImageUnderlay(
                    relPath = tileBgRelPath,
                    backgroundColor = background.copy(
                        alpha = if (dragSource) background.alpha * 0.4f else background.alpha,
                    ),
                    shapeDp = shapeDp,
                    settingsViewModel = settingsViewModel,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            CompositionLocalProvider(
                LocalWidgetTitleScale provides normalizeWidgetScale(cfg.titleScale),
                LocalWidgetTextScale provides normalizeWidgetScale(cfg.textScale),
                LocalWidgetIconScale provides normalizeWidgetScale(cfg.iconScale),
                LocalWidgetTextAlign provides widgetTextAlignToCompose(normalizeWidgetTextAlign(cfg.textAlign)),
                LocalWidgetFontWeight provides widgetFontWeightToCompose(normalizeWidgetFontWeight(cfg.fontWeight)),
                LocalWidgetTitlePosition provides normalizeWidgetTitlePosition(cfg.titlePosition),
            ) {
                val dialNumberPlaceholder =
                    stringResource(R.string.widget_phone_dial_number_placeholder)
                val sample = remember(
                    cfg.dataKey,
                    cfg.valueAccuracy,
                    cfg.showUnit,
                    cfg.dialPhoneNumber,
                    cfg.dialShowNumber,
                    dialNumberPlaceholder,
                ) {
                    widgetPreviewSample(
                        dataKey = cfg.dataKey,
                        config = cfg,
                        unit = WidgetsRepository.getUnitForDataKey(context, cfg.dataKey),
                        dialNumberPlaceholder = dialNumberPlaceholder,
                    )
                }
                val title = cfg.customTitle.ifBlank {
                    WidgetsRepository.getTitleForDataKey(context, cfg.dataKey)
                }
                val controls = rememberResolvedControlAppearance(
                    config = cfg,
                    currentTheme = themeCode,
                    tileTextColor = textColor,
                    dataKey = cfg.dataKey,
                )
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(shape)
                        .background(contentBackground),
                ) {
                    val availableHeight = maxHeight
                    val textAlign = LocalWidgetTextAlign.current
                    when (sample.kind) {
                        WidgetPreviewKind.CENTERED_TITLE -> {
                            // Live phone-dial hides the number: title alone, centered.
                            Text(
                                text = title,
                                style = calculateResponsiveTextStyle(
                                    containerHeight = availableHeight,
                                    textType = TextType.TITLE,
                                    forWidgetTitle = true,
                                ),
                                color = textColor,
                                textAlign = TextAlign.Center,
                                maxLines = 3,
                                softWrap = true,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp),
                            )
                        }
                        else -> {
                            DashboardWidgetContentWithOptionalTitle(
                                showTitle = cfg.showTitle,
                                titleText = title,
                                availableHeight = availableHeight,
                                resolvedTextColor = textColor,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(4.dp),
                            ) { contentModifier ->
                                when (sample.kind) {
                                    WidgetPreviewKind.CONTROL -> {
                                        PreviewControlHint(
                                            controls = controls,
                                            modifier = contentModifier.fillMaxSize(),
                                        )
                                    }
                                    WidgetPreviewKind.METRIC,
                                    WidgetPreviewKind.SPECIAL,
                                    -> {
                                        if (sample.valueText.isEmpty()) {
                                            Spacer(contentModifier)
                                        } else {
                                            val faded =
                                                sample.valueText == dialNumberPlaceholder
                                            Text(
                                                text = sample.valueText,
                                                style = calculateResponsiveTextStyle(
                                                    containerHeight = availableHeight,
                                                    textType = TextType.VALUE,
                                                ),
                                                color = textColor.copy(
                                                    alpha = if (faded) 0.45f else 1f,
                                                ),
                                                textAlign = if (faded ||
                                                    cfg.dataKey == PHONE_DIAL_WIDGET_DATA_KEY
                                                ) {
                                                    TextAlign.Center
                                                } else {
                                                    textAlign
                                                },
                                                maxLines = 2,
                                                softWrap = true,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = contentModifier
                                                    .fillMaxWidth()
                                                    .fillMaxHeight(),
                                            )
                                        }
                                    }
                                    WidgetPreviewKind.CENTERED_TITLE -> Unit
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewControlHint(
    controls: ResolvedControlColors,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(controls.paddingDp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val chipModifier = Modifier
            .weight(1f)
            .fillMaxHeight(0.55f)
            .clip(RoundedCornerShape(controls.shapeDp))
        Box(
            modifier = chipModifier.background(
                if (controls.inactiveBackground == Color.Transparent) {
                    controls.inactiveContent.copy(alpha = 0.18f)
                } else {
                    controls.inactiveBackground
                },
            ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.35f)
                    .fillMaxHeight(0.35f)
                    .clip(RoundedCornerShape(controls.shapeDp))
                    .background(controls.inactiveContent),
            )
        }
        Box(
            modifier = chipModifier.background(
                if (controls.activeBackground == Color.Transparent) {
                    controls.activeContent.copy(alpha = 0.22f)
                } else {
                    controls.activeBackground
                },
            ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.35f)
                    .fillMaxHeight(0.35f)
                    .clip(RoundedCornerShape(controls.shapeDp))
                    .background(controls.activeContent),
            )
        }
    }
}

@Composable
private fun TileNavigationRow(
    state: WidgetSelectionDialogState,
    tileCount: Int,
    onGoTo: (Int) -> Unit,
) {
    if (state.level != WidgetEditorLevel.TILE) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(
            onClick = rememberWrappedOnClick {
                onGoTo(minOf(state.editingIndex - 1, tileCount - 1))
            },
            enabled = state.editingIndex > 0,
        ) {
            Text("‹", style = MaterialTheme.typography.tboxHeadline)
        }
        Text(
            text = stringResource(R.string.widget_editor_tile_of, state.editingIndex + 1, tileCount),
            style = MaterialTheme.typography.tboxTitle,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        OutlinedButton(
            onClick = rememberWrappedOnClick { onGoTo(state.editingIndex + 1) },
            enabled = state.editingIndex < tileCount - 1,
        ) {
            Text("›", style = MaterialTheme.typography.tboxHeadline)
        }
    }
}

@Composable
private fun ClipboardCard(
    state: WidgetSelectionDialogState,
    currentWidgetConfigs: List<FloatingDashboardWidgetConfig>,
) {
    val wholePanel = state.level == WidgetEditorLevel.PANEL
    EditorCard(
        title = stringResource(
            if (wholePanel) R.string.widget_editor_clipboard_panel else R.string.widget_editor_clipboard_tile
        ),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (wholePanel) {
                EditorToggleButton(
                    text = stringResource(R.string.action_copy),
                    selected = false,
                    onClick = {
                        WidgetDialogClipboard.copyPanel(
                            state.toWholePanelClipboardSnapshot(
                                currentWidgetConfigs = currentWidgetConfigs,
                                widgetIndex = state.editingIndex,
                            )
                        )
                    },
                    compact = true,
                    modifier = Modifier.weight(1f),
                )
                EditorToggleButton(
                    text = stringResource(R.string.action_paste),
                    selected = false,
                    onClick = {
                        WidgetDialogClipboard.panelSnapshot?.let {
                            state.applyWholePanelFromClipboard(it, widgetIndex = state.editingIndex)
                        }
                    },
                    enabled = WidgetDialogClipboard.hasPanel,
                    compact = true,
                    modifier = Modifier.weight(1f),
                )
            } else {
                EditorToggleButton(
                    text = stringResource(R.string.action_copy),
                    selected = false,
                    onClick = { WidgetDialogClipboard.copyTile(state.toTileClipboardSnapshot()) },
                    compact = true,
                    modifier = Modifier.weight(1f),
                )
                EditorToggleButton(
                    text = stringResource(R.string.action_paste),
                    selected = false,
                    onClick = {
                        WidgetDialogClipboard.tileSnapshot?.let {
                            state.applyTileClipboardSnapshot(it, preserveDataKey = false)
                        }
                    },
                    enabled = WidgetDialogClipboard.hasTile,
                    compact = true,
                    modifier = Modifier.weight(1f),
                )
                EditorToggleButton(
                    text = stringResource(R.string.widget_paste_without_type),
                    selected = false,
                    onClick = {
                        WidgetDialogClipboard.tileSnapshot?.let {
                            state.applyTileClipboardSnapshot(it, preserveDataKey = true)
                        }
                    },
                    enabled = WidgetDialogClipboard.hasTile && state.selectedDataKey.isNotEmpty(),
                    compact = true,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun TypeDescriptionCard(dataKey: String) {
    val resources = resolveWidgetSelectionDescriptionResources(dataKey, dataKey) ?: return
    EditorCard(title = stringResource(R.string.widget_editor_type_description)) {
        Text(
            text = stringResource(resources.descriptionRes),
            style = MaterialTheme.typography.tboxBody,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        resources.actionsRes?.let { actionsRes ->
            Text(
                text = stringResource(R.string.widget_actions_template, stringResource(actionsRes)),
                style = MaterialTheme.typography.tboxCaption,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
