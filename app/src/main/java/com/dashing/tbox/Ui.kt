package com.dashing.tbox

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun TboxApp(
    settingsManager: SettingsManager,
    onTboxRestart: () -> Unit,
    onModemCheck: () -> Unit,
    onModemOn: () -> Unit,
    onModemFly: () -> Unit,
    onModemOff: () -> Unit,
    onLocSubscribeClick: () -> Unit,
    onLocUnsubscribeClick: () -> Unit
) {
    val viewModel = TboxViewModel()
    val settingsViewModel = remember { SettingsViewModel(settingsManager) }
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
    )
}

@Composable
fun TboxAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        content = content
    )
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
               onLocUnsubscribeClick: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Модем", "Геопозиция", "Настройки", "Журнал")
    val tboxConnected by viewModel.tboxConnected.collectAsStateWithLifecycle()
    val tboxConnectionTime by viewModel.tboxConnectionTime.collectAsStateWithLifecycle()
    val serviceStartTime by viewModel.serviceStartTime.collectAsStateWithLifecycle()
    val conTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(tboxConnectionTime)
    val serviceTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(serviceStartTime)

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
                .background(MaterialTheme.colorScheme.surfaceVariant),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (tboxConnected) "TBox подключен в $conTime"
                else "TBox отключен в $conTime",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = if (tboxConnected) Color(0xFF4CAF50) else Color(0xFFFF0000),
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Text(
                text = "Служба запущена в $serviceTime",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Column(
                modifier = Modifier.weight(1f),
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
                text = "Версия программы 0.3.1",
                fontSize = 12.sp,
                textAlign = TextAlign.Right,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }

        // Содержимое справа
        Box(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                0 -> ModemTab(viewModel, onModemOn, onModemFly, onModemOff)
                1 -> LocationTab(viewModel, onLocSubscribeClick, onLocUnsubscribeClick)
                2 -> SettingsTab(viewModel, settingsViewModel, onTboxRestart)
                3 -> LogsTab(viewModel, settingsViewModel)
            }
        }
    }
}

@Composable
fun TabMenuItem(
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    val textColor = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(backgroundColor)
            .padding(vertical = 16.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            color = textColor,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Left,
            fontSize = 26.sp
        )
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
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        LazyColumn(modifier = Modifier.weight(1f)) {
            item { StatusRow("CSQ", if (netState.csq != 99) netState.csq.toString() else "-") }
            item { StatusRow("IMEI", netValues.imei) }
            item { StatusRow("ICCID", netValues.iccid) }
            item { StatusRow("IMSI", netValues.imsi) }
            item { StatusRow("Оператор", netValues.operator) }
            item { StatusRow("Регистрация", netState.regStatus) }
            item { StatusRow("SIM статус", netState.simStatus) }
            item { StatusRow("Сеть", netState.netStatus) }

            item { StatusRow("APN", apn1State.apnStatus) }
            item { StatusRow("Тип APN", apn1State.apnType) }
            item { StatusRow("IP APN", apn1State.apnIP) }
            item { StatusRow("Шлюз APN", apn1State.apnGate) }
            item { StatusRow("DNS1 APN", apn1State.apnDNS1) }
            item { StatusRow("DNS2 APN", apn1State.apnDNS2) }

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
fun SettingsTab(
    viewModel: TboxViewModel,
    settingsViewModel: SettingsViewModel,
    onTboxRestartClick: () -> Unit,
) {
    val isAutoRestartEnabled by settingsViewModel.isAutoModemRestartEnabled.collectAsStateWithLifecycle()
    val isAutoTboxRebootEnabled by settingsViewModel.isAutoTboxRebootEnabled.collectAsStateWithLifecycle()
    val isAutoStopTboxAppEnabled by settingsViewModel.isAutoStopTboxAppEnabled.collectAsStateWithLifecycle()
    val tboxConnected by viewModel.tboxConnected.collectAsStateWithLifecycle()
    val preventRestartSend by viewModel.preventRestartSend.collectAsStateWithLifecycle()
    val suspendTboxAppSend by viewModel.suspendTboxAppSend.collectAsStateWithLifecycle()
    //val isAutoPreventTboxRestartEnabled by settingsViewModel.isAutoPreventTboxRestartEnabled.collectAsStateWithLifecycle()

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
        SettingSwitch(
            isAutoRestartEnabled,
            { enabled ->
                settingsViewModel.saveAutoRestartSetting(enabled)
            },
            "Автоматический перезапуск модема",
            "Автоматически перезапускать модем при потере подключения к сети. " +
                    "Проверка происходит с периодичностью 10 секунд с нарастанием в 10 секунд " +
                    "после каждого перезапуска модема (сброс таймера до 10 секунд происходит при " +
                    "подключении сети, а также когда он превысит 60 с"
        )
        SettingSwitch(
            isAutoTboxRebootEnabled,
            { enabled ->
                settingsViewModel.saveAutoTboxRebootSetting(enabled)
            },
            "Автоматическая перезагрузка TBox",
            "Автоматически презагружать TBox, если перезапуск модема не помогает. " +
                    "Перезагрузка просходит через 60 секунд после попытки перезапуска модема, " +
                    "если это не помогло. Далее таймер увеличиваться каждый раз на 10 минут " +
                    "(сброс таймера до 60 секунд происходит при подключении сети, а также когда " +
                    "он превысит 60 минут"
        )
        SettingSwitch(
            isAutoStopTboxAppEnabled,
            { enabled ->
                settingsViewModel.saveAutoStopTboxAppSetting(enabled)
            },
            "Предотвращение перезагрузки TBox",
            "Отключение приложения APP и проверки состояния сети в TBox " +
                    "позволяет избежать периодической перезагрузки TBox. Необходимые команды " +
                    "отправляются фоновой службой этого приложения каждый раз при запуске " +
                    "головного устройства, а также сразу же при включении данной опции"
        )
        /*SettingSwitch(
            isAutoPreventTboxRestartEnabled,
            { enabled ->
                settingsViewModel.saveAutoPreventTboxRestartSetting(enabled)
            },
            "Автоматическое предотвращение перезагрузки TBox по состоянию сети",
            "TBox не будет проверять состояние сети, это поможет избежать автоматической перезагрузки TBox"
        )*/
        //Spacer(modifier = Modifier.width(16.dp))
        StatusRow("Отправка команды SUSPEND приложению APP", if (suspendTboxAppSend) "да" else "нет")
        StatusRow("Отправка команды PREVENT RESTART приложению SWD", if (preventRestartSend) "да" else "нет")
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
                    fontSize = 20.sp,
                    maxLines = 2,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun SettingSwitch(
    isEnabled: Boolean,
    onCheckedChange: (enabled: Boolean) -> Unit,
    text: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Switch(
            checked = isEnabled,
            onCheckedChange = { enabled ->
                onCheckedChange(enabled)
            }
        )

        Column(
            modifier = Modifier.weight(1f).padding(horizontal = 10.dp)
        ) {
            Text(
                text = text,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium
            )

            Text(
                text = description,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
fun LocationTab(
    viewModel: TboxViewModel,
    onLocSubscribeClick: () -> Unit,
    onLocUnsubscribeClick: () -> Unit
) {
    val locValues by viewModel.locValues.collectAsStateWithLifecycle()
    val locUpdateTime by viewModel.locUpdateTime.collectAsStateWithLifecycle()
    val locationSubscribed by viewModel.locationSubscribed.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        LazyColumn(modifier = Modifier.weight(1f)) {
            val dateTime = "${locValues.utcTime.day}.${locValues.utcTime.month}.${locValues.utcTime.year} " +
                    "${locValues.utcTime.hour}:${locValues.utcTime.minute}:${locValues.utcTime.second}"
            val lastUpdate = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(locUpdateTime)
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
            item { StatusRow("Дата и время", dateTime) }
        }
        Spacer(modifier = Modifier.width(16.dp))
        LocationSubscribeSelector(locationSubscribed, onLocSubscribeClick, onLocUnsubscribeClick)
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = locValues.rawValue, fontSize = 16.sp)
        }
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
fun LogsTab(
    viewModel: TboxViewModel,
    settingsViewModel: SettingsViewModel
) {
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val logLevel by settingsViewModel.logLevel.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Spacer(modifier = Modifier.width(12.dp))
        LogsCard(
            logs = logs,
            logLevel = logLevel
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
fun ModeButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surface
    }

    val textColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = backgroundColor,
            contentColor = textColor
        ),
        elevation = if (isSelected) {
            ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
        } else {
            ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
        }
    ) {
        Text(
            text = text,
            fontSize = 20.sp,
            maxLines = 2,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun LogsCard(logs: List<String>, logLevel: String) {
    val listState = rememberLazyListState()
    val filteredLogs = remember(logs, logLevel) {
        when (logLevel) {
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
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Журнал службы TBox",
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp
                )
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
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
fun StatusRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 20.sp)
        Text(text = value, fontSize = 20.sp)
    }
}

@Composable
fun ColoredLogEntry(log: String) {
    val color = when {
        log.contains("ERROR", ignoreCase = false) -> Color(0xFFFF0000)
        log.contains("WARN", ignoreCase = false) -> Color(0xFFFFA000) // Orange
        log.contains("INFO", ignoreCase = false) -> Color(0xFF2196F3) // Blue
        log.contains("DEBUG", ignoreCase = false) -> Color(0xFF4CAF50) // Green
        else -> Color(0xFFFFF550)
    }

    Text(
        text = log,
        fontSize = 16.sp,
        color = color,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
    )
}