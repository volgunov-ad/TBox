package vad.dashing.tbox.ui

import android.appwidget.AppWidgetHost
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.runBlocking
import vad.dashing.tbox.mbcan.MbCanRepository
import vad.dashing.tbox.AppDataViewModel
import vad.dashing.tbox.CanDataViewModel
import vad.dashing.tbox.DashboardManager
import vad.dashing.tbox.DashboardState
import vad.dashing.tbox.FloatingDashboardWidgetConfig
import vad.dashing.tbox.HIDE_FLOATING_PANELS_WIDGET_DATA_KEY
import vad.dashing.tbox.TOGGLE_FLOATING_PANELS_ENABLED_WIDGET_DATA_KEY
import vad.dashing.tbox.MUSIC_WIDGET_DATA_KEY
import vad.dashing.tbox.R
import vad.dashing.tbox.TboxViewModel
import vad.dashing.tbox.SettingsViewModel
import vad.dashing.tbox.normalizeWidgetConfigs
import vad.dashing.tbox.normalizeWidgetScale
import vad.dashing.tbox.normalizeWidgetShape

/**
 * Shared widget grid and frame overlays for floating overlay panels and MainScreen embedded panels.
 */
@Composable
internal fun DashboardPanelGridAndFrames(
    mbCanInterestSourceId: String,
    dashboardRows: Int,
    dashboardCols: Int,
    dashboardState: DashboardState,
    widgetConfigs: List<FloatingDashboardWidgetConfig>,
    settingsViewModel: SettingsViewModel,
    tboxViewModel: TboxViewModel,
    canViewModel: CanDataViewModel,
    appDataViewModel: AppDataViewModel,
    dataProvider: TboxDataProvider,
    dashboardManager: DashboardManager,
    dashboardChart: Boolean,
    tboxConnected: Boolean,
    currentTheme: Int,
    restartEnabled: Boolean,
    onTripFinishAndStart: () -> Unit,
    isEditMode: Boolean,
    showDialogOpen: Boolean,
    widgetInteractionPolicy: DashboardWidgetInteractionPolicy,
    widgetCardElevation: Dp,
    onWidgetClick: (widgetIndex: Int) -> Unit,
    onWidgetLongClick: () -> Unit,
    onMusicSelectedPlayerChange: (widgetIndex: Int, selectedPackage: String) -> Unit,
    onHideFloatingPanelsDoubleClick: (widgetIndex: Int) -> Unit = {},
    onToggleFloatingPanelsEnabledDoubleClick: (widgetIndex: Int) -> Unit = {},
    onRestartRequested: () -> Unit,
    showTboxDisconnectIndicator: Boolean,
    enableMusicInnerInteractions: Boolean,
    externalWidgetHost: AppWidgetHost? = null,
    gridSpacingDp: Dp = 4.dp,
    fuelTankLiters: Int = 57,
) {
    val normalizedConfigs = rememberWidgetConfigsForPanel(widgetConfigs, dashboardRows * dashboardCols)
    val panelNeedsMbCan = remember(widgetConfigs) {
        MbCanRepository.widgetConfigsNeedMbCan(widgetConfigs.map { it.dataKey })
    }
    if (panelNeedsMbCan) {
        LaunchedEffect(mbCanInterestSourceId, widgetConfigs) {
            val activeKeys = widgetConfigs
                .map { it.dataKey.trim() }
                .filter { it.isNotBlank() && it != "null" }
                .toSet()
            MbCanRepository.setSourceWidgetKeys(mbCanInterestSourceId, activeKeys)
        }
        DisposableEffect(mbCanInterestSourceId) {
            onDispose {
                runBlocking {
                    MbCanRepository.clearSource(mbCanInterestSourceId)
                }
            }
        }
    }
    val hasConfiguredWidgets = normalizedConfigs.any { config ->
        config.dataKey.isNotBlank() && config.dataKey != "null"
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(0.dp),
        verticalArrangement = Arrangement.spacedBy(gridSpacingDp)
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
            for (row in 0 until dashboardRows) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(gridSpacingDp)
                ) {
                    for (col in 0 until dashboardCols) {
                        val index = row * dashboardCols + col
                        val widget = dashboardState.widgets.getOrNull(index) ?: continue
                        val widgetConfig = normalizedConfigs.getOrNull(index)
                            ?: FloatingDashboardWidgetConfig(dataKey = "")
                        val widgetTextScale = normalizeWidgetScale(widgetConfig.scale)
                        val widgetTextColor = widget.resolveTextColorForTheme(currentTheme)
                        val widgetBackgroundColor =
                            widget.resolveBackgroundColorForTheme(currentTheme)

                        Box(modifier = Modifier.weight(1f)) {
                            if (isEditMode) {
                                Canvas(
                                    modifier = Modifier.matchParentSize()
                                ) {
                                    drawRect(
                                        color = Color(0x7E00BCD4),
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
                                    )
                                }
                            }
                            CompositionLocalProvider(
                                LocalWidgetTextScale provides widgetTextScale,
                                LocalDashboardWidgetInteractionPolicy provides widgetInteractionPolicy
                            ) {
                                DashboardWidgetRenderer(
                                    widget = widget,
                                    widgetConfig = widgetConfig,
                                    settingsViewModel = settingsViewModel,
                                    tboxViewModel = tboxViewModel,
                                    canViewModel = canViewModel,
                                    appDataViewModel = appDataViewModel,
                                    dataProvider = dataProvider,
                                    dashboardManager = dashboardManager,
                                    dashboardChart = dashboardChart,
                                    tboxConnected = tboxConnected,
                                    restartEnabled = restartEnabled,
                                    onTripFinishAndStart = onTripFinishAndStart,
                                    widgetTextColor = widgetTextColor,
                                    widgetBackgroundColor = widgetBackgroundColor,
                                    onClick = { onWidgetClick(index) },
                                    onLongClick = onWidgetLongClick,
                                    onMusicSelectedPlayerChange = { selectedPackage ->
                                        onMusicSelectedPlayerChange(index, selectedPackage)
                                    },
                                    onHideFloatingPanelsDoubleClick = {
                                        if (widget.dataKey == HIDE_FLOATING_PANELS_WIDGET_DATA_KEY) {
                                            onHideFloatingPanelsDoubleClick(index)
                                        }
                                    },
                                    onToggleFloatingPanelsEnabledDoubleClick = {
                                        if (widget.dataKey == TOGGLE_FLOATING_PANELS_ENABLED_WIDGET_DATA_KEY) {
                                            onToggleFloatingPanelsEnabledDoubleClick(index)
                                        }
                                    },
                                    onRestartRequested = onRestartRequested,
                                    externalWidgetHost = externalWidgetHost,
                                    isEditMode = isEditMode,
                                    elevation = widgetCardElevation,
                                    shape = normalizeWidgetShape(widgetConfig.shape).dp,
                                    enableMusicInnerInteractions = enableMusicInnerInteractions,
                                    fuelTankLiters = fuelTankLiters
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    val showEditIndicators = isEditMode && !showDialogOpen
    val showTboxDisconnectFrame = showTboxDisconnectIndicator && !tboxConnected
    if (!hasConfiguredWidgets || showTboxDisconnectFrame || showEditIndicators) {
        Canvas(
            modifier = Modifier.matchParentSize()
        ) {
            val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
            if (!hasConfiguredWidgets) {
                val inset = 4.dp.toPx()
                drawRect(
                    color = Color(0xFF008507),
                    topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                    size = androidx.compose.ui.geometry.Size(
                        width = (size.width - inset * 2f).coerceAtLeast(0f),
                        height = (size.height - inset * 2f).coerceAtLeast(0f)
                    ),
                    style = stroke
                )
            }
            if (showTboxDisconnectFrame) {
                drawRect(
                    color = Color(0xD9FF9800),
                    style = stroke
                )
            }
            if (showEditIndicators) {
                val editInset = 2.dp.toPx()
                drawRect(
                    color = Color(0xFF00BCD4),
                    topLeft = androidx.compose.ui.geometry.Offset(editInset, editInset),
                    size = androidx.compose.ui.geometry.Size(
                        width = (size.width - editInset * 2f).coerceAtLeast(0f),
                        height = (size.height - editInset * 2f).coerceAtLeast(0f)
                    ),
                    style = stroke
                )
                val topLeft = resizeHandleAreaTopLeft(
                    width = size.width,
                    height = size.height
                )
                val handleSize = resizeHandleAreaSize(
                    width = size.width,
                    height = size.height
                )
                drawRect(
                    color = Color(0xFF00BCD4),
                    topLeft = topLeft,
                    size = handleSize,
                    style = stroke
                )
            }
        }
    }
    }
}

@Composable
private fun rememberWidgetConfigsForPanel(
    widgetConfigs: List<FloatingDashboardWidgetConfig>,
    widgetCount: Int
): List<FloatingDashboardWidgetConfig> {
    return remember(widgetConfigs, widgetCount) {
        normalizeWidgetConfigs(widgetConfigs, widgetCount)
    }
}

fun persistDashboardPanelMediaSelectedPlayer(
    currentWidgetConfigs: List<FloatingDashboardWidgetConfig>,
    widgetIndex: Int,
    selectedPackage: String,
    saveConfigs: (List<FloatingDashboardWidgetConfig>) -> Unit
) {
    val normalizedConfigs = normalizeWidgetConfigs(
        configs = currentWidgetConfigs,
        widgetCount = currentWidgetConfigs.size
    ).toMutableList()
    val currentConfig = normalizedConfigs.getOrNull(widgetIndex) ?: return
    if (currentConfig.dataKey != MUSIC_WIDGET_DATA_KEY) return
    if (currentConfig.mediaSelectedPlayer == selectedPackage) return

    normalizedConfigs[widgetIndex] = currentConfig.copy(
        mediaSelectedPlayer = selectedPackage
    )
    saveConfigs(normalizedConfigs)
}
