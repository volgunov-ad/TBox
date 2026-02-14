package vad.dashing.tbox.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import vad.dashing.tbox.BackgroundService
import vad.dashing.tbox.SettingsViewModel
import vad.dashing.tbox.TboxViewModel
import vad.dashing.tbox.valueToString
import java.text.SimpleDateFormat
import java.util.Locale
import vad.dashing.tbox.utils.MockLocationUtils
import vad.dashing.tbox.utils.canUseMockLocation

@Composable
fun ModemTabContent(
    viewModel: TboxViewModel,
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
            item {
                StatusRow(
                    "APN",
                    valueToString(
                        apn1State.apnStatus,
                        booleanTrue = "подключен",
                        booleanFalse = "отключен"
                    )
                )
            }
            item { StatusRow("Тип APN", apn1State.apnType) }
            item { StatusRow("IP APN", apn1State.apnIP) }
            item { StatusRow("Шлюз APN", apn1State.apnGate) }
            item { StatusRow("DNS1 APN", apn1State.apnDNS1) }
            item { StatusRow("DNS2 APN", apn1State.apnDNS2) }
            item { StatusRow("Время изменения", formattedAPN1ChangeTime) }

            item { StatusHeader("APN 2") }
            item {
                StatusRow(
                    "APN2",
                    valueToString(
                        apn2State.apnStatus,
                        booleanTrue = "подключен",
                        booleanFalse = "отключен"
                    )
                )
            }
            item { StatusRow("Тип APN2", apn2State.apnType) }
            item { StatusRow("IP APN2", apn2State.apnIP) }
            item { StatusRow("Шлюз APN2", apn2State.apnGate) }
            item { StatusRow("DNS1 APN2", apn2State.apnDNS1) }
            item { StatusRow("DNS2 APN2", apn2State.apnDNS2) }
            item { StatusRow("Время изменения", formattedAPN2ChangeTime) }
        }

        ModemModeSelectorContent(
            selectedMode = modemStatus,
            onServiceCommand = onServiceCommand,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun ModemModeSelectorContent(
    selectedMode: Int,
    onServiceCommand: (String, String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var buttonsEnabled by remember { mutableStateOf(true) }

    LaunchedEffect(buttonsEnabled) {
        if (!buttonsEnabled) {
            delay(1000)
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
fun SettingsTabContent(
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
    val floatingDashboardRows by settingsViewModel.floatingDashboardRows.collectAsStateWithLifecycle()
    val floatingDashboardCols by settingsViewModel.floatingDashboardCols.collectAsStateWithLifecycle()
    val activeFloatingDashboardId by settingsViewModel.activeFloatingDashboardId.collectAsStateWithLifecycle()

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
            delay(15000)
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
                        context
                    )
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
                        context
                    )
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
                        context
                    )
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
                        context
                    )
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
                    } else {
                        showOverlayRequirementsDialog(context)
                    }
                } else {
                    settingsViewModel.saveFloatingDashboardSetting(false)
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
                    showAlertDialog(
                        "ПРЕДУПРЕЖДЕНИЕ",
                        "Все изменения в экспертном режиме вы делаете на свой страх и " +
                            "риск.\nНо к необратимым последствиям ваши действия в этом " +
                            "режиме привести не могут",
                        context
                    )
                }
            },
            "Экспертный режим",
            "",
            true
        )

        if (isExpertModeEnabled) {
            SettingInt(
                canDataSaveCount,
                { value ->
                    settingsViewModel.saveCanDataSaveCount(value)
                },
                "Количество сохраняемых CAN фреймов (1...3600)",
                "",
                1,
                3600
            )
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
        .setPositiveButton("Настроить") { _, _ ->
            val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
            context.startActivity(intent)
        }
        .setNegativeButton("Отмена", null)
        .show()
}

private fun showOverlayRequirementsDialog(context: Context) {
    android.app.AlertDialog.Builder(context)
        .setTitle("Требуется разрешение")
        .setMessage(
            "Для работы приложения необходимо разрешение\n" +
                "«Отображение поверх других приложений»"
        )
        .setPositiveButton("Настроить") { _, _ ->
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                "package:${context.packageName}".toUri()
            )
            context.startActivity(intent)
        }
        .setNegativeButton("Отмена", null)
        .show()
}

@Composable
fun LocationTabContent(
    viewModel: TboxViewModel
) {
    val locValues by viewModel.locValues.collectAsStateWithLifecycle()
    val locationUpdateTime by viewModel.locationUpdateTime.collectAsStateWithLifecycle()
    val isLocValuesTrue by viewModel.isLocValuesTrue.collectAsStateWithLifecycle()

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
    }
}

@Composable
fun InfoTabContent(
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
            delay(30000)
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
