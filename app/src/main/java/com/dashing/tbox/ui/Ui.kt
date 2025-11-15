package com.dashing.tbox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dashing.tbox.SettingsManager
import com.dashing.tbox.SettingsViewModel
import com.dashing.tbox.TboxViewModel
import com.dashing.tbox.WidgetViewModel
import com.dashing.tbox.WidgetsRepository
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Locale
import com.dashing.tbox.ui.theme.TboxAppTheme

@Composable
fun TboxApp(
    settingsManager: SettingsManager,
    onTboxRestart: () -> Unit,
    onModemCheck: () -> Unit,
    onModemOn: () -> Unit,
    onModemFly: () -> Unit,
    onModemOff: () -> Unit,
    onUpdateInfoClick: () -> Unit,
    onSaveToFile: (List<String>) -> Unit
) {
    // Используем viewModel() для правильного создания ViewModel
    val viewModel: TboxViewModel = viewModel()
    val widgetViewModel: WidgetViewModel = viewModel()
    val settingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModelFactory(settingsManager))

    val currentTheme by viewModel.currentTheme.collectAsStateWithLifecycle()

    TboxAppTheme(theme = currentTheme) {
        TboxScreen(
            viewModel = viewModel,
            widgetViewModel = widgetViewModel,
            settingsViewModel = settingsViewModel,
            onTboxRestart = onTboxRestart,
            onModemCheck = onModemCheck,
            onModemOn = onModemOn,
            onModemFly = onModemFly,
            onModemOff = onModemOff,
            onUpdateInfoClick = onUpdateInfoClick,
            onSaveToFile = onSaveToFile
        )
    }
}

// Factory для SettingsViewModel
class SettingsViewModelFactory(private val settingsManager: SettingsManager) :
    ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(settingsManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TboxScreen(
    viewModel: TboxViewModel,
    widgetViewModel: WidgetViewModel,
    settingsViewModel: SettingsViewModel,
    onTboxRestart: () -> Unit,
    onModemCheck: () -> Unit,
    onModemOn: () -> Unit,
    onModemFly: () -> Unit,
    onModemOff: () -> Unit,
    onUpdateInfoClick: () -> Unit,
    onSaveToFile: (List<String>) -> Unit
) {
    val selectedTab by settingsViewModel.selectedTab.collectAsStateWithLifecycle()
    val tabs = listOf("Модем", "Геопозиция", "Данные авто", "Настройки", "Журнал", "Информация", "CAN", "Плитки")
    val tboxConnected by viewModel.tboxConnected.collectAsStateWithLifecycle()
    val tboxConnectionTime by viewModel.tboxConnectionTime.collectAsStateWithLifecycle()
    val serviceStartTime by viewModel.serviceStartTime.collectAsStateWithLifecycle()

    // Используем remember для форматтеров даты
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val conTime = remember(tboxConnectionTime) { timeFormat.format(tboxConnectionTime) }
    val serviceTime = remember(serviceStartTime) { timeFormat.format(serviceStartTime) }

    val context = LocalContext.current
    val packageInfo = remember { context.packageManager.getPackageInfo(context.packageName, 0) }
    val versionName = remember { packageInfo.versionName }

    val scrollState = rememberScrollState()

    // Показываем loading пока загружается сохраненная вкладка
    if (selectedTab == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Text("Загрузка...", fontSize = 18.sp)
        }
        return
    }

    LaunchedEffect(selectedTab) {
        if (selectedTab == 0) {
            onModemCheck()
        }
    }

    Row(modifier = Modifier.fillMaxSize()) {
        // Меню слева
        Column(
            modifier = Modifier
                .width(300.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.background),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = if (tboxConnected) "TBox подключен в $conTime"
                else "TBox отключен в $conTime",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = if (tboxConnected) Color(0xFF4CAF50) else Color(0xFFFF0000),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(horizontal = 8.dp)
            )
            Text(
                text = "Служба запущена в $serviceTime",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(horizontal = 8.dp)
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.Center
            ) {
                tabs.forEachIndexed { index, title ->
                    TabMenuItem(
                        title = title,
                        selected = selectedTab == index,
                        onClick = {
                            // Сохраняем выбор вкладки через ViewModel
                            settingsViewModel.saveSelectedTab(index)
                        }
                    )
                }
            }

            Text(
                text = "Версия программы $versionName",
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(horizontal = 8.dp)
            )
        }

        // Содержимое справа
        Box(
            modifier = Modifier
                .weight(1f)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (selectedTab) {
                0 -> ModemTab(viewModel, onModemOn, onModemFly, onModemOff)
                1 -> LocationTab(viewModel)
                2 -> CarDataTab(viewModel)
                3 -> SettingsTab(viewModel, settingsViewModel, onTboxRestart)
                4 -> LogsTab(viewModel, settingsViewModel)
                5 -> InfoTab(viewModel, settingsViewModel, onUpdateInfoClick)
                6 -> CanTab(viewModel, onSaveToFile)
                7 -> DashboardTab(viewModel, widgetViewModel, settingsViewModel)
            }
        }
    }
}

@Composable
fun ModemTab(
    viewModel: TboxViewModel,
    onModemOn: () -> Unit,
    onModemFly: () -> Unit,
    onModemOff: () -> Unit
) {
    val netState by viewModel.netState.collectAsStateWithLifecycle()
    val netValues by viewModel.netValues.collectAsStateWithLifecycle()
    val apn1State by viewModel.apn1State.collectAsStateWithLifecycle()
    val apn2State by viewModel.apn2State.collectAsStateWithLifecycle()
    val modemStatus by viewModel.modemStatus.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        LazyColumn(modifier = Modifier.weight(1f)) {
            item { StatusHeader("Данные модема и SIM") }
            item { StatusRow("IMEI", netValues.imei) }
            item { StatusRow("ICCID", netValues.iccid) }
            item { StatusRow("IMSI", netValues.imsi) }
            item { StatusRow("Оператор", netValues.operator) }

            item { StatusHeader("Данные подключения") }
            item { StatusRow("CSQ", if (netState.csq != 99) netState.csq.toString() else "-") }
            item { StatusRow("Регистрация", netState.regStatus) }
            item { StatusRow("SIM статус", netState.simStatus) }
            item { StatusRow("Сеть", netState.netStatus) }

            item { StatusHeader("APN 1") }
            item { StatusRow("APN", apn1State.apnStatus) }
            item { StatusRow("Тип APN", apn1State.apnType) }
            item { StatusRow("IP APN", apn1State.apnIP) }
            item { StatusRow("Шлюз APN", apn1State.apnGate) }
            item { StatusRow("DNS1 APN", apn1State.apnDNS1) }
            item { StatusRow("DNS2 APN", apn1State.apnDNS2) }

            item { StatusHeader("APN 2") }
            item { StatusRow("APN2", apn2State.apnStatus) }
            item { StatusRow("Тип APN2", apn2State.apnType) }
            item { StatusRow("IP APN2", apn2State.apnIP) }
            item { StatusRow("Шлюз APN2", apn2State.apnGate) }
            item { StatusRow("DNS1 APN2", apn2State.apnDNS1) }
            item { StatusRow("DNS2 APN2", apn2State.apnDNS2) }
        }

        ModemModeSelector(
            selectedMode = modemStatus,
            onModemOn = onModemOn,
            onModemFly = onModemFly,
            onModemOff = onModemOff,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun ModemModeSelector(
    selectedMode: Int,
    onModemOn: () -> Unit,
    onModemFly: () -> Unit,
    onModemOff: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var buttonsEnabled by remember { mutableStateOf(true) }

    LaunchedEffect(buttonsEnabled) {
        if (!buttonsEnabled) {
            delay(1000) // Блокировка на 1 секунду
            buttonsEnabled = true
        }
    }

    Column(modifier = modifier) {
        Text(
            text = "Режим модема",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ModeButton(
                text = "Включен",
                isSelected = selectedMode == 1,
                onClick = {
                    if (buttonsEnabled) {
                        buttonsEnabled = false
                        onModemOn()
                    }
                },
                enabled = buttonsEnabled,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(8.dp))

            ModeButton(
                text = "Режим полета",
                isSelected = selectedMode == 4,
                onClick = {
                    if (buttonsEnabled) {
                        buttonsEnabled = false
                        onModemFly()
                    }
                },
                enabled = buttonsEnabled,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(8.dp))

            ModeButton(
                text = "Выключен",
                isSelected = selectedMode == 0,
                onClick = {
                    if (buttonsEnabled) {
                        buttonsEnabled = false
                        onModemOff()
                    }
                },
                enabled = buttonsEnabled,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun SettingsTab(
    viewModel: TboxViewModel,
    settingsViewModel: SettingsViewModel,
    onTboxRestartClick: () -> Unit,
) {
    val isAutoRestartEnabled by settingsViewModel.isAutoModemRestartEnabled.collectAsStateWithLifecycle()
    val isAutoTboxRebootEnabled by settingsViewModel.isAutoTboxRebootEnabled.collectAsStateWithLifecycle()
    val isAutoPreventTboxRestartEnabled by settingsViewModel.isAutoPreventTboxRestartEnabled.collectAsStateWithLifecycle()
    val isGetCanFrameEnabled by settingsViewModel.isGetCanFrameEnabled.collectAsStateWithLifecycle()
    val isGetLocDataEnabled by settingsViewModel.isGetLocDataEnabled.collectAsStateWithLifecycle()
    val isWidgetShowIndicatorEnabled by settingsViewModel.isWidgetShowIndicatorEnabled.collectAsStateWithLifecycle()
    val isWidgetShowLocIndicatorEnabled by settingsViewModel.isWidgetShowLocIndicatorEnabled.collectAsStateWithLifecycle()
    val tboxConnected by viewModel.tboxConnected.collectAsStateWithLifecycle()

    val scrollState = rememberScrollState()

    var restartButtonEnabled by remember { mutableStateOf(true) }

    LaunchedEffect(restartButtonEnabled) {
        if (!restartButtonEnabled) {
            delay(15000) // Блокировка на 15 секунд
            restartButtonEnabled = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(18.dp)
    ) {
        Text(
            text = "Настройки контроля сети",
            fontSize = 24.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Left
        )
        SettingSwitch(
            isAutoRestartEnabled,
            { enabled ->
                settingsViewModel.saveAutoRestartSetting(enabled)
            },
            "Автоматический перезапуск модема",
            "Автоматически перезапускать модем при потере подключения к сети. " +
                    "Проверка происходит с периодичностью 10 секунд в первый раз и " +
                    "5 минут в последующие разы " +
                    "(сброс таймера до 10 секунд происходит при " +
                    "подключении сети)",
            true
        )
        SettingSwitch(
            isAutoTboxRebootEnabled,
            { enabled ->
                settingsViewModel.saveAutoTboxRebootSetting(enabled)
            },
            "Автоматическая перезагрузка TBox",
            "Автоматически презагружать TBox, если перезапуск модема не помогает. " +
                    "Перезагрузка просходит через 60 секунд после попытки перезапуска модема, " +
                    "если это не помогло, в первый раз. " +
                    "Далее таймер устанавливается на 30 минут " +
                    "(сброс таймера до 60 секунд происходит при подключении сети)",
            isAutoRestartEnabled
        )

        Text(
            text = "Настройки предотвращения перезагрузки",
            fontSize = 24.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Left
        )
        SettingSwitch(
            isAutoPreventTboxRestartEnabled,
            { enabled ->
                settingsViewModel.saveAutoPreventTboxRestartSetting(enabled)
            },
            "Предотвращение перезагрузки TBox",
            "Отключение приложения APP и проверки состояния сети в TBox " +
                    "позволяет избежать периодической перезагрузки TBox. Необходимые команды " +
                    "отправляются фоновой службой этого приложения каждый раз при запуске " +
                    "головного устройства, а также сразу же при включении данной опции",
            true
        )

        Text(
            text = "Настройки виджета",
            fontSize = 24.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Left
        )
        SettingSwitch(
            isWidgetShowIndicatorEnabled,
            { enabled ->
                settingsViewModel.saveWidgetShowIndicatorSetting(enabled)
            },
            "Показывать индикатор подключения TBox в виджете",
            "Индикатор в виджете в виде круга может иметь 3 цвета: \n" +
                    "- красный - нет данных от фоновой службы;\n" +
                    "- желтый - нет связи с TBox;\n" +
                    "- зеленый - есть связь с TBox",
            true
        )
        SettingSwitch(
            isWidgetShowLocIndicatorEnabled,
            { enabled ->
                settingsViewModel.saveWidgetShowLocIndicatorSetting(enabled)
            },
            "Показывать индикатор состояния геопозиции в виджете",
            "Индикатор в виджете в виде стрелки может иметь 3 цвета: \n" +
                    "- красный - нет фиксации местоположения;\n" +
                    "- желтый - данные о реальной скорости сильно не совпадают с данными со спутников;\n" +
                    "- зеленый - есть фиксация местоположения, данные в норме",
            isGetLocDataEnabled
        )

        Text(
            text = "Экспериментальные настройки",
            fontSize = 24.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Left
        )
        SettingSwitch(
            isGetCanFrameEnabled,
            { enabled ->
                settingsViewModel.saveGetCanFrameSetting(enabled)
            },
            "Получать данные CAN от TBox",
            "",
            true
        )
        SettingSwitch(
            isGetLocDataEnabled,
            { enabled ->
                settingsViewModel.saveGetLocDataSetting(enabled)
            },
            "Получать данные о геопозиции от TBox",
            "",
            true
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = {
                    if (restartButtonEnabled) {
                        restartButtonEnabled = false
                        onTboxRestartClick()
                    }
                },
                enabled = restartButtonEnabled && tboxConnected
            ) {
                Text(
                    text = "Перезагрузка TBox",
                    fontSize = 24.sp,
                    maxLines = 2,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun LocationTab(
    viewModel: TboxViewModel,
) {
    val locValues by viewModel.locValues.collectAsStateWithLifecycle()

    // Используем remember для форматтера
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    val dateTime = locValues.utcTime?.formatDateTime() ?: ""
    val lastUpdate = remember(locValues.updateTime) {
        locValues.updateTime?.let { updateTime ->
            timeFormat.format(updateTime)
        } ?: ""
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        LazyColumn(modifier = Modifier.weight(1f)) {
            item { StatusRow("Последнее обновление", lastUpdate) }
            item { StatusRow("Фиксация местоположения", if (locValues.locateStatus) {"да"} else {"нет"}) }
            item { StatusRow("Долгота", locValues.longitude.toString()) }
            item { StatusRow("Широта", locValues.latitude.toString()) }
            item { StatusRow("Высота, м", locValues.altitude.toString()) }
            item { StatusRow("Видимые спутники", locValues.visibleSatellites.toString()) }
            item { StatusRow("Используемые спутники", locValues.usingSatellites.toString()) }
            item { StatusRow("Скорость, км/ч", String.format(Locale.getDefault(), "%.1f", locValues.speed)) }
            item { StatusRow("Истинное направление", String.format(Locale.getDefault(), "%.1f", locValues.trueDirection)) }
            item { StatusRow("Магнитное направление", String.format(Locale.getDefault(), "%.1f", locValues.magneticDirection)) }
            item { StatusRow("Дата и время UTC", dateTime) }
            item { StatusRow("Сырые данные", locValues.rawValue) }
        }
    }
}

@Composable
fun InfoTab(
    viewModel: TboxViewModel,
    settingsViewModel: SettingsViewModel,
    onUpdateInfoClick: () -> Unit,
) {
    val tboxConnected by viewModel.tboxConnected.collectAsStateWithLifecycle()
    val preventRestartSend by viewModel.preventRestartSend.collectAsStateWithLifecycle()
    val suspendTboxAppSend by viewModel.suspendTboxAppSend.collectAsStateWithLifecycle()
    val ipList by viewModel.ipList.collectAsStateWithLifecycle()
    val appVersion by settingsViewModel.appVersion.collectAsStateWithLifecycle()
    val mdcVersion by settingsViewModel.mdcVersion.collectAsStateWithLifecycle()
    val swdVersion by settingsViewModel.swdVersion.collectAsStateWithLifecycle()
    val crtVersion by settingsViewModel.crtVersion.collectAsStateWithLifecycle()
    val locVersion by settingsViewModel.locVersion.collectAsStateWithLifecycle()
    val swVersion by settingsViewModel.swVersion.collectAsStateWithLifecycle()
    val hwVersion by settingsViewModel.hwVersion.collectAsStateWithLifecycle()
    val vinCode by settingsViewModel.vinCode.collectAsStateWithLifecycle()
    val tboxIP by settingsViewModel.tboxIP.collectAsStateWithLifecycle()

    var updateVersionButtonEnabled by remember { mutableStateOf(true) }

    LaunchedEffect(updateVersionButtonEnabled) {
        if (!updateVersionButtonEnabled) {
            delay(30000) // Блокировка на 30 секунд
            updateVersionButtonEnabled = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(18.dp)
    ) {
        LazyColumn(modifier = Modifier.weight(1f)) {
            item {
                StatusRow(
                    "Подтверждение команды SUSPEND приложению APP",
                    if (suspendTboxAppSend) "да" else "нет"
                )
            }
            item {
                StatusRow(
                    "Подтверждение команды PREVENT RESTART приложению SWD",
                    if (preventRestartSend) "да" else "нет"
                )
            }
            item { StatusRow("Сохраненный IP адрес TBox", tboxIP) }
            item { StatusRow("Список возможных IP адресов TBox", ipList.joinToString("; ")) }
            item { StatusRow("Версия приложения APP", appVersion) }
            item { StatusRow("Версия приложения CRT", crtVersion) }
            item { StatusRow("Версия приложения LOC", locVersion) }
            item { StatusRow("Версия приложения MDC", mdcVersion) }
            item { StatusRow("Версия приложения SWD", swdVersion) }
            item { StatusRow("Версия SW", swVersion) }
            item { StatusRow("Версия HW", hwVersion) }
            item { StatusRow("VIN код", vinCode) }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            if (updateVersionButtonEnabled) {
                                updateVersionButtonEnabled = false
                                onUpdateInfoClick()
                            }
                        },
                        enabled = updateVersionButtonEnabled && tboxConnected
                    ) {
                        Text(
                            text = "Запросить информацию из TBox",
                            fontSize = 24.sp,
                            maxLines = 2,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CarDataTab(
    viewModel: TboxViewModel,
) {
    val odometer by viewModel.odometer.collectAsStateWithLifecycle()
    val engineRPM by viewModel.engineRPM.collectAsStateWithLifecycle()
    val voltage by viewModel.voltage.collectAsStateWithLifecycle()
    val fuelLevelPercentage by viewModel.fuelLevelPercentage.collectAsStateWithLifecycle()
    val carSpeed by viewModel.carSpeed.collectAsStateWithLifecycle()
    val cruiseSetSpeed by viewModel.cruiseSetSpeed.collectAsStateWithLifecycle()
    val steerAngle by viewModel.steerAngle.collectAsStateWithLifecycle()
    val steerSpeed by viewModel.steerSpeed.collectAsStateWithLifecycle()
    val engineTemperature by viewModel.engineTemperature.collectAsStateWithLifecycle()
    val gearBoxMode by viewModel.gearBoxMode.collectAsStateWithLifecycle()
    val gearBoxCurrentGear by viewModel.gearBoxCurrentGear.collectAsStateWithLifecycle()
    val gearBoxPreparedGear by viewModel.gearBoxPreparedGear.collectAsStateWithLifecycle()
    val gearBoxChangeGear by viewModel.gearBoxChangeGear.collectAsStateWithLifecycle()
    val gearBoxOilTemperature by viewModel.gearBoxOilTemperature.collectAsStateWithLifecycle()
    val gearBoxDriveMode by viewModel.gearBoxDriveMode.collectAsStateWithLifecycle()
    val gearBoxWork by viewModel.gearBoxWork.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(18.dp)
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("voltage"), valueToString(voltage, 1)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("steerAngle"), valueToString(steerAngle, 1)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("steerSpeed"), valueToString(steerSpeed)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("engineRPM"), valueToString(engineRPM, 1)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("carSpeed"), valueToString(carSpeed, 1)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("cruiseSetSpeed"), valueToString(cruiseSetSpeed)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("odometer"), valueToString(odometer)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("fuelLevelPercentage"), valueToString(fuelLevelPercentage)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("engineTemperature"), valueToString(engineTemperature, 1)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("gearBoxOilTemperature"), valueToString(gearBoxOilTemperature)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("gearBoxMode"), gearBoxMode) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("gearBoxDriveMode"), gearBoxDriveMode) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("gearBoxWork"), gearBoxWork) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("gearBoxCurrentGear"), valueToString(gearBoxCurrentGear)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("gearBoxPreparedGear"), valueToString(gearBoxPreparedGear)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("gearBoxChangeGear"),
                valueToString(gearBoxChangeGear, booleanTrue = "переключение", booleanFalse = "нет")) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsTab(
    viewModel: TboxViewModel,
    settingsViewModel: SettingsViewModel
) {
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val logLevel by settingsViewModel.logLevel.collectAsStateWithLifecycle()

    var expanded by remember { mutableStateOf(false) }
    val logLevels = listOf("DEBUG", "INFO", "WARN", "ERROR")
    var searchText by remember { mutableStateOf("") }

    val focusManager = LocalFocusManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchText,
                onValueChange = { newText ->
                    searchText = newText
                },
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                label = {
                    Text(
                        text = "Фильтр по тексту (минимум 3 символа)",
                        fontSize = 16.sp
                    )
                },
                placeholder = {
                    Text(
                        text = "Введите текст для поиска...",
                        fontSize = 16.sp
                    )
                },
                singleLine = true,
                trailingIcon = {
                    if (searchText.isNotEmpty()) {
                        IconButton(
                            onClick = { searchText = "" }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Очистить"
                            )
                        }
                    }
                },
                keyboardOptions = KeyboardOptions.Default.copy(
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                    }
                )
            )

            Box(modifier = Modifier.wrapContentSize()) {
                OutlinedButton(
                    onClick = { expanded = true },
                    modifier = Modifier.width(200.dp)
                ) {
                    Text(
                        text = logLevel,
                        fontSize = 20.sp,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = if (expanded) "Свернуть" else "Развернуть"
                    )
                }

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.width(200.dp)
                ) {
                    logLevels.forEach { level ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = level,
                                    fontSize = 20.sp,
                                    color = if (level == logLevel) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                            },
                            onClick = {
                                settingsViewModel.saveLogLevel(level)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }

        LogsCard(
            logs = logs,
            logLevel = logLevel,
            searchText = searchText
        )
    }
}

@Composable
fun LogsCard(
    logs: List<String>,
    logLevel: String,
    searchText: String = ""
) {
    val listState = rememberLazyListState()

    val filteredLogs = remember(logs, logLevel, searchText) {
        val levelFilteredLogs = when (logLevel) {
            "DEBUG" -> {
                logs.filter { it.contains("DEBUG") ||
                        it.contains("INFO") ||
                        it.contains("WARN") ||
                        it.contains("ERROR")}
            }
            "INFO" -> {
                logs.filter { it.contains("INFO") ||
                        it.contains("WARN") ||
                        it.contains("ERROR") }
            }
            "WARN" -> {
                logs.filter { it.contains("WARN") ||
                        it.contains("ERROR") }
            }
            else -> {
                logs.filter { it.contains("ERROR") }
            }
        }

        if (searchText.length >= 3) {
            levelFilteredLogs.filter { log ->
                log.contains(searchText, ignoreCase = true)
            }
        } else {
            levelFilteredLogs
        }
    }

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        elevation = CardDefaults.cardElevation(4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                items(count = filteredLogs.size) { index ->
                    val logEntry = filteredLogs[index]
                    ColoredLogEntry(log = logEntry)
                }
            }
        }
    }
}

@Composable
fun CanTab(
    viewModel: TboxViewModel,
    onSaveToFile: (List<String>) -> Unit
) {
    val canFramesStructured by viewModel.canFramesStructured.collectAsStateWithLifecycle()
    val canFrameTime by viewModel.canFrameTime.collectAsStateWithLifecycle()

    var showSaveDialog by remember { mutableStateOf(false) }

    // Используем remember для форматтера
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }

    val formattedTime = remember(canFrameTime) {
        canFrameTime?.let { timeFormat.format(it) } ?: "нет данных"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        val sortedCanEntries = remember(canFramesStructured) {
            canFramesStructured.entries.sortedBy { it.key }
        }

        Text(
            text = "CAN ID (${sortedCanEntries.size}). " +
                    "Последние данные: $formattedTime",
            modifier = Modifier.padding(6.dp),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { showSaveDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Сохранить текущие CAN данные в файл",
                    fontSize = 24.sp,
                    maxLines = 2,
                    textAlign = TextAlign.Center
                )
            }

            if (showSaveDialog) {
                AlertDialog(
                    onDismissRequest = { showSaveDialog = false },
                    title = { Text("Сохранение файла") },
                    text = {
                        Text("Сохранить ${sortedCanEntries.size} CAN ID в папку Загрузки")
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val csvCanEntries = mutableListOf<String>()
                                sortedCanEntries.forEach { (canId, frames) ->
                                    frames.forEach { frame ->
                                        val timestamp = dateFormat.format(frame.date)
                                        val rawValueHex =
                                            frame.rawValue.joinToString(" ") { "%02X".format(it) }
                                        csvCanEntries.add("$timestamp;$canId;$rawValueHex")
                                    }
                                }
                                onSaveToFile(csvCanEntries)
                                showSaveDialog = false
                            }
                        ) {
                            Text("Сохранить")
                        }
                    },
                    dismissButton = {
                        OutlinedButton(
                            onClick = { showSaveDialog = false }
                        ) {
                            Text("Отмена")
                        }
                    }
                )
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            elevation = CardDefaults.cardElevation(4.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(
                        items = sortedCanEntries,
                        key = { it.key }
                    ) { (canId, frames) ->
                        val lastFrame = frames.lastOrNull()
                        CanIdEntry(
                            canId = canId,
                            lastFrame = lastFrame
                        )
                    }
                }
            }
        }
    }
}

fun valueToString(value: Any?, accuracy: Int = 1, booleanTrue: String = "да", booleanFalse: String = "нет"): String {
    if (value == null) {
        return ""
    }
    return when (value) {
        is Int -> value.toString()
        is UInt -> value.toString()
        is Float -> when (accuracy) {
            1 -> String.format(Locale.getDefault(), "%.1f", value)
            2 -> String.format(Locale.getDefault(), "%.2f", value)
            else -> String.format(Locale.getDefault(), "%.3f", value)
        }
        is Boolean -> if (value) booleanTrue else booleanFalse
        is String -> value
        else -> ""
    }
}