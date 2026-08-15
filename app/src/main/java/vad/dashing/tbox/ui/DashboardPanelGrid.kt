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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.TileBackgroundImageStorage
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import vad.dashing.tbox.ui.theme.tboxBody
import vad.dashing.tbox.mbcan.UniversalCanRepository
import vad.dashing.tbox.AppDataViewModel
import vad.dashing.tbox.CanDataViewModel
import vad.dashing.tbox.DashboardManager
import vad.dashing.tbox.DashboardState
import vad.dashing.tbox.FloatingDashboardWidgetConfig
import vad.dashing.tbox.HIDE_FLOATING_PANELS_WIDGET_DATA_KEY
import vad.dashing.tbox.TOGGLE_FLOATING_PANELS_ENABLED_WIDGET_DATA_KEY
import vad.dashing.tbox.isMusicWidgetDataKey
import vad.dashing.tbox.isRoadMatchMapWidgetDataKey
import vad.dashing.tbox.R
import vad.dashing.tbox.isSeatHeatVentSingleWidgetDataKey
import vad.dashing.tbox.TboxViewModel
import vad.dashing.tbox.SettingsViewModel
import vad.dashing.tbox.isMbCanVhalEngineRpmEnabled
import vad.dashing.tbox.isMbCanVhalEngineTemperatureEnabled
import vad.dashing.tbox.isMbCanVhalMediaVolumeEnabled
import vad.dashing.tbox.isMbCanVhalCarSpeedEnabled
import vad.dashing.tbox.isMbCanVhalGearBoxModeEnabled
import vad.dashing.tbox.isMbCanVhalOdometerEnabled
import vad.dashing.tbox.isMbCanVhalFuelLevelPercentageEnabled
import vad.dashing.tbox.isMbCanVhalOutsideTemperatureEnabled
import vad.dashing.tbox.isMbCanVhalWheelsPressureEnabled
import vad.dashing.tbox.isMbCanVhalCurrentFuelConsumptionEnabled
import vad.dashing.tbox.isMbCanVhalDistanceToNextMaintenanceEnabled
import vad.dashing.tbox.isMbCanVhalDistanceToFuelEmptyEnabled
import vad.dashing.tbox.isMbCanVhalAirQualityEnabled
import vad.dashing.tbox.isMbCanVhalSteeringEnabled
import vad.dashing.tbox.normalizeWidgetConfigs
import vad.dashing.tbox.normalizeWidgetScale
import vad.dashing.tbox.normalizeWidgetShape
import vad.dashing.tbox.normalizeWidgetTitlePosition
import vad.dashing.tbox.normalizeWidgetTextAlign
import vad.dashing.tbox.normalizeWidgetFontWeight
import vad.dashing.tbox.mbcan.MbCanSignal

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
    onSeatHeatVentSelectedVariantChange: (widgetIndex: Int, variant: Int) -> Unit,
    onRoadMatchHeadingUpChange: (widgetIndex: Int, headingUp: Boolean) -> Unit = { _, _ -> },
    onHideFloatingPanelsDoubleClick: (widgetIndex: Int) -> Unit = {},
    onToggleFloatingPanelsEnabledDoubleClick: (widgetIndex: Int) -> Unit = {},
    onRestartRequested: () -> Unit,
    showTboxDisconnectIndicator: Boolean,
    enableInnerInteractions: Boolean,
    externalWidgetHost: AppWidgetHost? = null,
    gridSpacingDp: Dp = 0.dp,
    panelStorageId: String = mbCanInterestSourceId,
    onPanelTileTap: () -> Unit = {},
) {
    val noTboxConnect by settingsViewModel.noTboxConnect.collectAsStateWithLifecycle()
    val normalizedConfigs = rememberWidgetConfigsForPanel(widgetConfigs, dashboardRows * dashboardCols)
    val panelNeedsMbCan = remember(widgetConfigs) {
        UniversalCanRepository.widgetConfigsNeedMbCan(widgetConfigs.map { it.dataKey })
    }
    val panelNeedsMbCanVhalMediaVolume = remember(widgetConfigs) {
        widgetConfigs.any { it.isMbCanVhalMediaVolumeEnabled() }
    }
    val panelNeedsMbCanVhalEngineRpm = remember(widgetConfigs) {
        widgetConfigs.any { it.isMbCanVhalEngineRpmEnabled() }
    }
    val panelNeedsMbCanVhalEngineTemperature = remember(widgetConfigs) {
        widgetConfigs.any { it.isMbCanVhalEngineTemperatureEnabled() }
    }
    val panelNeedsMbCanVhalCarSpeed = remember(widgetConfigs) {
        widgetConfigs.any { it.isMbCanVhalCarSpeedEnabled() }
    }
    val panelNeedsMbCanVhalGearBoxMode = remember(widgetConfigs) {
        widgetConfigs.any { it.isMbCanVhalGearBoxModeEnabled() }
    }
    val panelNeedsMbCanVhalOdometer = remember(widgetConfigs) {
        widgetConfigs.any { it.isMbCanVhalOdometerEnabled() }
    }
    val panelNeedsMbCanVhalFuelLevel = remember(widgetConfigs) {
        widgetConfigs.any { it.isMbCanVhalFuelLevelPercentageEnabled() }
    }
    val panelNeedsMbCanVhalOutsideTemp = remember(widgetConfigs) {
        widgetConfigs.any { it.isMbCanVhalOutsideTemperatureEnabled() }
    }
    val panelNeedsMbCanVhalWheelsPressure = remember(widgetConfigs) {
        widgetConfigs.any { it.isMbCanVhalWheelsPressureEnabled() }
    }
    val panelNeedsMbCanVhalCurrentFuel = remember(widgetConfigs) {
        widgetConfigs.any { it.isMbCanVhalCurrentFuelConsumptionEnabled() }
    }
    val panelNeedsMbCanVhalMaintenance = remember(widgetConfigs) {
        widgetConfigs.any { it.isMbCanVhalDistanceToNextMaintenanceEnabled() }
    }
    val panelNeedsMbCanVhalDistanceToEmpty = remember(widgetConfigs) {
        widgetConfigs.any { it.isMbCanVhalDistanceToFuelEmptyEnabled() }
    }
    val panelNeedsMbCanVhalAirQuality = remember(widgetConfigs) {
        widgetConfigs.any { it.isMbCanVhalAirQualityEnabled() }
    }
    val panelNeedsMbCanVhalSteering = remember(widgetConfigs) {
        widgetConfigs.any { it.isMbCanVhalSteeringEnabled() }
    }
    if (panelNeedsMbCan) {
        LaunchedEffect(mbCanInterestSourceId, widgetConfigs) {
            val activeKeys = widgetConfigs
                .map { it.dataKey.trim() }
                .filter { it.isNotBlank() && it != "null" }
                .toSet()
            UniversalCanRepository.setSourceWidgetKeys(mbCanInterestSourceId, activeKeys)
        }
        DisposableEffect(mbCanInterestSourceId) {
            onDispose {
                UniversalCanRepository.enqueueClearSource(mbCanInterestSourceId)
            }
        }
    }
    if (panelNeedsMbCanVhalMediaVolume) {
        LaunchedEffect(mbCanInterestSourceId, widgetConfigs) {
            UniversalCanRepository.setSourceSignals(
                "$mbCanInterestSourceId-media-volume",
                setOf(MbCanSignal.AudioVolume)
            )
        }
        DisposableEffect(mbCanInterestSourceId) {
            onDispose {
                UniversalCanRepository.enqueueClearSource("$mbCanInterestSourceId-media-volume")
            }
        }
    }
    if (panelNeedsMbCanVhalEngineRpm) {
        LaunchedEffect(mbCanInterestSourceId, widgetConfigs) {
            UniversalCanRepository.setSourceSignals(
                "$mbCanInterestSourceId-engine-rpm",
                setOf(MbCanSignal.EngineRpm)
            )
        }
        DisposableEffect(mbCanInterestSourceId) {
            onDispose {
                UniversalCanRepository.enqueueClearSource("$mbCanInterestSourceId-engine-rpm")
            }
        }
    }
    if (panelNeedsMbCanVhalEngineTemperature) {
        LaunchedEffect(mbCanInterestSourceId, widgetConfigs) {
            UniversalCanRepository.setSourceSignals(
                "$mbCanInterestSourceId-engine-temperature",
                setOf(MbCanSignal.EngineTemperature)
            )
        }
        DisposableEffect(mbCanInterestSourceId) {
            onDispose {
                UniversalCanRepository.enqueueClearSource("$mbCanInterestSourceId-engine-temperature")
            }
        }
    }
    if (panelNeedsMbCanVhalCarSpeed) {
        LaunchedEffect(mbCanInterestSourceId, widgetConfigs) {
            UniversalCanRepository.setSourceSignals(
                "$mbCanInterestSourceId-car-speed",
                setOf(MbCanSignal.CarSpeed)
            )
        }
        DisposableEffect(mbCanInterestSourceId) {
            onDispose {
                UniversalCanRepository.enqueueClearSource("$mbCanInterestSourceId-car-speed")
            }
        }
    }
    if (panelNeedsMbCanVhalGearBoxMode) {
        LaunchedEffect(mbCanInterestSourceId, widgetConfigs) {
            UniversalCanRepository.setSourceSignals(
                "$mbCanInterestSourceId-gear-box-mode",
                setOf(MbCanSignal.VehicleGear, MbCanSignal.ReverseGearSwitch)
            )
        }
        DisposableEffect(mbCanInterestSourceId) {
            onDispose {
                UniversalCanRepository.enqueueClearSource("$mbCanInterestSourceId-gear-box-mode")
            }
        }
    }
    if (panelNeedsMbCanVhalOdometer) {
        LaunchedEffect(mbCanInterestSourceId, widgetConfigs) {
            UniversalCanRepository.setSourceSignals(
                "$mbCanInterestSourceId-odometer",
                setOf(MbCanSignal.TotalOdometer)
            )
        }
        DisposableEffect(mbCanInterestSourceId) {
            onDispose {
                UniversalCanRepository.enqueueClearSource("$mbCanInterestSourceId-odometer")
            }
        }
    }
    if (panelNeedsMbCanVhalFuelLevel) {
        LaunchedEffect(mbCanInterestSourceId, widgetConfigs) {
            UniversalCanRepository.setSourceSignals(
                "$mbCanInterestSourceId-fuel-level",
                setOf(MbCanSignal.FuelLevel)
            )
        }
        DisposableEffect(mbCanInterestSourceId) {
            onDispose {
                UniversalCanRepository.enqueueClearSource("$mbCanInterestSourceId-fuel-level")
            }
        }
    }
    if (panelNeedsMbCanVhalOutsideTemp) {
        LaunchedEffect(mbCanInterestSourceId, widgetConfigs) {
            UniversalCanRepository.setSourceSignals(
                "$mbCanInterestSourceId-outside-temp",
                setOf(MbCanSignal.OutsideTemperature)
            )
        }
        DisposableEffect(mbCanInterestSourceId) {
            onDispose {
                UniversalCanRepository.enqueueClearSource("$mbCanInterestSourceId-outside-temp")
            }
        }
    }
    if (panelNeedsMbCanVhalWheelsPressure) {
        LaunchedEffect(mbCanInterestSourceId, widgetConfigs) {
            UniversalCanRepository.setSourceSignals(
                "$mbCanInterestSourceId-vehicle-tires",
                setOf(MbCanSignal.VehicleTires)
            )
        }
        DisposableEffect(mbCanInterestSourceId) {
            onDispose {
                UniversalCanRepository.enqueueClearSource("$mbCanInterestSourceId-vehicle-tires")
            }
        }
    }
    if (panelNeedsMbCanVhalCurrentFuel) {
        LaunchedEffect(mbCanInterestSourceId, widgetConfigs) {
            UniversalCanRepository.setSourceSignals(
                "$mbCanInterestSourceId-current-fuel",
                setOf(MbCanSignal.CurrentFuelConsumption)
            )
        }
        DisposableEffect(mbCanInterestSourceId) {
            onDispose {
                UniversalCanRepository.enqueueClearSource("$mbCanInterestSourceId-current-fuel")
            }
        }
    }
    if (panelNeedsMbCanVhalMaintenance) {
        LaunchedEffect(mbCanInterestSourceId, widgetConfigs) {
            UniversalCanRepository.setSourceSignals(
                "$mbCanInterestSourceId-maintenance",
                setOf(MbCanSignal.DistanceToNextMaintenance)
            )
        }
        DisposableEffect(mbCanInterestSourceId) {
            onDispose {
                UniversalCanRepository.enqueueClearSource("$mbCanInterestSourceId-maintenance")
            }
        }
    }
    if (panelNeedsMbCanVhalDistanceToEmpty) {
        LaunchedEffect(mbCanInterestSourceId, widgetConfigs) {
            UniversalCanRepository.setSourceSignals(
                "$mbCanInterestSourceId-distance-to-empty",
                setOf(MbCanSignal.DistanceToFuelEmpty)
            )
        }
        DisposableEffect(mbCanInterestSourceId) {
            onDispose {
                UniversalCanRepository.enqueueClearSource("$mbCanInterestSourceId-distance-to-empty")
            }
        }
    }
    if (panelNeedsMbCanVhalAirQuality) {
        LaunchedEffect(mbCanInterestSourceId, widgetConfigs) {
            UniversalCanRepository.setSourceSignals(
                "$mbCanInterestSourceId-pm25",
                setOf(MbCanSignal.Pm25AirQuality)
            )
        }
        DisposableEffect(mbCanInterestSourceId) {
            onDispose {
                UniversalCanRepository.enqueueClearSource("$mbCanInterestSourceId-pm25")
            }
        }
    }
    if (panelNeedsMbCanVhalSteering) {
        LaunchedEffect(mbCanInterestSourceId, widgetConfigs) {
            UniversalCanRepository.setSourceSignals(
                "$mbCanInterestSourceId-steering",
                setOf(MbCanSignal.SteeringAngle)
            )
        }
        DisposableEffect(mbCanInterestSourceId) {
            onDispose {
                UniversalCanRepository.enqueueClearSource("$mbCanInterestSourceId-steering")
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
                Text(
                    text = stringResource(R.string.loading),
                    style = MaterialTheme.typography.tboxBody,
                )
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
                        val tileBgRelPath = (
                            if (currentTheme == 2) {
                                widgetConfig.tileBackgroundImageRelPathDark
                            } else {
                                widgetConfig.tileBackgroundImageRelPathLight
                            }
                            )?.takeIf { TileBackgroundImageStorage.isAllowedStoredRelPath(it) }
                        val useTileBackgroundUnderlay = tileBgRelPath != null
                        val shapeDp = normalizeWidgetShape(widgetConfig.shape).dp

                        Box(modifier = Modifier.weight(1f)) {
                            WidgetCellContentPadding(widgetConfig = widgetConfig) {
                            if (useTileBackgroundUnderlay) {
                                DashboardTileBackgroundImageUnderlay(
                                    relPath = tileBgRelPath,
                                    backgroundColor = widgetBackgroundColor,
                                    shapeDp = shapeDp,
                                    settingsViewModel = settingsViewModel,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            if (isEditMode) {
                                Canvas(
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    drawRect(
                                        color = Color(0x7E00BCD4),
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
                                    )
                                }
                            }
                            CompositionLocalProvider(
                                LocalWidgetTextScale provides widgetTextScale,
                                LocalWidgetTextAlign provides widgetTextAlignToCompose(
                                    normalizeWidgetTextAlign(widgetConfig.textAlign)
                                ),
                                LocalWidgetFontWeight provides widgetFontWeightToCompose(
                                    normalizeWidgetFontWeight(widgetConfig.fontWeight)
                                ),
                                LocalWidgetTitlePosition provides normalizeWidgetTitlePosition(
                                    widgetConfig.titlePosition
                                ),
                                LocalDashboardWidgetInteractionPolicy provides widgetInteractionPolicy,
                                LocalNotifyPanelTileTap provides onPanelTileTap,
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
                                    widgetBackgroundColor = if (useTileBackgroundUnderlay) {
                                        Color.Transparent
                                    } else {
                                        widgetBackgroundColor
                                    },
                                    onClick = { onWidgetClick(index) },
                                    onLongClick = onWidgetLongClick,
                                    onMusicSelectedPlayerChange = { selectedPackage ->
                                        onMusicSelectedPlayerChange(index, selectedPackage)
                                    },
                                    onSeatHeatVentSelectedVariantChange = { variant ->
                                        onSeatHeatVentSelectedVariantChange(index, variant)
                                    },
                                    onRoadMatchHeadingUpChange = { headingUp ->
                                        onRoadMatchHeadingUpChange(index, headingUp)
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
                                    shape = shapeDp,
                                    enableInnerInteractions = enableInnerInteractions,
                                    panelStorageId = panelStorageId,
                                )
                            }
                            }
                        }
                    }
                }
            }
        }
    }

    val showEditIndicators = isEditMode && !showDialogOpen
    val showTboxDisconnectFrame =
        showTboxDisconnectIndicator && !tboxConnected && !noTboxConnect
    if (!hasConfiguredWidgets || showTboxDisconnectFrame || showEditIndicators) {
        Canvas(
            modifier = Modifier.fillMaxSize()
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
    if (!isMusicWidgetDataKey(currentConfig.dataKey)) return
    if (currentConfig.mediaSelectedPlayer == selectedPackage) return

    normalizedConfigs[widgetIndex] = currentConfig.copy(
        mediaSelectedPlayer = selectedPackage
    )
    saveConfigs(normalizedConfigs)
}

fun persistDashboardPanelSeatHeatVentSelectedVariant(
    currentWidgetConfigs: List<FloatingDashboardWidgetConfig>,
    widgetIndex: Int,
    selectedVariant: Int,
    saveConfigs: (List<FloatingDashboardWidgetConfig>) -> Unit
) {
    val normalizedConfigs = normalizeWidgetConfigs(
        configs = currentWidgetConfigs,
        widgetCount = currentWidgetConfigs.size
    ).toMutableList()
    val currentConfig = normalizedConfigs.getOrNull(widgetIndex) ?: return
    if (!isSeatHeatVentSingleWidgetDataKey(currentConfig.dataKey)) return
    val coerced = selectedVariant.coerceIn(0, 1)
    if (currentConfig.selectedVariant == coerced) return

    normalizedConfigs[widgetIndex] = currentConfig.copy(
        selectedVariant = coerced
    )
    saveConfigs(normalizedConfigs)
}

fun persistDashboardPanelRoadMatchHeadingUp(
    currentWidgetConfigs: List<FloatingDashboardWidgetConfig>,
    widgetIndex: Int,
    headingUp: Boolean,
    saveConfigs: (List<FloatingDashboardWidgetConfig>) -> Unit
) {
    val normalizedConfigs = normalizeWidgetConfigs(
        configs = currentWidgetConfigs,
        widgetCount = currentWidgetConfigs.size
    ).toMutableList()
    val currentConfig = normalizedConfigs.getOrNull(widgetIndex) ?: return
    if (!isRoadMatchMapWidgetDataKey(currentConfig.dataKey)) return
    if (currentConfig.roadMatchHeadingUp == headingUp) return

    normalizedConfigs[widgetIndex] = currentConfig.copy(
        roadMatchHeadingUp = headingUp
    )
    saveConfigs(normalizedConfigs)
}
