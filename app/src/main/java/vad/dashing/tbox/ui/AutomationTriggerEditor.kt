package vad.dashing.tbox.ui

import vad.dashing.tbox.ruEn

import android.content.res.Resources
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.HeadUnitCanMode
import vad.dashing.tbox.R
import vad.dashing.tbox.automation.Android10HardKeyDecode
import vad.dashing.tbox.automation.AUTOMATION_DEFAULT_INTERVAL_MS
import vad.dashing.tbox.automation.AUTOMATION_ESP_BLE_BTN_MAX
import vad.dashing.tbox.automation.AUTOMATION_ESP_BLE_BTN_MIN
import vad.dashing.tbox.automation.AUTOMATION_HARD_KEY_DEBOUNCE_MS
import vad.dashing.tbox.automation.AUTOMATION_HARD_KEY_DOUBLE_TAP_MS
import vad.dashing.tbox.automation.AUTOMATION_HARD_KEY_LONG_PRESS_MS
import vad.dashing.tbox.automation.AUTOMATION_MAX_INTERVAL_MS
import vad.dashing.tbox.automation.AUTOMATION_MIN_INTERVAL_MS
import vad.dashing.tbox.automation.AutomationEspBleBtnAction
import vad.dashing.tbox.automation.AutomationGeofenceDirection
import vad.dashing.tbox.automation.AutomationHardKeyStatus
import vad.dashing.tbox.automation.AutomationSignalCatalog
import vad.dashing.tbox.automation.AutomationSignalId
import vad.dashing.tbox.automation.AutomationSignalValueType
import vad.dashing.tbox.automation.AutomationStartupBehavior
import vad.dashing.tbox.automation.AutomationSystemEvent
import vad.dashing.tbox.automation.AutomationThresholdDirection
import vad.dashing.tbox.automation.AutomationTimeOfDay
import vad.dashing.tbox.automation.AutomationTrigger
import vad.dashing.tbox.automation.AutomationWidgetPressKind
import vad.dashing.tbox.automation.automationGeofenceRearmRadius
import vad.dashing.tbox.automation.automationWidgetDoubleTapTimeoutMillis
import vad.dashing.tbox.automation.instant
import vad.dashing.tbox.automation.sortedByAutomationLabel
import vad.dashing.tbox.esp.EspCompanionRepository
import vad.dashing.tbox.location.GeoCoordinateParse
import vad.dashing.tbox.mbcan.MbCanHardKeyUiLabels
import vad.dashing.tbox.mbcan.UniversalCanRepository
import vad.dashing.tbox.normalizeAutomationTriggerId
import vad.dashing.tbox.ui.theme.tboxCaption
import vad.dashing.tbox.ui.theme.tboxTitle

@Composable
internal fun AutomationTriggerEditor(
    trigger: AutomationTrigger,
    index: Int,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    apps: List<LaunchableAppEntry>,
    onChange: (AutomationTrigger) -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    AutomationCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = ruEn("Триггер ${index + 1}", "Trigger ${index + 1}"),
                    style = MaterialTheme.typography.tboxTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(
                    onClick = rememberWrappedOnClick(onMoveUp),
                    enabled = canMoveUp,
                ) {
                    AutomationButtonLabel("↑")
                }
                OutlinedButton(
                    onClick = rememberWrappedOnClick(onMoveDown),
                    enabled = canMoveDown,
                ) {
                    AutomationButtonLabel("↓")
                }
                OutlinedButton(onClick = rememberWrappedOnClick(onDelete)) {
                    AutomationButtonLabel(ruEn("Удалить", "Delete"))
                }
            }
            AutomationDropdown(
                label = ruEn("Тип триггера", "Trigger type"),
                value = triggerUiKind(trigger),
                options = TriggerUiKind.entries.sortedByAutomationLabel { it.label() },
                optionLabel = TriggerUiKind::label,
                onValueChange = { onChange(defaultTrigger(it, trigger.id)) },
            )
            AutomationTextField(
                value = trigger.id,
                onValueChange = { id -> onChange(trigger.withId(id)) },
                label = ruEn("ID триггера", "Trigger ID"),
                modifier = Modifier.fillMaxWidth(),
            )
            when (trigger) {
                is AutomationTrigger.SystemEvent -> SystemEventFields(trigger, onChange)
                is AutomationTrigger.WidgetPressed -> WidgetPressedTriggerFields(trigger, onChange)
                is AutomationTrigger.HardKey -> HardKeyTriggerFields(trigger, onChange)
                is AutomationTrigger.EspBleBtn -> EspBleBtnTriggerFields(trigger, onChange)
                is AutomationTrigger.EspGpioBtn -> EspGpioBtnTriggerFields(trigger, onChange)
                is AutomationTrigger.Interval -> IntervalTriggerFields(trigger, onChange)
                is AutomationTrigger.NumericThreshold -> NumericTriggerFields(trigger, onChange)
                is AutomationTrigger.StateEquals -> StateTriggerFields(trigger, apps, onChange)
                is AutomationTrigger.Geofence -> GeofenceTriggerFields(trigger, onChange)
                is AutomationTrigger.Time -> TimeTriggerFields(trigger, onChange)
                is AutomationTrigger.Solar -> SolarTriggerFields(trigger, onChange)
            }
    }
}

@Composable
private fun SystemEventFields(
    trigger: AutomationTrigger.SystemEvent,
    onChange: (AutomationTrigger) -> Unit,
) {
    AutomationDropdown(
        label = ruEn("Событие", "Event"),
        value = trigger.event,
        options = AutomationSystemEvent.entries.sortedByAutomationLabel(::systemEventLabel),
        optionLabel = ::systemEventLabel,
        onValueChange = { onChange(trigger.copy(event = it)) },
    )
    AutomationSecondsField(
        label = ruEn("В течение, с", "For, s"),
        valueMillis = trigger.holdMillis,
        onValueChange = { onChange(trigger.copy(holdMillis = it)) },
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        text = stringResource(R.string.automation_system_event_hold_hint),
        style = MaterialTheme.typography.tboxCaption,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun WidgetPressedTriggerFields(
    trigger: AutomationTrigger.WidgetPressed,
    onChange: (AutomationTrigger) -> Unit,
) {
    AutomationTextField(
        value = trigger.triggerId,
        onValueChange = { raw ->
            onChange(trigger.copy(triggerId = normalizeAutomationTriggerId(raw)))
        },
        label = stringResource(R.string.automation_widget_press_trigger_id_label),
        modifier = Modifier.fillMaxWidth(),
    )
    val singleLabel = stringResource(R.string.automation_widget_press_kind_single)
    val doubleLabel = stringResource(R.string.automation_widget_press_kind_double)
    AutomationDropdown(
        label = stringResource(R.string.automation_widget_press_kind_label),
        value = trigger.pressKind,
        options = AutomationWidgetPressKind.entries,
        optionLabel = { kind ->
            when (kind) {
                AutomationWidgetPressKind.SINGLE -> singleLabel
                AutomationWidgetPressKind.DOUBLE -> doubleLabel
            }
        },
        onValueChange = { onChange(trigger.copy(pressKind = it)) },
    )
    Text(
        text = stringResource(
            R.string.automation_widget_press_kind_hint,
            automationWidgetDoubleTapTimeoutMillis(),
        ),
        style = MaterialTheme.typography.tboxCaption,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Verified A9 mbCAN key codes (docs/MBCAN_VHAL_PARAMETERS_RU.md). */
internal val HARD_KEY_PICKER_CODES = listOf(
    29, 30, 31, 32, 33, 216, 316,
    115, 114, 163, 165, 28, 158, 582,
    210, 211, 212,
)

internal fun hardKeyPickerCodes(mode: HeadUnitCanMode): List<Int> =
    if (mode == HeadUnitCanMode.Android10Vhal) Android10HardKeyDecode.pickerCodes
    else HARD_KEY_PICKER_CODES

internal fun defaultHardKeyCode(mode: HeadUnitCanMode): Int =
    if (mode == HeadUnitCanMode.Android10Vhal) 24 else 115

internal fun hardKeyCodeLabel(resources: Resources, keyCode: Int): String =
    MbCanHardKeyUiLabels.formatLabel(resources, keyCode)

@Composable
private fun HardKeyTriggerFields(
    trigger: AutomationTrigger.HardKey,
    onChange: (AutomationTrigger) -> Unit,
) {
    val resources = LocalContext.current.resources
    val mode by UniversalCanRepository.mode.collectAsStateWithLifecycle()
    val pickerCodes = hardKeyPickerCodes(mode)
    val options = remember(trigger.keyCode, pickerCodes) {
        if (trigger.keyCode in pickerCodes) pickerCodes else pickerCodes + trigger.keyCode
    }
    AutomationDropdown(
        label = ruEn("Кнопка", "Button"),
        value = trigger.keyCode,
        options = options,
        optionLabel = { hardKeyCodeLabel(resources, it) },
        onValueChange = { onChange(trigger.copy(keyCode = it)) },
    )
    AutomationIntField(
        label = ruEn("Код кнопки вручную", "Button code, manual"),
        value = trigger.keyCode,
        onValueChange = { onChange(trigger.copy(keyCode = it)) },
        modifier = Modifier.fillMaxWidth(),
    )
    val pressedLabel = stringResource(R.string.automation_hard_key_status_pressed)
    val releasedLabel = stringResource(R.string.automation_hard_key_status_released)
    val singleLabel = stringResource(R.string.automation_hard_key_status_single)
    val doubleLabel = stringResource(R.string.automation_hard_key_status_double)
    val longLabel = stringResource(R.string.automation_hard_key_status_long)
    AutomationDropdown(
        label = ruEn("Событие кнопки", "Button event"),
        value = trigger.keyStatus,
        options = AutomationHardKeyStatus.entries,
        optionLabel = { status ->
            when (status) {
                AutomationHardKeyStatus.PRESSED -> pressedLabel
                AutomationHardKeyStatus.RELEASED -> releasedLabel
                AutomationHardKeyStatus.SINGLE -> singleLabel
                AutomationHardKeyStatus.DOUBLE -> doubleLabel
                AutomationHardKeyStatus.LONG -> longLabel
            }
        },
        onValueChange = { onChange(trigger.copy(keyStatus = it)) },
    )
    Text(
        text = stringResource(
            R.string.automation_hard_key_event_hint,
            AUTOMATION_HARD_KEY_DOUBLE_TAP_MS,
            AUTOMATION_HARD_KEY_LONG_PRESS_MS,
            AUTOMATION_HARD_KEY_DEBOUNCE_MS,
        ),
        style = MaterialTheme.typography.tboxCaption,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun EspBleBtnTriggerFields(
    trigger: AutomationTrigger.EspBleBtn,
    onChange: (AutomationTrigger) -> Unit,
) {
    AutomationEspBleDevicePicker(
        label = ruEn("Устройство", "Device"),
        mac = trigger.mac,
        onValueChange = { onChange(trigger.copy(mac = it)) },
    )
    AutomationDropdown(
        label = ruEn("Кнопка пульта", "Remote button"),
        value = trigger.btn,
        options = (AUTOMATION_ESP_BLE_BTN_MIN..AUTOMATION_ESP_BLE_BTN_MAX).toList(),
        optionLabel = { ruEn("Кнопка $it", "Button $it") },
        onValueChange = { onChange(trigger.copy(btn = it)) },
    )
    AutomationDropdown(
        label = ruEn("Действие", "Action"),
        value = trigger.act,
        options = AutomationEspBleBtnAction.entries,
        optionLabel = { act ->
            when (act) {
                AutomationEspBleBtnAction.PRESS -> ruEn("Нажатие", "Press")
                AutomationEspBleBtnAction.DOUBLE -> ruEn("Двойное", "Double")
                AutomationEspBleBtnAction.TRIPLE -> ruEn("Тройное", "Triple")
                AutomationEspBleBtnAction.LONG -> ruEn("Долгое", "Long")
                AutomationEspBleBtnAction.HOLD -> ruEn("Удержание", "Hold")
            }
        },
        onValueChange = { onChange(trigger.copy(act = it)) },
    )
    Text(
        text = ruEn("Shelly Blu Button 1 / RC Button 4 через компаньон ESP32 ", "Shelly Blu Button 1 / RC Button 4 via the ESP32 companion ") +
            ruEn("(вкладка «Компаньон»: BLE → Обучить). Button 1 = кнопка 1; RC4 = кнопки 1…4. ", "(Companion tab: BLE → Learn). Button 1 = button 1; RC4 = buttons 1…4. ") +
            ruEn("MAC обязателен; забытый пульт остаётся в правиле, пока не выберете другой.", "MAC is required; a forgotten remote stays in the rule until you choose another."),
        style = MaterialTheme.typography.tboxCaption,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun EspGpioBtnTriggerFields(
    trigger: AutomationTrigger.EspGpioBtn,
    onChange: (AutomationTrigger) -> Unit,
) {
    AutomationDropdown(
        label = ruEn("ESP-вход", "ESP input"),
        value = trigger.channel,
        options = (0..3).toList(),
        optionLabel = { ruEn("Вход $it", "Input $it") },
        onValueChange = { onChange(trigger.copy(channel = it)) },
    )
    val pressedLabel = stringResource(R.string.automation_hard_key_status_pressed)
    val releasedLabel = stringResource(R.string.automation_hard_key_status_released)
    val singleLabel = stringResource(R.string.automation_hard_key_status_single)
    val doubleLabel = stringResource(R.string.automation_hard_key_status_double)
    val longLabel = stringResource(R.string.automation_hard_key_status_long)
    AutomationDropdown(
        label = ruEn("Событие", "Event"),
        value = trigger.status,
        options = AutomationHardKeyStatus.entries,
        optionLabel = { status ->
            when (status) {
                AutomationHardKeyStatus.PRESSED -> pressedLabel
                AutomationHardKeyStatus.RELEASED -> releasedLabel
                AutomationHardKeyStatus.SINGLE -> singleLabel
                AutomationHardKeyStatus.DOUBLE -> doubleLabel
                AutomationHardKeyStatus.LONG -> longLabel
            }
        },
        onValueChange = { onChange(trigger.copy(status = it)) },
    )
    Text(
        text = ruEn("Входы компаньона ESP32 (активный уровень — замкнуто/нажато). ", "ESP32 companion inputs (active level — closed/pressed). ") +
            ruEn("«Нажал» / «Отпустил» — сырые фронты. «Одиночное» / «Двойное» / «Долгое» — ", "“Pressed” / “Released” — raw edges. “Single” / “Double” / “Long” — ") +
            ruEn("как у кнопок руля (двойное окно ${AUTOMATION_HARD_KEY_DOUBLE_TAP_MS} мс, ", "like the steering-wheel buttons (double-tap window ${AUTOMATION_HARD_KEY_DOUBLE_TAP_MS} ms, ") +
            ruEn("долгое ≥ ${AUTOMATION_HARD_KEY_LONG_PRESS_MS} мс).", "long ≥ ${AUTOMATION_HARD_KEY_LONG_PRESS_MS} ms)."),
        style = MaterialTheme.typography.tboxCaption,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun IntervalTriggerFields(
    trigger: AutomationTrigger.Interval,
    onChange: (AutomationTrigger) -> Unit,
) {
    var unit by remember(trigger.id) {
        mutableStateOf(preferredIntervalUnit(trigger.intervalMillis))
    }
    val value = (trigger.intervalMillis / unit.millis)
        .coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong())
        .toInt()
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AutomationIntField(
            label = ruEn("Каждые", "Every"),
            value = value,
            onValueChange = { raw ->
                onChange(trigger.copy(intervalMillis = raw.toLong() * unit.millis))
            },
            modifier = Modifier.weight(1f),
        )
        AutomationDropdown(
            label = ruEn("Единицы", "Units"),
            value = unit,
            options = AutomationIntervalUnit.entries,
            optionLabel = AutomationIntervalUnit::label,
            onValueChange = { nextUnit ->
                val nextValue = intervalValueRoundedUp(trigger.intervalMillis, nextUnit)
                    .coerceIn(nextUnit.minValue, nextUnit.maxValue)
                unit = nextUnit
                onChange(trigger.copy(intervalMillis = nextValue * nextUnit.millis))
            },
            modifier = Modifier.weight(1f),
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        IntervalPresetButton(ruEn("30 с", "30 s"), 30_000L, AutomationIntervalUnit.SECONDS, trigger, onChange) {
            unit = it
        }
        IntervalPresetButton(ruEn("1 мин", "1 min"), 60_000L, AutomationIntervalUnit.MINUTES, trigger, onChange) {
            unit = it
        }
        IntervalPresetButton(ruEn("1 ч", "1 h"), 3_600_000L, AutomationIntervalUnit.HOURS, trigger, onChange) {
            unit = it
        }
    }
    Text(
        text = ruEn("Отсчёт начинается после полного запуска фоновой службы. Первый запуск — через ", "The countdown starts after the background service is fully started. The first run is after ") +
            ruEn("полный период. После перезапуска отсчёт начинается заново; пропущенные периоды ", "a full period. After a restart the countdown starts over; missed periods ") +
            ruEn("не выполняются пачкой.", "are not run as a batch."),
        style = MaterialTheme.typography.tboxCaption,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun IntervalPresetButton(
    label: String,
    intervalMillis: Long,
    unit: AutomationIntervalUnit,
    trigger: AutomationTrigger.Interval,
    onChange: (AutomationTrigger) -> Unit,
    onUnitChange: (AutomationIntervalUnit) -> Unit,
) {
    OutlinedButton(
        onClick = rememberWrappedOnClick {
            onUnitChange(unit)
            onChange(trigger.copy(intervalMillis = intervalMillis))
        },
    ) {
        AutomationButtonLabel(label)
    }
}

@Composable
private fun NumericTriggerFields(
    trigger: AutomationTrigger.NumericThreshold,
    onChange: (AutomationTrigger) -> Unit,
) {
    val signals = AutomationSignalCatalog.pickerSignalsOfType(AutomationSignalValueType.NUMBER)
    val signalOptions = remember(signals) { signals.toAutomationSignalOptions() }
    AutomationSearchablePickerField(
        label = ruEn("Сигнал", "Signal"),
        value = trigger.signal,
        options = signalOptions,
        optionLabel = { AutomationSignalCatalog.get(it).label },
        onValueChange = { signal ->
            onChange(
                trigger.copy(
                    signal = signal,
                    source = AutomationSignalCatalog.resolveSource(signal, trigger.source),
                    mac = if (signal == AutomationSignalId.ESP_BLE_BATTERY) {
                        trigger.mac.ifBlank {
                            EspCompanionRepository.bleMacs.value.firstOrNull().orEmpty()
                        }
                    } else {
                        ""
                    },
                ),
            )
        },
    )
    AutomationSignalValueHint(trigger.signal)
    if (trigger.signal == AutomationSignalId.ESP_BLE_BATTERY) {
        AutomationEspBleDevicePicker(
            label = ruEn("Устройство", "Device"),
            mac = trigger.mac,
            onValueChange = { onChange(trigger.copy(mac = it)) },
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AutomationDropdown(
            label = ruEn("Источник", "Source"),
            value = AutomationSignalCatalog.resolveSource(trigger.signal, trigger.source),
            options = AutomationSignalCatalog.sourcesForUi(trigger.signal),
            optionLabel = ::automationSourceLabel,
            onValueChange = { onChange(trigger.copy(source = it)) },
            modifier = Modifier.weight(1f),
        )
        AutomationDropdown(
            label = ruEn("Направление", "Direction"),
            value = trigger.direction,
            options = AutomationThresholdDirection.entries,
            optionLabel = {
                when (it) {
                    AutomationThresholdDirection.ABOVE -> ruEn("Стало больше", "Became greater")
                    AutomationThresholdDirection.BELOW -> ruEn("Стало меньше", "Became less")
                }
            },
            onValueChange = { direction ->
                onChange(
                    trigger.copy(
                        direction = direction,
                        resetThreshold = trigger.threshold,
                    ),
                )
            },
            modifier = Modifier.weight(1f),
        )
    }
    SettingSwitch(
        isChecked = trigger.rearmEnabled,
        onCheckedChange = { enabled ->
            onChange(trigger.copy(rearmEnabled = enabled))
        },
        text = ruEn("Порог повторного взведения", "Rearm threshold"),
        description = "",
        enabled = true,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AutomationDoubleField(
            label = ruEn("Порог", "Threshold"),
            value = trigger.threshold,
            onValueChange = { threshold ->
                val reset = trigger.resetThreshold ?: threshold
                onChange(trigger.copy(threshold = threshold, resetThreshold = reset))
            },
            modifier = Modifier.weight(1f),
        )
        if (trigger.rearmEnabled) {
            AutomationDoubleField(
                label = ruEn("Порог повторного взведения", "Rearm threshold"),
                value = trigger.resetThreshold ?: trigger.threshold,
                onValueChange = { onChange(trigger.copy(resetThreshold = it)) },
                modifier = Modifier.weight(1f),
            )
        }
        AutomationSecondsField(
            label = ruEn("В течение, с", "For, s"),
            valueMillis = trigger.holdMillis,
            onValueChange = { onChange(trigger.copy(holdMillis = it)) },
            modifier = Modifier.weight(1f),
        )
    }
    Text(
        text = if (trigger.rearmEnabled) {
            ruEn("«В течение»: после пересечения порога значение должно оставаться выполненным N секунд (0 — сразу). ", "“For”: after the threshold is crossed, the value must stay satisfied for N seconds (0 — immediately). ") +
                ruEn("Повторное взведение: сигнал должен дойти до порога взведения или уйти за него, не обязательно попасть точно в число.", "Rearm: the signal must reach the rearm threshold or go past it; it does not have to land exactly on the number.")
        } else {
            ruEn("Без порога повторного взведения автоматизация запускается на каждое новое значение сигнала, ", "Without a rearm threshold the automation runs on every new signal value, ") +
                ruEn("пока условие порога выполняется. Повтор той же цифры с датчика не считается событием. ", "while the threshold condition holds. The same number from the sensor again is not an event. ") +
                ruEn("Мелкая дрожь (9.1 → 9.0 → 9.1) даст много запусков. Для температуры и CAN ", "Small jitter (9.1 → 9.0 → 9.1) causes many runs. For temperature and CAN ") +
                ruEn("обычно лучше оставить порог взведения включённым.", "it is usually better to leave the rearm threshold on.")
        },
        style = MaterialTheme.typography.tboxCaption,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    )
    StartupBehaviorField(trigger.startupBehavior) {
        onChange(trigger.copy(startupBehavior = it))
    }
}

@Composable
private fun StateTriggerFields(
    trigger: AutomationTrigger.StateEquals,
    apps: List<LaunchableAppEntry>,
    onChange: (AutomationTrigger) -> Unit,
) {
    val signals = AutomationSignalCatalog.pickerSignalsOfType(AutomationSignalValueType.STATE)
    val signalOptions = remember(signals) { signals.toAutomationSignalOptions() }
    AutomationSearchablePickerField(
        label = ruEn("Сигнал", "Signal"),
        value = trigger.signal,
        options = signalOptions,
        optionLabel = { AutomationSignalCatalog.get(it).label },
        onValueChange = { signal ->
            onChange(
                trigger.copy(
                    signal = signal,
                    source = AutomationSignalCatalog.resolveSource(signal, trigger.source),
                    expectedState = automationTriggerExpectedStateForSignal(
                        signal,
                        trigger.expectedState,
                    ),
                    startupBehavior = if (
                        signal == AutomationSignalId.ROAD_TUNNEL ||
                        signal == AutomationSignalId.ROAD_SETTLEMENT
                    ) {
                        AutomationStartupBehavior.INITIALIZE_ONLY
                    } else {
                        trigger.startupBehavior
                    },
                    relayId = bleRelayIdForSignal(signal),
                ),
            )
        },
    )
    AutomationSignalValueHint(trigger.signal)
    BleRelaySignalIdField(trigger.signal, trigger.relayId) {
        onChange(trigger.copy(relayId = it))
    }
    val descriptor = AutomationSignalCatalog.get(trigger.signal)
    val isForegroundApp = trigger.signal == AutomationSignalId.FOREGROUND_APP
    val isWifiSsid = trigger.signal == AutomationSignalId.WIFI_SSID
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AutomationDropdown(
            label = ruEn("Источник", "Source"),
            value = AutomationSignalCatalog.resolveSource(trigger.signal, trigger.source),
            options = AutomationSignalCatalog.sourcesForUi(descriptor.id),
            optionLabel = ::automationSourceLabel,
            onValueChange = { onChange(trigger.copy(source = it)) },
            modifier = Modifier.weight(1f),
        )
        if (!isForegroundApp && !isWifiSsid) {
            if (descriptor.stateOptions.isNotEmpty()) {
                AutomationDropdown(
                    label = ruEn("Состояние", "State"),
                    value = trigger.expectedState,
                    options = stateOptionsWithCurrent(descriptor.stateOptions, trigger.expectedState),
                    optionLabel = { automationTriggerStateLabel(trigger.signal, it) },
                    onValueChange = { onChange(trigger.copy(expectedState = it)) },
                    modifier = Modifier.weight(1f),
                )
            } else {
                AutomationTextField(
                    value = trigger.expectedState,
                    onValueChange = { onChange(trigger.copy(expectedState = it)) },
                    label = ruEn("Состояние", "State"),
                    modifier = Modifier.weight(1f),
                )
            }
        }
        AutomationSecondsField(
            label = ruEn("В течение, с", "For, s"),
            valueMillis = trigger.holdMillis,
            onValueChange = { onChange(trigger.copy(holdMillis = it)) },
            modifier = Modifier.weight(1f),
        )
    }
    if (isForegroundApp) {
        AutomationPackagePicker(
            label = ruEn("Приложение", "App"),
            packageName = trigger.expectedState,
            apps = apps,
            onValueChange = { onChange(trigger.copy(expectedState = it)) },
        )
    }
    if (isWifiSsid) {
        AutomationWifiSsidPicker(
            label = ruEn("Точка доступа", "Hotspot"),
            ssid = trigger.expectedState,
            includeNone = true,
            onValueChange = { onChange(trigger.copy(expectedState = it)) },
        )
    }
    if (trigger.signal != AutomationSignalId.ROAD_TUNNEL &&
        trigger.signal != AutomationSignalId.ROAD_SETTLEMENT
    ) {
        StartupBehaviorField(trigger.startupBehavior) {
            onChange(trigger.copy(startupBehavior = it))
        }
    }
}

@Composable
private fun GeofenceTriggerFields(
    trigger: AutomationTrigger.Geofence,
    onChange: (AutomationTrigger) -> Unit,
) {
    val parsed = GeoCoordinateParse.parse(trigger.queryText)
    AutomationTextField(
        value = trigger.queryText,
        onValueChange = { text ->
            val point = GeoCoordinateParse.parse(text)
            onChange(
                trigger.copy(
                    queryText = text,
                    latitude = point?.lat ?: Double.NaN,
                    longitude = point?.lon ?: Double.NaN,
                ),
            )
        },
        label = ruEn("Координаты или ссылка на точку", "Coordinates or a link to a point"),
        singleLine = false,
        minLines = 2,
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        text = when {
            parsed != null ->
                ruEn("Распознано: ${formatGeofenceCoord(parsed.lat)}, ${formatGeofenceCoord(parsed.lon)}", "Recognized: ${formatGeofenceCoord(parsed.lat)}, ${formatGeofenceCoord(parsed.lon)}")
            trigger.queryText.isBlank() ->
                ruEn("Вставьте координаты или ссылку (Яндекс, Google, 2GIS, geo:, градусы).", "Paste coordinates or a link (Yandex, Google, 2GIS, geo:, degrees).")
            else -> ruEn("Строка не распознана", "The line was not recognized")
        },
        style = MaterialTheme.typography.tboxCaption,
        color = if (parsed != null || trigger.queryText.isBlank()) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.error
        },
        modifier = Modifier.fillMaxWidth(),
    )
    AutomationDropdown(
        label = ruEn("Направление", "Direction"),
        value = trigger.direction,
        options = AutomationGeofenceDirection.entries,
        optionLabel = {
            when (it) {
                AutomationGeofenceDirection.ENTER -> ruEn("Вошёл в зону", "Entered the zone")
                AutomationGeofenceDirection.EXIT -> ruEn("Выехал из зоны", "Left the zone")
            }
        },
        onValueChange = { direction ->
            onChange(
                trigger.copy(
                    direction = direction,
                    rearmRadiusMeters = automationGeofenceRearmRadius(
                        direction,
                        trigger.zoneRadiusMeters,
                        trigger.rearmRadiusMeters,
                    ),
                ),
            )
        },
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AutomationDoubleField(
            label = ruEn("Радиус зоны, м", "Zone radius, m"),
            value = trigger.zoneRadiusMeters,
            onValueChange = { zone ->
                onChange(
                    trigger.copy(
                        zoneRadiusMeters = zone,
                        rearmRadiusMeters = automationGeofenceRearmRadius(
                            trigger.direction,
                            zone,
                            trigger.rearmRadiusMeters,
                        ),
                    ),
                )
            },
            modifier = Modifier.weight(1f),
        )
        AutomationDoubleField(
            label = ruEn("Радиус повторного взведения, м", "Rearm radius, m"),
            value = trigger.rearmRadiusMeters,
            onValueChange = { onChange(trigger.copy(rearmRadiusMeters = it)) },
            modifier = Modifier.weight(1f),
        )
        AutomationSecondsField(
            label = ruEn("В течение, с", "For, s"),
            valueMillis = trigger.holdMillis,
            onValueChange = { onChange(trigger.copy(holdMillis = it)) },
            modifier = Modifier.weight(1f),
        )
    }
    Text(
        text = when (trigger.direction) {
            AutomationGeofenceDirection.ENTER ->
                ruEn("Срабатывает при входе в радиус зоны. Повторно взводится после выхода за больший радиус взведения.", "Fires on entering the zone radius. Rearms after leaving the larger rearm radius.")
            AutomationGeofenceDirection.EXIT ->
                ruEn("Срабатывает при выходе за радиус зоны. Повторно взводится после входа в меньший радиус взведения.", "Fires on leaving the zone radius. Rearms after entering the smaller rearm radius.")
        },
        style = MaterialTheme.typography.tboxCaption,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    )
    StartupBehaviorField(trigger.startupBehavior) {
        onChange(trigger.copy(startupBehavior = it))
    }
}

@Composable
private fun TimeTriggerFields(
    trigger: AutomationTrigger.Time,
    onChange: (AutomationTrigger) -> Unit,
) {
    AutomationTimeOfDayPicker(
        label = ruEn("В", "V"),
        value = trigger.at,
        onValueChange = { onChange(trigger.copy(at = it)) },
    )
    AutomationWeekdayPicker(
        selected = trigger.weekdays,
        onChange = { onChange(trigger.copy(weekdays = it)) },
        caption = ruEn("Ничего не отмечено — каждый день. Сработает один раз в эту минуту по часам ГУ. ", "Nothing selected — every day. It fires once during that minute by the HU clock. ") +
            ruEn("Если служба в эту минуту не работала, запуск пропускается, кроме «Запустить автоматизацию».", "If the service was not running during that minute, the run is skipped, except for “Run automation”."),
    )
    StartupBehaviorField(trigger.startupBehavior) {
        onChange(trigger.copy(startupBehavior = it))
    }
}

@Composable
private fun SolarTriggerFields(
    trigger: AutomationTrigger.Solar,
    onChange: (AutomationTrigger) -> Unit,
) {
    AutomationSolarInstantFields(
        instant = trigger.instant(),
        onChange = { next ->
            onChange(
                trigger.copy(
                    event = next.event,
                    offsetMinutes = next.offsetMinutes,
                    offsetDirection = next.offsetDirection,
                ),
            )
        },
    )
    AutomationWeekdayPicker(
        selected = trigger.weekdays,
        onChange = { onChange(trigger.copy(weekdays = it)) },
        caption = ruEn("Ничего не отмечено — каждый день. Момент считается по текущей или последней ", "Nothing selected — every day. The moment is taken from the current or last ") +
            ruEn("геопозиции и часам ГУ. Нет точки или нет восхода/заката в этот день — не сработает.", "location and the HU clock. No point, or no sunrise/sunset that day — it will not fire."),
    )
    StartupBehaviorField(trigger.startupBehavior) {
        onChange(trigger.copy(startupBehavior = it))
    }
}

@Composable
private fun StartupBehaviorField(
    behavior: AutomationStartupBehavior,
    onChange: (AutomationStartupBehavior) -> Unit,
) {
    AutomationDropdown(
        label = ruEn("Если условие выполнено после перезапуска службы", "If the condition is met after the service restarts"),
        value = behavior,
        options = AutomationStartupBehavior.entries,
        optionLabel = {
            when (it) {
                AutomationStartupBehavior.INITIALIZE_ONLY -> ruEn("Запомнить начальное состояние", "Remember the initial state")
                AutomationStartupBehavior.FIRE_IF_MATCHING -> ruEn("Запустить автоматизацию", "Run automation")
            }
        },
        onValueChange = onChange,
    )
    Text(
        text = when (behavior) {
            AutomationStartupBehavior.INITIALIZE_ONLY ->
                ruEn("После перезапуска фоновой службы, если условие уже выполнено, правило не запускается — ", "After the background service restarts, if the condition is already met, the rule does not run — ") +
                    ruEn("текущее значение запоминается как база. Сработает только после повторного взведения ", "the current value is stored as the baseline. It fires only after rearming ") +
                    ruEn("и нового выполнения условия. Включение и правка правила всегда только запоминают базу, ", "and the condition is met again. Turning a rule on and editing it only store the baseline, ") +
                    ruEn("без запуска.", "without a run.")
            AutomationStartupBehavior.FIRE_IF_MATCHING ->
                ruEn("После перезапуска фоновой службы, если условие уже выполнено, правило запускается ", "After the background service restarts, if the condition is already met, the rule runs ") +
                    ruEn("(с учётом выдержки «в течение»). Затем триггер разряжается до повторного взведения. ", "(including the “for” hold). The trigger then disarms until it is rearmed. ") +
                    ruEn("Включение и правка правила всё равно только запоминают базу, без запуска.", "Turning a rule on and editing it still only store the baseline, without a run.")
        },
        style = MaterialTheme.typography.tboxCaption,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    )
}

private enum class TriggerUiKind {
    SYSTEM_EVENT,
    WIDGET_PRESS,
    HARD_KEY,
    ESP_BLE_BTN,
    ESP_GPIO_BTN,
    INTERVAL,
    NUMERIC_THRESHOLD,
    STATE,
    GEOFENCE,
    TIME,
    SOLAR;

    fun label(): String = when (this) {
        SYSTEM_EVENT -> ruEn("Событие программы", "App event")
        WIDGET_PRESS -> ruEn("Нажатие виджета-триггера", "Widget-trigger press")
        HARD_KEY -> ruEn("Кнопка на руле / двери", "Steering-wheel / door button")
        ESP_BLE_BTN -> ruEn("Кнопка Shelly Blu (компаньон)", "Shelly Blu button (companion)")
        ESP_GPIO_BTN -> ruEn("ESP-вход (компаньон)", "ESP input (companion)")
        INTERVAL -> ruEn("Периодически", "Periodically")
        NUMERIC_THRESHOLD -> ruEn("Числовой порог", "Numeric threshold")
        STATE -> ruEn("Состояние", "State")
        GEOFENCE -> ruEn("Геопозиция", "Location")
        TIME -> ruEn("Время", "Time")
        SOLAR -> ruEn("Восход / закат", "Sunrise / sunset")
    }
}

private fun triggerUiKind(trigger: AutomationTrigger): TriggerUiKind = when (trigger) {
    is AutomationTrigger.SystemEvent -> TriggerUiKind.SYSTEM_EVENT
    is AutomationTrigger.WidgetPressed -> TriggerUiKind.WIDGET_PRESS
    is AutomationTrigger.HardKey -> TriggerUiKind.HARD_KEY
    is AutomationTrigger.EspBleBtn -> TriggerUiKind.ESP_BLE_BTN
    is AutomationTrigger.EspGpioBtn -> TriggerUiKind.ESP_GPIO_BTN
    is AutomationTrigger.Interval -> TriggerUiKind.INTERVAL
    is AutomationTrigger.NumericThreshold -> TriggerUiKind.NUMERIC_THRESHOLD
    is AutomationTrigger.StateEquals -> TriggerUiKind.STATE
    is AutomationTrigger.Geofence -> TriggerUiKind.GEOFENCE
    is AutomationTrigger.Time -> TriggerUiKind.TIME
    is AutomationTrigger.Solar -> TriggerUiKind.SOLAR
}

private fun defaultTrigger(kind: TriggerUiKind, id: String): AutomationTrigger = when (kind) {
    TriggerUiKind.SYSTEM_EVENT -> AutomationTrigger.SystemEvent(
        id = id,
        event = AutomationSystemEvent.BACKGROUND_SERVICE_STARTED,
    )

    TriggerUiKind.WIDGET_PRESS -> AutomationTrigger.WidgetPressed(
        id = id,
        triggerId = "",
    )

    TriggerUiKind.HARD_KEY -> AutomationTrigger.HardKey(
        id = id,
        keyCode = defaultHardKeyCode(UniversalCanRepository.mode.value),
    )

    TriggerUiKind.ESP_BLE_BTN -> AutomationTrigger.EspBleBtn(
        id = id,
        mac = EspCompanionRepository.bleMacs.value.firstOrNull().orEmpty(),
        btn = 1,
        act = AutomationEspBleBtnAction.PRESS,
    )

    TriggerUiKind.ESP_GPIO_BTN -> AutomationTrigger.EspGpioBtn(
        id = id,
        channel = 0,
        status = AutomationHardKeyStatus.SINGLE,
    )

    TriggerUiKind.INTERVAL -> AutomationTrigger.Interval(
        id = id,
        intervalMillis = AUTOMATION_DEFAULT_INTERVAL_MS,
    )

    TriggerUiKind.NUMERIC_THRESHOLD -> {
        val signal = AutomationSignalId.ENGINE_RPM
        AutomationTrigger.NumericThreshold(
            id = id,
            signal = signal,
            source = AutomationSignalCatalog.preferredSource(signal),
            direction = AutomationThresholdDirection.ABOVE,
            threshold = 1_000.0,
            resetThreshold = 1_000.0,
        )
    }

    TriggerUiKind.STATE -> {
        val signal = AutomationSignalId.GEAR_MODE
        AutomationTrigger.StateEquals(
            id = id,
            signal = signal,
            source = AutomationSignalCatalog.preferredSource(signal),
            expectedState = "P",
        )
    }

    TriggerUiKind.GEOFENCE -> AutomationTrigger.Geofence(id = id)

    TriggerUiKind.TIME -> AutomationTrigger.Time(
        id = id,
        at = AutomationTimeOfDay.DEFAULT,
    )

    TriggerUiKind.SOLAR -> AutomationTrigger.Solar(id = id)
}

private fun AutomationTrigger.withId(id: String): AutomationTrigger = when (this) {
    is AutomationTrigger.SystemEvent -> copy(id = id)
    is AutomationTrigger.WidgetPressed -> copy(id = id)
    is AutomationTrigger.HardKey -> copy(id = id)
    is AutomationTrigger.EspBleBtn -> copy(id = id)
    is AutomationTrigger.EspGpioBtn -> copy(id = id)
    is AutomationTrigger.Interval -> copy(id = id)
    is AutomationTrigger.NumericThreshold -> copy(id = id)
    is AutomationTrigger.StateEquals -> copy(id = id)
    is AutomationTrigger.Geofence -> copy(id = id)
    is AutomationTrigger.Time -> copy(id = id)
    is AutomationTrigger.Solar -> copy(id = id)
}

private enum class AutomationIntervalUnit(
    val millis: Long,
    val minValue: Long,
    val maxValue: Long,
) {
    SECONDS(
        millis = 1_000L,
        minValue = AUTOMATION_MIN_INTERVAL_MS / 1_000L,
        maxValue = AUTOMATION_MAX_INTERVAL_MS / 1_000L,
    ),
    MINUTES(
        millis = 60_000L,
        minValue = 1L,
        maxValue = AUTOMATION_MAX_INTERVAL_MS / 60_000L,
    ),
    HOURS(
        millis = 3_600_000L,
        minValue = 1L,
        maxValue = AUTOMATION_MAX_INTERVAL_MS / 3_600_000L,
    );

    fun label(): String = when (this) {
        SECONDS -> ruEn("Секунды", "Seconds")
        MINUTES -> ruEn("Минуты", "Minutes")
        HOURS -> ruEn("Часы", "Hours")
    }
}

private fun preferredIntervalUnit(intervalMillis: Long): AutomationIntervalUnit = when {
    intervalMillis > 0L && intervalMillis % AutomationIntervalUnit.HOURS.millis == 0L ->
        AutomationIntervalUnit.HOURS
    intervalMillis > 0L && intervalMillis % AutomationIntervalUnit.MINUTES.millis == 0L ->
        AutomationIntervalUnit.MINUTES
    else -> AutomationIntervalUnit.SECONDS
}

private fun intervalValueRoundedUp(
    intervalMillis: Long,
    unit: AutomationIntervalUnit,
): Long {
    if (intervalMillis <= 0L) return unit.minValue
    val whole = intervalMillis / unit.millis
    return whole + if (intervalMillis % unit.millis == 0L) 0L else 1L
}

private fun formatGeofenceCoord(value: Double): String =
    String.format(java.util.Locale.US, "%.6f", value)

internal fun systemEventLabel(event: AutomationSystemEvent): String = when (event) {
    AutomationSystemEvent.BACKGROUND_SERVICE_STARTED -> ruEn("Фоновая служба полностью запущена", "Background service fully started")
    AutomationSystemEvent.MAIN_SCREEN_OPENED -> ruEn("Открыт главный экран программы", "App home screen opened")
    AutomationSystemEvent.MENU_OPENED -> ruEn("Открыто меню программы", "App menu is open")
}
