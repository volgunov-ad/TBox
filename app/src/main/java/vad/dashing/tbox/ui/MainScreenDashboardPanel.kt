package vad.dashing.tbox.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
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
import vad.dashing.tbox.AppDataViewModel
import vad.dashing.tbox.CanDataViewModel
import vad.dashing.tbox.DEFAULT_WIDGET_BACKGROUND_COLOR_DARK_FLOATING
import vad.dashing.tbox.DEFAULT_WIDGET_BACKGROUND_COLOR_LIGHT_FLOATING
import vad.dashing.tbox.FLOATING_DASHBOARD_DEFAULT_WIDGET_ELEVATION
import vad.dashing.tbox.ExternalWidgetHostManager
import vad.dashing.tbox.FloatingDashboardViewModel
import vad.dashing.tbox.normalizePanelShape
import vad.dashing.tbox.resolvePanelBackgroundColor
import vad.dashing.tbox.resolvePanelBackgroundImageRelPath
import vad.dashing.tbox.FloatingDashboardViewModelFactory
import vad.dashing.tbox.MainScreenPanelConfig
import vad.dashing.tbox.MainScreenPanelInterestIds
import vad.dashing.tbox.PanelCollapseEdge
import vad.dashing.tbox.normalizePanelCollapseOnTileTapDelaySec
import vad.dashing.tbox.PanelCollapseStates
import vad.dashing.tbox.PanelPxBounds
import vad.dashing.tbox.SettingsManager
import vad.dashing.tbox.SettingsViewModel
import vad.dashing.tbox.SharedMediaControlService
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
import vad.dashing.tbox.collectMediaPlayersFromWidgetConfigs
import vad.dashing.tbox.loadWidgetsFromConfig
import vad.dashing.tbox.MIN_MAIN_SCREEN_PANEL_REL_FRACTION
import vad.dashing.tbox.normalizePanelLayoutSnapDp
import vad.dashing.tbox.maybeSnapToGrid
import vad.dashing.tbox.resolveDriveModeWidgetOption
import vad.dashing.tbox.nextDriveModeCycleTarget
import vad.dashing.tbox.resolveDriveModeCycleCurrentRaw
import vad.dashing.tbox.mbcan.UniversalCanRepository
import vad.dashing.tbox.collapseEdgeOrNone
import vad.dashing.tbox.collapsedPanelBounds
import vad.dashing.tbox.lerpPanelBounds
import vad.dashing.tbox.normalizePanelCollapseStripThicknessDp
import vad.dashing.tbox.PANEL_COLLAPSE_ANIMATION_MS
import vad.dashing.tbox.resolveStripColor
import vad.dashing.tbox.resolveStripExpandedColor
import vad.dashing.tbox.freeform.WindowModeUiGuard
import kotlin.math.roundToInt

private data class PanelPxLayout(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
)

private fun panelLayoutFromRel(
    relX: Float,
    relY: Float,
    relW: Float,
    relH: Float,
    containerW: Float,
    containerH: Float,
): PanelPxLayout {
    val w = (relW * containerW).coerceIn(1f, containerW)
    val h = (relH * containerH).coerceIn(1f, containerH)
    val rangeX = (containerW - w).coerceAtLeast(0f)
    val rangeY = (containerH - h).coerceAtLeast(0f)
    return PanelPxLayout(
        x = (relX * rangeX).coerceIn(0f, rangeX),
        y = (relY * rangeY).coerceIn(0f, rangeY),
        width = w,
        height = h,
    )
}

private fun panelPxToRel(layout: PanelPxLayout, containerW: Float, containerH: Float): MainScreenRelLayout {
    val relW = (layout.width / containerW).coerceIn(MIN_MAIN_SCREEN_PANEL_REL_FRACTION, 1f)
    val relH = (layout.height / containerH).coerceIn(MIN_MAIN_SCREEN_PANEL_REL_FRACTION, 1f)
    val w = relW * containerW
    val h = relH * containerH
    val rangeX = (containerW - w).coerceAtLeast(1f)
    val rangeY = (containerH - h).coerceAtLeast(1f)
    return MainScreenRelLayout(
        relX = (layout.x / rangeX).coerceIn(0f, 1f),
        relY = (layout.y / rangeY).coerceIn(0f, 1f),
        relWidth = relW,
        relHeight = relH,
    )
}

private data class MainScreenRelLayout(
    val relX: Float,
    val relY: Float,
    val relWidth: Float,
    val relHeight: Float,
)

@Composable
fun MainScreenDashboardPanel(
    panel: MainScreenPanelConfig,
    containerWidthPx: Float,
    containerHeightPx: Float,
    tboxViewModel: TboxViewModel,
    canViewModel: CanDataViewModel,
    appDataViewModel: AppDataViewModel,
    settingsViewModel: SettingsViewModel,
    onRebootTbox: () -> Unit,
    onTripFinishAndStart: () -> Unit,
    windowMode: Boolean = false,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val minPanelPx = with(density) { 50.dp.toPx() }
    val mainScreenPanelsLayoutSnapDp by
        settingsViewModel.mainScreenPanelsLayoutSnapDp.collectAsStateWithLifecycle()
    val mainScreenPanelsLayoutSnapEnabled by
        settingsViewModel.mainScreenPanelsLayoutSnapEnabled.collectAsStateWithLifecycle()
    val layoutSnapDp = normalizePanelLayoutSnapDp(mainScreenPanelsLayoutSnapDp)
    val layoutSnapStepPx = with(density) { layoutSnapDp.dp.toPx() }
    val effectiveLayoutSnapStepPx =
        if (mainScreenPanelsLayoutSnapEnabled) layoutSnapStepPx else 0f
    val appWidgetHost = remember(context) { ExternalWidgetHostManager.acquireHost(context) }

    DisposableEffect(appWidgetHost) {
        onDispose {
            ExternalWidgetHostManager.releaseHost()
        }
    }

    val vmKey = "main-screen-${panel.id}"
    val dashboardViewModel: FloatingDashboardViewModel = viewModel(
        key = vmKey,
        factory = FloatingDashboardViewModelFactory(vmKey)
    )
    val dashboardState by dashboardViewModel.dashboardManager.dashboardState.collectAsStateWithLifecycle()

    val widgetConfigs = panel.widgetsConfig
    val dashboardRows = panel.rows
    val dashboardCols = panel.cols
    val mediaSourceId = remember(panel.id, windowMode) {
        MainScreenPanelInterestIds.mediaSourceId(panel.id, windowMode)
    }
    val mbCanInterestSourceId = remember(panel.id, windowMode) {
        MainScreenPanelInterestIds.mbCanInterestSourceId(panel.id, windowMode)
    }
    val requestedMediaPlayers = remember(widgetConfigs) {
        collectMediaPlayersFromWidgetConfigs(widgetConfigs)
    }

    val tboxConnected by tboxViewModel.tboxConnected.collectAsStateWithLifecycle()
    val currentTheme by tboxViewModel.currentTheme.collectAsStateWithLifecycle()
    val themeActivating by settingsViewModel.themeActivationInProgress.collectAsStateWithLifecycle()
    val panelCollapseStates by settingsViewModel.panelCollapseStates.collectAsStateWithLifecycle()

    var isEditMode by remember { mutableStateOf(false) }
    var showDialogForIndex by remember { mutableStateOf<Int?>(null) }
    var isDraggingMode by remember { mutableStateOf(false) }
    var isResizingMode by remember { mutableStateOf(false) }
    var pendingMusicSelection by remember(panel.id) { mutableStateOf<Pair<Int, String>?>(null) }
    var pendingSeatHeatVentVariant by remember(panel.id) { mutableStateOf<Pair<Int, Int>?>(null) }
    val canManipulatePanel = isEditMode && showDialogForIndex == null
    val latestWidgetConfigs by rememberUpdatedState(widgetConfigs)
    val collapseAfterTapScope = rememberCoroutineScope()
    var collapseAfterTapJob by remember(panel.id) { mutableStateOf<Job?>(null) }

    var layoutInteraction by remember { mutableStateOf(false) }
    var layoutPx by remember(panel.id) {
        mutableStateOf(
            panelLayoutFromRel(
                panel.relX,
                panel.relY,
                panel.relWidth,
                panel.relHeight,
                containerWidthPx.coerceAtLeast(1f),
                containerHeightPx.coerceAtLeast(1f)
            )
        )
    }

    LaunchedEffect(
        panel.relX,
        panel.relY,
        panel.relWidth,
        panel.relHeight,
        containerWidthPx,
        containerHeightPx
    ) {
        if (layoutInteraction) return@LaunchedEffect
        val cw = containerWidthPx.coerceAtLeast(1f)
        val ch = containerHeightPx.coerceAtLeast(1f)
        layoutPx = panelLayoutFromRel(panel.relX, panel.relY, panel.relWidth, panel.relHeight, cw, ch)
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
                showDialogForIndex = null
            }
        }
    }
    LaunchedEffect(pendingMusicSelection, panel.id) {
        val pending = pendingMusicSelection ?: return@LaunchedEffect
        delay(2000)
        if (pendingMusicSelection != pending) return@LaunchedEffect
        persistDashboardPanelMediaSelectedPlayer(
            currentWidgetConfigs = latestWidgetConfigs,
            widgetIndex = pending.first,
            selectedPackage = pending.second,
            saveConfigs = { configs ->
                settingsViewModel.saveMainScreenDashboardWidgets(panel.id, configs)
            }
        )
        if (pendingMusicSelection == pending) {
            pendingMusicSelection = null
        }
    }
    LaunchedEffect(pendingSeatHeatVentVariant, panel.id) {
        val pending = pendingSeatHeatVentVariant ?: return@LaunchedEffect
        delay(2000)
        if (pendingSeatHeatVentVariant != pending) return@LaunchedEffect
        persistDashboardPanelSeatHeatVentSelectedVariant(
            currentWidgetConfigs = latestWidgetConfigs,
            widgetIndex = pending.first,
            selectedVariant = pending.second,
            saveConfigs = { configs ->
                settingsViewModel.saveMainScreenDashboardWidgets(panel.id, configs)
            }
        )
        if (pendingSeatHeatVentVariant == pending) {
            pendingSeatHeatVentVariant = null
        }
    }

    val dataProvider = remember(context) {
        TboxDataProvider(tboxViewModel, canViewModel, appDataViewModel, settingsViewModel, context)
    }

    LaunchedEffect(widgetConfigs, dashboardRows, dashboardCols, context, themeActivating) {
        if (themeActivating) return@LaunchedEffect
        val totalWidgets = dashboardRows * dashboardCols
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
                mode = DashboardWidgetInteractionMode.EDIT
            )
        } else {
            DashboardWidgetInteractionPolicy()
        }
    }

    LaunchedEffect(restartEnabled) {
        if (!restartEnabled) {
            delay(15000)
            restartEnabled = true
        }
    }

    val cw = containerWidthPx.coerceAtLeast(1f)
    val ch = containerHeightPx.coerceAtLeast(1f)
    val collapseEdge = panel.collapseEdgeOrNone()
    val panelCollapsed = PanelCollapseStates.isCollapsed(panelCollapseStates, panel.id)
    val effectiveCollapsed = panelCollapsed && !isEditMode && collapseEdge != PanelCollapseEdge.NONE
    val collapseOnTileTapLatest by rememberUpdatedState(panel.collapseOnTileTap)
    val collapseEdgeLatest by rememberUpdatedState(collapseEdge)
    val collapseDelaySecLatest by rememberUpdatedState(panel.collapseOnTileTapDelaySec)
    val isEditModeLatest by rememberUpdatedState(isEditMode)
    val notifyPanelTileTap = remember(panel.id, settingsViewModel, collapseAfterTapScope) {
        {
            if (!isEditModeLatest &&
                collapseOnTileTapLatest &&
                collapseEdgeLatest != PanelCollapseEdge.NONE
            ) {
                collapseAfterTapJob?.cancel()
                val delaySec = normalizePanelCollapseOnTileTapDelaySec(collapseDelaySecLatest)
                collapseAfterTapJob = collapseAfterTapScope.launch {
                    delay(delaySec * 1_000L)
                    settingsViewModel.setPanelCollapsed(panel.id, true)
                }
            }
        }
    }
    val collapseProgress by animateFloatAsState(
        targetValue = if (effectiveCollapsed) 1f else 0f,
        animationSpec = tween(durationMillis = PANEL_COLLAPSE_ANIMATION_MS),
        label = "mainPanelCollapse",
    )
    val expandedBounds = PanelPxBounds(
        x = layoutPx.x.roundToInt(),
        y = layoutPx.y.roundToInt(),
        width = layoutPx.width.roundToInt(),
        height = layoutPx.height.roundToInt(),
    )
    val collapsedBounds = collapsedPanelBounds(
        expanded = expandedBounds,
        edge = collapseEdge,
        thicknessPx = with(density) {
            normalizePanelCollapseStripThicknessDp(panel.collapseStripThicknessDp).dp.roundToPx()
        },
    )
    val displayedBounds = lerpPanelBounds(expandedBounds, collapsedBounds, collapseProgress)
    val resizeHandleWidthDp = with(density) { resizeHandleOffsetForDimension(layoutPx.width).toDp() }
    val resizeHandleHeightDp = with(density) { resizeHandleOffsetForDimension(layoutPx.height).toDp() }

    Box(
        modifier = Modifier
            .offset {
                IntOffset(displayedBounds.x, displayedBounds.y)
            }
            .size(
                width = with(density) { displayedBounds.width.toDp() },
                height = with(density) { displayedBounds.height.toDp() }
            )
            .background(Color.Transparent)
            .then(
                if (canManipulatePanel) {
                    // Do not use layoutPx width/height as keys — they change during resize and cancel the gesture.
                    Modifier.pointerInput(panel.id, cw, ch, minPanelPx, effectiveLayoutSnapStepPx) {
                        detectDragGestures(
                            onDragStart = { startOffset ->
                                layoutInteraction = true
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
                                    val maxX = (cw - layoutPx.width).coerceAtLeast(0f)
                                    val maxY = (ch - layoutPx.height).coerceAtLeast(0f)
                                    layoutPx = layoutPx.copy(
                                        x = maybeSnapToGrid(
                                            layoutPx.x + dragAmount.x,
                                            effectiveLayoutSnapStepPx,
                                        ).coerceIn(0f, maxX),
                                        y = maybeSnapToGrid(
                                            layoutPx.y + dragAmount.y,
                                            effectiveLayoutSnapStepPx,
                                        ).coerceIn(0f, maxY),
                                    )
                                } else if (isResizingMode) {
                                    val newW = maybeSnapToGrid(
                                        (layoutPx.width + dragAmount.x)
                                            .coerceIn(minPanelPx, cw - layoutPx.x),
                                        effectiveLayoutSnapStepPx,
                                    ).coerceIn(minPanelPx, cw - layoutPx.x)
                                    val newH = maybeSnapToGrid(
                                        (layoutPx.height + dragAmount.y)
                                            .coerceIn(minPanelPx, ch - layoutPx.y),
                                        effectiveLayoutSnapStepPx,
                                    ).coerceIn(minPanelPx, ch - layoutPx.y)
                                    layoutPx = layoutPx.copy(width = newW, height = newH)
                                }
                            },
                            onDragEnd = {
                                val rel = panelPxToRel(layoutPx, cw, ch)
                                settingsViewModel.saveMainScreenPanelLayout(
                                    panel.id,
                                    rel.relX,
                                    rel.relY,
                                    rel.relWidth,
                                    rel.relHeight
                                )
                                isDraggingMode = false
                                isResizingMode = false
                                layoutInteraction = false
                            },
                            onDragCancel = {
                                isDraggingMode = false
                                isResizingMode = false
                                layoutInteraction = false
                                layoutPx = panelLayoutFromRel(
                                    panel.relX,
                                    panel.relY,
                                    panel.relWidth,
                                    panel.relHeight,
                                    cw,
                                    ch
                                )
                            }
                        )
                    }
                } else {
                    Modifier
                }
            )
    ) {
        val panelShapeDp = normalizePanelShape(panel.panelShape).dp
        val panelBgColor = Color(panel.resolvePanelBackgroundColor(currentTheme))
        val panelBgImagePath = panel.resolvePanelBackgroundImageRelPath(currentTheme)
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
                stripThicknessDp = normalizePanelCollapseStripThicknessDp(panel.collapseStripThicknessDp),
                stripColor = Color(panel.resolveStripColor(currentTheme)),
                stripExpandedColor = Color(panel.resolveStripExpandedColor(currentTheme)),
                isEditMode = isEditMode,
                onCollapsedChange = { settingsViewModel.setPanelCollapsed(panel.id, it) },
                modifier = Modifier.fillMaxSize(),
            ) {
        DashboardPanelGridAndFrames(
            mbCanInterestSourceId = mbCanInterestSourceId,
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
            panelStorageId = panel.id,
            restartEnabled = restartEnabled,
            onTripFinishAndStart = onTripFinishAndStart,
            isEditMode = isEditMode,
            showDialogOpen = showDialogForIndex != null,
            widgetInteractionPolicy = widgetInteractionPolicy,
            widgetCardElevation = FLOATING_DASHBOARD_DEFAULT_WIDGET_ELEVATION.dp,
            onWidgetClick = { index ->
                val cfg = widgetConfigs.getOrNull(index)
                if (isEditMode && !isDraggingMode && !isResizingMode) {
                    if (windowMode) {
                        WindowModeUiGuard.toastEditingBlocked(context)
                    } else {
                        showDialogForIndex = index
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
                } else if (
                    panel.clickAction &&
                    cfg != null &&
                    isActiveTripWidgetDataKey(cfg.dataKey)
                ) {
                    settingsViewModel.saveSelectedTab(SettingsManager.TRIPS_TAB_KEY)
                }
            },
            onWidgetLongClick = {
                if (windowMode) {
                    WindowModeUiGuard.toastEditingBlocked(context)
                } else {
                    isEditMode = !isEditMode
                    isDraggingMode = false
                    isResizingMode = false
                }
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
                        settingsViewModel.saveMainScreenDashboardWidgets(panel.id, configs)
                    },
                )
            },
            onHideFloatingPanelsDoubleClick = {
                val cfg = widgetConfigs.getOrNull(it)
                if (cfg?.dataKey == HIDE_FLOATING_PANELS_WIDGET_DATA_KEY) {
                    sendToggleHideOtherFloatingPanels(
                        context = context,
                        originPanelId = "",
                        excludeOriginPanel = false
                    )
                }
            },
            onToggleFloatingPanelsEnabledDoubleClick = {
                val cfg = widgetConfigs.getOrNull(it)
                if (cfg?.dataKey == TOGGLE_FLOATING_PANELS_ENABLED_WIDGET_DATA_KEY) {
                    sendToggleFloatingPanelsEnabled(
                        context = context,
                        originPanelId = "",
                        toggleAllPanels = true
                    )
                }
            },
            onRestartRequested = {
                if (restartEnabled) {
                    restartEnabled = false
                    onRebootTbox()
                }
            },
            showTboxDisconnectIndicator = panel.showTboxDisconnectIndicator,
            enableInnerInteractions = !isEditMode,
            gridSpacingDp = panel.gridSpacingDp.dp,
            externalWidgetHost = appWidgetHost,
            onPanelTileTap = notifyPanelTileTap,
        )
        }
        if (isEditMode) {
            // Reserve the panel-level resize corner so long-press there cannot toggle tile edit mode.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(resizeHandleWidthDp, resizeHandleHeightDp)
                    .pointerInput(panel.id, isEditMode, resizeHandleWidthDp, resizeHandleHeightDp) {
                        detectTapGestures(
                            onTap = {},
                            onLongPress = {}
                        )
                    }
            )
        }
        }
    }

    if (!windowMode) {
        showDialogForIndex?.let { index ->
            MainScreenPanelWidgetSelectionDialog(
                dashboardManager = dashboardViewModel.dashboardManager,
                settingsViewModel = settingsViewModel,
                panelId = panel.id,
                widgetIndex = index,
                currentWidgets = dashboardState.widgets,
                currentWidgetConfigs = widgetConfigs,
                currentTheme = currentTheme,
                onDismiss = { showDialogForIndex = null },
                onDeletePanel = { settingsViewModel.deleteMainScreenDashboard(panel.id) }
            )
        }
    }
}
