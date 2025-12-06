package com.dashing.tbox.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.dashing.tbox.utils.MockLocationUtils
import com.dashing.tbox.utils.canUseMockLocation
import kotlin.collections.component1
import kotlin.collections.component2

@Composable
fun TboxApp(
    settingsManager: SettingsManager,
    onTboxRestart: () -> Unit,
    onModemCheck: () -> Unit,
    onModemMode: (String) -> Unit,
    onUpdateInfoClick: () -> Unit,
    onSaveToFile: (String, List<String>) -> Unit,
    onTboxApplicationCommand: (String, String) -> Unit,
    onMockLocationSettingChanged: (Boolean) -> Unit,
    onATcmdSend: (String) -> Unit,
) {
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
            onModemMode = onModemMode,
            onUpdateInfoClick = onUpdateInfoClick,
            onSaveToFile = onSaveToFile,
            onTboxApplicationCommand = onTboxApplicationCommand,
            onMockLocationSettingChanged = onMockLocationSettingChanged,
            onATcmdSend = onATcmdSend
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
    onModemMode: (String) -> Unit,
    onUpdateInfoClick: () -> Unit,
    onSaveToFile: (String, List<String>) -> Unit,
    onTboxApplicationCommand: (String, String) -> Unit,
    onMockLocationSettingChanged: (Boolean) -> Unit,
    onATcmdSend: (String) -> Unit,
) {
    val selectedTab by settingsViewModel.selectedTab.collectAsStateWithLifecycle()
    val tabs = listOf("Модем", "AT команды", "Геопозиция", "Данные авто", "Настройки", "Журнал", "Информация", "CAN", "Плитки")
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
                0 -> ModemTab(viewModel, onModemMode)
                1 -> ATcmdTab (viewModel, onATcmdSend)
                2 -> LocationTab(viewModel, onTboxApplicationCommand)
                3 -> CarDataTab(viewModel)
                4 -> SettingsTab(
                    viewModel,
                    settingsViewModel,
                    onTboxRestart,
                    onMockLocationSettingChanged)
                5 -> LogsTab(viewModel, settingsViewModel, onSaveToFile)
                6 -> InfoTab(viewModel, settingsViewModel, onUpdateInfoClick)
                7 -> CanTab(viewModel, onSaveToFile)
                8 -> DashboardTab(viewModel, widgetViewModel, settingsViewModel)
                else -> ModemTab(viewModel, onModemMode)
            }
        }
    }
}

@Composable
fun ModemTab(
    viewModel: TboxViewModel,
    onModemMode: (String) -> Unit,
) {
    val netState by viewModel.netState.collectAsStateWithLifecycle()
    val netValues by viewModel.netValues.collectAsStateWithLifecycle()
    val apn1State by viewModel.apn1State.collectAsStateWithLifecycle()
    val apn2State by viewModel.apn2State.collectAsStateWithLifecycle()
    val apnStatus by viewModel.apnStatus.collectAsStateWithLifecycle()
    val modemStatus by viewModel.modemStatus.collectAsStateWithLifecycle()

    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    val formattedConnectionChangeTime = remember(netState.connectionChangeTime) {
        netState.connectionChangeTime?.let { timeFormat.format(it) } ?: "нет данных"
    }
    val formattedAPN1ChangeTime = remember(apn1State.changeTime) {
        apn1State.changeTime?.let { timeFormat.format(it) } ?: "нет данных"
    }
    val formattedAPN2ChangeTime = remember(apn2State.changeTime) {
        apn2State.changeTime?.let { timeFormat.format(it) } ?: "нет данных"
    }

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
            item { StatusRow("APN", if (apnStatus) "подключен" else "отключен") }
            item { StatusRow("Время подключения", formattedConnectionChangeTime) }

            item { StatusHeader("APN 1") }
            item { StatusRow("APN", valueToString(apn1State.apnStatus, booleanTrue = "подключен", booleanFalse = "отключен")) }
            item { StatusRow("Тип APN", apn1State.apnType) }
            item { StatusRow("IP APN", apn1State.apnIP) }
            item { StatusRow("Шлюз APN", apn1State.apnGate) }
            item { StatusRow("DNS1 APN", apn1State.apnDNS1) }
            item { StatusRow("DNS2 APN", apn1State.apnDNS2) }
            item { StatusRow("Время изменения", formattedAPN1ChangeTime) }

            item { StatusHeader("APN 2") }
            item { StatusRow("APN2", valueToString(apn2State.apnStatus, booleanTrue = "подключен", booleanFalse = "отключен")) }
            item { StatusRow("Тип APN2", apn2State.apnType) }
            item { StatusRow("IP APN2", apn2State.apnIP) }
            item { StatusRow("Шлюз APN2", apn2State.apnGate) }
            item { StatusRow("DNS1 APN2", apn2State.apnDNS1) }
            item { StatusRow("DNS2 APN2", apn2State.apnDNS2) }
            item { StatusRow("Время изменения", formattedAPN2ChangeTime) }
        }

        ModemModeSelector(
            selectedMode = modemStatus,
            onModemMode = onModemMode,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun ModemModeSelector(
    selectedMode: Int,
    onModemMode: (String) -> Unit,
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
                        onModemMode("on")
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
                        onModemMode("fly")
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
                        onModemMode("off")
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
    onMockLocationSettingChanged: (Boolean) -> Unit
) {
    val isAutoRestartEnabled by settingsViewModel.isAutoModemRestartEnabled.collectAsStateWithLifecycle()
    val isAutoTboxRebootEnabled by settingsViewModel.isAutoTboxRebootEnabled.collectAsStateWithLifecycle()
    val isAutoSuspendTboxAppEnabled by settingsViewModel.isAutoSuspendTboxAppEnabled.collectAsStateWithLifecycle()
    val isAutoStopTboxAppEnabled by settingsViewModel.isAutoStopTboxAppEnabled.collectAsStateWithLifecycle()
    val isAutoPreventTboxRestartEnabled by settingsViewModel.isAutoPreventTboxRestartEnabled.collectAsStateWithLifecycle()
    val isGetCanFrameEnabled by settingsViewModel.isGetCanFrameEnabled.collectAsStateWithLifecycle()
    val isGetLocDataEnabled by settingsViewModel.isGetLocDataEnabled.collectAsStateWithLifecycle()
    val isMockLocationEnabled by settingsViewModel.isMockLocationEnabled.collectAsStateWithLifecycle()
    val isWidgetShowIndicatorEnabled by settingsViewModel.isWidgetShowIndicatorEnabled.collectAsStateWithLifecycle()
    val isWidgetShowLocIndicatorEnabled by settingsViewModel.isWidgetShowLocIndicatorEnabled.collectAsStateWithLifecycle()
    val tboxConnected by viewModel.tboxConnected.collectAsStateWithLifecycle()

    val scrollState = rememberScrollState()

    val context = LocalContext.current
    val canUseMockLocation = remember { context.canUseMockLocation() }

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
            isAutoSuspendTboxAppEnabled,
            { enabled ->
                settingsViewModel.saveAutoSuspendTboxAppSetting(enabled)
                if (enabled && isAutoStopTboxAppEnabled) {
                    settingsViewModel.saveAutoStopTboxAppSetting(false)
                    showAlertDialog("ПРЕДУПРЕЖДЕНИЕ", "Требуется ручная перезагрузка TBox", context)
                }
            },
            "Автоматическая отправка команды SUSPEND приложению APP в TBox",
            "Приостановка приложения APP " +
                    "позволяет избежать периодической перезагрузки TBox, " +
                    "но может происходить регулярное переподключение модема, если установлена SIM-карта",
            true
        )
        SettingSwitch(
            isAutoStopTboxAppEnabled,
            { enabled ->
                settingsViewModel.saveAutoStopTboxAppSetting(enabled)
                if (enabled && isAutoSuspendTboxAppEnabled) {
                    settingsViewModel.saveAutoSuspendTboxAppSetting(false)
                    showAlertDialog("ПРЕДУПРЕЖДЕНИЕ", "Требуется ручная перезагрузка TBox", context)
                }
            },
            "Автоматическая отправка команды STOP приложению APP в TBox",
            "Полное отключение приложения APP " +
                    "позволяет избежать периодической перезагрузки TBox и переподключения модема. " +
                    "После включения опции может произойти однократная перезагрузка TBox",
            true
        )
        SettingSwitch(
            isAutoPreventTboxRestartEnabled,
            { enabled ->
                settingsViewModel.saveAutoPreventTboxRestartSetting(enabled)
            },
            "Автоматическая отправка команды PREVENT RESTART приложению SWD в TBox",
            "Отключение проверки состояния сети и аномалий в работе TBox. " +
                    "Эти проверки могут приводить к лишним перезагрузкам",
            true
        )

        Text(
            text = "Настройки виджетов",
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
            text = "Получение данных от TBox",
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
        SettingSwitch(
            isMockLocationEnabled,
            { enabled ->
                onMockLocationSettingChanged(enabled)
            },
            "(Экспериментальная опция!) Подменять системные данные о геопозиции",
            if (canUseMockLocation) {
                "Готово к использованию"
            } else {
                "Требует настройки разрешений и настройки фиктивных местоположений"
            },
            isGetLocDataEnabled
        )

        if (!canUseMockLocation) {
            Text(
                text = "Нажмите для просмотра требований к фиктивным местоположениям",
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable { showLocationRequirementsDialog(context) }
                    .padding(top = 4.dp)
            )
        }

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

private fun showAlertDialog(title: String, message: String, context: Context) {
    android.app.AlertDialog.Builder(context)
        .setTitle(title)
        .setMessage(message)
        .setNeutralButton("Закрыть", null)
        .show()
}

private fun showLocationRequirementsDialog(context: Context) {
    val status = MockLocationUtils.checkMockLocationCapabilities(context)

    val requirements = buildString {
        if (!status.hasLocationPermissions) {
            append("Нет разрешения на доступ к местоположению\n")
        }
        if (!status.isMockLocationEnabled) {
            append("Не включена mock-локация в настройках разработчика\n")
        }
        if (!status.canAddTestProvider) {
            append("Не удается добавить приложение в список провайдеров фиктивных местоположений\n")
        }
    }

    android.app.AlertDialog.Builder(context)
        .setTitle("Требования для mock-локации (Настройки разработчика)")
        .setMessage(requirements)
        .setPositiveButton("Настроить") { dialog, _ ->
            // Открываем настройки разработчика
            val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
            context.startActivity(intent)
        }
        .setNegativeButton("Отмена", null)
        .show()
}

@Composable
fun LocationTab(
    viewModel: TboxViewModel,
    onTboxApplicationCommand: (String, String) -> Unit,
) {
    val locValues by viewModel.locValues.collectAsStateWithLifecycle()
    val locationUpdateTime by viewModel.locationUpdateTime.collectAsStateWithLifecycle()
    val isLocValuesTrue by viewModel.isLocValuesTrue.collectAsStateWithLifecycle()
    val tboxConnected by viewModel.tboxConnected.collectAsStateWithLifecycle()

    // Используем remember для форматтера
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    val dateTime = locValues.utcTime?.formatDateTime() ?: ""

    val lastUpdate = remember(locValues.updateTime) {
        locValues.updateTime?.let { updateTime ->
            timeFormat.format(updateTime)
        } ?: ""
    }

    val lastRefresh = remember(locationUpdateTime) {
        locationUpdateTime?.let { locationUpdateTime ->
            timeFormat.format(locationUpdateTime)
        } ?: ""
    }

    var commandButtonsEnabled by remember { mutableStateOf(true) }

    LaunchedEffect(commandButtonsEnabled) {
        if (!commandButtonsEnabled) {
            delay(5000) // Блокировка на 5 секунд
            commandButtonsEnabled = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        LazyColumn(modifier = Modifier.weight(1f)) {
            item { StatusRow("Последнее обновление", lastRefresh) }
            item { StatusRow("Последнее изменение", lastUpdate) }
            item { StatusRow("Фиксация местоположения", if (locValues.locateStatus) {"да"} else {"нет"}) }
            item { StatusRow("Правдивость местоположения", if (isLocValuesTrue) {"да"} else {"нет"}) }
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = {
                    if (commandButtonsEnabled) {
                        commandButtonsEnabled = false
                        onTboxApplicationCommand("LOC", "suspend")
                    }
                },
                enabled = commandButtonsEnabled && tboxConnected
            ) {
                Text(
                    text = "Приостановить LOC",
                    fontSize = 24.sp,
                    maxLines = 2,
                    textAlign = TextAlign.Center
                )
            }
            Button(
                onClick = {
                    if (commandButtonsEnabled) {
                        commandButtonsEnabled = false
                        onTboxApplicationCommand("LOC", "resume")
                    }
                },
                enabled = commandButtonsEnabled && tboxConnected
            ) {
                Text(
                    text = "Возобновить LOC",
                    fontSize = 24.sp,
                    maxLines = 2,
                    textAlign = TextAlign.Center
                )
            }
            Button(
                onClick = {
                    if (commandButtonsEnabled) {
                        commandButtonsEnabled = false
                        onTboxApplicationCommand("LOC", "stop")
                    }
                },
                enabled = commandButtonsEnabled && tboxConnected
            ) {
                Text(
                    text = "Остановить LOC",
                    fontSize = 24.sp,
                    maxLines = 2,
                    textAlign = TextAlign.Center
                )
            }
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
    val tboxAppStoped by viewModel.tboxAppStoped.collectAsStateWithLifecycle()
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
                    "Подтверждение команды STOP приложению APP",
                    if (tboxAppStoped) "да" else "нет"
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
    val distanceToNextMaintenance by viewModel.distanceToNextMaintenance.collectAsStateWithLifecycle()
    val distanceToFuelEmpty by viewModel.distanceToFuelEmpty.collectAsStateWithLifecycle()
    val breakingForce by viewModel.breakingForce.collectAsStateWithLifecycle()
    val engineRPM by viewModel.engineRPM.collectAsStateWithLifecycle()
    val voltage by viewModel.voltage.collectAsStateWithLifecycle()
    val fuelLevelPercentage by viewModel.fuelLevelPercentage.collectAsStateWithLifecycle()
    val fuelLevelPercentageFiltered by viewModel.fuelLevelPercentageFiltered.collectAsStateWithLifecycle()
    val carSpeed by viewModel.carSpeed.collectAsStateWithLifecycle()
    val carSpeedAccurate by viewModel.carSpeedAccurate.collectAsStateWithLifecycle()
    val wheelsSpeed by viewModel.wheelsSpeed.collectAsStateWithLifecycle()
    val wheelsPressure by viewModel.wheelsPressure.collectAsStateWithLifecycle()
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
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("carSpeedAccurate"), valueToString(carSpeedAccurate, 1)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("wheel1Speed"), valueToString(wheelsSpeed.wheel1, 1)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("wheel2Speed"), valueToString(wheelsSpeed.wheel2, 1)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("wheel3Speed"), valueToString(wheelsSpeed.wheel3, 1)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("wheel4Speed"), valueToString(wheelsSpeed.wheel4, 1)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("cruiseSetSpeed"), valueToString(cruiseSetSpeed)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("odometer"), valueToString(odometer)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("distanceToNextMaintenance"), valueToString(distanceToNextMaintenance)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("distanceToFuelEmpty"), valueToString(distanceToFuelEmpty)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("fuelLevelPercentage"), valueToString(fuelLevelPercentage)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("fuelLevelPercentageFiltered"), valueToString(fuelLevelPercentageFiltered)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("breakingForce"), valueToString(breakingForce)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("engineTemperature"), valueToString(engineTemperature, 1)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("gearBoxOilTemperature"), valueToString(gearBoxOilTemperature)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("gearBoxMode"), gearBoxMode) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("gearBoxDriveMode"), gearBoxDriveMode) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("gearBoxWork"), gearBoxWork) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("gearBoxCurrentGear"), valueToString(gearBoxCurrentGear)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("gearBoxPreparedGear"), valueToString(gearBoxPreparedGear)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("gearBoxChangeGear"),
                valueToString(gearBoxChangeGear, booleanTrue = "переключение", booleanFalse = "нет")) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("wheel1Pressure"), valueToString(wheelsPressure.wheel1, 2)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("wheel2Pressure"), valueToString(wheelsPressure.wheel2, 2)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("wheel3Pressure"), valueToString(wheelsPressure.wheel3, 2)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("wheel4Pressure"), valueToString(wheelsPressure.wheel4, 2)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsTab(
    viewModel: TboxViewModel,
    settingsViewModel: SettingsViewModel,
    onSaveToFile: (String, List<String>) -> Unit
) {
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val logLevel by settingsViewModel.logLevel.collectAsStateWithLifecycle()

    var expanded by remember { mutableStateOf(false) }
    val logLevels = listOf("DEBUG", "INFO", "WARN", "ERROR")
    var searchText by remember { mutableStateOf("") }

    var showSaveDialog by remember { mutableStateOf(false) }

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

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { showSaveDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Сохранить в файл",
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
                        Text("Сохранить журнал в папку Загрузки")
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val csvLogEntries = mutableListOf<String>()
                                logs.forEach { logEntry ->
                                    csvLogEntries.add(logEntry)
                                }
                                onSaveToFile("log", csvLogEntries)
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

        LogsCard(
            logs = logs,
            logLevel = logLevel,
            searchText = searchText
        )
    }
}

@Composable
fun CanTab(
    viewModel: TboxViewModel,
    onSaveToFile: (String, List<String>) -> Unit
) {
    val canFramesStructured by viewModel.canFramesStructured.collectAsStateWithLifecycle()
    val canFrameTime by viewModel.canFrameTime.collectAsStateWithLifecycle()

    var showSaveDialog by remember { mutableStateOf(false) }

    // Используем remember для форматтера
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val dateTimeFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }

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
                                        val timestamp = dateTimeFormat.format(frame.date)
                                        val rawValueHex =
                                            frame.rawValue.joinToString(" ") {
                                                "%02X".format(it)
                                            }
                                        val rawValueDec =
                                            frame.rawValue.joinToString(";") {
                                            (it.toInt() and 0xFF).toString()
                                            }
                                        csvCanEntries.add("$timestamp;$canId;$rawValueHex; ;$rawValueDec")
                                    }
                                }
                                onSaveToFile("can", csvCanEntries)
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

@Composable
fun ATcmdTab(
    viewModel: TboxViewModel,
    onATcmdSend: (String) -> Unit
) {
    val atLogs by viewModel.atLogs.collectAsStateWithLifecycle()

    var atCmdText by remember { mutableStateOf("") }

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
                value = atCmdText,
                onValueChange = { newText ->
                    atCmdText = newText
                },
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                label = {
                    Text(
                        text = "AT команда",
                        fontSize = 16.sp
                    )
                },
                placeholder = {
                    Text(
                        text = "Введите AT команду",
                        fontSize = 16.sp
                    )
                },
                singleLine = true,
                trailingIcon = {
                    if (atCmdText.isNotEmpty()) {
                        IconButton(
                            onClick = { atCmdText = "" }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Очистить"
                            )
                        }
                    }
                },
                keyboardOptions = KeyboardOptions.Default.copy(
                    imeAction = ImeAction.Send
                ),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (atCmdText.isNotEmpty()) {
                            onATcmdSend(atCmdText)
                            atCmdText = ""
                        }
                        focusManager.clearFocus()
                    }
                )
            )
            Box(modifier = Modifier.wrapContentSize()) {
                Button(
                    onClick = {
                        if (atCmdText != "") {
                            onATcmdSend(atCmdText)
                            atCmdText = ""
                        }
                    },
                    modifier = Modifier.width(200.dp)
                ) {
                    Text(
                        text = "Отправить",
                        fontSize = 24.sp,
                        maxLines = 2,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        ATLogsCard(
            logs = atLogs
        )
    }
}

fun valueToString(value: Any?, accuracy: Int = 1, booleanTrue: String = "да", booleanFalse: String = "нет"): String {
    if (value == null) {
        return ""
    }
    return when (value) {
        is Int -> value.toString()
        is UInt -> value.toString()
        is Float, is Double -> when (accuracy) {
            1 -> String.format(Locale.getDefault(), "%.1f", value)
            2 -> String.format(Locale.getDefault(), "%.2f", value)
            3 -> String.format(Locale.getDefault(), "%.3f", value)
            4 -> String.format(Locale.getDefault(), "%.4f", value)
            5 -> String.format(Locale.getDefault(), "%.5f", value)
            6 -> String.format(Locale.getDefault(), "%.6f", value)
            else -> String.format(Locale.getDefault(), "%.1f", value)
        }
        is Boolean -> if (value) booleanTrue else booleanFalse
        is String -> value
        else -> ""
    }
}