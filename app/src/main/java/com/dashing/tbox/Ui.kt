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
    onSetModemMode: () -> Unit,
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
        onSetModemMode = { onSetModemMode },
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
               onSetModemMode: (String) -> Unit,
               onLocSubscribeClick: () -> Unit,
               onLocUnsubscribeClick: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Модем", "Геопозиция", "Настройки", "Журнал")
    val tboxConnected by viewModel.tboxConnected.collectAsStateWithLifecycle()
    val tboxConnectionTime by viewModel.tboxConnectionTime.collectAsStateWithLifecycle()
    val conTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(tboxConnectionTime)

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
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (tboxConnected) Color(0xFF4CAF50) else Color(0xFFFF0000),
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
                text = "Версия программы 0.1",
                fontSize = 12.sp,
                textAlign = TextAlign.Right
            )
        }

        // Содержимое справа
        Box(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                0 -> ModemTab(viewModel, onSetModemMode)
                1 -> LocationTab(viewModel, onLocSubscribeClick, onLocUnsubscribeClick)
                2 -> SettingsTab(settingsViewModel, onTboxRestart)
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
            textAlign = TextAlign.Left
        )
    }
}

@Composable
fun ModemTab(viewModel: TboxViewModel, setModemMode: (String) -> Unit) {
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
            item { StatusRow("CSQ", netState.csq.toString()) }
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
        Spacer(modifier = Modifier.width(12.dp))
        ModemModeSelector(
            selectedMode = modemStatus,
            onModemOn = {
                setModemMode("on")
            },
            onModemOff = {
                setModemMode("off")
            },
            onModemFly = {
                setModemMode("fly")
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun SettingsTab(
    settingsViewModel: SettingsViewModel,
    onTboxRestartClick: () -> Unit
) {
    val isAutoRestartEnabled by settingsViewModel.isAutoModemRestartEnabled.collectAsStateWithLifecycle()
    val isAutoTboxRebootEnabled by settingsViewModel.isAutoTboxRebootEnabled.collectAsStateWithLifecycle()
    val isAutoStopTboxAppEnabled by settingsViewModel.isAutoStopTboxAppEnabled.collectAsStateWithLifecycle()
    val isAutoPreventTboxRestartEnabled by settingsViewModel.isAutoPreventTboxRestartEnabled.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        SettingSwitch(
            isAutoRestartEnabled,
            { enabled ->
                settingsViewModel.saveAutoRestartSetting(enabled)
            },
            "Автоматический перезапуск модема",
            "Автоматически перезапускать модем при потере подключения к сети"
        )
        SettingSwitch(
            isAutoTboxRebootEnabled,
            { enabled ->
                settingsViewModel.saveAutoTboxRebootSetting(enabled)
            },
            "Автоматическая перезагрузка TBox",
            "Автоматически презагружать TBox, если перезапуск модема не помогает"
        )
        SettingSwitch(
            isAutoStopTboxAppEnabled,
            { enabled ->
                settingsViewModel.saveAutoStopTboxAppSetting(enabled)
            },
            "Автоматическое отключение приложения APP на TBox",
            "Отключение приложения APP на TBox позволяет избежать перезагрузки TBox каждые 30 минут"
        )
        SettingSwitch(
            isAutoPreventTboxRestartEnabled,
            { enabled ->
                settingsViewModel.saveAutoPreventTboxRestartSetting(enabled)
            },
            "Автоматическое предотвращение перезагрузки TBox по состоянию сети",
            "TBox не будет проверять состояние сети, это поможет избежать автоматической перезагрузки TBox"
        )
        Spacer(modifier = Modifier.width(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onTboxRestartClick,
            ) {
                Text(
                    text = "Перезагрузка TBox",
                    fontSize = 12.sp,
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
        modifier = Modifier.fillMaxWidth(),
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
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )

            Text(
                text = description,
                fontSize = 14.sp,
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
            item { StatusRow("Фиксация местоположения", if (locValues.locateStatus == 1) {"да"} else {"нет"}) }
            item { StatusRow("Долгота", locValues.longitude.toString()) }
            item { StatusRow("Широта", locValues.latitude.toString()) }
            item { StatusRow("Высота", locValues.altitude.toString()) }
            item { StatusRow("Видимые спутники", locValues.visibleSatellites.toString()) }
            item { StatusRow("Используемые спутники", locValues.usingSatellites.toString()) }
            item { StatusRow("Скорость", locValues.speed.toString()) }
            item { StatusRow("Истинное направление", locValues.trueDirection.toString()) }
            item { StatusRow("Магнитное направление", locValues.magneticDirection.toString()) }
            item { StatusRow("Дата и время", dateTime) }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onLocSubscribeClick,
                enabled = !locationSubscribed
            ) {
                Text(
                    text = "Включить обновление местоположения",
                    fontSize = 12.sp,
                    maxLines = 2,
                    textAlign = TextAlign.Center
                )
            }

            Button(
                onClick = onLocUnsubscribeClick,
                enabled = locationSubscribed
            ) {
                Text(
                    text = "Выключить обновление местоположения",
                    fontSize = 12.sp,
                    maxLines = 2,
                    textAlign = TextAlign.Center
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = locValues.rawValue, fontSize = 14.sp)
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
    onModemOff: () -> Unit,
    onModemFly: () -> Unit,
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
            text = "Режим модема",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        // Индикатор блокировки
        if (!buttonsEnabled) {
            Text(
                text = "Ожидание выполнения команды",
                color = Color.Gray,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

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
            fontSize = 16.sp,
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
                    fontSize = 16.sp
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
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 14.sp)
        Text(text = value, fontSize = 14.sp)
    }
}

@Composable
fun ColoredLogEntry(log: String) {
    val color = when {
        log.contains("ERROR", ignoreCase = true) -> Color(0xFFFF0000)
        log.contains("WARN", ignoreCase = true) -> Color(0xFFFFA000) // Orange
        log.contains("INFO", ignoreCase = true) -> Color(0xFF2196F3) // Blue
        log.contains("DEBUG", ignoreCase = true) -> Color(0xFF4CAF50) // Green
        else -> Color(0xFFFFF550)
    }

    Text(
        text = log,
        fontSize = 12.sp,
        color = color,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
    )
}