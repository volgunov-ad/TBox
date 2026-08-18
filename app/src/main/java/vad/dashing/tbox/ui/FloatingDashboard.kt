package vad.dashing.tbox.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import vad.dashing.tbox.AppDataManager
import vad.dashing.tbox.AppDataViewModel
import vad.dashing.tbox.AppDataViewModelFactory
import vad.dashing.tbox.normalizePanelShape
import vad.dashing.tbox.resolvePanelBackgroundColor
import vad.dashing.tbox.resolvePanelBackgroundImageRelPath
import vad.dashing.tbox.CanDataViewModel
import vad.dashing.tbox.DEFAULT_WIDGET_BACKGROUND_COLOR_DARK_FLOATING
import vad.dashing.tbox.DEFAULT_WIDGET_BACKGROUND_COLOR_LIGHT_FLOATING
import vad.dashing.tbox.ExternalWidgetHostManager
import vad.dashing.tbox.FloatingPanelCollapseAnimationGate
import vad.dashing.tbox.FloatingPanelEditModeTracker
import vad.dashing.tbox.SettingsManager
import vad.dashing.tbox.SettingsViewModel
import vad.dashing.tbox.MainActivityIntentHelper
import vad.dashing.tbox.PANEL_COLLAPSE_ANIMATION_MS
import vad.dashing.tbox.PanelCollapseEdge
import vad.dashing.tbox.PanelCollapseStates
import vad.dashing.tbox.PanelPxBounds
import vad.dashing.tbox.normalizePanelCollapseOnTileTapDelaySec
import vad.dashing.tbox.APP_LAUNCHER_WIDGET_DATA_KEY
import vad.dashing.tbox.DRIVE_MODE_WIDGET_DATA_KEY
import vad.dashing.tbox.DRIVE_MODE_CYCLE_WIDGET_DATA_KEY
import vad.dashing.tbox.HVAC_SYNC_WIDGET_DATA_KEY
import vad.dashing.tbox.MIRROR_ADJUST_MODE_WIDGET_DATA_KEY
import vad.dashing.tbox.HIDE_FLOATING_PANELS_WIDGET_DATA_KEY
import vad.dashing.tbox.PARKING_RADAR_WIDGET_DATA_KEY
import vad.dashing.tbox.TOGGLE_FLOATING_PANELS_ENABLED_WIDGET_DATA_KEY
import vad.dashing.tbox.WIPER_MAINTENANCE_WIDGET_DATA_KEY
import vad.dashing.tbox.isActiveTripWidgetDataKey
import vad.dashing.tbox.TboxViewModel
import vad.dashing.tbox.FloatingDashboardViewModel
import vad.dashing.tbox.FloatingDashboardViewModelFactory
import vad.dashing.tbox.R
import vad.dashing.tbox.SettingsViewModelFactory
import vad.dashing.tbox.SharedMediaControlService
import vad.dashing.tbox.collectMediaPlayersFromWidgetConfigs
import vad.dashing.tbox.collapsedPanelBounds
import vad.dashing.tbox.lerpPanelBounds
import vad.dashing.tbox.loadWidgetsFromConfig
import vad.dashing.tbox.normalizePanelLayoutSnapDp
import vad.dashing.tbox.resolveDriveModeWidgetOption
import vad.dashing.tbox.nextDriveModeCycleTarget
import vad.dashing.tbox.resolveDriveModeCycleCurrentRaw
import vad.dashing.tbox.mbcan.UniversalCanRepository
import vad.dashing.tbox.snapToGrid
import vad.dashing.tbox.FLOATING_DASHBOARD_DEFAULT_WIDGET_ELEVATION
import vad.dashing.tbox.collapseEdgeOrNone
import vad.dashing.tbox.normalizePanelCollapseStripThicknessDp
import vad.dashing.tbox.resolveStripColor
import vad.dashing.tbox.resolveStripExpandedColor
import vad.dashing.tbox.freeform.WindowModeUiGuard
import vad.dashing.tbox.ui.theme.TboxAppTheme
import kotlin.math.abs
import kotlin.math.roundToInt
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
    onUpdateWindowFrame: (String, Int, Int, Int, Int) -> Unit,
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
        appDataManager,
        settingsManager,
    )
    )
    val currentTheme by tboxViewModel.currentTheme.collectAsStateWithLifecycle()
    val appFontFamilyId by settingsViewModel.appFontFamilyId.collectAsStateWithLifecycle()
    val uiClickSoundsEnabled by settingsViewModel.uiClickSoundsEnabled.collectAsStateWithLifecycle()

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

    TboxAppTheme(theme = currentTheme, fontFamilyId = appFontFamilyId) {
        CompositionLocalProvider(LocalClickSoundEnabled provides uiClickSoundsEnabled) {
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
                    onUpdateWindowFrame = onUpdateWindowFrame,
                    onRebootTbox = onRebootTbox,
                    onTripFinishAndStart = onTripFinishAndStart,
                    windowParams = params
                )
            }
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
    onUpdateWindowFrame: (String, Int, Int, Int, Int) -> Unit,
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
    val floatingPanelsLayoutSnapDp by
        settingsViewModel.floatingPanelsLayoutSnapDp.collectAsStateWithLifecycle()
    val layoutSnapDp = normalizePanelLayoutSnapDp(floatingPanelsLayoutSnapDp)
    val layoutSnapStepPx = with(density) { layoutSnapDp.dp.toPx() }
    val widgetConfigs = panelConfig.widgetsConfig
    val dashboardRows = panelConfig.rows
    val dashboardCols = panelConfig.cols
    val mediaSourceId = remember(panelId) { "floating-dashboard-$panelId" }
    val requestedMediaPlayers = remember(widgetConfigs) {
        collectMediaPlayersFromWidgetConfigs(widgetConfigs)
    }
    val isFloatingDashboardClickAction = panelConfig.clickAction
    val panelCollapseStates by settingsViewModel.panelCollapseStates.collectAsStateWithLifecycle()

    val tboxConnected by tboxViewModel.tboxConnected.collectAsStateWithLifecycle()
    val currentTheme by tboxViewModel.currentTheme.collectAsStateWithLifecycle()
    // Состояния
    var isEditMode by remember { mutableStateOf(false) }
    var isDraggingMode by remember { mutableStateOf(false) }
    var isResizingMode by remember { mutableStateOf(false) }
    var pendingMusicSelection by remember(panelId) { mutableStateOf<Pair<Int, String>?>(null) }
    var pendingSeatHeatVentVariant by remember(panelId) { mutableStateOf<Pair<Int, Int>?>(null) }
    val canManipulatePanel = isEditMode
    val collapseAfterTapScope = rememberCoroutineScope()
    var collapseAfterTapJob by remember(panelId) { mutableStateOf<Job?>(null) }
    val collapseEdge = panelConfig.collapseEdgeOrNone()
    val panelCollapsed = PanelCollapseStates.isCollapsed(panelCollapseStates, panelId)
    val effectiveCollapsed = panelCollapsed && !isEditMode && collapseEdge != PanelCollapseEdge.NONE
    val collapseOnTileTapLatest by rememberUpdatedState(panelConfig.collapseOnTileTap)
    val collapseEdgeLatest by rememberUpdatedState(collapseEdge)
    val collapseDelaySecLatest by rememberUpdatedState(panelConfig.collapseOnTileTapDelaySec)
    val isEditModeLatest by rememberUpdatedState(isEditMode)
    val notifyPanelTileTap = remember(panelId, settingsViewModel, collapseAfterTapScope) {
        {
            if (!isEditModeLatest &&
                collapseOnTileTapLatest &&
                collapseEdgeLatest != PanelCollapseEdge.NONE
            ) {
                collapseAfterTapJob?.cancel()
                val delaySec = normalizePanelCollapseOnTileTapDelaySec(collapseDelaySecLatest)
                collapseAfterTapJob = collapseAfterTapScope.launch {
                    delay(delaySec * 1_000L)
                    settingsViewModel.setPanelCollapsed(panelId, true)
                }
            }
        }
    }
    val latestWidgetConfigs by rememberUpdatedState(widgetConfigs)
    val collapseProgress = remember(panelId) {
        Animatable(if (effectiveCollapsed) 1f else 0f)
    }
    /** True while WM is held at expanded size so Compose can animate the strip/content. */
    var hostExpandedForCollapseAnim by remember(panelId) {
        mutableStateOf(!effectiveCollapsed)
    }
    val thicknessPx = with(density) {
        normalizePanelCollapseStripThicknessDp(panelConfig.collapseStripThicknessDp).dp.roundToPx()
    }
    val expandedBounds = remember(
        panelConfig.startX,
        panelConfig.startY,
        panelConfig.width,
        panelConfig.height,
    ) {
        PanelPxBounds(
            x = panelConfig.startX.coerceAtLeast(0),
            y = panelConfig.startY.coerceAtLeast(0),
            width = panelConfig.width.coerceAtLeast(1),
            height = panelConfig.height.coerceAtLeast(1),
        )
    }
    val collapsedBounds = remember(expandedBounds, collapseEdge, thicknessPx) {
        collapsedPanelBounds(expandedBounds, collapseEdge, thicknessPx)
    }

    LaunchedEffect(
        effectiveCollapsed,
        collapseEdge,
        expandedBounds,
        collapsedBounds,
    ) {
        if (collapseEdge == PanelCollapseEdge.NONE) {
            FloatingPanelCollapseAnimationGate.setAnimating(panelId, false)
            hostExpandedForCollapseAnim = true
            if (collapseProgress.value != 0f) {
                collapseProgress.snapTo(0f)
            }
            onUpdateWindowFrame(
                panelId,
                expandedBounds.x,
                expandedBounds.y,
                expandedBounds.width,
                expandedBounds.height,
            )
            return@LaunchedEffect
        }
        val target = if (effectiveCollapsed) 1f else 0f
        if (abs(collapseProgress.value - target) < 0.001f) {
            val rest = if (effectiveCollapsed) collapsedBounds else expandedBounds
            hostExpandedForCollapseAnim = !effectiveCollapsed
            onUpdateWindowFrame(panelId, rest.x, rest.y, rest.width, rest.height)
            return@LaunchedEffect
        }
        FloatingPanelCollapseAnimationGate.setAnimating(panelId, true)
        try {
            // Always animate inside an expanded WM frame, then shrink when fully collapsed.
            onUpdateWindowFrame(
                panelId,
                expandedBounds.x,
                expandedBounds.y,
                expandedBounds.width,
                expandedBounds.height,
            )
            hostExpandedForCollapseAnim = true
            collapseProgress.animateTo(
                targetValue = target,
                animationSpec = tween(durationMillis = PANEL_COLLAPSE_ANIMATION_MS),
            )
            if (effectiveCollapsed) {
                onUpdateWindowFrame(
                    panelId,
                    collapsedBounds.x,
                    collapsedBounds.y,
                    collapsedBounds.width,
                    collapsedBounds.height,
                )
                hostExpandedForCollapseAnim = false
            }
        } finally {
            FloatingPanelCollapseAnimationGate.setAnimating(panelId, false)
        }
    }

    DisposableEffect(panelId) {
        onDispose {
            FloatingPanelCollapseAnimationGate.setAnimating(panelId, false)
        }
    }

    DisposableEffect(panelId, isEditMode) {
        FloatingPanelEditModeTracker.setOverlayEditMode(panelId, isEditMode)
        onDispose {
            FloatingPanelEditModeTracker.setOverlayEditMode(panelId, false)
        }
    }

    LaunchedEffect(isEditMode) {
        if (isEditMode) {
            collapseAfterTapJob?.cancel()
            collapseAfterTapJob = null
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
        delay(2000)
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
                            Modifier.pointerInput(panelId, layoutSnapStepPx) {
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
                                            val newX = snapToGrid(
                                                windowParams.x + dragAmount.x,
                                                layoutSnapStepPx,
                                            ).toInt().coerceAtLeast(0)
                                            val newY = snapToGrid(
                                                windowParams.y + dragAmount.y,
                                                layoutSnapStepPx,
                                            ).toInt().coerceAtLeast(-100)
                                            onUpdateWindowPosition(panelId, newX, newY)
                                        } else if (isResizingMode) {
                                            val newWidth = snapToGrid(
                                                (windowParams.width + dragAmount.x)
                                                    .coerceAtLeast(50f),
                                                layoutSnapStepPx,
                                            ).toInt().coerceAtLeast(50)
                                            val newHeight = snapToGrid(
                                                (windowParams.height + dragAmount.y)
                                                    .coerceAtLeast(50f),
                                                layoutSnapStepPx,
                                            ).toInt().coerceAtLeast(50)
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
                val progress = collapseProgress.value
                val animateInsideExpandedHost =
                    collapseEdge != PanelCollapseEdge.NONE && hostExpandedForCollapseAnim
                val panelContentModifier = if (animateInsideExpandedHost) {
                    val localExpanded = PanelPxBounds(
                        x = 0,
                        y = 0,
                        width = expandedBounds.width,
                        height = expandedBounds.height,
                    )
                    val localCollapsed = collapsedPanelBounds(
                        expanded = localExpanded,
                        edge = collapseEdge,
                        thicknessPx = thicknessPx,
                    )
                    val visual = lerpPanelBounds(localExpanded, localCollapsed, progress)
                    Modifier
                        .offset { IntOffset(visual.x, visual.y) }
                        .size(
                            width = with(density) { visual.width.toDp() },
                            height = with(density) { visual.height.toDp() },
                        )
                } else {
                    Modifier.fillMaxSize()
                }
                Box(modifier = panelContentModifier) {
                val panelShapeDp = normalizePanelShape(panelConfig.panelShape).dp
                val panelBgColor = Color(panelConfig.resolvePanelBackgroundColor(currentTheme))
                val panelBgImagePath = panelConfig.resolvePanelBackgroundImageRelPath(currentTheme)
                    ?.takeIf { it.isNotBlank() }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(panelShapeDp))
                ) {
                DashboardPanelBackgroundUnderlay(
                    relPath = panelBgImagePath,
                    backgroundColor = panelBgColor,
                    shapeDp = panelShapeDp,
                    settingsViewModel = settingsViewModel,
                )
                CollapsiblePanelFrame(
                    edge = collapseEdge,
                    collapsed = effectiveCollapsed,
                    stripThicknessDp = normalizePanelCollapseStripThicknessDp(
                        panelConfig.collapseStripThicknessDp,
                    ),
                    stripColor = Color(panelConfig.resolveStripColor(currentTheme)),
                    stripExpandedColor = Color(panelConfig.resolveStripExpandedColor(currentTheme)),
                    isEditMode = isEditMode,
                    onCollapsedChange = { settingsViewModel.setPanelCollapsed(panelId, it) },
                    modifier = Modifier.fillMaxSize(),
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
                    panelStorageId = panelId,
                    restartEnabled = restartEnabled,
                    onTripFinishAndStart = onTripFinishAndStart,
                    isEditMode = isEditMode,
                    showDialogOpen = false,
                    widgetInteractionPolicy = widgetInteractionPolicy,
                    widgetCardElevation = FLOATING_DASHBOARD_DEFAULT_WIDGET_ELEVATION.dp,
                    onWidgetClick = { index ->
                        val cfg = widgetConfigs.getOrNull(index)
                        if (isEditMode && !isDraggingMode && !isResizingMode) {
                            if (!WindowModeUiGuard.blockEditingIfActive(context)) {
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
                            }
                        } else if (cfg?.dataKey == "steeringWheelHeatWidget") {
                            sendToggleSteeringWheelHeat(context)
                        } else if (cfg?.dataKey == MIRROR_ADJUST_MODE_WIDGET_DATA_KEY) {
                            sendToggleMirrorAdjustMode(context)
                        } else if (cfg?.dataKey == WIPER_MAINTENANCE_WIDGET_DATA_KEY) {
                            sendToggleWiperMaintenance(context)
                        } else if (cfg?.dataKey == PARKING_RADAR_WIDGET_DATA_KEY) {
                            sendToggleParkingRadar(context)
                        } else if (cfg?.dataKey == "frontWindscreenHeatWidget") {
                            sendToggleFrontWindscreenHeat(context)
                        } else if (cfg?.dataKey == "rearWindowMirrorsDefrostWidget") {
                            sendToggleRearWindowMirrorsDefrost(context)
                        } else if (cfg?.dataKey == "hvacAirRecirculationWidget") {
                            sendToggleHvacAirRecirculation(context)
                        } else if (cfg?.dataKey == "hvacAcWidget") {
                            sendToggleHvacAc(context)
                        } else if (cfg?.dataKey == "hvacAcCleanWhenLockedWidget") {
                            sendToggleHvacAcCleanWhenLocked(context)
                        } else if (cfg?.dataKey == "hvacAutoWidget") {
                            sendToggleHvacAuto(context)
                        } else if (cfg?.dataKey == "hvacDefrosterFrontWidget") {
                            sendToggleHvacDefrosterFront(context)
                        } else if (cfg?.dataKey == HVAC_SYNC_WIDGET_DATA_KEY) {
                            sendToggleHvacSync(context)
                        } else if (cfg?.dataKey == DRIVE_MODE_WIDGET_DATA_KEY) {
                            val selectedMode = resolveDriveModeWidgetOption(cfg.selectedDriveMode)
                            sendSetMbCanProperty(
                                context = context,
                                propertyId = selectedMode.propertyId,
                                value = selectedMode.propertyValue
                            )
                        } else if (cfg?.dataKey == DRIVE_MODE_CYCLE_WIDGET_DATA_KEY) {
                            val currentRaw = resolveDriveModeCycleCurrentRaw(
                                UniversalCanRepository.carSettingsDriveMode.value,
                                UniversalCanRepository.carSettingsDriveMode6dctWet.value,
                                cfg.selectedDriveModes,
                            )
                            val nextMode = nextDriveModeCycleTarget(
                                currentRaw,
                                cfg.selectedDriveModes,
                            )
                            sendSetMbCanProperty(
                                context = context,
                                propertyId = nextMode.propertyId,
                                value = nextMode.propertyValue
                            )
                        } else if (
                            cfg?.dataKey == APP_LAUNCHER_WIDGET_DATA_KEY &&
                            cfg.launcherAppPackage.isNotBlank()
                        ) {
                            launchAppFromWidget(context, cfg, settingsViewModel)
                        } else if (isFloatingDashboardClickAction) {
                            if (cfg != null && isActiveTripWidgetDataKey(cfg.dataKey)) {
                                settingsViewModel.saveSelectedTab(SettingsManager.TRIPS_TAB_KEY)
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
                    onRoadMatchHeadingUpChange = { index, headingUp ->
                        persistDashboardPanelRoadMatchHeadingUp(
                            currentWidgetConfigs = latestWidgetConfigs,
                            widgetIndex = index,
                            headingUp = headingUp,
                            saveConfigs = { configs ->
                                settingsViewModel.saveFloatingDashboardWidgets(panelId, configs)
                            },
                        )
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
                    gridSpacingDp = panelConfig.gridSpacingDp.dp,
                    externalWidgetHost = appWidgetHost,
                    onPanelTileTap = notifyPanelTileTap,
                )
                }
                }
                }
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
