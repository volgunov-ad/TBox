package com.dashing.tbox

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
//import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dashing.tbox.ui.CanIdEntry
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Locale
import com.dashing.tbox.ui.theme.TboxAppTheme
import com.dashing.tbox.ui.StatusRow
import com.dashing.tbox.ui.ModeButton
import com.dashing.tbox.ui.ColoredLogEntry
import com.dashing.tbox.ui.TabMenuItem
import com.dashing.tbox.ui.SettingSwitch
import com.dashing.tbox.ui.StatusHeader
import java.io.File
import java.io.FileWriter

@Composable
fun TboxApp(
    settingsManager: SettingsManager,
    onTboxRestart: () -> Unit,
    onModemCheck: () -> Unit,
    onModemOn: () -> Unit,
    onModemFly: () -> Unit,
    onModemOff: () -> Unit,
    onLocSubscribeClick: () -> Unit,
    onLocUnsubscribeClick: () -> Unit,
    //onGetCanFrame: () -> Unit,
    onUpdateVersions: () -> Unit,
    //currentSavePath: String,
    //onSelectFolder: () -> Unit,
    onSaveToFile: (List<String>) -> Unit
) {
    val viewModel = TboxViewModel()
    val settingsViewModel = remember { SettingsViewModel(settingsManager) }

    val currentTheme by viewModel.currentTheme.collectAsStateWithLifecycle()

    TboxAppTheme(theme = currentTheme) {
        TboxScreen(
            viewModel = viewModel,
            settingsViewModel = settingsViewModel,
            onTboxRestart = onTboxRestart,
            onModemCheck = onModemCheck,
            onModemOn = onModemOn,
            onModemFly = onModemFly,
            onModemOff = onModemOff,
            onLocSubscribeClick = onLocSubscribeClick,
            onLocUnsubscribeClick = onLocUnsubscribeClick,
            //onGetCanFrame = onGetCanFrame,
            onUpdateVersions = onUpdateVersions,
            //currentSavePath = currentSavePath,
            //onSelectFolder = onSelectFolder,
            onSaveToFile = onSaveToFile
        )
    }
}

@Composable
fun TboxScreen(viewModel: TboxViewModel,
               settingsViewModel: SettingsViewModel,
               onTboxRestart: () -> Unit,
               onModemCheck: () -> Unit,
               onModemOn: () -> Unit,
               onModemFly: () -> Unit,
               onModemOff: () -> Unit,
               onLocSubscribeClick: () -> Unit,
               onLocUnsubscribeClick: () -> Unit,
               //onGetCanFrame: () -> Unit,
               onUpdateVersions: () -> Unit,
               //currentSavePath: String,
               //onSelectFolder: () -> Unit,
               onSaveToFile: (List<String>) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Модем", "Геопозиция", "Данные авто", "Настройки", "Журнал", "Информация", "CAN")
    val tboxConnected by viewModel.tboxConnected.collectAsStateWithLifecycle()
    val tboxConnectionTime by viewModel.tboxConnectionTime.collectAsStateWithLifecycle()
    val serviceStartTime by viewModel.serviceStartTime.collectAsStateWithLifecycle()
    val conTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(tboxConnectionTime)
    val serviceTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(serviceStartTime)

    val scrollState = rememberScrollState()

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
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Text(
                text = "Служба запущена в $serviceTime",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Column(
                modifier = Modifier.weight(1f).verticalScroll(scrollState),
                verticalArrangement = Arrangement.Center
            ) {
                tabs.forEachIndexed { index, title ->
                    TabMenuItem(
                        title = title,
                        selected = selectedTab == index,
                        onClick = { selectedTab = index }
                    )
                }
            }

            Text(
                text = "Версия программы 0.5.1",
                fontSize = 16.sp,
                textAlign = TextAlign.Right,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }

        // Содержимое справа
        Box(modifier = Modifier
            .weight(1f)
            .background(MaterialTheme.colorScheme.background)
        ) {
            when (selectedTab) {
                0 -> ModemTab(viewModel, onModemOn, onModemFly, onModemOff)
                1 -> LocationTab(viewModel, onLocSubscribeClick, onLocUnsubscribeClick)
                2 -> CarDataTab(viewModel)
                3 -> SettingsTab(viewModel, settingsViewModel, onTboxRestart, onSaveToFile)
                4 -> LogsTab(viewModel, settingsViewModel)
                5 -> InfoTab(viewModel, settingsViewModel, onUpdateVersions)
                6 -> CanTab(viewModel, onSaveToFile)
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
        Spacer(modifier = Modifier.width(16.dp))
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
        // Заголовок
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
    onSaveToFile: (List<String>) -> Unit
) {
    val isAutoRestartEnabled by settingsViewModel.isAutoModemRestartEnabled.collectAsStateWithLifecycle()
    val isAutoTboxRebootEnabled by settingsViewModel.isAutoTboxRebootEnabled.collectAsStateWithLifecycle()
    val isAutoPreventTboxRestartEnabled by settingsViewModel.isAutoPreventTboxRestartEnabled.collectAsStateWithLifecycle()
    val isUpdateVoltagesEnabled by settingsViewModel.isUpdateVoltagesEnabled.collectAsStateWithLifecycle()
    val isGetCanFrameEnabled by settingsViewModel.isGetCanFrameEnabled.collectAsStateWithLifecycle()
    val isWidgetShowIndicatorEnabled by settingsViewModel.isWidgetShowIndicatorEnabled.collectAsStateWithLifecycle()
    val tboxConnected by viewModel.tboxConnected.collectAsStateWithLifecycle()
    val canFramesList by viewModel.canFramesList.collectAsStateWithLifecycle()
    val didDataCSV by viewModel.didDataCSV.collectAsStateWithLifecycle()

    val scrollState = rememberScrollState()

    var showSaveDialog by remember { mutableStateOf(false) }
    var showSaveDidDialog by remember { mutableStateOf(false) }

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
                    "подключении сети)"
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
                    "(сброс таймера до 60 секунд происходит при подключении сети)"
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
                    "головного устройства, а также сразу же при включении данной опции"
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
                    "красный - нет данных от фоновой службы;\n" +
                    "желтый - нет связи с TBox;\n" +
                    "зеленый - есть связь с TBox"
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
            isUpdateVoltagesEnabled,
            { enabled ->
                settingsViewModel.saveUpdateVoltagesSetting(enabled)
            },
            "Получать данные о напряжении TBox",
            ""
        )
        SettingSwitch(
            isGetCanFrameEnabled,
            { enabled ->
                settingsViewModel.saveGetCanFrameSetting(enabled)
            },
            "Получать данные CAN от TBox",
            ""
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
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

        /*Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { showSaveDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Сохранить в CAN Frames в файл", fontSize = 24.sp, maxLines = 2,
                    textAlign = TextAlign.Center
                )
            }
            if (showSaveDialog) {
                AlertDialog(
                    onDismissRequest = { showSaveDialog = false },
                    title = { Text("Сохранение файла") },
                    text = {
                        Text("Сохранить ${canFramesList.size} CAN Frames в папку Загрузки")
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                onSaveToFile(canFramesList)
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

        Button(
            onClick = { showSaveDidDialog = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "Сохранить в DIDs в файл", fontSize = 24.sp, maxLines = 2,
                textAlign = TextAlign.Center)
        }
        if (showSaveDidDialog) {
            AlertDialog(
                onDismissRequest = { showSaveDidDialog = false },
                title = { Text("Сохранение файла") },
                text = {
                    Text("Сохранить ${didDataCSV.size} DIDs в папку Загрузки")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            onSaveToFile(didDataCSV)
                            showSaveDidDialog = false
                        }
                    ) {
                        Text("Сохранить")
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = { showSaveDidDialog = false }
                    ) {
                        Text("Отмена")
                    }
                }
            )
        }*/
    }
}

@Composable
fun LocationTab(
    viewModel: TboxViewModel,
    onLocSubscribeClick: () -> Unit,
    onLocUnsubscribeClick: () -> Unit
) {
    val locValues by viewModel.locValues.collectAsStateWithLifecycle()
    val locationSubscribed by viewModel.locationSubscribed.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        LazyColumn(modifier = Modifier.weight(1f)) {
            val dateTime = String.format(
                Locale.getDefault(),
                "%02d.%02d.%02d %02d:%02d:%02d",
                locValues.utcTime.day,
                locValues.utcTime.month,
                locValues.utcTime.year,
                locValues.utcTime.hour,
                locValues.utcTime.minute,
                locValues.utcTime.second
            )
            val lastUpdate = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(locValues.updateTime)
            item { StatusRow("Последнее обновление", lastUpdate) }
            item { StatusRow("Фиксация местоположения", if (locValues.locateStatus) {"да"} else {"нет"}) }
            item { StatusRow("Долгота", locValues.longitude.toString()) }
            item { StatusRow("Широта", locValues.latitude.toString()) }
            item { StatusRow("Высота, м", locValues.altitude.toString()) }
            item { StatusRow("Видимые спутники", locValues.visibleSatellites.toString()) }
            item { StatusRow("Используемые спутники", locValues.usingSatellites.toString()) }
            item { StatusRow("Скорость, км/ч", locValues.speed.toString()) }
            item { StatusRow("Истинное направление", locValues.trueDirection.toString()) }
            item { StatusRow("Магнитное направление", locValues.magneticDirection.toString()) }
            item { StatusRow("Дата и время UTC", dateTime) }
            item { StatusRow("Сырые данные", locValues.rawValue) }
        }
        Spacer(modifier = Modifier.width(16.dp))
        LocationSubscribeSelector(locationSubscribed, onLocSubscribeClick, onLocUnsubscribeClick)
    }
}

@Composable
fun LocationSubscribeSelector(
    locationSubscribed: Boolean,
    onOn: () -> Unit,
    onOff: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var buttonsEnabled by remember { mutableStateOf(true) }

    LaunchedEffect(buttonsEnabled) {
        if (!buttonsEnabled) {
            delay(5000) // Блокировка на 5 секунд
            buttonsEnabled = true
        }
    }

    Column(modifier = modifier) {
        // Заголовок
        Text(
            text = "Обновление местоположения",
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
                text = "Включено",
                isSelected = locationSubscribed,
                onClick = {
                    if (buttonsEnabled) {
                        buttonsEnabled = false
                        onOn()
                    }
                },
                enabled = buttonsEnabled,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(8.dp))

            ModeButton(
                text = "Выключено",
                isSelected = !locationSubscribed,
                onClick = {
                    if (buttonsEnabled) {
                        buttonsEnabled = false
                        onOff()
                    }
                },
                enabled = buttonsEnabled,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun InfoTab(
    viewModel: TboxViewModel,
    settingsViewModel: SettingsViewModel,
    onUpdateVersionsClick: () -> Unit,
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
    val tboxIP by settingsViewModel.tboxIP.collectAsStateWithLifecycle()

    val scrollState = rememberScrollState()

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

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            if (updateVersionButtonEnabled) {
                                updateVersionButtonEnabled = false
                                onUpdateVersionsClick()
                            }
                        },
                        enabled = updateVersionButtonEnabled && tboxConnected
                    ) {
                        Text(
                            text = "Проверить версии приложений TBox",
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
    val voltages by viewModel.voltages.collectAsStateWithLifecycle()
    val odo by viewModel.odo.collectAsStateWithLifecycle()
    val engineSpeed by viewModel.engineSpeed.collectAsStateWithLifecycle()
    val carSpeed by viewModel.carSpeed.collectAsStateWithLifecycle()
    val cruise by viewModel.cruise.collectAsStateWithLifecycle()
    val wheels by viewModel.wheels.collectAsStateWithLifecycle()
    val steer by viewModel.steer.collectAsStateWithLifecycle()
    //val hdm by viewModel.hdm.collectAsStateWithLifecycle()

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(18.dp)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
        ) {
            item { StatusRow("Напряжение, В", voltages.voltage3.toString()) }
            item { StatusHeader("00 C4") }
            item { StatusRow("Угол поворота руля", steer.angle.toString()) }
            item { StatusRow("Скорость вращения руля", steer.speed.toString()) }
            item { StatusHeader("00 FA") }
            item { StatusRow("Обороты двигателя, об/мин", engineSpeed.rpm.toString()) }
            item { StatusHeader("02 00")}
            item { StatusRow("Скорость автомобиля, км/ч", carSpeed.speed.toString()) }
            item { StatusHeader("03 05") }
            item { StatusRow("Скорость круиз-контроля, км/ч", cruise.speed.toString()) }
            item { StatusHeader("03 10") }
            item { StatusRow("Скорость колеса 1, км/ч", wheels.speed1.toString()) }
            item { StatusRow("Скорость колеса 2, км/ч", wheels.speed2.toString()) }
            item { StatusRow("Скорость колеса 3, км/ч", wheels.speed3.toString()) }
            item { StatusRow("Скорость колеса 4, км/ч", wheels.speed4.toString()) }
            item { StatusHeader("04 30") }
            item { StatusRow("Одометр, км", odo.odometer.toString()) }
        }
    }
}

@Composable
fun LogsTab(
    viewModel: TboxViewModel,
    settingsViewModel: SettingsViewModel
) {
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val logLevel by settingsViewModel.logLevel.collectAsStateWithLifecycle()

    // Состояние для раскрывающегося списка
    var expanded by remember { mutableStateOf(false) }
    val logLevels = listOf("DEBUG", "INFO", "WARN", "ERROR")

    // Состояние для текстового фильтра
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
            // Текстовое поле для фильтрации логов
            OutlinedTextField(
                value = searchText,
                onValueChange = { newText ->
                    searchText = newText
                },
                modifier = Modifier
                    .padding(vertical = 8.dp),
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

            // Раскрывающийся список для выбора уровня логов
            Box(
                modifier = Modifier.wrapContentSize()
            ) {
                // Кнопка для открытия списка
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

                // Выпадающее меню
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
        //Spacer(modifier = Modifier.width(12.dp))
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

        // Применяем текстовый фильтр если есть поисковый запрос
        if (searchText.length >= 3) {
            levelFilteredLogs.filter { log ->
                log.contains(searchText, ignoreCase = true)
            }
        } else {
            levelFilteredLogs
        }
    }

    // Автопрокрутка к новым логам
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Преобразуем Map в отсортированный список
        val sortedCanEntries = remember(canFramesStructured) {
            canFramesStructured.entries.sortedBy { it.key }
        }
        // Заголовок с количеством CAN ID
        Text(
            text = "CAN ID (${sortedCanEntries.size}). " +
                    "Последние данные: ${SimpleDateFormat("HH:mm:ss",
                        Locale.getDefault()).format(canFrameTime)}",
            modifier = Modifier.padding(6.dp),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { showSaveDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Сохранить текущие CAN данные в файл", fontSize = 24.sp, maxLines = 2,
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
                                val csvCanEnries = mutableListOf<String>()
                                sortedCanEntries.forEach { (canId, frames) ->
                                    frames.forEach { frame ->
                                        val timestamp = SimpleDateFormat(
                                            "yyyy-MM-dd HH:mm:ss",
                                            Locale.getDefault()
                                        ).format(frame.date)
                                        val rawValueHex =
                                            frame.rawValue.joinToString(" ") { "%02X".format(it) }
                                        csvCanEnries.add("$timestamp;$canId;$rawValueHex")
                                    }
                                }
                                onSaveToFile(csvCanEnries)
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
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                ) {
                    items(
                        items = sortedCanEntries,
                        key = { it.key } // Важно для стабильной анимации
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