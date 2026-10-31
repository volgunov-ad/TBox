package vad.dashing.tbox.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.FloatingDashboardWidgetConfig
import vad.dashing.tbox.MAX_WIDGET_PADDING_PERCENT
import vad.dashing.tbox.MIN_WIDGET_PADDING_PERCENT
import vad.dashing.tbox.R
import vad.dashing.tbox.SettingsViewModel
import vad.dashing.tbox.WIDGET_FONT_WEIGHT_MEDIUM
import vad.dashing.tbox.WIDGET_FONT_WEIGHT_NORMAL
import vad.dashing.tbox.WIDGET_FONT_WEIGHT_SEMI_BOLD
import vad.dashing.tbox.WIDGET_TEXT_ALIGN_CENTER
import vad.dashing.tbox.WIDGET_TEXT_ALIGN_END
import vad.dashing.tbox.WIDGET_TEXT_ALIGN_START
import vad.dashing.tbox.WidgetTypeSectionId
import vad.dashing.tbox.WidgetTypeSections
import vad.dashing.tbox.WidgetsRepository
import vad.dashing.tbox.normalizeWidgetControlPadding
import vad.dashing.tbox.normalizeWidgetControlShape
import vad.dashing.tbox.normalizeWidgetFontWeight
import vad.dashing.tbox.normalizeWidgetPaddingPercent
import vad.dashing.tbox.normalizeWidgetScale
import vad.dashing.tbox.normalizeWidgetShape
import vad.dashing.tbox.normalizeWidgetTextAlign
import vad.dashing.tbox.ui.theme.tboxBody
import vad.dashing.tbox.ui.theme.tboxButton
import vad.dashing.tbox.ui.theme.tboxCaption
import vad.dashing.tbox.ui.theme.tboxTitle
import java.util.Locale

private const val TYPE_RAIL_RECENT = "recent"
private const val TYPE_RAIL_ON_PANEL = "on_panel"

/** «Тип» tab: section rail, search across all types and a card grid. */
@Composable
internal fun TileTypeTab(
    state: WidgetSelectionDialogState,
    settingsViewModel: SettingsViewModel,
    dataKeyFilter: (String) -> Boolean,
    panelTileConfigs: List<FloatingDashboardWidgetConfig>,
    externalAppWidgetId: Int?,
    onPickExternalWidget: () -> Unit,
) {
    val context = LocalContext.current
    val noTboxConnect by settingsViewModel.noTboxConnect.collectAsStateWithLifecycle()
    val climateControlType by settingsViewModel.climateControlType.collectAsStateWithLifecycle()
    val cruiseControlTypeSetting by settingsViewModel.cruiseControlType.collectAsStateWithLifecycle()
    val widgetPairs = remember(noTboxConnect, climateControlType, cruiseControlTypeSetting, dataKeyFilter) {
        WidgetsRepository.getAvailableDataKeysWidgets(
            noTboxConnect = noTboxConnect,
            climateType = climateControlType,
            cruiseType = cruiseControlTypeSetting,
        )
            .filter { it.isNotEmpty() && dataKeyFilter(it) }
            .map { key -> key to WidgetsRepository.getTitleUnitForDataKey(context, key) }
    }
    val availableKeys = remember(widgetPairs) { widgetPairs.map { it.first }.toSet() }
    val recentKeys = WidgetEditorRecentTypes.keys.filter { it in availableKeys }
    val onPanelKeys = tileTypesOnPanel(panelTileConfigs).filter { it in availableKeys }
    var filterText by rememberSaveable { mutableStateOf("") }
    val needle = filterText.trim().lowercase()
    val searching = needle.isNotEmpty()
    val sections = remember(widgetPairs) {
        WidgetTypeSectionId.entries.filter { section ->
            widgetPairs.any { WidgetTypeSections.sectionFor(it.first) == section }
        }
    }
    val defaultRail = remember(widgetPairs) {
        WidgetTypeSections.sectionFor(state.selectedDataKey)?.name
            ?: sections.firstOrNull()?.name
            ?: TYPE_RAIL_RECENT
    }
    var railId by rememberSaveable { mutableStateOf(defaultRail) }

    fun matches(pair: Pair<String, String>): Boolean {
        if (!searching) return true
        val description = WidgetsRepository.getDescriptionResForDataKey(pair.first)
            ?.let(context::getString).orEmpty()
        val actions = WidgetsRepository.getActionsDescriptionResForDataKey(pair.first)
            ?.let(context::getString).orEmpty()
        return pair.second.lowercase().contains(needle) ||
            pair.first.lowercase().contains(needle) ||
            description.lowercase().contains(needle) ||
            actions.lowercase().contains(needle)
    }

    val shownPairs = when {
        searching -> widgetPairs.filter(::matches).sortedBy { it.second }
        railId == TYPE_RAIL_RECENT -> recentKeys.mapNotNull { k -> widgetPairs.find { it.first == k } }
        railId == TYPE_RAIL_ON_PANEL -> onPanelKeys.mapNotNull { k -> widgetPairs.find { it.first == k } }
        else -> widgetPairs
            .filter { WidgetTypeSections.sectionFor(it.first)?.name == railId }
            .sortedBy { it.second }
    }
    val selectType: (String) -> Unit = { key ->
        state.applySelectedDataKey(key)
        WidgetEditorRecentTypes.record(key)
    }

    Row(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .width(220.dp)
                .fillMaxHeight()
                .padding(end = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (recentKeys.isNotEmpty()) {
                item(TYPE_RAIL_RECENT) {
                    TypeRailItem(
                        text = stringResource(R.string.widget_editor_section_recent),
                        count = recentKeys.size,
                        selected = !searching && railId == TYPE_RAIL_RECENT,
                        onClick = { filterText = ""; railId = TYPE_RAIL_RECENT },
                    )
                }
            }
            if (onPanelKeys.isNotEmpty()) {
                item(TYPE_RAIL_ON_PANEL) {
                    TypeRailItem(
                        text = stringResource(R.string.widget_editor_section_on_panel),
                        count = onPanelKeys.size,
                        selected = !searching && railId == TYPE_RAIL_ON_PANEL,
                        onClick = { filterText = ""; railId = TYPE_RAIL_ON_PANEL },
                    )
                }
            }
            items(sections, key = { it.name }) { section ->
                TypeRailItem(
                    text = stringResource(section.titleRes),
                    count = widgetPairs.count { WidgetTypeSections.sectionFor(it.first) == section },
                    selected = !searching && railId == section.name,
                    onClick = { filterText = ""; railId = section.name },
                )
            }
        }
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            OutlinedTextField(
                value = filterText,
                onValueChange = { filterText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                textStyle = MaterialTheme.typography.tboxTitle,
                label = {
                    Text(
                        text = stringResource(R.string.widget_editor_search_hint),
                        style = MaterialTheme.typography.tboxBody,
                    )
                },
                singleLine = true,
            )
            CurrentTypeRow(
                state = state,
                widgetPairs = widgetPairs,
                onClear = { state.applySelectedDataKey("") },
                onRestore = { selectType(state.loadedTileDataKey) },
            )
            if (state.isExternalAppWidgetSelected) {
                ExternalAppWidgetPickerSection(
                    appWidgetId = externalAppWidgetId,
                    onPickClick = onPickExternalWidget,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            if (shownPairs.isEmpty()) {
                Text(
                    text = stringResource(R.string.widget_editor_no_results),
                    style = MaterialTheme.typography.tboxBody,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 200.dp),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(shownPairs, key = { it.first }) { (key, title) ->
                        TypeCard(
                            title = title,
                            selected = key == state.selectedDataKey,
                            onClick = { selectType(key) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TypeRailItem(text: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        } else {
            Color.Transparent
        },
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickableWithSound(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.tboxBody,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.tboxCaption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TypeCard(title: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        },
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickableWithSound(onClick = onClick),
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.tboxBody,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CurrentTypeRow(
    state: WidgetSelectionDialogState,
    widgetPairs: List<Pair<String, String>>,
    onClear: () -> Unit,
    onRestore: () -> Unit,
) {
    val context = LocalContext.current
    fun titleFor(key: String): String = when {
        key.isEmpty() -> context.getString(R.string.widget_option_not_selected)
        else -> widgetPairs.find { it.first == key }?.second
            ?: WidgetsRepository.getTitleUnitForDataKey(context, key)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.widget_editor_current_type, titleFor(state.selectedDataKey)),
                style = MaterialTheme.typography.tboxTitle,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (state.selectedDataKey != state.loadedTileDataKey) {
                Text(
                    text = stringResource(R.string.widget_editor_previous_type, titleFor(state.loadedTileDataKey)),
                    style = MaterialTheme.typography.tboxCaption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (state.selectedDataKey != state.loadedTileDataKey) {
            OutlinedButton(onClick = rememberWrappedOnClick(onRestore)) {
                Text(stringResource(R.string.widget_editor_restore_type), style = MaterialTheme.typography.tboxButton)
            }
        }
        OutlinedButton(
            onClick = rememberWrappedOnClick(onClear),
            enabled = state.selectedDataKey.isNotEmpty(),
        ) {
            Text(stringResource(R.string.widget_editor_clear_type), style = MaterialTheme.typography.tboxButton)
        }
    }
}

@Composable
private fun SelectTypeFirstHint() {
    Text(
        text = stringResource(R.string.widget_editor_select_type_first),
        style = MaterialTheme.typography.tboxBody,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 12.dp),
    )
}

/** «Вид» tab: title/value, scales, typography, shape, paddings, control geometry. */
@Composable
internal fun TileViewTab(state: WidgetSelectionDialogState) {
    val enabled = state.togglesEnabled
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        if (!enabled) SelectTypeFirstHint()
        EditorCard(title = stringResource(R.string.widget_editor_card_title_value)) {
            TileTitleAndValueSettings(state)
        }
        EditorCard(
            title = stringResource(R.string.widget_editor_card_size),
            description = stringResource(R.string.widget_scale_hint),
        ) {
            EditorScaleStepper(
                label = stringResource(R.string.widget_editor_scale_title),
                value = state.titleScale,
                onValueChange = { state.titleScale = normalizeWidgetScale(it) },
                enabled = enabled,
            )
            EditorScaleStepper(
                label = stringResource(R.string.widget_editor_scale_icons),
                value = state.iconScale,
                onValueChange = { state.iconScale = normalizeWidgetScale(it) },
                enabled = enabled,
            )
            EditorScaleStepper(
                label = stringResource(R.string.widget_editor_scale_text),
                value = state.textScale,
                onValueChange = { state.textScale = normalizeWidgetScale(it) },
                enabled = enabled,
            )
        }
        EditorCard(title = stringResource(R.string.widget_editor_card_typography)) {
            EditorChoiceChips(
                label = stringResource(R.string.widget_font_weight_title),
                options = listOf(
                    WIDGET_FONT_WEIGHT_NORMAL to stringResource(R.string.widget_font_weight_normal),
                    WIDGET_FONT_WEIGHT_MEDIUM to stringResource(R.string.widget_font_weight_medium),
                    WIDGET_FONT_WEIGHT_SEMI_BOLD to stringResource(R.string.widget_font_weight_semi_bold),
                ),
                selected = normalizeWidgetFontWeight(state.fontWeight),
                onSelect = { state.fontWeight = it },
                enabled = enabled,
            )
            EditorChoiceChips(
                label = stringResource(R.string.widget_text_align_title),
                options = listOf(
                    WIDGET_TEXT_ALIGN_START to stringResource(R.string.widget_text_align_start),
                    WIDGET_TEXT_ALIGN_CENTER to stringResource(R.string.widget_text_align_center),
                    WIDGET_TEXT_ALIGN_END to stringResource(R.string.widget_text_align_end),
                ),
                selected = normalizeWidgetTextAlign(state.textAlign),
                onSelect = { state.textAlign = it },
                enabled = enabled,
            )
        }
        EditorCard(
            title = stringResource(R.string.widget_editor_card_shape),
            description = stringResource(R.string.widget_shape_hint),
        ) {
            EditorIntStepper(
                label = stringResource(R.string.widget_editor_shape_radius),
                value = state.shape,
                onValueChange = { state.shape = normalizeWidgetShape(it) },
                minValue = 0,
                maxValue = 50,
                enabled = enabled,
                presets = listOf(0, 8, 12, 16, 24, 50).map { it to it.toString() },
            )
        }
        TilePaddingCard(state, enabled)
        TileControlGeometryCard(state, enabled)
    }
}

@Composable
private fun TilePaddingCard(state: WidgetSelectionDialogState, enabled: Boolean) {
    val allEqual = state.paddingTopPercent == state.paddingBottomPercent &&
        state.paddingTopPercent == state.paddingStartPercent &&
        state.paddingTopPercent == state.paddingEndPercent
    var linked by remember(state.editingIndex) { mutableStateOf(allEqual) }
    EditorCard(
        title = stringResource(R.string.widget_editor_card_padding),
        description = stringResource(R.string.widget_padding_hint),
    ) {
        SettingSwitch(
            linked,
            { checked ->
                linked = checked
                if (checked) {
                    val value = state.paddingTopPercent
                    state.paddingBottomPercent = value
                    state.paddingStartPercent = value
                    state.paddingEndPercent = value
                }
            },
            stringResource(R.string.widget_editor_padding_linked),
            "",
            enabled,
        )
        if (linked) {
            EditorIntStepper(
                label = stringResource(R.string.widget_editor_padding_all),
                value = state.paddingTopPercent,
                onValueChange = {
                    val value = normalizeWidgetPaddingPercent(it)
                    state.paddingTopPercent = value
                    state.paddingBottomPercent = value
                    state.paddingStartPercent = value
                    state.paddingEndPercent = value
                },
                minValue = MIN_WIDGET_PADDING_PERCENT,
                maxValue = MAX_WIDGET_PADDING_PERCENT,
                suffix = "%",
                enabled = enabled,
            )
        } else {
            EditorIntStepper(
                label = stringResource(R.string.widget_editor_padding_top),
                value = state.paddingTopPercent,
                onValueChange = { state.paddingTopPercent = normalizeWidgetPaddingPercent(it) },
                minValue = MIN_WIDGET_PADDING_PERCENT,
                maxValue = MAX_WIDGET_PADDING_PERCENT,
                suffix = "%",
                enabled = enabled,
            )
            EditorIntStepper(
                label = stringResource(R.string.widget_editor_padding_bottom),
                value = state.paddingBottomPercent,
                onValueChange = { state.paddingBottomPercent = normalizeWidgetPaddingPercent(it) },
                minValue = MIN_WIDGET_PADDING_PERCENT,
                maxValue = MAX_WIDGET_PADDING_PERCENT,
                suffix = "%",
                enabled = enabled,
            )
            EditorIntStepper(
                label = stringResource(R.string.widget_editor_padding_start),
                value = state.paddingStartPercent,
                onValueChange = { state.paddingStartPercent = normalizeWidgetPaddingPercent(it) },
                minValue = MIN_WIDGET_PADDING_PERCENT,
                maxValue = MAX_WIDGET_PADDING_PERCENT,
                suffix = "%",
                enabled = enabled,
            )
            EditorIntStepper(
                label = stringResource(R.string.widget_editor_padding_end),
                value = state.paddingEndPercent,
                onValueChange = { state.paddingEndPercent = normalizeWidgetPaddingPercent(it) },
                minValue = MIN_WIDGET_PADDING_PERCENT,
                maxValue = MAX_WIDGET_PADDING_PERCENT,
                suffix = "%",
                enabled = enabled,
            )
        }
    }
}

@Composable
private fun TileControlGeometryCard(state: WidgetSelectionDialogState, enabled: Boolean) {
    val kind = controlAppearanceKindForDataKey(state.selectedDataKey)
    val defaultShape = defaultControlShapeDpForKind(kind)
    val defaultPadding = defaultControlPaddingDpForDataKey(state.selectedDataKey)
    EditorCard(
        title = stringResource(R.string.widget_control_colors_section),
        description = stringResource(R.string.widget_editor_controls_geometry_desc),
    ) {
        EditorIntStepper(
            label = stringResource(R.string.widget_editor_control_shape),
            value = state.controlShape ?: defaultShape,
            onValueChange = { state.controlShape = normalizeWidgetControlShape(it) },
            minValue = 0,
            maxValue = 50,
            enabled = enabled,
            hint = stringResource(R.string.widget_control_shape_hint),
        )
        DefaultValueButton(
            isDefault = state.controlShape == null,
            defaultValue = defaultShape,
            enabled = enabled,
            onReset = { state.controlShape = null },
        )
        EditorIntStepper(
            label = stringResource(R.string.widget_editor_control_padding),
            value = state.controlPadding ?: defaultPadding,
            onValueChange = { state.controlPadding = normalizeWidgetControlPadding(it) },
            minValue = 0,
            maxValue = 50,
            enabled = enabled,
            hint = stringResource(R.string.widget_control_padding_hint),
        )
        DefaultValueButton(
            isDefault = state.controlPadding == null,
            defaultValue = defaultPadding,
            enabled = enabled,
            onReset = { state.controlPadding = null },
        )
    }
}

@Composable
private fun DefaultValueButton(isDefault: Boolean, defaultValue: Int, enabled: Boolean, onReset: () -> Unit) {
    EditorToggleButton(
        text = stringResource(R.string.widget_editor_default_value, defaultValue),
        selected = isDefault,
        onClick = onReset,
        enabled = enabled,
        compact = true,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

/** Cell of the day/night color tables on the «Цвета» tab. */
internal enum class TileColorRow(val isControl: Boolean, val active: Boolean, val background: Boolean) {
    TEXT(false, false, false),
    BACKGROUND(false, false, true),
    CONTROL_INACTIVE(true, false, false),
    CONTROL_INACTIVE_BACKGROUND(true, false, true),
    CONTROL_ACTIVE(true, true, false),
    CONTROL_ACTIVE_BACKGROUND(true, true, true),
}

private fun WidgetSelectionDialogState.colorFor(row: TileColorRow, dark: Boolean): Int = when (row) {
    TileColorRow.TEXT -> if (dark) textColorDark else textColorLight
    TileColorRow.BACKGROUND -> if (dark) backgroundColorDark else backgroundColorLight
    else -> controlColor(active = row.active, background = row.background, dark = dark)
}

private fun WidgetSelectionDialogState.setColorFor(row: TileColorRow, dark: Boolean, color: Int) {
    when (row) {
        TileColorRow.TEXT -> if (dark) textColorDark = color else textColorLight = color
        TileColorRow.BACKGROUND -> if (dark) backgroundColorDark = color else backgroundColorLight = color
        else -> setControlColor(active = row.active, background = row.background, dark = dark, color = color)
    }
}

@Composable
private fun tileColorRowLabel(row: TileColorRow): String = stringResource(
    when (row) {
        TileColorRow.TEXT -> R.string.widget_editor_row_text
        TileColorRow.BACKGROUND -> R.string.widget_editor_row_background
        TileColorRow.CONTROL_INACTIVE -> R.string.widget_editor_row_control_inactive
        TileColorRow.CONTROL_INACTIVE_BACKGROUND -> R.string.widget_editor_row_control_inactive_background
        TileColorRow.CONTROL_ACTIVE -> R.string.widget_editor_row_control_active
        TileColorRow.CONTROL_ACTIVE_BACKGROUND -> R.string.widget_editor_row_control_active_background
    }
)

/** «Цвета» tab: day/night tables for tile and control colors, tile background images. */
@Composable
internal fun TileColorsTab(
    state: WidgetSelectionDialogState,
    settingsViewModel: SettingsViewModel,
    tileBackgroundPanelStorageId: String,
    tileBackgroundSlot: Int,
) {
    val enabled = state.togglesEnabled
    val presetSlots by settingsViewModel.widgetColorPresetSlots.collectAsStateWithLifecycle()
    var selectedRow by remember { mutableStateOf(TileColorRow.TEXT) }
    val selectedDark = state.previewDark
    val selectCell: (TileColorRow, Boolean) -> Unit = { row, dark ->
        selectedRow = row
        state.previewDark = dark
    }
    val musicStepperBgArgb = MaterialTheme.colorScheme.surfaceVariant
        .copy(alpha = DEFAULT_MUSIC_STEPPER_CONTROL_BG_ALPHA)
        .toArgb()

    @Composable
    fun SelectedColorEditor(rowsOfCard: List<TileColorRow>, editorEnabled: Boolean) {
        if (selectedRow !in rowsOfCard) return
        key(selectedRow, selectedDark, state.editingIndex, state.controlAppearanceEpoch) {
            WidgetColorSetting(
                title = stringResource(
                    R.string.widget_editor_editing_color,
                    tileColorRowLabel(selectedRow),
                    stringResource(if (selectedDark) R.string.widget_editor_night else R.string.widget_editor_day),
                ),
                colorValue = state.colorFor(selectedRow, selectedDark),
                enabled = editorEnabled,
                onColorChange = { state.setColorFor(selectedRow, selectedDark, it) },
                presetSlots = presetSlots,
                onPresetSlotColorSave = settingsViewModel::saveWidgetColorPresetSlot,
                valueTextStyle = MaterialTheme.typography.tboxTitle,
                valueLabelStyle = MaterialTheme.typography.tboxBody,
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        if (!enabled) SelectTypeFirstHint()
        val tileRows = listOf(TileColorRow.TEXT, TileColorRow.BACKGROUND)
        EditorCard(
            title = stringResource(R.string.widget_editor_card_tile_colors),
            description = stringResource(R.string.widget_editor_color_table_hint),
        ) {
            DayNightColorTable(
                rows = tileRows,
                colorFor = { row, dark -> state.colorFor(row, dark) },
                selectedRow = selectedRow,
                selectedDark = selectedDark,
                enabled = enabled,
                onSelect = selectCell,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                EditorToggleButton(
                    text = stringResource(R.string.widget_editor_copy_day_to_night),
                    selected = false,
                    onClick = { state.copyTileColorsBetweenThemes(toDark = true) },
                    enabled = enabled,
                    compact = true,
                    modifier = Modifier.weight(1f),
                )
                EditorToggleButton(
                    text = stringResource(R.string.widget_editor_copy_night_to_day),
                    selected = false,
                    onClick = { state.copyTileColorsBetweenThemes(toDark = false) },
                    enabled = enabled,
                    compact = true,
                    modifier = Modifier.weight(1f),
                )
                EditorToggleButton(
                    text = stringResource(R.string.widget_reset_text_background_colors),
                    selected = false,
                    onClick = { state.resetTileTextAndBackgroundColors() },
                    enabled = enabled,
                    compact = true,
                    modifier = Modifier.weight(1f),
                )
            }
            SelectedColorEditor(tileRows, enabled)
        }
        EditorCard(
            title = stringResource(R.string.widget_tile_background_image_title),
            description = stringResource(R.string.widget_editor_bg_image_desc),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                TileBackgroundImageSettingsSection(
                    state = state,
                    settingsViewModel = settingsViewModel,
                    panelStorageId = tileBackgroundPanelStorageId,
                    widgetIndex = tileBackgroundSlot,
                    darkTheme = false,
                    title = stringResource(R.string.widget_editor_day),
                    modifier = Modifier.weight(1f),
                )
                TileBackgroundImageSettingsSection(
                    state = state,
                    settingsViewModel = settingsViewModel,
                    panelStorageId = tileBackgroundPanelStorageId,
                    widgetIndex = tileBackgroundSlot,
                    darkTheme = true,
                    title = stringResource(R.string.widget_editor_night),
                    modifier = Modifier.weight(1f),
                )
            }
        }
        val controlRows = listOf(
            TileColorRow.CONTROL_INACTIVE,
            TileColorRow.CONTROL_INACTIVE_BACKGROUND,
            TileColorRow.CONTROL_ACTIVE,
            TileColorRow.CONTROL_ACTIVE_BACKGROUND,
        )
        EditorCard(title = stringResource(R.string.widget_control_colors_section)) {
            SettingSwitch(
                state.controlColorsUseDefaults,
                { useDefaults ->
                    if (useDefaults) {
                        state.clearControlColorsToDefaults()
                    } else {
                        state.applyControlColorSeed(
                            seedControlColorsFromDefaults(
                                kind = controlAppearanceKindForDataKey(state.selectedDataKey),
                                tileTextColorLight = state.textColorLight,
                                tileTextColorDark = state.textColorDark,
                                musicStepperBgArgb = musicStepperBgArgb,
                                dataKey = state.selectedDataKey,
                            )
                        )
                    }
                    state.controlAppearanceEpoch++
                },
                stringResource(R.string.widget_control_colors_use_defaults),
                "",
                enabled,
            )
            val controlEditorsEnabled = enabled && !state.controlColorsUseDefaults
            DayNightColorTable(
                rows = controlRows,
                colorFor = { row, dark -> state.colorFor(row, dark) },
                selectedRow = selectedRow,
                selectedDark = selectedDark,
                enabled = controlEditorsEnabled,
                onSelect = selectCell,
            )
            SelectedColorEditor(controlRows, controlEditorsEnabled)
        }
    }
}

@Composable
private fun DayNightColorTable(
    rows: List<TileColorRow>,
    colorFor: (TileColorRow, Boolean) -> Int,
    selectedRow: TileColorRow,
    selectedDark: Boolean,
    enabled: Boolean,
    onSelect: (TileColorRow, Boolean) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.weight(1.4f))
            listOf(R.string.widget_editor_day, R.string.widget_editor_night).forEach { res ->
                Text(
                    text = stringResource(res),
                    style = MaterialTheme.typography.tboxCaption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        rows.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = tileColorRowLabel(row),
                    style = MaterialTheme.typography.tboxBody,
                    modifier = Modifier.weight(1.4f),
                )
                listOf(false, true).forEach { dark ->
                    ColorCell(
                        color = colorFor(row, dark),
                        selected = row == selectedRow && dark == selectedDark,
                        enabled = enabled,
                        onClick = { onSelect(row, dark) },
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ColorCell(
    color: Int,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .background(Color(color).copy(alpha = if (enabled) Color(color).alpha else Color(color).alpha * 0.4f))
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                shape = shape,
            )
            .clickableWithSound(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = String.format(Locale.US, "#%08X", color),
            style = MaterialTheme.typography.tboxCaption,
            color = if (Color(color).luminanceForLabel() > 0.5f) Color.Black else Color.White,
            maxLines = 1,
        )
    }
}

private fun Color.luminanceForLabel(): Float =
    if (alpha < 0.35f) 1f else 0.2126f * red + 0.7152f * green + 0.0722f * blue

/** «Поведение» tab: options that depend on the tile type. */
@Composable
internal fun TileBehaviorTab(
    state: WidgetSelectionDialogState,
    settingsViewModel: SettingsViewModel,
    tileBackgroundPanelStorageId: String,
    externalAppWidgetId: Int?,
    onPickExternalWidget: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        when {
            !state.togglesEnabled -> SelectTypeFirstHint()
            !tileHasBehaviorSettings(state.selectedDataKey) -> Text(
                text = stringResource(R.string.widget_editor_behavior_empty),
                style = MaterialTheme.typography.tboxBody,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> EditorCard(title = null) {
                if (state.isExternalAppWidgetSelected) {
                    ExternalAppWidgetPickerSection(
                        appWidgetId = externalAppWidgetId,
                        onPickClick = onPickExternalWidget,
                    )
                }
                TileBehaviorSettings(
                    state = state,
                    settingsViewModel = settingsViewModel,
                    tileBackgroundPanelStorageId = tileBackgroundPanelStorageId,
                    widgetIndex = state.editingIndex,
                )
            }
        }
    }
}
