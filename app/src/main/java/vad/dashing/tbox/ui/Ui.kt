package vad.dashing.tbox.ui

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import vad.dashing.tbox.SettingsManager
import vad.dashing.tbox.SettingsViewModel
import vad.dashing.tbox.TboxViewModel
import vad.dashing.tbox.WidgetsRepository
import vad.dashing.tbox.seatModeToString
import kotlinx.coroutines.delay
import vad.dashing.tbox.AppDataManager
import vad.dashing.tbox.AppDataViewModel
import vad.dashing.tbox.AppDataViewModelFactory
import vad.dashing.tbox.BackgroundService
import vad.dashing.tbox.CanDataViewModel
import vad.dashing.tbox.CycleDataViewModel
import vad.dashing.tbox.R
import vad.dashing.tbox.SettingsViewModelFactory
import java.text.SimpleDateFormat
import java.util.Locale
import vad.dashing.tbox.ui.theme.TboxAppTheme
import vad.dashing.tbox.utils.MockLocationUtils
import vad.dashing.tbox.utils.canUseMockLocation
import vad.dashing.tbox.valueToString

data class TabItem(
    val title: String,
    val icon: ImageVector
)

@Composable
fun TboxApp(
    settingsManager: SettingsManager,
    appDataManager: AppDataManager,
    onTboxRestart: () -> Unit,
    onSaveToFile: (String, List<String>) -> Unit,
    onServiceCommand: (String, String, String) -> Unit,
    onMockLocationSettingChanged: (Boolean) -> Unit
) {
    val viewModel: TboxViewModel = viewModel()

    val settingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModelFactory(
        settingsManager
    )
    )
    val appDataViewModel: AppDataViewModel = viewModel(factory = AppDataViewModelFactory(
        appDataManager
    )
    )

    val currentTheme by viewModel.currentTheme.collectAsStateWithLifecycle()

    TboxAppTheme(theme = currentTheme) {
        TboxScreen(
            viewModel = viewModel,
            settingsViewModel = settingsViewModel,
            appDataViewModel = appDataViewModel,
            onTboxRestart = onTboxRestart,
            onSaveToFile = onSaveToFile,
            onServiceCommand = onServiceCommand,
            onMockLocationSettingChanged = onMockLocationSettingChanged
        )
    }
}

object TabItems {
    @Composable
    fun getItems(): List<TabItem> {
        return listOf(
            TabItem("Модем", ImageVector.vectorResource(R.drawable.menu_icon_modem)),
            TabItem("AT команды", ImageVector.vectorResource(R.drawable.menu_icon_at)),
            TabItem("Геопозиция", Icons.Filled.Place),
            TabItem("Данные авто", Icons.Filled.Build),
            TabItem("Настройки", Icons.Filled.Settings),
            TabItem("Журнал", ImageVector.vectorResource(R.drawable.menu_icon_log)),
            TabItem("Информация", Icons.Filled.Info),
            TabItem("CAN", ImageVector.vectorResource(R.drawable.menu_icon_data)),
            TabItem("Плитки", ImageVector.vectorResource(R.drawable.menu_icon_widgets))
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TboxScreen(
    viewModel: TboxViewModel,
    settingsViewModel: SettingsViewModel,
    appDataViewModel: AppDataViewModel,
    onTboxRestart: () -> Unit,
    onSaveToFile: (String, List<String>) -> Unit,
    onServiceCommand: (String, String, String) -> Unit,
    onMockLocationSettingChanged: (Boolean) -> Unit,
) {
    val canViewModel: CanDataViewModel = viewModel()
    val cycleViewModel: CycleDataViewModel = viewModel()

    val selectedTab by settingsViewModel.selectedTab.collectAsStateWithLifecycle()
    val isExpertModeEnabled by settingsViewModel.isExpertModeEnabled.collectAsStateWithLifecycle()

    val tabs = TabItems.getItems()

    val tboxConnected by viewModel.tboxConnected.collectAsStateWithLifecycle()
    val tboxConnectionTime by viewModel.tboxConnectionTime.collectAsStateWithLifecycle()
    val serviceStartTime by viewModel.serviceStartTime.collectAsStateWithLifecycle()
    val isMenuVisible by settingsViewModel.isLeftMenuVisible.collectAsStateWithLifecycle()

    // Используем remember для форматтеров даты
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val conTime = remember(tboxConnectionTime) { timeFormat.format(tboxConnectionTime) }
    val serviceTime = remember(serviceStartTime) { timeFormat.format(serviceStartTime) }

    val context = LocalContext.current
    val packageInfo = remember { context.packageManager.getPackageInfo(context.packageName, 0) }
    val versionName = remember { packageInfo.versionName }

    val scrollState = rememberScrollState()

    val menuIconSize = 28.dp
    val menuButtonSize = 64.dp

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
            onServiceCommand(
                BackgroundService.ACTION_MODEM_CHECK,
                "",
                ""
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .width(if (isMenuVisible) 300.dp else menuButtonSize)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                vertical = 16.dp,
                                horizontal = 8.dp
                            ),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Icon(
                            imageVector = if (isMenuVisible) ImageVector.vectorResource(R.drawable.menu_icon_close) else ImageVector.vectorResource(R.drawable.menu_icon_open),
                            contentDescription = if (isMenuVisible) "Скрыть меню" else "Показать меню",
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier
                                .size(menuIconSize)
                                .clickable(onClick = {
                                    if (isMenuVisible) {
                                        settingsViewModel.saveLeftMenuVisibleSetting(false)
                                    } else {
                                        settingsViewModel.saveLeftMenuVisibleSetting(true)
                                    }
                                })
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(scrollState),
                        verticalArrangement = Arrangement.Center
                    ) {
                        tabs.forEachIndexed { index, tab ->
                            if (isExpertModeEnabled || index !in setOf(1, 5, 7)) {
                                TabMenuItem(
                                    title = tab.title,
                                    icon = tab.icon,
                                    selected = selectedTab == index,
                                    showText = isMenuVisible,
                                    onClick = {
                                        // Сохраняем выбор вкладки через ViewModel
                                        settingsViewModel.saveSelectedTab(index)
                                    }
                                )
                            }
                        }
                    }

                    if (isMenuVisible) {
                        Text(
                            text = if (tboxConnected) "TBox подключен в $conTime"
                            else "TBox отключен в $conTime",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (tboxConnected) Color(0xFF4CAF50) else Color(0xFFFF0000),
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(start = 8.dp, end = 8.dp, top = 8.dp)
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
                    } else {
                        Text(
                            text = "TBox",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (tboxConnected) Color(0xFF4CAF50) else Color(0xFFFF0000),
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(start = 8.dp, end = 8.dp, top = 8.dp)
                        )
                    }

                    if (isMenuVisible) {
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
                }


            }

            // Содержимое справа
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                when (selectedTab) {
                    0 -> ModemTab(viewModel, settingsViewModel, onServiceCommand)
                    1 -> if (isExpertModeEnabled) {
                        ATcmdTab (viewModel, onServiceCommand)
                    } else {
                        ModemTab(viewModel, settingsViewModel, onServiceCommand)
                    }
                    2 -> LocationTab(viewModel, settingsViewModel)
                    3 -> CarDataTab(
                        canViewModel,
                        cycleViewModel,
                        appDataViewModel,
                        settingsViewModel)
                    4 -> SettingsTab(
                        viewModel,
                        settingsViewModel,
                        onTboxRestart,
                        onMockLocationSettingChanged,
                        onServiceCommand)
                    5 -> if (isExpertModeEnabled) {
                        LogsTab(viewModel, settingsViewModel, onSaveToFile)
                    } else {
                        ModemTab(viewModel, settingsViewModel, onServiceCommand)
                    }
                    6 -> InfoTab(viewModel, settingsViewModel, onServiceCommand)
                    7 -> if (isExpertModeEnabled) {
                        CanTab(viewModel, canViewModel, onSaveToFile)
                    } else {
                        ModemTab(viewModel, settingsViewModel, onServiceCommand)
                    }
                    8 -> MainDashboardTab(
                        viewModel,
                        canViewModel,
                        settingsViewModel,
                        appDataViewModel,
                        onTboxRestart)
                    else -> ModemTab(viewModel, settingsViewModel, onServiceCommand)
                }
            }
        }

    }
}

@Composable
fun ModemTab(
    viewModel: TboxViewModel,
    settingsViewModel: SettingsViewModel,
    onServiceCommand: (String, String, String) -> Unit,
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
            onServiceCommand = onServiceCommand,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun ModemModeSelector(
    selectedMode: Int,
    onServiceCommand: (String, String, String) -> Unit,
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
                        onServiceCommand(
                            BackgroundService.ACTION_MODEM_ON,
                            "",
                            ""
                        )
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
                        onServiceCommand(
                            BackgroundService.ACTION_MODEM_FLY,
                            "",
                            ""
                        )
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
                        onServiceCommand(
                            BackgroundService.ACTION_MODEM_OFF,
                            "",
                            ""
                        )
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
    onMockLocationSettingChanged: (Boolean) -> Unit,
    onServiceCommand: (String, String, String) -> Unit,
) {
    val isAutoRestartEnabled by settingsViewModel.isAutoModemRestartEnabled.collectAsStateWithLifecycle()
    val isAutoTboxRebootEnabled by settingsViewModel.isAutoTboxRebootEnabled.collectAsStateWithLifecycle()
    val isAutoSuspendTboxAppEnabled by settingsViewModel.isAutoSuspendTboxAppEnabled.collectAsStateWithLifecycle()
    val isAutoSuspendTboxMdcEnabled by settingsViewModel.isAutoSuspendTboxMdcEnabled.collectAsStateWithLifecycle()
    val isAutoSuspendTboxSwdEnabled by settingsViewModel.isAutoSuspendTboxSwdEnabled.collectAsStateWithLifecycle()
    val isAutoStopTboxAppEnabled by settingsViewModel.isAutoStopTboxAppEnabled.collectAsStateWithLifecycle()
    val isAutoStopTboxMdcEnabled by settingsViewModel.isAutoStopTboxMdcEnabled.collectAsStateWithLifecycle()
    val isAutoPreventTboxRestartEnabled by settingsViewModel.isAutoPreventTboxRestartEnabled.collectAsStateWithLifecycle()
    val isGetCanFrameEnabled by settingsViewModel.isGetCanFrameEnabled.collectAsStateWithLifecycle()
    val isGetCycleSignalEnabled by settingsViewModel.isGetCycleSignalEnabled.collectAsStateWithLifecycle()
    val isGetLocDataEnabled by settingsViewModel.isGetLocDataEnabled.collectAsStateWithLifecycle()
    val isMockLocationEnabled by settingsViewModel.isMockLocationEnabled.collectAsStateWithLifecycle()
    val isWidgetShowIndicatorEnabled by settingsViewModel.isWidgetShowIndicatorEnabled.collectAsStateWithLifecycle()
    val isWidgetShowLocIndicatorEnabled by settingsViewModel.isWidgetShowLocIndicatorEnabled.collectAsStateWithLifecycle()
    val isExpertModeEnabled by settingsViewModel.isExpertModeEnabled.collectAsStateWithLifecycle()

    val isFloatingDashboardEnabled by settingsViewModel.isFloatingDashboardEnabled.collectAsStateWithLifecycle()
    val isFloatingDashboardBackground by settingsViewModel.isFloatingDashboardBackground.collectAsStateWithLifecycle()
    val isFloatingDashboardClickAction by settingsViewModel.isFloatingDashboardClickAction.collectAsStateWithLifecycle()
    val isFloatingDashboardHideOnKeyboard by settingsViewModel.isFloatingDashboardHideOnKeyboard.collectAsStateWithLifecycle()
    val floatingDashboardRows by settingsViewModel.floatingDashboardRows.collectAsStateWithLifecycle()
    val floatingDashboardCols by settingsViewModel.floatingDashboardCols.collectAsStateWithLifecycle()
    val activeFloatingDashboardId by settingsViewModel.activeFloatingDashboardId.collectAsStateWithLifecycle()
    val floatingDashboards by settingsViewModel.floatingDashboards.collectAsStateWithLifecycle()

    val isTboxIPRotation by settingsViewModel.tboxIPRotation.collectAsStateWithLifecycle()

    val dashboardCols by settingsViewModel.dashboardCols.collectAsStateWithLifecycle()
    val dashboardRows by settingsViewModel.dashboardRows.collectAsStateWithLifecycle()
    val dashboardChart by settingsViewModel.dashboardChart.collectAsStateWithLifecycle()

    val canDataSaveCount by settingsViewModel.canDataSaveCount.collectAsStateWithLifecycle()

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
        SettingsTitle("Настройки контроля сети")
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

        SettingsTitle("Настройки предотвращения перезагрузки")
        SettingSwitch(
            isAutoSuspendTboxAppEnabled,
            { enabled ->
                settingsViewModel.saveAutoSuspendTboxAppSetting(enabled)
                if (enabled && isAutoStopTboxAppEnabled) {
                    settingsViewModel.saveAutoStopTboxAppSetting(false)
                    showAlertDialog(
                        "ПРЕДУПРЕЖДЕНИЕ",
                        "При переключении опций SUSPEND и STOP требуется ручная перезагрузка TBox",
                        context)
                }
            },
            "Автоматическая отправка команды SUSPEND приложению APP в TBox",
            "Приостановка приложения APP " +
                    "позволяет избежать периодической перезагрузки TBox, " +
                    "но может происходить регулярное переподключение модема, если установлена SIM-карта на TBox HW 0.0.5",
            true
        )
        SettingSwitch(
            isAutoStopTboxAppEnabled,
            { enabled ->
                settingsViewModel.saveAutoStopTboxAppSetting(enabled)
                if (enabled && isAutoSuspendTboxAppEnabled) {
                    settingsViewModel.saveAutoSuspendTboxAppSetting(false)
                    showAlertDialog(
                        "ПРЕДУПРЕЖДЕНИЕ",
                        "При переключении опций SUSPEND и STOP требуется ручная перезагрузка TBox",
                        context)
                }
            },
            "Автоматическая отправка команды STOP приложению APP в TBox",
            "Полное отключение приложения APP " +
                    "позволяет избежать периодической перезагрузки TBox и переподключения модема. " +
                    "После включения опции может произойти однократная перезагрузка TBox.\n" +
                    "Не рекомендуется использовать данную опцию на TBox HW 0.0.1, 0.0.4",
            true
        )

        SettingSwitch(
            isAutoSuspendTboxMdcEnabled,
            { enabled ->
                settingsViewModel.saveAutoSuspendTboxMdcSetting(enabled)
                if (enabled && isAutoStopTboxMdcEnabled) {
                    settingsViewModel.saveAutoStopTboxMdcSetting(false)
                    showAlertDialog(
                        "ПРЕДУПРЕЖДЕНИЕ",
                        "При переключении опций SUSPEND и STOP требуется ручная перезагрузка TBox",
                        context)
                }
            },
            "Автоматическая отправка команды SUSPEND приложению MDC в TBox",
            "Не рекомендуется включать данную опцию, если в TBox установлена SIM карта",
            true
        )
        SettingSwitch(
            isAutoStopTboxMdcEnabled,
            { enabled ->
                settingsViewModel.saveAutoStopTboxMdcSetting(enabled)
                if (enabled && isAutoSuspendTboxMdcEnabled) {
                    settingsViewModel.saveAutoSuspendTboxMdcSetting(false)
                    showAlertDialog(
                        "ПРЕДУПРЕЖДЕНИЕ",
                        "При переключении опций SUSPEND и STOP требуется ручная перезагрузка TBox",
                        context)
                }
            },
            "Автоматическая отправка команды STOP приложению MDC в TBox",
            "Не рекомендуется включать данную опцию, если в TBox установлена SIM карта",
            true
        )

        SettingSwitch(
            isAutoSuspendTboxSwdEnabled,
            { enabled ->
                settingsViewModel.saveAutoSuspendTboxSwdSetting(enabled)
            },
            "Автоматическая отправка команды SUSPEND приложению SWD в TBox",
            "",
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

        SettingsTitle("Настройки плавающих панелей")
        FloatingDashboardProfileSelector(
            selectedId = activeFloatingDashboardId,
            onSelect = { panelId ->
                settingsViewModel.saveSelectedFloatingDashboardId(panelId)
            },
            modifier = Modifier.padding(bottom = 8.dp)
        )
        SettingSwitch(
            isFloatingDashboardEnabled,
            { enabled ->
                if (enabled) {
                    if (Settings.canDrawOverlays(context)) {
                        settingsViewModel.saveFloatingDashboardSetting(true)
                        //onFloatingDashboardChanged(true)
                    } else {
                        showOverlayRequirementsDialog(context)
                    }
                } else {
                    settingsViewModel.saveFloatingDashboardSetting(false)
                    //onFloatingDashboardChanged(false)
                }
            },
            "Показывать плавающую панель",
            "",
            true
        )
        SettingSwitch(
            isFloatingDashboardBackground,
            { enabled ->
                settingsViewModel.saveFloatingDashboardBackground(enabled)
            },
            "Включить фон плавающей панели",
            "Если выключено, то фон будет прозрачный",
            true
        )
        SettingSwitch(
            isFloatingDashboardClickAction,
            { enabled ->
                settingsViewModel.saveFloatingDashboardClickAction(enabled)
            },
            "Открывать окно программы при одиночном нажатии на элемент плавающей панели",
            "",
            true
        )
        val hasEnabledDashboards = floatingDashboards.any { it.enabled }
        SettingSwitchWithAction(
            isChecked = isFloatingDashboardHideOnKeyboard,
            onCheckedChange = { enabled ->
                settingsViewModel.saveFloatingDashboardHideOnKeyboard(enabled)
            },
            text = "Скрывать плавающую панель при открытой клавиатуре",
            description = "Панель будет скрываться при появлении системной клавиатуры",
            enabled = true,
            actionText = "Доступ",
            onActionClick = {
                if (hasEnabledDashboards) {
                    showAccessibilitySettingsDialog(context) {
                        requestSuspendOverlays(context)
                        Handler(Looper.getMainLooper()).postDelayed({
                            openAccessibilitySettings(context)
                        }, 500)
                    }
                } else {
                    openAccessibilitySettings(context)
                }
            }
        )
        SettingDropdownGeneric(
            floatingDashboardRows,
            { rows ->
                settingsViewModel.saveFloatingDashboardRows(rows)
            },
            "Количество строк плиток плавающей панели",
            "",
            true,
            listOf(1, 2, 3, 4, 5, 6)
        )
        SettingDropdownGeneric(
            floatingDashboardCols,
            { cols ->
                settingsViewModel.saveFloatingDashboardCols(cols)
            },
            "Количество столбцов плиток плавающей панели",
            "",
            true,
            listOf(1, 2, 3, 4, 5, 6)
        )
        FloatingDashboardPositionSizeSettings(settingsViewModel, Modifier)

        SettingsTitle("Настройки виджетов для Overlays")
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

        SettingsTitle("Настройки экрана Плитки")
        SettingSwitch(
            dashboardChart,
            { enabled ->
                settingsViewModel.saveDashboardChart(enabled)
            },
            "Показывать графики изменения величин на плитках",
            "",
            true
        )
        SettingDropdownGeneric(
            dashboardRows,
            { rows ->
                settingsViewModel.saveDashboardRows(rows)
            },
            "Количество строк плиток",
            "",
            true,
            listOf(1, 2, 3, 4, 5, 6)
        )
        SettingDropdownGeneric(
            dashboardCols,
            { cols ->
                settingsViewModel.saveDashboardCols(cols)
            },
            "Количество столбцов плиток",
            "",
            true,
            listOf(1, 2, 3, 4, 5, 6)
        )

        SettingsTitle("Получение данных от TBox")
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

        SettingsTitle("Прочее")
        SettingSwitch(
            isExpertModeEnabled,
            { enabled ->
                settingsViewModel.saveExpertModeSetting(enabled)
                if (!enabled) {
                    settingsViewModel.saveMockLocationSetting(false)
                } else {
                    showAlertDialog("ПРЕДУПРЕЖДЕНИЕ",
                        "Все изменения в экспертном режиме вы делаете на свой страх и " +
                                "риск.\nНо к необратимым последствиям ваши действия в этом " +
                                "режиме привести не могут", context)
                }
            },
            "Экспертный режим",
            "",
            true
        )

        if (isExpertModeEnabled) {
            SettingInt(
                canDataSaveCount,
                {value ->
                    settingsViewModel.saveCanDataSaveCount(value)
                },
                "Количество сохраняемых CAN фреймов (1...3600)",
                "",
                1,
                3600
                )
            /*SettingSwitch(
                isGetCycleSignalEnabled,
                { enabled ->
                    settingsViewModel.saveGetCycleSignalSetting(enabled)
                },
                "Получать циклические данные",
                "",
                true
            )*/
            SettingSwitch(
                isTboxIPRotation,
                { enabled ->
                    settingsViewModel.saveTboxIPRotationSetting(enabled)
                },
                "Искать другие IP адреса TBox",
                "",
                true
            )
            SettingSwitch(
                isMockLocationEnabled,
                { enabled ->
                    onMockLocationSettingChanged(enabled)
                },
                "Подменять системные данные о геопозиции (Фиктивные местоположения)",
                if (canUseMockLocation) {
                    "Готово к использованию"
                } else {
                    "Требует настройки разрешений и настройки фиктивных местоположений"
                },
                true
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

            /*SettingsTitle("Управление приложениями TBox")
            Text(
                text = "Используйте эти кнопки с осторожностью. В первую очередь они предназначены для отладки",
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp
            )
            TboxApplicationControls(
                "APP",
                tboxConnected,
                onServiceCommand)
            TboxApplicationControls(
                "LOC",
                tboxConnected,
                onServiceCommand)
            TboxApplicationControls(
                "MDC",
                tboxConnected,
                onServiceCommand)
            TboxApplicationControls(
                "SWD",
                tboxConnected,
                onServiceCommand)*/
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
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
            val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
            context.startActivity(intent)
        }
        .setNegativeButton("Отмена", null)
        .show()
}

private fun showOverlayRequirementsDialog(context: Context) {
    android.app.AlertDialog.Builder(context)
        .setTitle("Требуется разрешение")
        .setMessage("Для работы приложения необходимо разрешение\n" +
                "«Отображение поверх других приложений»")
        .setPositiveButton("Настроить") { dialog, _ ->
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                "package:${context.packageName}".toUri()
            )
            context.startActivity(intent)
        }
        .setNegativeButton("Отмена", null)
        .show()
}

private fun openAccessibilitySettings(context: Context) {
    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

private fun requestSuspendOverlays(context: Context) {
    val intent = Intent(context, BackgroundService::class.java).apply {
        action = BackgroundService.ACTION_SUSPEND_OVERLAYS
    }
    context.startService(intent)
}

private fun showAccessibilitySettingsDialog(
    context: Context,
    onDisablePanels: () -> Unit
) {
    android.app.AlertDialog.Builder(context)
        .setTitle("Нужно временно скрыть панели")
        .setMessage(
            "Android блокирует запрос доступа, если поверх есть оверлей.\n" +
                "Временно скрыть панели и открыть настройки специальных возможностей?"
        )
        .setPositiveButton("Скрыть и открыть") { _, _ ->
            onDisablePanels()
            openAccessibilitySettings(context)
        }
        .setNegativeButton("Отмена", null)
        .show()
}

@Composable
fun LocationTab(
    viewModel: TboxViewModel,
    settingsViewModel: SettingsViewModel
) {
    val locValues by viewModel.locValues.collectAsStateWithLifecycle()
    val locationUpdateTime by viewModel.locationUpdateTime.collectAsStateWithLifecycle()
    val isLocValuesTrue by viewModel.isLocValuesTrue.collectAsStateWithLifecycle()

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

            /*Row(
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
                            onServiceCommand("CRT", "close")
                        }
                    },
                    enabled = commandButtonsEnabled && tboxConnected
                ) {
                    Text(
                        text = "Закрыть",
                        fontSize = 24.sp,
                        maxLines = 2,
                        textAlign = TextAlign.Center
                    )
                }
                Button(
                    onClick = {
                        if (commandButtonsEnabled) {
                            commandButtonsEnabled = false
                            onServiceCommand("CRT", "open")
                        }
                    },
                    enabled = commandButtonsEnabled && tboxConnected
                ) {
                    Text(
                        text = "Открыть",
                        fontSize = 24.sp,
                        maxLines = 2,
                        textAlign = TextAlign.Center
                    )
                }
            }*/
    }
}

@Composable
fun InfoTab(
    viewModel: TboxViewModel,
    settingsViewModel: SettingsViewModel,
    onServiceCommand: (String, String, String) -> Unit
) {
    val tboxConnected by viewModel.tboxConnected.collectAsStateWithLifecycle()
    val preventRestartSend by viewModel.preventRestartSend.collectAsStateWithLifecycle()
    val tboxAppSuspended by viewModel.tboxAppSuspended.collectAsStateWithLifecycle()
    val tboxMdcSuspended by viewModel.tboxMdcSuspended.collectAsStateWithLifecycle()
    val tboxSwdSuspended by viewModel.tboxSwdSuspended.collectAsStateWithLifecycle()
    val tboxAppStoped by viewModel.tboxAppStoped.collectAsStateWithLifecycle()
    val tboxMdcStoped by viewModel.tboxMdcStoped.collectAsStateWithLifecycle()
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
                    if (tboxAppSuspended) "да" else "нет"
                )
            }
            item {
                StatusRow(
                    "Подтверждение команды SUSPEND приложению MDC",
                    if (tboxMdcSuspended) "да" else "нет"
                )
            }
            item {
                StatusRow(
                    "Подтверждение команды SUSPEND приложению SWD",
                    if (tboxSwdSuspended) "да" else "нет"
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
                    "Подтверждение команды STOP приложению MDC",
                    if (tboxMdcStoped) "да" else "нет"
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
                                onServiceCommand(
                                    BackgroundService.ACTION_GET_INFO,
                                    "",
                                    ""
                                )
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
    canViewModel: CanDataViewModel,
    cycleViewModel: CycleDataViewModel,
    appDataViewModel: AppDataViewModel,
    settingsViewModel: SettingsViewModel,
) {
    val isGetCycleSignalEnabled by settingsViewModel.isGetCycleSignalEnabled.collectAsStateWithLifecycle()

    val odometer by canViewModel.odometer.collectAsStateWithLifecycle()
    val distanceToNextMaintenance by canViewModel.distanceToNextMaintenance.collectAsStateWithLifecycle()
    val distanceToFuelEmpty by canViewModel.distanceToFuelEmpty.collectAsStateWithLifecycle()
    val breakingForce by canViewModel.breakingForce.collectAsStateWithLifecycle()
    val engineRPM by canViewModel.engineRPM.collectAsStateWithLifecycle()
    val param1 by canViewModel.param1.collectAsStateWithLifecycle()
    val param2 by canViewModel.param2.collectAsStateWithLifecycle()
    val param3 by canViewModel.param3.collectAsStateWithLifecycle()
    val param4 by canViewModel.param4.collectAsStateWithLifecycle()
    val voltage by canViewModel.voltage.collectAsStateWithLifecycle()
    val fuelLevelPercentage by canViewModel.fuelLevelPercentage.collectAsStateWithLifecycle()
    val fuelLevelPercentageFiltered by canViewModel.fuelLevelPercentageFiltered.collectAsStateWithLifecycle()
    val carSpeed by canViewModel.carSpeed.collectAsStateWithLifecycle()
    val carSpeedAccurate by canViewModel.carSpeedAccurate.collectAsStateWithLifecycle()
    val wheelsSpeed by canViewModel.wheelsSpeed.collectAsStateWithLifecycle()
    val wheelsPressure by canViewModel.wheelsPressure.collectAsStateWithLifecycle()
    val wheelsTemperature by canViewModel.wheelsTemperature.collectAsStateWithLifecycle()
    val cruiseSetSpeed by canViewModel.cruiseSetSpeed.collectAsStateWithLifecycle()
    val steerAngle by canViewModel.steerAngle.collectAsStateWithLifecycle()
    val steerSpeed by canViewModel.steerSpeed.collectAsStateWithLifecycle()
    val engineTemperature by canViewModel.engineTemperature.collectAsStateWithLifecycle()
    val gearBoxMode by canViewModel.gearBoxMode.collectAsStateWithLifecycle()
    val gearBoxCurrentGear by canViewModel.gearBoxCurrentGear.collectAsStateWithLifecycle()
    val gearBoxPreparedGear by canViewModel.gearBoxPreparedGear.collectAsStateWithLifecycle()
    val gearBoxChangeGear by canViewModel.gearBoxChangeGear.collectAsStateWithLifecycle()
    val gearBoxOilTemperature by canViewModel.gearBoxOilTemperature.collectAsStateWithLifecycle()
    val gearBoxDriveMode by canViewModel.gearBoxDriveMode.collectAsStateWithLifecycle()
    val gearBoxWork by canViewModel.gearBoxWork.collectAsStateWithLifecycle()
    val frontRightSeatMode by canViewModel.frontRightSeatMode.collectAsStateWithLifecycle()
    val frontLeftSeatMode by canViewModel.frontLeftSeatMode.collectAsStateWithLifecycle()
    val outsideTemperature by canViewModel.outsideTemperature.collectAsStateWithLifecycle()
    val insideTemperature by canViewModel.insideTemperature.collectAsStateWithLifecycle()
    val isWindowsBlocked by canViewModel.isWindowsBlocked.collectAsStateWithLifecycle()
    val motorHoursTrip by canViewModel.motorHoursTrip.collectAsStateWithLifecycle()

    val voltageC by cycleViewModel.voltage.collectAsStateWithLifecycle()
    val carSpeedC by cycleViewModel.carSpeed.collectAsStateWithLifecycle()
    val lateralAccelerationC by cycleViewModel.lateralAcceleration.collectAsStateWithLifecycle()
    val longitudinalAccelerationC by cycleViewModel.longitudinalAcceleration.collectAsStateWithLifecycle()
    val pressure1C by cycleViewModel.pressure1.collectAsStateWithLifecycle()
    val pressure2C by cycleViewModel.pressure2.collectAsStateWithLifecycle()
    val pressure3C by cycleViewModel.pressure3.collectAsStateWithLifecycle()
    val pressure4C by cycleViewModel.pressure4.collectAsStateWithLifecycle()
    val temperature1C by cycleViewModel.temperature1.collectAsStateWithLifecycle()
    val temperature2C by cycleViewModel.temperature2.collectAsStateWithLifecycle()
    val temperature3C by cycleViewModel.temperature3.collectAsStateWithLifecycle()
    val temperature4C by cycleViewModel.temperature4.collectAsStateWithLifecycle()
    val engineRPMC by cycleViewModel.engineRPM.collectAsStateWithLifecycle()

    val motorHours by appDataViewModel.motorHours.collectAsStateWithLifecycle()

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
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("param1"), valueToString(param1, 1)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("param2"), valueToString(param2, 1)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("param3"), valueToString(param3, 1)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("param4"), valueToString(param4, 1)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("carSpeed"), valueToString(carSpeed, 1)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("carSpeedAccurate"), valueToString(carSpeedAccurate, 1)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("wheel1Speed"), valueToString(wheelsSpeed.wheel1, 1)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("wheel2Speed"), valueToString(wheelsSpeed.wheel2, 1)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("wheel3Speed"), valueToString(wheelsSpeed.wheel3, 1)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("wheel4Speed"), valueToString(wheelsSpeed.wheel4, 1)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("wheel1Pressure"), valueToString(wheelsPressure.wheel1, 2)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("wheel2Pressure"), valueToString(wheelsPressure.wheel2, 2)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("wheel3Pressure"), valueToString(wheelsPressure.wheel3, 2)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("wheel4Pressure"), valueToString(wheelsPressure.wheel4, 2)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("wheel1Temperature"), valueToString(wheelsTemperature.wheel1, 0)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("wheel2Temperature"), valueToString(wheelsTemperature.wheel2, 0)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("wheel3Temperature"), valueToString(wheelsTemperature.wheel3, 0)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("wheel4Temperature"), valueToString(wheelsTemperature.wheel4, 0)) }
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
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("frontLeftSeatMode"), seatModeToString(frontLeftSeatMode)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("frontRightSeatMode"), seatModeToString(frontRightSeatMode)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("outsideTemperature"), valueToString(outsideTemperature, 1)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("insideTemperature"), valueToString(insideTemperature, 1)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("isWindowsBlocked"), valueToString(isWindowsBlocked,
                booleanTrue = "заблокированы", booleanFalse = "разблокированы")) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("motorHours"), valueToString(motorHours, 1)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("motorHoursTrip"), valueToString(motorHoursTrip, 1)) }
            if (isGetCycleSignalEnabled) {
                item { StatusRow("Cycle напряжение, В", valueToString(voltageC, 1)) }
                item { StatusRow("Cycle скорость, км/ч", valueToString(carSpeedC, 1)) }
                item { StatusRow("Cycle обороты двигателя, об/мин", valueToString(engineRPMC, 1)) }
                //item { StatusRow("Cycle угловая скорость рысканья, °/с", valueToString(yawRateC, 2)) }
                item {
                    StatusRow(
                        "Cycle поперечное ускорение, м/с2",
                        valueToString(lateralAccelerationC, 2)
                    )
                }
                item {
                    StatusRow(
                        "Cycle продольное ускорение, м/с2",
                        valueToString(longitudinalAccelerationC, 2)
                    )
                }
                item { StatusRow("Cycle давление ПЛ, бар", valueToString(pressure1C, 1)) }
                item { StatusRow("Cycle давление ПП, бар", valueToString(pressure2C, 1)) }
                item { StatusRow("Cycle давление ЗЛ, бар", valueToString(pressure3C, 1)) }
                item { StatusRow("Cycle давление ЗП, бар", valueToString(pressure4C, 1)) }
                item { StatusRow("Cycle температура ПЛ, °C", valueToString(temperature1C, 1)) }
                item { StatusRow("Cycle температура ПП, °C", valueToString(temperature2C, 1)) }
                item { StatusRow("Cycle температура ЗП, °C", valueToString(temperature3C, 1)) }
                item { StatusRow("Cycle температура ЗЛ, °C", valueToString(temperature4C, 1)) }
            }
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
                textStyle = TextStyle(fontSize = 20.sp),
                onValueChange = { newText ->
                    searchText = newText
                },
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                label = {
                    Text(
                        text = "Фильтр по тексту (минимум 3 символа)",
                        fontSize = 20.sp
                    )
                },
                placeholder = {
                    Text(
                        text = "Введите текст для поиска...",
                        fontSize = 18.sp
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
    canViewModel: CanDataViewModel,
    onSaveToFile: (String, List<String>) -> Unit
) {
    val canFramesStructured by canViewModel.canFramesStructured.collectAsStateWithLifecycle()
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
    onServiceCommand: (String, String, String) -> Unit
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
                textStyle = TextStyle(fontSize = 20.sp),
                onValueChange = { newText ->
                    atCmdText = newText
                },
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                label = {
                    Text(
                        text = "AT команда",
                        fontSize = 20.sp
                    )
                },
                placeholder = {
                    Text(
                        text = "Введите AT команду",
                        fontSize = 18.sp
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
                            onServiceCommand(
                                BackgroundService.ACTION_SEND_AT,
                                BackgroundService.EXTRA_AT_CMD,
                                atCmdText
                            )
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
                            onServiceCommand(
                                BackgroundService.ACTION_SEND_AT,
                                BackgroundService.EXTRA_AT_CMD,
                                atCmdText
                            )
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