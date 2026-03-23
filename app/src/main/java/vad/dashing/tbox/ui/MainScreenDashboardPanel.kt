package vad.dashing.tbox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import vad.dashing.tbox.AppDataViewModel
import vad.dashing.tbox.CanDataViewModel
import vad.dashing.tbox.DEFAULT_WIDGET_BACKGROUND_COLOR_DARK_FLOATING
import vad.dashing.tbox.DEFAULT_WIDGET_BACKGROUND_COLOR_LIGHT_FLOATING
import vad.dashing.tbox.FLOATING_DASHBOARD_DEFAULT_WIDGET_ELEVATION
import vad.dashing.tbox.FloatingDashboardViewModel
import vad.dashing.tbox.FloatingDashboardViewModelFactory
import vad.dashing.tbox.ExternalWidgetHostManager
import vad.dashing.tbox.MainScreenPanelConfig
import vad.dashing.tbox.SettingsViewModel
import vad.dashing.tbox.SharedMediaControlService
import vad.dashing.tbox.APP_LAUNCHER_WIDGET_DATA_KEY
import vad.dashing.tbox.TboxViewModel
import vad.dashing.tbox.collectMediaPlayersFromWidgetConfigs
import vad.dashing.tbox.loadWidgetsFromConfig
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
    val relW = (layout.width / containerW).coerceIn(0.08f, 1f)
    val relH = (layout.height / containerH).coerceIn(0.08f, 1f)
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
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val minPanelPx = with(density) { 80.dp.toPx() }
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
    val mediaSourceId = remember(panel.id) { "main-screen-dashboard-${panel.id}" }
    val requestedMediaPlayers = remember(widgetConfigs) {
        collectMediaPlayersFromWidgetConfigs(widgetConfigs)
    }

    val tboxConnected by tboxViewModel.tboxConnected.collectAsStateWithLifecycle()
    val currentTheme by tboxViewModel.currentTheme.collectAsStateWithLifecycle()

    var isEditMode by remember { mutableStateOf(false) }
    var showDialogForIndex by remember { mutableStateOf<Int?>(null) }
    var isDraggingMode by remember { mutableStateOf(false) }
    var isResizingMode by remember { mutableStateOf(false) }
    val canManipulatePanel = isEditMode && showDialogForIndex == null

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
            delay(300000)
            if (isEditMode) {
                isEditMode = false
                isDraggingMode = false
                isResizingMode = false
                showDialogForIndex = null
            }
        }
    }

    val dataProvider = remember(context) {
        TboxDataProvider(tboxViewModel, canViewModel, appDataViewModel, context)
    }

    LaunchedEffect(widgetConfigs, dashboardRows, dashboardCols, context) {
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
                mode = DashboardWidgetInteractionMode.EDIT,
                exclusions = listOf(ResizeHandleWidgetHitExclusion)
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

    Box(
        modifier = Modifier
            .offset {
                IntOffset(layoutPx.x.roundToInt(), layoutPx.y.roundToInt())
            }
            .size(
                width = with(density) { layoutPx.width.toDp() },
                height = with(density) { layoutPx.height.toDp() }
            )
            .background(Color.Transparent)
            .then(
                if (canManipulatePanel) {
                    // Do not use layoutPx width/height as keys — they change during resize and cancel the gesture.
                    Modifier.pointerInput(panel.id, cw, ch, minPanelPx) {
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
                                    layoutPx = layoutPx.copy(
                                        x = (layoutPx.x + dragAmount.x).coerceIn(
                                            0f,
                                            (cw - layoutPx.width).coerceAtLeast(0f)
                                        ),
                                        y = (layoutPx.y + dragAmount.y).coerceIn(
                                            0f,
                                            (ch - layoutPx.height).coerceAtLeast(0f)
                                        )
                                    )
                                } else if (isResizingMode) {
                                    val newW = (layoutPx.width + dragAmount.x)
                                        .coerceIn(minPanelPx, cw - layoutPx.x)
                                    val newH = (layoutPx.height + dragAmount.y)
                                        .coerceIn(minPanelPx, ch - layoutPx.y)
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
                } else if (panel.clickAction) {
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
                        settingsViewModel.saveMainScreenDashboardWidgets(panel.id, configs)
                    }
                )
            },
            onRestartRequested = {
                if (restartEnabled) {
                    restartEnabled = false
                    onRebootTbox()
                }
            },
            showTboxDisconnectIndicator = panel.showTboxDisconnectIndicator,
            enableMusicInnerInteractions = !isEditMode,
            externalWidgetHost = appWidgetHost
        )
    }

    showDialogForIndex?.let { index ->
        MainScreenPanelWidgetSelectionDialog(
            dashboardManager = dashboardViewModel.dashboardManager,
            settingsViewModel = settingsViewModel,
            panelId = panel.id,
            widgetIndex = index,
            currentWidgets = dashboardState.widgets,
            currentWidgetConfigs = widgetConfigs,
            onDismiss = { showDialogForIndex = null },
            onDeletePanel = { settingsViewModel.deleteMainScreenDashboard(panel.id) }
        )
    }
}
