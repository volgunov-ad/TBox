package vad.dashing.tbox.ui

import android.content.Context
import android.content.Intent
import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults.cardElevation
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import vad.dashing.tbox.FloatingDashboardWidgetConfig
import vad.dashing.tbox.MUSIC_WIDGET_DATA_KEY
import vad.dashing.tbox.SettingsManager
import vad.dashing.tbox.SettingsViewModel
import vad.dashing.tbox.TboxViewModel
import vad.dashing.tbox.FloatingDashboardViewModel
import vad.dashing.tbox.FloatingDashboardViewModelFactory
import vad.dashing.tbox.MainActivity
import vad.dashing.tbox.R
import vad.dashing.tbox.SettingsViewModelFactory
import vad.dashing.tbox.SharedMediaControlService
import vad.dashing.tbox.collectMediaPlayersFromWidgetConfigs
import vad.dashing.tbox.loadWidgetsFromConfig
import vad.dashing.tbox.normalizeWidgetScale
import vad.dashing.tbox.normalizeWidgetConfigs
import vad.dashing.tbox.ui.theme.TboxAppTheme

@Composable
fun FloatingDashboardUI(
    settingsManager: SettingsManager,
    appDataManager: AppDataManager,
    onUpdateWindowSize: (String, Int, Int) -> Unit,
    onUpdateWindowPosition: (String, Int, Int) -> Unit,
    onRebootTbox: () -> Unit,
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
    windowParams: WindowManager.LayoutParams
) {
    val context = LocalContext.current

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
    val hasConfiguredWidgets = widgetConfigs.any { config ->
        config.dataKey.isNotBlank() && config.dataKey != "null"
    }

    val isFloatingDashboardClickAction = panelConfig.clickAction

    val tboxConnected by tboxViewModel.tboxConnected.collectAsStateWithLifecycle()
    val currentTheme by tboxViewModel.currentTheme.collectAsStateWithLifecycle()

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
        TboxDataProvider(tboxViewModel, canViewModel, appDataViewModel, context)
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
                            Modifier.pointerInput(panelId, windowParams.width, windowParams.height) {
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
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(0.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (dashboardState.widgets.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .weight(1f)
                                .background(color = Color.Transparent),
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
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                for (col in 0 until dashboardCols) {
                                    val index = row * dashboardCols + col
                                    val widget = dashboardState.widgets.getOrNull(index) ?: continue
                                    val widgetConfig = widgetConfigs.getOrNull(index)
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
                                            val onWidgetClick = {
                                                if (isEditMode && !isDraggingMode && !isResizingMode) {
                                                    showDialogForIndex = index
                                                } else if (isFloatingDashboardClickAction) {
                                                    openMainActivityForWidgetKey(
                                                        context = context,
                                                        settingsViewModel = settingsViewModel,
                                                        dataKey = widget.dataKey
                                                    )
                                                }
                                            }
                                            val onWidgetLongClick = {
                                                isEditMode = !isEditMode
                                                isDraggingMode = false
                                                isResizingMode = false
                                            }
                                            DashboardWidgetRenderer(
                                                widget = widget,
                                                widgetConfig = widgetConfig,
                                                tboxViewModel = tboxViewModel,
                                                canViewModel = canViewModel,
                                                appDataViewModel = appDataViewModel,
                                                dataProvider = dataProvider,
                                                dashboardManager = dashboardViewModel.dashboardManager,
                                                dashboardChart = false,
                                                tboxConnected = tboxConnected,
                                                restartEnabled = restartEnabled,
                                                widgetTextColor = widgetTextColor,
                                                widgetBackgroundColor = widgetBackgroundColor,
                                                onClick = onWidgetClick,
                                                onLongClick = onWidgetLongClick,
                                                onMusicSelectedPlayerChange = { selectedPackage ->
                                                    persistFloatingMediaWidgetSelectedPlayer(
                                                        settingsViewModel = settingsViewModel,
                                                        panelId = panelId,
                                                        currentWidgetConfigs = widgetConfigs,
                                                        widgetIndex = index,
                                                        selectedPackage = selectedPackage
                                                    )
                                                },
                                                onRestartRequested = {
                                                    if (restartEnabled) {
                                                        restartEnabled = false
                                                        onRebootTbox()
                                                    }
                                                },
                                                elevation = 0.dp,
                                                shape = widgetConfig.shape.dp,
                                                enableMusicInnerInteractions = !isEditMode
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                val showEditIndicators = isEditMode && showDialogForIndex == null
                if (!hasConfiguredWidgets || !tboxConnected || showEditIndicators) {
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
                        if (!tboxConnected) {
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
                    .weight(1f)
            )

            // Кнопки действий
            WidgetSelectionDialogActions(
                state = state,
                onDismiss = onDismiss,
                onSave = {
                    applyWidgetSelectionChanges(
                        context = context,
                        dashboardManager = dashboardManager,
                        currentWidgets = currentWidgets,
                        currentWidgetConfigs = currentWidgetConfigs,
                        widgetIndex = widgetIndex,
                        state = state,
                        saveConfigs = { configs ->
                            settingsViewModel.saveFloatingDashboardWidgets(panelId, configs)
                        }
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

private fun openMainActivity(context: Context) {
    try {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

private fun openMainActivityForWidgetKey(
    context: Context,
    settingsViewModel: SettingsViewModel,
    dataKey: String
) {
    val targetTab = when (dataKey) {
        "netWidget", "netWidgetNew", "netWidgetColored" -> 0
        "locWidget" -> 2
        else -> null
    }
    targetTab?.let(settingsViewModel::saveSelectedTab)
    openMainActivity(context)
}

private fun persistFloatingMediaWidgetSelectedPlayer(
    settingsViewModel: SettingsViewModel,
    panelId: String,
    currentWidgetConfigs: List<FloatingDashboardWidgetConfig>,
    widgetIndex: Int,
    selectedPackage: String
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
    settingsViewModel.saveFloatingDashboardWidgets(panelId, normalizedConfigs)
}
