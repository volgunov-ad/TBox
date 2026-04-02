package vad.dashing.tbox.ui

import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults.cardElevation
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import vad.dashing.tbox.AppDataManager
import vad.dashing.tbox.AppDataViewModel
import vad.dashing.tbox.AppDataViewModelFactory
import vad.dashing.tbox.CanDataViewModel
import vad.dashing.tbox.DEFAULT_WIDGET_BACKGROUND_COLOR_DARK_FLOATING
import vad.dashing.tbox.DEFAULT_WIDGET_BACKGROUND_COLOR_LIGHT_FLOATING
import vad.dashing.tbox.DashboardManager
import vad.dashing.tbox.DashboardWidget
import vad.dashing.tbox.ExternalWidgetHostManager
import vad.dashing.tbox.FloatingDashboardWidgetConfig
import vad.dashing.tbox.SettingsManager
import vad.dashing.tbox.SettingsViewModel
import vad.dashing.tbox.APP_LAUNCHER_WIDGET_DATA_KEY
import vad.dashing.tbox.TboxViewModel
import vad.dashing.tbox.FloatingDashboardViewModel
import vad.dashing.tbox.FloatingDashboardViewModelFactory
import vad.dashing.tbox.R
import vad.dashing.tbox.SettingsViewModelFactory
import vad.dashing.tbox.SharedMediaControlService
import vad.dashing.tbox.WidgetPickerActivity
import vad.dashing.tbox.collectMediaPlayersFromWidgetConfigs
import vad.dashing.tbox.loadWidgetsFromConfig
import vad.dashing.tbox.FLOATING_DASHBOARD_DEFAULT_WIDGET_ELEVATION
import vad.dashing.tbox.ui.theme.TboxAppTheme

@Composable
private fun FloatingDashboardAppLauncherIconCacheDisposeEffect(panelId: String) {
    DisposableEffect(panelId) {
        onDispose { disposeAppLauncherPickerIconCache() }
    }
}

@Composable
fun FloatingDashboardUI(
    settingsManager: SettingsManager,
    appDataManager: AppDataManager,
    onUpdateWindowSize: (String, Int, Int) -> Unit,
    onUpdateWindowPosition: (String, Int, Int) -> Unit,
    onRebootTbox: () -> Unit,
    onTripFinishAndStart: () -> Unit,
    panelId: String,
    params: WindowManager.LayoutParams
) {
    val tboxViewModel: TboxViewModel = viewModel()
    val canViewModel: CanDataViewModel = viewModel()
    val settingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModelFactory(
        settingsManager
    )
    )
    val appDataViewModel: AppDataViewModel = viewModel(factory = AppDataViewModelFactory(
        appDataManager
    )
    )
    val currentTheme by tboxViewModel.currentTheme.collectAsStateWithLifecycle()

    FloatingDashboardAppLauncherIconCacheDisposeEffect(panelId)

    // Эффект при появлении окна
    LaunchedEffect(panelId) {
        tboxViewModel.updateFloatingDashboardShown(panelId, true)
    }

    // Эффект при закрытии окна
    DisposableEffect(panelId) {
        onDispose {
            tboxViewModel.updateFloatingDashboardShown(panelId, false)
        }
    }

    TboxAppTheme(theme = currentTheme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Transparent
        ) {
            FloatingDashboard(
                tboxViewModel = tboxViewModel,
                canViewModel = canViewModel,
                settingsViewModel = settingsViewModel,
                appDataViewModel = appDataViewModel,
                panelId = panelId,
                onUpdateWindowSize = onUpdateWindowSize,
                onUpdateWindowPosition = onUpdateWindowPosition,
                onRebootTbox = onRebootTbox,
                onTripFinishAndStart = onTripFinishAndStart,
                windowParams = params
            )
        }
    }
}

@Composable
fun FloatingDashboard(
    tboxViewModel: TboxViewModel,
    canViewModel: CanDataViewModel,
    settingsViewModel: SettingsViewModel,
    appDataViewModel: AppDataViewModel,
    panelId: String,
    onUpdateWindowSize: (String, Int, Int) -> Unit,
    onUpdateWindowPosition: (String, Int, Int) -> Unit,
    onRebootTbox: () -> Unit,
    onTripFinishAndStart: () -> Unit,
    windowParams: WindowManager.LayoutParams
) {
    val context = LocalContext.current
    val appWidgetHost = remember(context) { ExternalWidgetHostManager.acquireHost(context) }

    DisposableEffect(appWidgetHost) {
        onDispose {
            ExternalWidgetHostManager.releaseHost()
        }
    }

    val dashboardViewModel: FloatingDashboardViewModel = viewModel(
        key = "floating-$panelId",
        factory = FloatingDashboardViewModelFactory(panelId)
    )
    val dashboardState by dashboardViewModel.dashboardManager.dashboardState.collectAsStateWithLifecycle()
    val panelConfig by settingsViewModel.floatingDashboardConfig(panelId).collectAsStateWithLifecycle()
    val widgetConfigs = panelConfig.widgetsConfig
    val dashboardRows = panelConfig.rows
    val dashboardCols = panelConfig.cols
    val mediaSourceId = remember(panelId) { "floating-dashboard-$panelId" }
    val requestedMediaPlayers = remember(widgetConfigs) {
        collectMediaPlayersFromWidgetConfigs(widgetConfigs)
    }
    val isFloatingDashboardClickAction = panelConfig.clickAction

    val tboxConnected by tboxViewModel.tboxConnected.collectAsStateWithLifecycle()
    val currentTheme by tboxViewModel.currentTheme.collectAsStateWithLifecycle()
    val fuelTankLiters by settingsViewModel.fuelTankLiters.collectAsStateWithLifecycle()

    // Состояния
    var isEditMode by remember { mutableStateOf(false) }
    var showDialogForIndex by remember { mutableStateOf<Int?>(null) }
    var isDraggingMode by remember { mutableStateOf(false) }
    var isResizingMode by remember { mutableStateOf(false) }
    val canManipulatePanel = isEditMode && showDialogForIndex == null

    LaunchedEffect(isEditMode) {
        if (isEditMode) {
            delay(300000)
            if (isEditMode) {
                isEditMode = false
                isDraggingMode = false
                isResizingMode = false
                showDialogForIndex = null
            }
        }
    }

    // Используем rememberUpdatedState для отслеживания текущих значений windowParams
    val currentWindowParams by rememberUpdatedState(windowParams)

    // Сохраняем состояние оригинальных размеров с помощью StateFlow
    val originalWidth = remember { mutableIntStateOf(currentWindowParams.width) }
    val originalHeight = remember { mutableIntStateOf(currentWindowParams.height) }
    val originalX = remember { mutableIntStateOf(currentWindowParams.x) }
    val originalY = remember { mutableIntStateOf(currentWindowParams.y) }

    // Получаем информацию о текущем окне
    val windowInfo = LocalWindowInfo.current
    val containerSize = windowInfo.containerSize

    // Увеличиваем окно при показе диалога и возвращаем при закрытии
    LaunchedEffect(showDialogForIndex) {
        if (showDialogForIndex != null) {
            // Сохраняем текущие размеры как оригинальные перед увеличением
            originalWidth.intValue = currentWindowParams.width
            originalHeight.intValue = currentWindowParams.height
            originalX.intValue = currentWindowParams.x
            originalY.intValue = currentWindowParams.y

            // Увеличиваем окно для диалога
            val dialogWidth = (containerSize.width * 0.6f).toInt()
            val dialogHeight = (containerSize.height * 0.8f).toInt()

            val newWidth = dialogWidth.coerceAtMost(containerSize.width)
            val newHeight = dialogHeight.coerceAtMost(containerSize.height)

            onUpdateWindowSize(panelId, newWidth, newHeight)

            // Центрируем окно
            val centerX = (containerSize.width - newWidth) / 2 + 100
            val centerY = (containerSize.height - newHeight) / 2 + 100
            onUpdateWindowPosition(panelId, centerX, centerY)
        } else {
            // Восстанавливаем оригинальные размеры и положение
            onUpdateWindowSize(panelId, originalWidth.intValue, originalHeight.intValue)
            onUpdateWindowPosition(panelId, originalX.intValue, originalY.intValue)
        }
    }

    val dataProvider = remember(context) {
        TboxDataProvider(tboxViewModel, canViewModel, appDataViewModel, settingsViewModel, context)
    }

    LaunchedEffect(widgetConfigs, dashboardRows, dashboardCols, context) {
        val totalWidgets = dashboardRows * dashboardCols

        // Всегда загружаем/создаем виджеты при изменении зависимостей
        val widgets = loadWidgetsFromConfig(
            configs = widgetConfigs,
            widgetCount = totalWidgets,
            context = context,
            defaultBackgroundLight = DEFAULT_WIDGET_BACKGROUND_COLOR_LIGHT_FLOATING,
            defaultBackgroundDark = DEFAULT_WIDGET_BACKGROUND_COLOR_DARK_FLOATING
        )

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
    val widgetInteractionPolicy = remember(isEditMode) {
        if (isEditMode) {
            DashboardWidgetInteractionPolicy(
                mode = DashboardWidgetInteractionMode.EDIT,
                exclusions = listOf(ResizeHandleWidgetHitExclusion)
            )
        } else {
            DashboardWidgetInteractionPolicy()
        }
    }

    LaunchedEffect(restartEnabled) {
        if (!restartEnabled) {
            delay(15000) // Блокировка на 15 секунд
            restartEnabled = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(color = Color.Transparent)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .background(color = Color.Transparent)
                    .then(
                        if (canManipulatePanel) {
                            // Avoid width/height in keys: they change while resizing and cancel the drag.
                            Modifier.pointerInput(panelId) {
                                detectDragGestures(
                                    onDragStart = { startOffset ->
                                        val isNearBottomRight = isInResizeHandleArea(
                                            offset = startOffset,
                                            width = size.width.toFloat(),
                                            height = size.height.toFloat()
                                        )
                                        if (isNearBottomRight) {
                                            isResizingMode = true
                                            isDraggingMode = false
                                        } else {
                                            isDraggingMode = true
                                            isResizingMode = false
                                        }
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        if (isDraggingMode) {
                                            val newX = (windowParams.x + dragAmount.x).toInt().coerceAtLeast(0)
                                            val newY = (windowParams.y + dragAmount.y).toInt().coerceAtLeast(-100)
                                            onUpdateWindowPosition(panelId, newX, newY)
                                        } else if (isResizingMode) {
                                            val newWidth = (windowParams.width + dragAmount.x).toInt()
                                                .coerceAtLeast(50)
                                            val newHeight = (windowParams.height + dragAmount.y).toInt()
                                                .coerceAtLeast(50)
                                            onUpdateWindowSize(panelId, newWidth, newHeight)
                                        }
                                    },
                                    onDragEnd = {
                                        if (isDraggingMode) {
                                            settingsViewModel.saveFloatingDashboardPosition(
                                                panelId,
                                                windowParams.x,
                                                windowParams.y
                                            )
                                        } else if (isResizingMode) {
                                            settingsViewModel.saveFloatingDashboardSize(
                                                panelId,
                                                windowParams.width,
                                                windowParams.height
                                            )
                                        }
                                        isDraggingMode = false
                                        isResizingMode = false
                                    },
                                    onDragCancel = {
                                        isDraggingMode = false
                                        isResizingMode = false
                                    }
                                )
                            }
                        } else {
                            Modifier
                        }
                    )
            ) {
                DashboardPanelGridAndFrames(
                    dashboardRows = dashboardRows,
                    dashboardCols = dashboardCols,
                    dashboardState = dashboardState,
                    widgetConfigs = widgetConfigs,
                    tboxViewModel = tboxViewModel,
                    canViewModel = canViewModel,
                    appDataViewModel = appDataViewModel,
                    dataProvider = dataProvider,
                    dashboardManager = dashboardViewModel.dashboardManager,
                    dashboardChart = false,
                    tboxConnected = tboxConnected,
                    currentTheme = currentTheme,
                    restartEnabled = restartEnabled,
                    onTripFinishAndStart = onTripFinishAndStart,
                    isEditMode = isEditMode,
                    showDialogOpen = showDialogForIndex != null,
                    widgetInteractionPolicy = widgetInteractionPolicy,
                    widgetCardElevation = FLOATING_DASHBOARD_DEFAULT_WIDGET_ELEVATION.dp,
                    onWidgetClick = { index ->
                        val cfg = widgetConfigs.getOrNull(index)
                        if (isEditMode && !isDraggingMode && !isResizingMode) {
                            showDialogForIndex = index
                        } else if (
                            cfg?.dataKey == APP_LAUNCHER_WIDGET_DATA_KEY &&
                            cfg.launcherAppPackage.isNotBlank()
                        ) {
                            launchAppFromWidget(context, cfg.launcherAppPackage)
                        } else if (isFloatingDashboardClickAction) {
                            openMainActivityFromWidget(context)
                        }
                    },
                    onWidgetLongClick = {
                        isEditMode = !isEditMode
                        isDraggingMode = false
                        isResizingMode = false
                    },
                    onMusicSelectedPlayerChange = { index, selectedPackage ->
                        persistDashboardPanelMediaSelectedPlayer(
                            currentWidgetConfigs = widgetConfigs,
                            widgetIndex = index,
                            selectedPackage = selectedPackage,
                            saveConfigs = { configs ->
                                settingsViewModel.saveFloatingDashboardWidgets(panelId, configs)
                            }
                        )
                    },
                    onRestartRequested = {
                        if (restartEnabled) {
                            restartEnabled = false
                            onRebootTbox()
                        }
                    },
                    showTboxDisconnectIndicator = panelConfig.showTboxDisconnectIndicator,
                    enableMusicInnerInteractions = !isEditMode,
                    externalWidgetHost = appWidgetHost,
                    fuelTankLiters = fuelTankLiters
                )
            }
        }

        // Показываем кастомный диалог когда есть showDialogForIndex
        showDialogForIndex?.let { index ->
            OverlayWidgetSelectionDialog(
                panelId = panelId,
                widgetIndex = index,
                currentWidgets = dashboardState.widgets,
                currentWidgetConfigs = widgetConfigs,
                settingsViewModel = settingsViewModel,
                dashboardManager = dashboardViewModel.dashboardManager,
                onDismiss = { showDialogForIndex = null }
            )
        }
    }
}

@Composable
fun OverlayWidgetSelectionDialog(
    panelId: String,
    widgetIndex: Int,
    currentWidgets: List<DashboardWidget>,
    currentWidgetConfigs: List<FloatingDashboardWidgetConfig>,
    settingsViewModel: SettingsViewModel,
    dashboardManager: DashboardManager,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val state = rememberWidgetSelectionDialogState(
        widgetIndex = widgetIndex,
        currentWidgets = currentWidgets,
        currentWidgetConfigs = currentWidgetConfigs,
        defaultBackgroundLight = DEFAULT_WIDGET_BACKGROUND_COLOR_LIGHT_FLOATING,
        defaultBackgroundDark = DEFAULT_WIDGET_BACKGROUND_COLOR_DARK_FLOATING
    )

    Card(
        modifier = Modifier
            .fillMaxSize()
            .clickable(enabled = false, onClick = {}),
        elevation = cardElevation(8.dp),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            WidgetSelectionDialogForm(
                titleText = if (state.showAdvancedSettings) {
                    stringResource(R.string.widget_additional_settings_for_tile, widgetIndex + 1)
                } else {
                    stringResource(R.string.widget_select_data_for_tile, widgetIndex + 1)
                },
                state = state,
                modifier = Modifier
                    .weight(1f),
                bottomContent = {
                    if (state.isExternalAppWidgetSelected) {
                        ExternalAppWidgetPickerSection(
                            appWidgetId = externalAppWidgetIdForApply(
                                state,
                                currentWidgetConfigs,
                                widgetIndex
                            ),
                            onPickClick = {
                                WidgetPickerActivity.start(
                                    context = context,
                                    saveTarget = WidgetPickerActivity.SAVE_TARGET_FLOATING,
                                    panelId = panelId,
                                    widgetIndex = widgetIndex,
                                    showTitle = state.showTitle,
                                    showUnit = state.showUnit
                                )
                                onDismiss()
                            }
                        )
                    }
                }
            )

            // Кнопки действий
            WidgetSelectionDialogActions(
                state = state,
                onDismiss = onDismiss,
                onSave = {
                    if (tryLaunchExternalWidgetPicker(
                            context = context,
                            saveTarget = WidgetPickerActivity.SAVE_TARGET_FLOATING,
                            panelId = panelId,
                            widgetIndex = widgetIndex,
                            state = state,
                            currentWidgetConfigs = currentWidgetConfigs,
                            onDismiss = onDismiss
                        )
                    ) {
                        return@WidgetSelectionDialogActions
                    }
                    applyWidgetSelectionChanges(
                        context = context,
                        dashboardManager = dashboardManager,
                        currentWidgets = currentWidgets,
                        currentWidgetConfigs = currentWidgetConfigs,
                        widgetIndex = widgetIndex,
                        state = state,
                        saveConfigs = { configs ->
                            settingsViewModel.saveFloatingDashboardWidgets(panelId, configs)
                        },
                        externalAppWidgetId = externalAppWidgetIdForApply(
                            state,
                            currentWidgetConfigs,
                            widgetIndex
                        )
                    )
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp),
                saveTextFontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
        }
    }
}
