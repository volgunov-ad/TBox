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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults.cardElevation
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import vad.dashing.tbox.AppDataManager
import vad.dashing.tbox.AppDataViewModel
import vad.dashing.tbox.AppDataViewModelFactory
import vad.dashing.tbox.CanDataViewModel
import vad.dashing.tbox.DashboardManager
import vad.dashing.tbox.DashboardWidget
import vad.dashing.tbox.FloatingDashboardWidgetConfig
import vad.dashing.tbox.SettingsManager
import vad.dashing.tbox.SettingsViewModel
import vad.dashing.tbox.TboxViewModel
import vad.dashing.tbox.FloatingDashboardViewModel
import vad.dashing.tbox.FloatingDashboardViewModelFactory
import vad.dashing.tbox.MainActivity
import vad.dashing.tbox.SettingsViewModelFactory
import vad.dashing.tbox.WidgetsRepository
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
    val hasConfiguredWidgets = widgetConfigs.any { config ->
        config.dataKey.isNotBlank() && config.dataKey != "null"
    }

    val isFloatingDashboardClickAction = panelConfig.clickAction
    val isFloatingDashboardBackground = panelConfig.background

    val tboxConnected by tboxViewModel.tboxConnected.collectAsStateWithLifecycle()
    val isKeyboardVisible by tboxViewModel.isKeyboardVisible.collectAsStateWithLifecycle()

    // Состояния
    var isEditMode by remember { mutableStateOf(false) }
    var showDialogForIndex by remember { mutableStateOf<Int?>(null) }
    var isDraggingMode by remember { mutableStateOf(false) }
    var isResizingMode by remember { mutableStateOf(false) }

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

    // Для определения области перетаскивания/изменения размера
    var dragStartPosition by remember { mutableStateOf<androidx.compose.ui.geometry.Offset?>(null) }
    var resizeStartPosition by remember { mutableStateOf<androidx.compose.ui.geometry.Offset?>(null) }

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

    val hideOnKeyboard = panelConfig.hideOnKeyboard
    var keyboardHidden by remember { mutableStateOf(false) }

    // Увеличиваем окно при показе диалога и возвращаем при закрытии
    LaunchedEffect(showDialogForIndex) {
        if (showDialogForIndex != null) {
            // Сохраняем текущие размеры как оригинальные перед увеличением
            originalWidth.intValue = currentWindowParams.width
            originalHeight.intValue = currentWindowParams.height
            originalX.intValue = currentWindowParams.x
            originalY.intValue = currentWindowParams.y

            // Увеличиваем окно для диалога
            val dialogWidth = (containerSize.width * 0.5f).toInt()
            val dialogHeight = (containerSize.height * 0.7f).toInt()

            val newWidth = dialogWidth.coerceAtMost(containerSize.width)
            val newHeight = dialogHeight.coerceAtMost(containerSize.height - 100)

            onUpdateWindowSize(panelId, newWidth, newHeight)

            // Центрируем окно
            val centerX = (containerSize.width - newWidth) / 2 + 100
            val centerY = (containerSize.height - newHeight) / 2
            onUpdateWindowPosition(panelId, centerX, centerY)
        } else {
            // Восстанавливаем оригинальные размеры и положение
            onUpdateWindowSize(panelId, originalWidth.intValue, originalHeight.intValue)
            onUpdateWindowPosition(panelId, originalX.intValue, originalY.intValue)
        }
    }

    LaunchedEffect(isKeyboardVisible, showDialogForIndex, hideOnKeyboard) {
        if (!hideOnKeyboard) {
            if (keyboardHidden) {
                onUpdateWindowSize(panelId, currentWindowParams.width, currentWindowParams.height)
                keyboardHidden = false
            }
            return@LaunchedEffect
        }
        if (showDialogForIndex != null) {
            if (keyboardHidden) {
                onUpdateWindowSize(panelId, currentWindowParams.width, currentWindowParams.height)
                keyboardHidden = false
            }
            return@LaunchedEffect
        }
        if (isKeyboardVisible && !keyboardHidden) {
            onUpdateWindowSize(panelId, 0, 0)
            keyboardHidden = true
        } else if (!isKeyboardVisible && keyboardHidden) {
            onUpdateWindowSize(panelId, currentWindowParams.width, currentWindowParams.height)
            keyboardHidden = false
        }
    }

    val dataProvider = remember { TboxDataProvider(tboxViewModel, canViewModel, appDataViewModel) }

    LaunchedEffect(widgetConfigs, dashboardRows, dashboardCols) {
        val totalWidgets = dashboardRows * dashboardCols

        // Всегда загружаем/создаем виджеты при изменении зависимостей
        val widgets = loadWidgetsFromConfig(widgetConfigs, totalWidgets)

        dashboardViewModel.dashboardManager.updateWidgets(widgets)
    }

    var restartEnabled by remember { mutableStateOf(true) }

    LaunchedEffect(restartEnabled) {
        if (!restartEnabled) {
            delay(15000) // Блокировка на 15 секунд
            restartEnabled = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(color = if (isFloatingDashboardBackground) MaterialTheme.colorScheme.surface else Color.Transparent)
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
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { startOffset ->
                                if (isEditMode && showDialogForIndex == null) { // Не позволяем перетаскивать при открытом диалоге
                                    // Определяем, в какой области началось перетаскивание
                                    val resizeOffsetX = if (size.width <= 60f) {
                                        30f
                                    } else if (size.width <= 100f) {
                                        50f
                                    } else {
                                        60f
                                    }
                                    val resizeOffsetY = if (size.height <= 60f) {
                                        30f
                                    } else if (size.height <= 100f) {
                                        50f
                                    } else {
                                        60f
                                    }
                                    val isNearBottomRight = startOffset.x > size.width - resizeOffsetX &&
                                            startOffset.y > size.height - resizeOffsetY

                                    if (isNearBottomRight) {
                                        // Изменение размера (за правый нижний угол)
                                        isResizingMode = true
                                        isDraggingMode = false
                                        resizeStartPosition = startOffset
                                    } else {
                                        // Перетаскивание окна (за края)
                                        isDraggingMode = true
                                        isResizingMode = false
                                        dragStartPosition = startOffset
                                    }
                                }
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()

                                if (isEditMode && showDialogForIndex == null && isDraggingMode && dragStartPosition != null) {
                                    // Обновляем положение окна
                                    val newX = (windowParams.x + dragAmount.x).toInt().coerceAtLeast(0)
                                    val newY = (windowParams.y + dragAmount.y).toInt().coerceAtLeast(-100)
                                    onUpdateWindowPosition(panelId, newX, newY)

                                } else if (isEditMode && showDialogForIndex == null && isResizingMode && resizeStartPosition != null) {
                                    // Обновляем размер окна
                                    val newWidth = (windowParams.width + dragAmount.x).toInt()
                                        .coerceAtLeast(50)
                                    val newHeight = (windowParams.height + dragAmount.y).toInt()
                                        .coerceAtLeast(50)
                                    onUpdateWindowSize(panelId, newWidth, newHeight)
                                }
                            },
                            onDragEnd = {
                                if (isEditMode && isDraggingMode) {
                                    settingsViewModel.saveFloatingDashboardPosition(
                                        panelId,
                                        windowParams.x,
                                        windowParams.y
                                    )
                                    // Сбрасываем режимы после завершения жеста
                                    isDraggingMode = false
                                    isResizingMode = false
                                    dragStartPosition = null
                                    resizeStartPosition = null
                                } else if (isEditMode && isResizingMode) {
                                    settingsViewModel.saveFloatingDashboardSize(
                                        panelId,
                                        windowParams.width,
                                        windowParams.height
                                    )
                                    // Сбрасываем режимы после завершения жеста
                                    isDraggingMode = false
                                    isResizingMode = false
                                    dragStartPosition = null
                                    resizeStartPosition = null
                                }
                            }
                        )
                    }
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
                            Text("Загрузка...")
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
                                        when (widget.dataKey) {
                                            "netWidget" -> {
                                                DashboardNetWidgetItem(
                                                    widget = widget,
                                                    onClick = {
                                                        if (isEditMode && !isDraggingMode && !isResizingMode) {
                                                            showDialogForIndex = index
                                                        } else if (isFloatingDashboardClickAction) {
                                                            settingsViewModel.saveSelectedTab(0)
                                                            openMainActivity(context)
                                                        }
                                                    },
                                                    onLongClick = {
                                                        isEditMode = !isEditMode
                                                        isDraggingMode = false
                                                        isResizingMode = false
                                                    },
                                                    viewModel = tboxViewModel,
                                                    elevation = 0.dp,
                                                    shape = 0.dp,
                                                    backgroundTransparent = true
                                                )
                                            }
                                            "locWidget" -> {
                                                DashboardLocWidgetItem(
                                                    widget = widget,
                                                    onClick = {
                                                        if (isEditMode && !isDraggingMode && !isResizingMode) {
                                                            showDialogForIndex = index
                                                        } else if (isFloatingDashboardClickAction) {
                                                            settingsViewModel.saveSelectedTab(2)
                                                            openMainActivity(context)
                                                        }
                                                    },
                                                    onLongClick = {
                                                        isEditMode = !isEditMode
                                                        isDraggingMode = false
                                                        isResizingMode = false
                                                    },
                                                    viewModel = tboxViewModel,
                                                    elevation = 0.dp,
                                                    shape = 0.dp,
                                                    backgroundTransparent = true
                                                )
                                            }
                                            "voltage+engineTemperatureWidget" -> {
                                                DashboardVoltEngTempWidgetItem(
                                                    widget = widget,
                                                    onClick = {
                                                        if (isEditMode && !isDraggingMode && !isResizingMode) {
                                                            showDialogForIndex = index
                                                        } else if (isFloatingDashboardClickAction) {
                                                            openMainActivity(context)
                                                        }
                                                    },
                                                    onLongClick = {
                                                        isEditMode = !isEditMode
                                                        isDraggingMode = false
                                                        isResizingMode = false
                                                    },
                                                    canViewModel = canViewModel,
                                                    elevation = 0.dp,
                                                    shape = 0.dp,
                                                    backgroundTransparent = true,
                                                    units = widgetConfig.showUnit
                                                )
                                            }
                                            "gearBoxWidget" -> {
                                                DashboardGearBoxWidgetItem(
                                                    widget = widget,
                                                    onClick = {
                                                        if (isEditMode && !isDraggingMode && !isResizingMode) {
                                                            showDialogForIndex = index
                                                        } else if (isFloatingDashboardClickAction) {
                                                            openMainActivity(context)
                                                        }
                                                    },
                                                    onLongClick = {
                                                        isEditMode = !isEditMode
                                                        isDraggingMode = false
                                                        isResizingMode = false
                                                    },
                                                    canViewModel = canViewModel,
                                                    elevation = 0.dp,
                                                    shape = 0.dp,
                                                    backgroundTransparent = true,
                                                    units = widgetConfig.showUnit
                                                )
                                            }
                                            "wheelsPressureWidget" -> {
                                                DashboardWheelsPressureWidgetItem(
                                                    widget = widget,
                                                    onClick = {
                                                        if (isEditMode && !isDraggingMode && !isResizingMode) {
                                                            showDialogForIndex = index
                                                        } else if (isFloatingDashboardClickAction) {
                                                            openMainActivity(context)
                                                        }
                                                    },
                                                    onLongClick = {
                                                        isEditMode = !isEditMode
                                                        isDraggingMode = false
                                                        isResizingMode = false
                                                    },
                                                    canViewModel = canViewModel,
                                                    elevation = 0.dp,
                                                    shape = 0.dp,
                                                    backgroundTransparent = true,
                                                    units = widgetConfig.showUnit
                                                )
                                            }
                                            "wheelsPressureTemperatureWidget" -> {
                                                DashboardWheelsPressureTemperatureWidgetItem(
                                                    widget = widget,
                                                    onClick = {
                                                        if (isEditMode && !isDraggingMode && !isResizingMode) {
                                                            showDialogForIndex = index
                                                        } else if (isFloatingDashboardClickAction) {
                                                            openMainActivity(context)
                                                        }
                                                    },
                                                    onLongClick = {
                                                        isEditMode = !isEditMode
                                                        isDraggingMode = false
                                                        isResizingMode = false
                                                    },
                                                    canViewModel = canViewModel,
                                                    elevation = 0.dp,
                                                    shape = 0.dp,
                                                    backgroundTransparent = true,
                                                    units = widgetConfig.showUnit
                                                )
                                            }
                                            "tempInOutWidget" -> {
                                                DashboardTempInOutWidgetItem(
                                                    widget = widget,
                                                    onClick = {
                                                        if (isEditMode && !isDraggingMode && !isResizingMode) {
                                                            showDialogForIndex = index
                                                        } else if (isFloatingDashboardClickAction) {
                                                            openMainActivity(context)
                                                        }
                                                    },
                                                    onLongClick = {
                                                        isEditMode = !isEditMode
                                                        isDraggingMode = false
                                                        isResizingMode = false
                                                    },
                                                    canViewModel = canViewModel,
                                                    elevation = 0.dp,
                                                    shape = 0.dp,
                                                    backgroundTransparent = true,
                                                    units = widgetConfig.showUnit
                                                )
                                            }
                                            "restartTbox" -> {
                                                DashboardWidgetItem(
                                                    widget = widget,
                                                    dataProvider = dataProvider,
                                                    onClick = {
                                                        if (isEditMode && !isDraggingMode && !isResizingMode) {
                                                            showDialogForIndex = index
                                                        } else if (isFloatingDashboardClickAction) {
                                                            openMainActivity(context)
                                                        }
                                                    },
                                                    onLongClick = {
                                                        isEditMode = !isEditMode
                                                        isDraggingMode = false
                                                        isResizingMode = false
                                                    },
                                                    onDoubleClick = {
                                                        if (restartEnabled) {
                                                            restartEnabled = false
                                                            onRebootTbox()
                                                        }
                                                    },
                                                    dashboardManager = dashboardViewModel.dashboardManager,
                                                    dashboardChart = false,
                                                    elevation = 0.dp,
                                                    shape = 0.dp,
                                                    title = widgetConfig.showTitle,
                                                    units = widgetConfig.showUnit,
                                                    backgroundTransparent = true,
                                                    textColor = if (restartEnabled) {
                                                        if (tboxConnected) {
                                                            Color(0xD900A400)
                                                        } else {
                                                            Color(0xD9FF0000)
                                                        }
                                                    } else {
                                                        Color(0xD97E4C4C)
                                                    }
                                                )
                                            }
                                            else -> {
                                                DashboardWidgetItem(
                                                    widget = widget,
                                                    dataProvider = dataProvider,
                                                    onClick = {
                                                        if (isEditMode && !isDraggingMode && !isResizingMode) {
                                                            showDialogForIndex = index
                                                        } else if (isFloatingDashboardClickAction) {
                                                            openMainActivity(context)
                                                        }
                                                    },
                                                    onLongClick = {
                                                        isEditMode = !isEditMode
                                                        isDraggingMode = false
                                                        isResizingMode = false
                                                    },
                                                    onDoubleClick = {
                                                        if (widget.dataKey == "motorHours") {
                                                            appDataViewModel.setMotorHours(0f)
                                                        }
                                                    },
                                                    dashboardManager = dashboardViewModel.dashboardManager,
                                                    dashboardChart = false,
                                                    elevation = 0.dp,
                                                    shape = 0.dp,
                                                    title = widgetConfig.showTitle,
                                                    units = widgetConfig.showUnit,
                                                    backgroundTransparent = true
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (!hasConfiguredWidgets) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .padding(4.dp)
                    ) {
                        Canvas(
                            modifier = Modifier.matchParentSize()
                        ) {
                            drawRect(
                                color = Color(0xFF008507),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                            )
                        }
                    }
                }

                if (!tboxConnected) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .padding(0.dp)
                    ) {
                        Canvas(
                            modifier = Modifier.matchParentSize()
                        ) {
                            drawRect(
                                color = Color(0xD9FF9800),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                            )
                        }
                    }
                }

                // Визуальные индикаторы режимов (не показываем при открытом диалоге)
                if (isEditMode && showDialogForIndex == null) {
                    // Показываем рамку в режиме редактирования
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .padding(2.dp)
                    ) {
                        Canvas(
                            modifier = Modifier.matchParentSize()
                        ) {
                            drawRect(
                                color = Color(0xFF00BCD4),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                            )
                        }
                    }

                    // Индикатор для изменения размера (правый нижний угол)
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                    ) {
                        Canvas(
                            modifier = Modifier.size(16.dp)
                        ) {
                            drawLine(
                                color = Color(0xFF00BCD4),
                                start = androidx.compose.ui.geometry.Offset(0f, size.height),
                                end = androidx.compose.ui.geometry.Offset(size.width, 0f),
                                strokeWidth = 2.dp.toPx()
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
    val togglesEnabled = selectedDataKey.isNotEmpty()

    // Получаем список опций
    val availableOptions = listOf("" to "Не выбрано") +
            WidgetsRepository.getAvailableDataKeysWidgets()
                .filter { it.isNotEmpty() }
                .map { key ->
                    key to WidgetsRepository.getTitleUnitForDataKey(key)
                }

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
            // Заголовок
            Text(
                text = "Выберите данные для плитки ${widgetIndex + 1}",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 5.dp),
                fontSize = 24.sp
            )

            // Список опций с прокруткой
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(2f)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f),
                        androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(androidx.compose.foundation.rememberScrollState())
                        .padding(12.dp)
                ) {
                    availableOptions.forEachIndexed { index, (key, displayName) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedDataKey = key }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.RadioButton(
                                selected = selectedDataKey == key,
                                onClick = { selectedDataKey = key }
                            )
                            Text(
                                text = displayName,
                                fontSize = 22.sp,
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

            Text(
                text = "Дополнительные настройки",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = 12.dp, bottom = 5.dp),
                fontSize = 24.sp
            )
            // Список опций с прокруткой
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f),
                        androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(androidx.compose.foundation.rememberScrollState())
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Switch(
                            checked = showTitle,
                            onCheckedChange = { showTitle = it },
                            enabled = togglesEnabled
                        )
                        Text(
                            text = "Отображать название",
                            fontSize = 22.sp,
                            modifier = Modifier.weight(1f).padding(start=8.dp)
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Switch(
                            checked = showUnit,
                            onCheckedChange = { showUnit = it },
                            enabled = togglesEnabled
                        )
                        Text(
                            text = "Отображать единицу измерения",
                            fontSize = 22.sp,
                            modifier = Modifier.weight(1f).padding(start=8.dp)
                        )
                    }
                }
            }

            // Кнопки действий
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.padding(end = 12.dp)
                ) {
                    Text(text = "Отмена", fontSize = 24.sp)
                }

                Button(
                    onClick = {
                        val updatedWidgets = currentWidgets.toMutableList()
                        val newWidget = if (selectedDataKey.isNotEmpty()) {
                            DashboardWidget(
                                id = currentWidgets[widgetIndex].id,
                                title = WidgetsRepository.getTitleForDataKey(selectedDataKey),
                                unit = WidgetsRepository.getUnitForDataKey(selectedDataKey),
                                dataKey = selectedDataKey
                            )
                        } else {
                            DashboardWidget(
                                id = currentWidgets[widgetIndex].id,
                                title = "",
                                dataKey = ""
                            )
                        }
                        updatedWidgets[widgetIndex] = newWidget

                        dashboardManager.updateWidgets(updatedWidgets)
                        val normalizedConfigs = currentWidgetConfigs.toMutableList()
                        if (normalizedConfigs.size < updatedWidgets.size) {
                            normalizedConfigs.addAll(
                                List(updatedWidgets.size - normalizedConfigs.size) {
                                    FloatingDashboardWidgetConfig(dataKey = "")
                                }
                            )
                        } else if (normalizedConfigs.size > updatedWidgets.size) {
                            normalizedConfigs.subList(
                                updatedWidgets.size,
                                normalizedConfigs.size
                            ).clear()
                        }
                        val newConfig = if (selectedDataKey.isNotEmpty()) {
                            FloatingDashboardWidgetConfig(
                                dataKey = selectedDataKey,
                                showTitle = showTitle,
                                showUnit = showUnit
                            )
                        } else {
                            FloatingDashboardWidgetConfig(dataKey = "")
                        }
                        normalizedConfigs[widgetIndex] = newConfig
                        settingsViewModel.saveFloatingDashboardWidgets(panelId, normalizedConfigs)
                        dashboardManager.clearWidgetHistory(currentWidgets[widgetIndex].id)

                        onDismiss()
                    }
                ) {
                    Text(
                        text = "Сохранить",
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        fontSize = 24.sp
                    )
                }
            }
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