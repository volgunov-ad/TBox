package vad.dashing.tbox.ui

import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import vad.dashing.tbox.ExternalWidgetHostManager
import vad.dashing.tbox.SettingsManager
import vad.dashing.tbox.SettingsViewModel
import vad.dashing.tbox.MainActivityIntentHelper
import vad.dashing.tbox.APP_LAUNCHER_WIDGET_DATA_KEY
import vad.dashing.tbox.HIDE_FLOATING_PANELS_WIDGET_DATA_KEY
import vad.dashing.tbox.TOGGLE_FLOATING_PANELS_ENABLED_WIDGET_DATA_KEY
import vad.dashing.tbox.isActiveTripWidgetDataKey
import vad.dashing.tbox.TboxViewModel
import vad.dashing.tbox.FloatingDashboardViewModel
import vad.dashing.tbox.FloatingDashboardViewModelFactory
import vad.dashing.tbox.R
import vad.dashing.tbox.SettingsViewModelFactory
import vad.dashing.tbox.SharedMediaControlService
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
    val density = LocalDensity.current
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
    // Состояния
    var isEditMode by remember { mutableStateOf(false) }
    var isDraggingMode by remember { mutableStateOf(false) }
    var isResizingMode by remember { mutableStateOf(false) }
    var pendingMusicSelection by remember(panelId) { mutableStateOf<Pair<Int, String>?>(null) }
    var pendingSeatHeatVentVariant by remember(panelId) { mutableStateOf<Pair<Int, Int>?>(null) }
    val canManipulatePanel = isEditMode
    val latestWidgetConfigs by rememberUpdatedState(widgetConfigs)

    LaunchedEffect(isEditMode) {
        if (isEditMode) {
            delay(300000)
            if (isEditMode) {
                isEditMode = false
                isDraggingMode = false
                isResizingMode = false
            }
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
    val resizeHandleWidthDp = with(density) { resizeHandleOffsetForDimension(windowParams.width.toFloat()).toDp() }
    val resizeHandleHeightDp = with(density) { resizeHandleOffsetForDimension(windowParams.height.toFloat()).toDp() }
    val widgetInteractionPolicy = remember(isEditMode) {
        if (isEditMode) {
            DashboardWidgetInteractionPolicy(
                mode = DashboardWidgetInteractionMode.EDIT
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
    LaunchedEffect(pendingMusicSelection, panelId) {
        val pending = pendingMusicSelection ?: return@LaunchedEffect
        delay(220)
        if (pendingMusicSelection != pending) return@LaunchedEffect
        persistDashboardPanelMediaSelectedPlayer(
            currentWidgetConfigs = latestWidgetConfigs,
            widgetIndex = pending.first,
            selectedPackage = pending.second,
            saveConfigs = { configs ->
                settingsViewModel.saveFloatingDashboardWidgets(panelId, configs)
            }
        )
        if (pendingMusicSelection == pending) {
            pendingMusicSelection = null
        }
    }
    LaunchedEffect(pendingSeatHeatVentVariant, panelId) {
        val pending = pendingSeatHeatVentVariant ?: return@LaunchedEffect
        delay(2000)
        if (pendingSeatHeatVentVariant != pending) return@LaunchedEffect
        persistDashboardPanelSeatHeatVentSelectedVariant(
            currentWidgetConfigs = latestWidgetConfigs,
            widgetIndex = pending.first,
            selectedVariant = pending.second,
            saveConfigs = { configs ->
                settingsViewModel.saveFloatingDashboardWidgets(panelId, configs)
            }
        )
        if (pendingSeatHeatVentVariant == pending) {
            pendingSeatHeatVentVariant = null
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
                    mbCanInterestSourceId = "floating-$panelId",
                    dashboardRows = dashboardRows,
                    dashboardCols = dashboardCols,
                    dashboardState = dashboardState,
                    widgetConfigs = widgetConfigs,
                    settingsViewModel = settingsViewModel,
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
                    showDialogOpen = false,
                    widgetInteractionPolicy = widgetInteractionPolicy,
                    widgetCardElevation = FLOATING_DASHBOARD_DEFAULT_WIDGET_ELEVATION.dp,
                    onWidgetClick = { index ->
                        val cfg = widgetConfigs.getOrNull(index)
                        if (isEditMode && !isDraggingMode && !isResizingMode) {
                            try {
                                context.startActivity(
                                    MainActivityIntentHelper.createFloatingDashboardTileEditIntent(
                                        context,
                                        panelId,
                                        index
                                    )
                                )
                            } catch (_: Exception) {
                            }
                        } else if (cfg?.dataKey == "steeringWheelHeatWidget") {
                            sendToggleSteeringWheelHeat(context)
                        } else if (
                            cfg?.dataKey == APP_LAUNCHER_WIDGET_DATA_KEY &&
                            cfg.launcherAppPackage.isNotBlank()
                        ) {
                            launchAppFromWidget(context, cfg.launcherAppPackage)
                        } else if (isFloatingDashboardClickAction) {
                            if (cfg != null && isActiveTripWidgetDataKey(cfg.dataKey)) {
                                settingsViewModel.saveSelectedTab(SettingsManager.TRIPS_SELECTED_TAB_INDEX)
                            }
                            openMainActivityFromWidget(context)
                        }
                    },
                    onWidgetLongClick = {
                        isEditMode = !isEditMode
                        isDraggingMode = false
                        isResizingMode = false
                    },
                    onMusicSelectedPlayerChange = { index, selectedPackage ->
                        pendingMusicSelection = index to selectedPackage
                    },
                    onSeatHeatVentSelectedVariantChange = { index, variant ->
                        pendingSeatHeatVentVariant = index to variant
                    },
                    onHideFloatingPanelsDoubleClick = {
                        val cfg = widgetConfigs.getOrNull(it)
                        if (cfg?.dataKey == HIDE_FLOATING_PANELS_WIDGET_DATA_KEY) {
                            sendToggleHideOtherFloatingPanels(
                                context = context,
                                originPanelId = panelId
                            )
                        }
                    },
                    onToggleFloatingPanelsEnabledDoubleClick = {
                        val cfg = widgetConfigs.getOrNull(it)
                        if (cfg?.dataKey == TOGGLE_FLOATING_PANELS_ENABLED_WIDGET_DATA_KEY) {
                            sendToggleFloatingPanelsEnabled(
                                context = context,
                                originPanelId = panelId,
                                toggleAllPanels = false
                            )
                        }
                    },
                    onRestartRequested = {
                        if (restartEnabled) {
                            restartEnabled = false
                            onRebootTbox()
                        }
                    },
                    showTboxDisconnectIndicator = panelConfig.showTboxDisconnectIndicator,
                    enableInnerInteractions = !isEditMode,
                    externalWidgetHost = appWidgetHost,
                )
                if (isEditMode) {
                    // Reserve panel resize corner to avoid accidental tile long-press there.
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(resizeHandleWidthDp, resizeHandleHeightDp)
                            .pointerInput(panelId, isEditMode, resizeHandleWidthDp, resizeHandleHeightDp) {
                                detectTapGestures(
                                    onTap = {},
                                    onLongPress = {}
                                )
                            }
                    )
                }
            }
        }
    }
}
