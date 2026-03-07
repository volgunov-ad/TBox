package vad.dashing.tbox.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import vad.dashing.tbox.AppDataViewModel
import vad.dashing.tbox.CanDataViewModel
import vad.dashing.tbox.DEFAULT_WIDGET_BACKGROUND_COLOR_DARK_MAIN
import vad.dashing.tbox.DEFAULT_WIDGET_BACKGROUND_COLOR_LIGHT_MAIN
import vad.dashing.tbox.DashboardManager
import vad.dashing.tbox.DashboardWidget
import vad.dashing.tbox.FloatingDashboardWidgetConfig
import vad.dashing.tbox.MainDashboardViewModel
import vad.dashing.tbox.MUSIC_WIDGET_DATA_KEY
import vad.dashing.tbox.R
import vad.dashing.tbox.SettingsViewModel
import vad.dashing.tbox.SharedMediaControlService
import vad.dashing.tbox.TboxViewModel
import vad.dashing.tbox.WidgetsRepository
import vad.dashing.tbox.collectMediaPlayersFromWidgetConfigs
import vad.dashing.tbox.loadWidgetsFromConfig
import vad.dashing.tbox.normalizeWidgetScale
import vad.dashing.tbox.normalizeWidgetConfigs
import vad.dashing.tbox.resolveSelectedMediaPlayerForWidget
import vad.dashing.tbox.ui.theme.DARK_THEME_BACKGROUND_COLOR_PRESET_1_INT
import vad.dashing.tbox.ui.theme.DARK_THEME_BACKGROUND_COLOR_PRESET_2_INT
import vad.dashing.tbox.ui.theme.DARK_THEME_TEXT_COLOR_PRESET_1_INT
import vad.dashing.tbox.ui.theme.DARK_THEME_TEXT_COLOR_PRESET_2_INT
import vad.dashing.tbox.ui.theme.LIGHT_THEME_BACKGROUND_COLOR_PRESET_1_INT
import vad.dashing.tbox.ui.theme.LIGHT_THEME_BACKGROUND_COLOR_PRESET_2_INT
import vad.dashing.tbox.ui.theme.LIGHT_THEME_TEXT_COLOR_PRESET_1_INT
import vad.dashing.tbox.ui.theme.LIGHT_THEME_TEXT_COLOR_PRESET_2_INT

@Composable
fun MainDashboardTab(
    tboxViewModel: TboxViewModel,
    canViewModel: CanDataViewModel,
    settingsViewModel: SettingsViewModel,
    appDataViewModel: AppDataViewModel,
    onTboxRestartClick: () -> Unit,
) {
    val context = LocalContext.current
    val dashboardViewModel: MainDashboardViewModel = viewModel()
    val dashboardState by dashboardViewModel.dashboardManager.dashboardState.collectAsStateWithLifecycle()
    val widgetsConfig by settingsViewModel.dashboardWidgetsConfig.collectAsStateWithLifecycle()
    val dashboardRows by settingsViewModel.dashboardRows.collectAsStateWithLifecycle()
    val dashboardCols by settingsViewModel.dashboardCols.collectAsStateWithLifecycle()
    val dashboardChart by settingsViewModel.dashboardChart.collectAsStateWithLifecycle()

    val tboxConnected by tboxViewModel.tboxConnected.collectAsStateWithLifecycle()
    val currentTheme by tboxViewModel.currentTheme.collectAsStateWithLifecycle()

    var showDialogForIndex by remember { mutableStateOf<Int?>(null) }
    val totalWidgets = dashboardRows * dashboardCols
    val widgetConfigs = remember(widgetsConfig, totalWidgets) {
        normalizeWidgetConfigs(widgetsConfig, totalWidgets)
    }
    val mediaSourceId = remember { "main-dashboard" }
    val requestedMediaPlayers = remember(widgetConfigs) {
        collectMediaPlayersFromWidgetConfigs(widgetConfigs)
    }

    LaunchedEffect(widgetConfigs, totalWidgets, context) {
        val widgets = loadWidgetsFromConfig(widgetConfigs, totalWidgets, context)
        dashboardViewModel.dashboardManager.updateWidgets(widgets)
    }
    LaunchedEffect(mediaSourceId, requestedMediaPlayers, context) {
        SharedMediaControlService.updateSourceSelection(
            context = context,
            sourceId = mediaSourceId,
            mediaPackages = requestedMediaPlayers
        )
    }
    DisposableEffect(mediaSourceId) {
        onDispose {
            SharedMediaControlService.clearSourceSelection(mediaSourceId)
        }
    }

    var restartEnabled by remember { mutableStateOf(true) }

    LaunchedEffect(restartEnabled) {
        if (!restartEnabled) {
            delay(15000) // Блокировка на 15 секунд
            restartEnabled = true
        }
    }

    val dataProvider = remember(context) {
        TboxDataProvider(tboxViewModel, canViewModel, appDataViewModel, context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        if (dashboardState.widgets.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.loading))
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (row in 0 until dashboardRows) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (col in 0 until dashboardCols) {
                            val index = row * dashboardCols + col
                            val widget = dashboardState.widgets.getOrNull(index) ?: continue
                            val widgetConfig = widgetConfigs.getOrNull(index)
                                ?: FloatingDashboardWidgetConfig(dataKey = "")
                            val widgetTextScale = normalizeWidgetScale(widgetConfig.scale)
                            val widgetTextColor = widget.resolveTextColorForTheme(currentTheme)
                            val widgetBackgroundColor = widget.resolveBackgroundColorForTheme(currentTheme)

                            Box(modifier = Modifier.weight(1f)) {
                                CompositionLocalProvider(
                                    LocalWidgetTextScale provides widgetTextScale
                                ) {
                                    DashboardWidgetRenderer(
                                        widget = widget,
                                        widgetConfig = widgetConfig,
                                        tboxViewModel = tboxViewModel,
                                        canViewModel = canViewModel,
                                        appDataViewModel = appDataViewModel,
                                        dataProvider = dataProvider,
                                        dashboardManager = dashboardViewModel.dashboardManager,
                                        dashboardChart = dashboardChart,
                                        tboxConnected = tboxConnected,
                                        restartEnabled = restartEnabled,
                                        widgetTextColor = widgetTextColor,
                                        widgetBackgroundColor = widgetBackgroundColor,
                                        onClick = {},
                                        onLongClick = { showDialogForIndex = index },
                                        onMusicSelectedPlayerChange = { selectedPackage ->
                                            persistMainMediaWidgetSelectedPlayer(
                                                settingsViewModel = settingsViewModel,
                                                currentWidgetConfigs = widgetConfigs,
                                                widgetIndex = index,
                                                currentWidgetConfig = widgetConfig,
                                                selectedPackage = selectedPackage
                                            )
                                        },
                                        onRestartRequested = {
                                            if (restartEnabled) {
                                                restartEnabled = false
                                                onTboxRestartClick()
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        showDialogForIndex?.let { index ->
            WidgetSelectionDialog(
                dashboardManager = dashboardViewModel.dashboardManager,
                settingsViewModel = settingsViewModel,
                widgetIndex = index,
                currentWidgets = dashboardState.widgets,
                currentWidgetConfigs = widgetConfigs,
                onDismiss = { showDialogForIndex = null }
            )
        }
    }
}

@Composable
fun WidgetSelectionDialog(
    dashboardManager: DashboardManager,
    settingsViewModel: SettingsViewModel,
    widgetIndex: Int,
    currentWidgets: List<DashboardWidget>,
    currentWidgetConfigs: List<FloatingDashboardWidgetConfig>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedDataKey by remember {
        mutableStateOf(currentWidgets.getOrNull(widgetIndex)?.dataKey ?: "")
    }
    val initialConfig = currentWidgetConfigs.getOrNull(widgetIndex)
        ?: FloatingDashboardWidgetConfig(dataKey = "")
    var showTitle by remember(widgetIndex, currentWidgetConfigs) {
        mutableStateOf(initialConfig.showTitle)
    }
    var showUnit by remember(widgetIndex, currentWidgetConfigs) {
        mutableStateOf(initialConfig.showUnit)
    }
    var scale by remember(widgetIndex, currentWidgetConfigs) {
        mutableFloatStateOf(normalizeWidgetScale(initialConfig.scale))
    }
    var textColorLight by remember(widgetIndex, currentWidgetConfigs) {
        mutableIntStateOf(initialConfig.textColorLight)
    }
    var textColorDark by remember(widgetIndex, currentWidgetConfigs) {
        mutableIntStateOf(initialConfig.textColorDark)
    }
    var backgroundColorLight by remember(widgetIndex, currentWidgetConfigs) {
        mutableIntStateOf(
            initialConfig.backgroundColorLight ?: DEFAULT_WIDGET_BACKGROUND_COLOR_LIGHT_MAIN
        )
    }
    var backgroundColorDark by remember(widgetIndex, currentWidgetConfigs) {
        mutableIntStateOf(
            initialConfig.backgroundColorDark ?: DEFAULT_WIDGET_BACKGROUND_COLOR_DARK_MAIN
        )
    }
    var selectedMediaPlayers by remember(widgetIndex, currentWidgetConfigs) {
        mutableStateOf(
            if (initialConfig.dataKey == MUSIC_WIDGET_DATA_KEY) {
                normalizeMediaPlayersSelection(initialConfig.mediaPlayers)
            } else {
                emptySet()
            }
        )
    }
    val selectedMediaPlayer = remember(widgetIndex, currentWidgetConfigs) {
        resolveSelectedMediaPlayerForWidget(initialConfig)
    }
    var mediaAutoPlayOnInit by remember(widgetIndex, currentWidgetConfigs) {
        mutableStateOf(initialConfig.mediaAutoPlayOnInit)
    }
    var showAdvancedSettings by remember(widgetIndex) { mutableStateOf(false) }
    val isMusicWidgetSelected = selectedDataKey == MUSIC_WIDGET_DATA_KEY
    val togglesEnabled = selectedDataKey.isNotEmpty()
    val canSaveSelection = !isMusicWidgetSelected || selectedMediaPlayers.isNotEmpty()

    val availableOptions = listOf("" to stringResource(R.string.widget_option_not_selected)) +
            WidgetsRepository.getAvailableDataKeysWidgets()
                .filter { it.isNotEmpty() }
                .map { key ->
                    key to WidgetsRepository.getTitleUnitForDataKey(context, key)
                }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {  },
        modifier = Modifier.fillMaxWidth(0.8f), // Модификатор для всего диалога
        properties = DialogProperties(
            usePlatformDefaultWidth = false // Отключает стандартную ширину платформы
        ),
        text = {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
            ) {
                SettingsTitle(
                    if (showAdvancedSettings) {
                        stringResource(R.string.widget_additional_settings_for_tile, widgetIndex + 1)
                    } else {
                        stringResource(R.string.widget_select_data_for_tile, widgetIndex + 1)
                    }
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    if (showAdvancedSettings) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(androidx.compose.foundation.rememberScrollState())
                                .padding(12.dp)
                        ) {
                            if (isMusicWidgetSelected) {
                                MediaPlayersInlineSelection(
                                    selectedPlayers = selectedMediaPlayers,
                                    onSelectionChange = { selectedMediaPlayers = it },
                                    enabled = togglesEnabled
                                )
                                if (selectedMediaPlayers.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.widget_music_players_required),
                                        color = MaterialTheme.colorScheme.error,
                                        fontSize = 20.sp
                                    )
                                }
                                SettingSwitch(
                                    mediaAutoPlayOnInit,
                                    { mediaAutoPlayOnInit = it },
                                    stringResource(R.string.widget_music_auto_play_on_init),
                                    "",
                                    togglesEnabled
                                )
                            }
                            SettingSwitch(
                                showTitle,
                                { showTitle = it },
                                stringResource(R.string.widget_show_title),
                                "",
                                togglesEnabled
                            )
                            SettingSwitch(
                                showUnit,
                                { showUnit = it },
                                stringResource(R.string.widget_show_unit),
                                "",
                                togglesEnabled
                            )
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.widget_scale, scale),
                                    fontSize = 24.sp
                                )
                                Text(
                                    text = stringResource(R.string.widget_scale_hint),
                                    fontSize = 20.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Slider(
                                    value = scale,
                                    onValueChange = { newValue ->
                                        scale = normalizeWidgetScale(newValue)
                                    },
                                    valueRange = 0.1f..2.0f,
                                    steps = 18,
                                    enabled = togglesEnabled,
                                    modifier = Modifier
                                        .padding(top = 6.dp)
                                )
                            }
                            WidgetTextColorSetting(
                                title = stringResource(R.string.widget_text_color_light),
                                colorValue = textColorLight,
                                enabled = togglesEnabled,
                                onColorChange = { textColorLight = it },
                                presetColors = listOf(
                                    LIGHT_THEME_TEXT_COLOR_PRESET_1_INT,
                                    LIGHT_THEME_TEXT_COLOR_PRESET_2_INT
                                )
                            )
                            WidgetTextColorSetting(
                                title = stringResource(R.string.widget_text_color_dark),
                                colorValue = textColorDark,
                                enabled = togglesEnabled,
                                onColorChange = { textColorDark = it },
                                presetColors = listOf(
                                    DARK_THEME_TEXT_COLOR_PRESET_1_INT,
                                    DARK_THEME_TEXT_COLOR_PRESET_2_INT
                                )
                            )
                            WidgetTextColorSetting(
                                title = stringResource(R.string.widget_background_color_light),
                                colorValue = backgroundColorLight,
                                enabled = togglesEnabled,
                                onColorChange = { backgroundColorLight = it },
                                presetColors = listOf(
                                    LIGHT_THEME_BACKGROUND_COLOR_PRESET_1_INT,
                                    LIGHT_THEME_BACKGROUND_COLOR_PRESET_2_INT
                                )
                            )
                            WidgetTextColorSetting(
                                title = stringResource(R.string.widget_background_color_dark),
                                colorValue = backgroundColorDark,
                                enabled = togglesEnabled,
                                onColorChange = { backgroundColorDark = it },
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
                                        .clickable { selectedDataKey = key }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    androidx.compose.material3.RadioButton(
                                        selected = selectedDataKey == key,
                                        onClick = { selectedDataKey = key }
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
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { showAdvancedSettings = !showAdvancedSettings },
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (showAdvancedSettings) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline
                        }
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (showAdvancedSettings) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                        } else {
                            Color.Transparent
                        },
                        contentColor = if (showAdvancedSettings) {
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
                    enabled = canSaveSelection,
                    onClick = {
                        val normalizedScale = normalizeWidgetScale(scale)
                        scale = normalizedScale
                        val updatedWidgets = currentWidgets.toMutableList()
                        val newWidget = if (selectedDataKey.isNotEmpty()) {
                            DashboardWidget(
                                id = currentWidgets[widgetIndex].id,
                                title = WidgetsRepository.getTitleForDataKey(context, selectedDataKey),
                                unit = WidgetsRepository.getUnitForDataKey(context, selectedDataKey),
                                dataKey = selectedDataKey,
                                textColorLight = textColorLight,
                                textColorDark = textColorDark,
                                backgroundColorLight = backgroundColorLight,
                                backgroundColorDark = backgroundColorDark
                            )
                        } else {
                            DashboardWidget(
                                id = currentWidgets[widgetIndex].id,
                                title = "",
                                dataKey = "",
                                textColorLight = textColorLight,
                                textColorDark = textColorDark,
                                backgroundColorLight = backgroundColorLight,
                                backgroundColorDark = backgroundColorDark
                            )
                        }
                        updatedWidgets[widgetIndex] = newWidget

                        dashboardManager.updateWidgets(updatedWidgets)

                        val normalizedConfigs = normalizeWidgetConfigs(
                            currentWidgetConfigs,
                            updatedWidgets.size
                        ).toMutableList()
                        normalizedConfigs[widgetIndex] = if (selectedDataKey.isNotEmpty()) {
                            FloatingDashboardWidgetConfig(
                                dataKey = selectedDataKey,
                                showTitle = showTitle,
                                showUnit = showUnit,
                                scale = normalizedScale,
                                textColorLight = textColorLight,
                                textColorDark = textColorDark,
                                backgroundColorLight = backgroundColorLight,
                                backgroundColorDark = backgroundColorDark,
                                mediaPlayers = if (selectedDataKey == MUSIC_WIDGET_DATA_KEY) {
                                    orderedMediaPlayersForStorage(selectedMediaPlayers)
                                } else {
                                    emptyList()
                                },
                                mediaSelectedPlayer = if (selectedDataKey == MUSIC_WIDGET_DATA_KEY) {
                                    resolveStoredMediaSelectedPlayer(
                                        selectedPlayers = selectedMediaPlayers,
                                        currentSelectedPlayer = selectedMediaPlayer
                                    )
                                } else {
                                    ""
                                },
                                mediaAutoPlayOnInit = if (selectedDataKey == MUSIC_WIDGET_DATA_KEY) {
                                    mediaAutoPlayOnInit
                                } else {
                                    false
                                }
                            )
                        } else {
                            FloatingDashboardWidgetConfig(dataKey = "")
                        }
                        settingsViewModel.saveDashboardWidgets(normalizedConfigs)
                        dashboardManager.clearWidgetHistory(currentWidgets[widgetIndex].id)
                        onDismiss()
                    }
                ) {
                    Text(text = stringResource(R.string.action_save), fontSize = 24.sp)
                }
            }
        },
        dismissButton = {}
    )
}

private fun persistMainMediaWidgetSelectedPlayer(
    settingsViewModel: SettingsViewModel,
    currentWidgetConfigs: List<FloatingDashboardWidgetConfig>,
    widgetIndex: Int,
    currentWidgetConfig: FloatingDashboardWidgetConfig,
    selectedPackage: String
) {
    val normalizedConfigs = normalizeWidgetConfigs(
        configs = currentWidgetConfigs,
        widgetCount = currentWidgetConfigs.size
    ).toMutableList()
    val currentConfig = normalizedConfigs.getOrNull(widgetIndex) ?: return
    if (currentConfig.dataKey != MUSIC_WIDGET_DATA_KEY) return
    if (currentConfig.mediaSelectedPlayer == selectedPackage) return

    normalizedConfigs[widgetIndex] = currentWidgetConfig.copy(
        mediaSelectedPlayer = selectedPackage
    )
    settingsViewModel.saveDashboardWidgets(normalizedConfigs)
}

private fun resolveStoredMediaSelectedPlayer(
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

