package vad.dashing.tbox.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import android.appwidget.AppWidgetManager
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Context
import vad.dashing.tbox.APP_LAUNCHER_WIDGET_DATA_KEY
import vad.dashing.tbox.DashboardManager
import vad.dashing.tbox.DashboardWidget
import vad.dashing.tbox.FloatingDashboardWidgetConfig
import vad.dashing.tbox.MUSIC_WIDGET_DATA_KEY
import vad.dashing.tbox.R
import vad.dashing.tbox.ExternalWidgetHostManager
import vad.dashing.tbox.WidgetPickerActivity
import vad.dashing.tbox.WidgetsRepository
import vad.dashing.tbox.normalizeWidgetConfigs
import vad.dashing.tbox.normalizeWidgetShape
import vad.dashing.tbox.normalizeWidgetScale
import vad.dashing.tbox.resolveSelectedMediaPlayerForWidget
import vad.dashing.tbox.ui.theme.DARK_THEME_BACKGROUND_COLOR_PRESET_1_INT
import vad.dashing.tbox.ui.theme.DARK_THEME_BACKGROUND_COLOR_PRESET_2_INT
import vad.dashing.tbox.ui.theme.DARK_THEME_TEXT_COLOR_PRESET_1_INT
import vad.dashing.tbox.ui.theme.DARK_THEME_TEXT_COLOR_PRESET_2_INT
import vad.dashing.tbox.ui.theme.LIGHT_THEME_BACKGROUND_COLOR_PRESET_1_INT
import vad.dashing.tbox.ui.theme.LIGHT_THEME_BACKGROUND_COLOR_PRESET_2_INT
import vad.dashing.tbox.ui.theme.LIGHT_THEME_TEXT_COLOR_PRESET_1_INT
import vad.dashing.tbox.ui.theme.LIGHT_THEME_TEXT_COLOR_PRESET_2_INT

internal class WidgetSelectionDialogState(
    initialDataKey: String,
    initialConfig: FloatingDashboardWidgetConfig,
    defaultBackgroundLight: Int,
    defaultBackgroundDark: Int
) {
    var selectedDataKey by mutableStateOf(initialDataKey)
    var showTitle by mutableStateOf(initialConfig.showTitle)
    var showUnit by mutableStateOf(initialConfig.showUnit)
    var singleLineDualMetrics by mutableStateOf(
        initialConfig.singleLineDualMetrics &&
            WidgetsRepository.supportsSingleLineDualMetrics(initialConfig.dataKey)
    )
    var scale by mutableFloatStateOf(normalizeWidgetScale(initialConfig.scale))
    var shape by mutableIntStateOf(normalizeWidgetShape(initialConfig.shape))
    var textColorLight by mutableIntStateOf(initialConfig.textColorLight)
    var textColorDark by mutableIntStateOf(initialConfig.textColorDark)
    var backgroundColorLight by mutableIntStateOf(
        initialConfig.backgroundColorLight ?: defaultBackgroundLight
    )
    var backgroundColorDark by mutableIntStateOf(
        initialConfig.backgroundColorDark ?: defaultBackgroundDark
    )
    var selectedMediaPlayers by mutableStateOf(
        if (initialConfig.dataKey == MUSIC_WIDGET_DATA_KEY) {
            normalizeMediaPlayersSelection(initialConfig.mediaPlayers)
        } else {
            emptySet()
        }
    )
    val selectedMediaPlayer: String = resolveSelectedMediaPlayerForWidget(initialConfig)
    var mediaAutoPlayOnInit by mutableStateOf(initialConfig.mediaAutoPlayOnInit)
    var mediaAutoPlayOnlyWhenEngineRunning by mutableStateOf(
        initialConfig.mediaAutoPlayOnlyWhenEngineRunning
    )
    var showAdvancedSettings by mutableStateOf(false)
    var launcherAppPackage by mutableStateOf(
        if (initialConfig.dataKey == APP_LAUNCHER_WIDGET_DATA_KEY) {
            initialConfig.launcherAppPackage
        } else {
            ""
        }
    )

    val isMusicWidgetSelected: Boolean
        get() = selectedDataKey == MUSIC_WIDGET_DATA_KEY

    val isAppLauncherWidgetSelected: Boolean
        get() = selectedDataKey == APP_LAUNCHER_WIDGET_DATA_KEY

    val isExternalAppWidgetSelected: Boolean
        get() = selectedDataKey == WidgetsRepository.EXTERNAL_WIDGET_DATA_KEY

    val togglesEnabled: Boolean
        get() = selectedDataKey.isNotEmpty()

    val canSaveSelection: Boolean
        get() = when {
            selectedDataKey.isEmpty() -> true
            isMusicWidgetSelected -> selectedMediaPlayers.isNotEmpty()
            isAppLauncherWidgetSelected -> launcherAppPackage.isNotBlank()
            else -> true
        }
}

@Composable
internal fun rememberWidgetSelectionDialogState(
    widgetIndex: Int,
    currentWidgets: List<DashboardWidget>,
    currentWidgetConfigs: List<FloatingDashboardWidgetConfig>,
    defaultBackgroundLight: Int,
    defaultBackgroundDark: Int
): WidgetSelectionDialogState {
    val initialConfig = currentWidgetConfigs.getOrNull(widgetIndex)
        ?: FloatingDashboardWidgetConfig(dataKey = "")
    val initialDataKey = currentWidgets.getOrNull(widgetIndex)?.dataKey ?: ""
    return remember(
        widgetIndex,
        currentWidgets,
        currentWidgetConfigs,
        defaultBackgroundLight,
        defaultBackgroundDark
    ) {
        WidgetSelectionDialogState(
            initialDataKey = initialDataKey,
            initialConfig = initialConfig,
            defaultBackgroundLight = defaultBackgroundLight,
            defaultBackgroundDark = defaultBackgroundDark
        )
    }
}

@Composable
internal fun ExternalAppWidgetPickerSection(
    appWidgetId: Int?,
    onPickClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appWidgetManager = remember { AppWidgetManager.getInstance(context) }
    val selectedWidgetLabel = remember(appWidgetId) {
        appWidgetId?.let { id ->
            appWidgetManager.getAppWidgetInfo(id)?.loadLabel(context.packageManager)?.toString()
        }.orEmpty()
    }
    val label = if (selectedWidgetLabel.isNotBlank()) {
        selectedWidgetLabel
    } else {
        stringResource(R.string.widget_external_app_not_selected)
    }
    Column(modifier = modifier.padding(top = 8.dp)) {
        SettingsTitle(stringResource(R.string.widget_external_app_title))
        Text(
            text = stringResource(R.string.widget_external_app_selected, label),
            fontSize = 20.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        OutlinedButton(onClick = onPickClick) {
            Text(text = stringResource(R.string.widget_external_app_pick), fontSize = 22.sp)
        }
    }
}

@Composable
internal fun WidgetSelectionDialogForm(
    titleText: String,
    state: WidgetSelectionDialogState,
    modifier: Modifier = Modifier,
    dataKeyFilter: (String) -> Boolean = { true },
    bottomContent: (@Composable () -> Unit)? = null
) {
    val context = LocalContext.current
    val notSelectedLabel = stringResource(R.string.widget_option_not_selected)
    val widgetPairs = WidgetsRepository.getAvailableDataKeysWidgets()
        .filter { it.isNotEmpty() && dataKeyFilter(it) }
        .map { key ->
            key to WidgetsRepository.getTitleUnitForDataKey(context, key)
        }
    val selectedKey = state.selectedDataKey
    val availableOptions = if (selectedKey.isEmpty()) {
        listOf("" to notSelectedLabel) + widgetPairs.sortedBy { it.second }
    } else {
        val selectedPair = widgetPairs.find { it.first == selectedKey }
        val othersSorted = widgetPairs
            .filter { it.first != selectedKey }
            .sortedBy { it.second }
        buildList {
            add("" to notSelectedLabel)
            if (selectedPair != null) add(selectedPair)
            addAll(othersSorted)
        }
    }

    Column(
        modifier = modifier
    ) {
        SettingsTitle(titleText)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (state.showAdvancedSettings) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(androidx.compose.foundation.rememberScrollState())
                        .padding(12.dp)
                ) {
                    if (state.isMusicWidgetSelected) {
                        MediaPlayersInlineSelection(
                            selectedPlayers = state.selectedMediaPlayers,
                            onSelectionChange = { state.selectedMediaPlayers = it },
                            enabled = state.togglesEnabled
                        )
                        if (state.selectedMediaPlayers.isEmpty()) {
                            Text(
                                text = stringResource(R.string.widget_music_players_required),
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 20.sp
                            )
                        }
                        SettingSwitch(
                            state.mediaAutoPlayOnInit,
                            {
                                state.mediaAutoPlayOnInit = it
                                if (!it) {
                                    state.mediaAutoPlayOnlyWhenEngineRunning = false
                                }
                            },
                            stringResource(R.string.widget_music_auto_play_on_init),
                            "",
                            state.togglesEnabled
                        )
                        SettingSwitch(
                            state.mediaAutoPlayOnlyWhenEngineRunning,
                            { state.mediaAutoPlayOnlyWhenEngineRunning = it },
                            stringResource(R.string.widget_music_auto_play_only_engine),
                            "",
                            state.togglesEnabled && state.mediaAutoPlayOnInit
                        )
                    }
                    AppLauncherWidgetSettingsSection(
                        state = state,
                        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                    )
                    SettingSwitch(
                        state.showTitle,
                        { state.showTitle = it },
                        stringResource(R.string.widget_show_title),
                        "",
                        state.togglesEnabled
                    )
                    SettingSwitch(
                        state.showUnit,
                        { state.showUnit = it },
                        stringResource(R.string.widget_show_unit),
                        "",
                        state.togglesEnabled
                    )
                    if (WidgetsRepository.supportsSingleLineDualMetrics(state.selectedDataKey)) {
                        SettingSwitch(
                            state.singleLineDualMetrics,
                            { state.singleLineDualMetrics = it },
                            stringResource(R.string.widget_single_line_dual_metrics),
                            "",
                            state.togglesEnabled
                        )
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.widget_scale, state.scale),
                            fontSize = 24.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.widget_scale_hint),
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Slider(
                            value = state.scale,
                            onValueChange = { newValue ->
                                state.scale = normalizeWidgetScale(newValue)
                            },
                            valueRange = 0.1f..2.0f,
                            steps = 18,
                            enabled = state.togglesEnabled,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.widget_shape, state.shape),
                            fontSize = 24.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.widget_shape_hint),
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Slider(
                            value = state.shape.toFloat(),
                            onValueChange = { newValue ->
                                state.shape = normalizeWidgetShape(newValue.toInt())
                            },
                            valueRange = 0f..50f,
                            steps = 49,
                            enabled = state.togglesEnabled,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                    WidgetTextColorSetting(
                        title = stringResource(R.string.widget_text_color_light),
                        colorValue = state.textColorLight,
                        enabled = state.togglesEnabled,
                        onColorChange = { state.textColorLight = it },
                        presetColors = listOf(
                            LIGHT_THEME_TEXT_COLOR_PRESET_1_INT,
                            LIGHT_THEME_TEXT_COLOR_PRESET_2_INT
                        )
                    )
                    WidgetTextColorSetting(
                        title = stringResource(R.string.widget_text_color_dark),
                        colorValue = state.textColorDark,
                        enabled = state.togglesEnabled,
                        onColorChange = { state.textColorDark = it },
                        presetColors = listOf(
                            DARK_THEME_TEXT_COLOR_PRESET_1_INT,
                            DARK_THEME_TEXT_COLOR_PRESET_2_INT
                        )
                    )
                    WidgetTextColorSetting(
                        title = stringResource(R.string.widget_background_color_light),
                        colorValue = state.backgroundColorLight,
                        enabled = state.togglesEnabled,
                        onColorChange = { state.backgroundColorLight = it },
                        presetColors = listOf(
                            LIGHT_THEME_BACKGROUND_COLOR_PRESET_1_INT,
                            LIGHT_THEME_BACKGROUND_COLOR_PRESET_2_INT
                        )
                    )
                    WidgetTextColorSetting(
                        title = stringResource(R.string.widget_background_color_dark),
                        colorValue = state.backgroundColorDark,
                        enabled = state.togglesEnabled,
                        onColorChange = { state.backgroundColorDark = it },
                        presetColors = listOf(
                            DARK_THEME_BACKGROUND_COLOR_PRESET_1_INT,
                            DARK_THEME_BACKGROUND_COLOR_PRESET_2_INT
                        )
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(androidx.compose.foundation.rememberScrollState())
                        .padding(12.dp)
                ) {
                    availableOptions.forEach { (key, displayName) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    state.selectedDataKey = key
                                    if (!WidgetsRepository.supportsSingleLineDualMetrics(key)) {
                                        state.singleLineDualMetrics = false
                                    }
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = state.selectedDataKey == key,
                                onClick = {
                                    state.selectedDataKey = key
                                    if (!WidgetsRepository.supportsSingleLineDualMetrics(key)) {
                                        state.singleLineDualMetrics = false
                                    }
                                }
                            )
                            Text(
                                text = displayName,
                                fontSize = 24.sp,
                                modifier = Modifier
                                    .padding(start = 8.dp)
                                    .weight(1f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
        bottomContent?.invoke()
    }
}

@Composable
internal fun WidgetSelectionDialogActions(
    state: WidgetSelectionDialogState,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    saveTextFontWeight: FontWeight = FontWeight.Normal,
    leadingExtra: (@Composable RowScope.() -> Unit)? = null
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        leadingExtra?.invoke(this)
        if (leadingExtra != null) {
            Spacer(modifier = Modifier.width(8.dp))
        }
        OutlinedButton(
            onClick = { state.showAdvancedSettings = !state.showAdvancedSettings },
            border = BorderStroke(
                width = 1.dp,
                color = if (state.showAdvancedSettings) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline
                }
            ),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = if (state.showAdvancedSettings) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                } else {
                    Color.Transparent
                },
                contentColor = if (state.showAdvancedSettings) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        ) {
            Text(text = stringResource(R.string.widget_toggle_advanced), fontSize = 20.sp)
        }
        Box(modifier = Modifier.weight(1f))
        OutlinedButton(
            onClick = onDismiss,
            modifier = Modifier.padding(end = 12.dp)
        ) {
            Text(text = stringResource(R.string.action_cancel), fontSize = 24.sp)
        }
        Button(
            enabled = state.canSaveSelection,
            onClick = onSave
        ) {
            Text(
                text = stringResource(R.string.action_save),
                fontSize = 24.sp,
                fontWeight = saveTextFontWeight
            )
        }
    }
}

internal fun applyWidgetSelectionChanges(
    context: Context,
    dashboardManager: DashboardManager,
    currentWidgets: List<DashboardWidget>,
    currentWidgetConfigs: List<FloatingDashboardWidgetConfig>,
    widgetIndex: Int,
    state: WidgetSelectionDialogState,
    saveConfigs: (List<FloatingDashboardWidgetConfig>) -> Unit,
    externalAppWidgetId: Int? = null
) {
    val normalizedScale = normalizeWidgetScale(state.scale)
    val normalizedShape = normalizeWidgetShape(state.shape)
    state.scale = normalizedScale
    state.shape = normalizedShape
    val currentWidget = currentWidgets[widgetIndex]
    val updatedWidgets = currentWidgets.toMutableList()
    val newWidget = if (state.selectedDataKey.isNotEmpty()) {
        DashboardWidget(
            id = currentWidget.id,
            title = WidgetsRepository.getTitleForDataKey(context, state.selectedDataKey),
            unit = WidgetsRepository.getUnitForDataKey(context, state.selectedDataKey),
            dataKey = state.selectedDataKey,
            textColorLight = state.textColorLight,
            textColorDark = state.textColorDark,
            backgroundColorLight = state.backgroundColorLight,
            backgroundColorDark = state.backgroundColorDark
        )
    } else {
        DashboardWidget(
            id = currentWidget.id,
            title = "",
            dataKey = "",
            textColorLight = state.textColorLight,
            textColorDark = state.textColorDark,
            backgroundColorLight = state.backgroundColorLight,
            backgroundColorDark = state.backgroundColorDark
        )
    }
    updatedWidgets[widgetIndex] = newWidget
    dashboardManager.updateWidgets(updatedWidgets)

    val normalizedConfigs = normalizeWidgetConfigs(
        currentWidgetConfigs,
        updatedWidgets.size
    ).toMutableList()
    val prevAppWidgetId = normalizedConfigs.getOrNull(widgetIndex)?.appWidgetId
    normalizedConfigs[widgetIndex] = if (state.selectedDataKey.isNotEmpty()) {
        FloatingDashboardWidgetConfig(
            dataKey = state.selectedDataKey,
            showTitle = state.showTitle,
            showUnit = state.showUnit,
            singleLineDualMetrics = if (WidgetsRepository.supportsSingleLineDualMetrics(
                    state.selectedDataKey
                )
            ) {
                state.singleLineDualMetrics
            } else {
                false
            },
            scale = normalizedScale,
            shape = normalizedShape,
            textColorLight = state.textColorLight,
            textColorDark = state.textColorDark,
            backgroundColorLight = state.backgroundColorLight,
            backgroundColorDark = state.backgroundColorDark,
            mediaPlayers = if (state.selectedDataKey == MUSIC_WIDGET_DATA_KEY) {
                orderedMediaPlayersForStorage(state.selectedMediaPlayers)
            } else {
                emptyList()
            },
            mediaSelectedPlayer = if (state.selectedDataKey == MUSIC_WIDGET_DATA_KEY) {
                resolveStoredMediaSelectedPlayer(
                    selectedPlayers = state.selectedMediaPlayers,
                    currentSelectedPlayer = state.selectedMediaPlayer
                )
            } else {
                ""
            },
            mediaAutoPlayOnInit = if (state.selectedDataKey == MUSIC_WIDGET_DATA_KEY) {
                state.mediaAutoPlayOnInit
            } else {
                false
            },
            mediaAutoPlayOnlyWhenEngineRunning = if (state.selectedDataKey == MUSIC_WIDGET_DATA_KEY) {
                state.mediaAutoPlayOnlyWhenEngineRunning && state.mediaAutoPlayOnInit
            } else {
                false
            },
            launcherAppPackage = if (state.selectedDataKey == APP_LAUNCHER_WIDGET_DATA_KEY) {
                state.launcherAppPackage.trim()
            } else {
                ""
            },
            appWidgetId = if (state.selectedDataKey == WidgetsRepository.EXTERNAL_WIDGET_DATA_KEY) {
                externalAppWidgetId
            } else {
                null
            }
        )
    } else {
        FloatingDashboardWidgetConfig(dataKey = "")
    }
    val newCfg = normalizedConfigs[widgetIndex]
    if (prevAppWidgetId != null) {
        val keep = newCfg.dataKey == WidgetsRepository.EXTERNAL_WIDGET_DATA_KEY &&
            newCfg.appWidgetId == prevAppWidgetId
        if (!keep) {
            ExternalWidgetHostManager.deleteAppWidgetId(context, prevAppWidgetId)
        }
    }
    saveConfigs(normalizedConfigs)
    dashboardManager.clearWidgetHistory(currentWidget.id)
}

internal fun externalAppWidgetIdForApply(
    state: WidgetSelectionDialogState,
    currentWidgetConfigs: List<FloatingDashboardWidgetConfig>,
    widgetIndex: Int
): Int? {
    if (state.selectedDataKey != WidgetsRepository.EXTERNAL_WIDGET_DATA_KEY) return null
    return currentWidgetConfigs.getOrNull(widgetIndex)
        ?.takeIf { it.dataKey == WidgetsRepository.EXTERNAL_WIDGET_DATA_KEY }
        ?.appWidgetId
}

internal fun tryLaunchExternalWidgetPicker(
    context: Context,
    saveTarget: Int,
    panelId: String,
    widgetIndex: Int,
    state: WidgetSelectionDialogState,
    currentWidgetConfigs: List<FloatingDashboardWidgetConfig>,
    onDismiss: () -> Unit
): Boolean {
    if (state.selectedDataKey != WidgetsRepository.EXTERNAL_WIDGET_DATA_KEY) return false
    val id = currentWidgetConfigs.getOrNull(widgetIndex)
        ?.takeIf { it.dataKey == WidgetsRepository.EXTERNAL_WIDGET_DATA_KEY }
        ?.appWidgetId
    if (id != null) return false
    WidgetPickerActivity.start(
        context = context,
        saveTarget = saveTarget,
        panelId = panelId,
        widgetIndex = widgetIndex,
        showTitle = state.showTitle,
        showUnit = state.showUnit
    )
    onDismiss()
    return true
}

internal fun resolveStoredMediaSelectedPlayer(
    selectedPlayers: Set<String>,
    currentSelectedPlayer: String
): String {
    val orderedPlayers = orderedMediaPlayersForStorage(selectedPlayers)
    if (orderedPlayers.isEmpty()) return ""
    return if (currentSelectedPlayer in orderedPlayers) {
        currentSelectedPlayer
    } else {
        orderedPlayers.first()
    }
}
