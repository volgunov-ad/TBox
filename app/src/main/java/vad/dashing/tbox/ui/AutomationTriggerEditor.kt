package vad.dashing.tbox.ui

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
import androidx.compose.ui.unit.dp
import vad.dashing.tbox.automation.AUTOMATION_DEFAULT_INTERVAL_MS
import vad.dashing.tbox.automation.AUTOMATION_MAX_INTERVAL_MS
import vad.dashing.tbox.automation.AUTOMATION_MIN_INTERVAL_MS
import vad.dashing.tbox.automation.AUTOMATION_SOLAR_MAX_OFFSET_MINUTES
import vad.dashing.tbox.automation.AutomationGeofenceDirection
import vad.dashing.tbox.automation.AutomationSignalCatalog
import vad.dashing.tbox.automation.AutomationSignalId
import vad.dashing.tbox.automation.AutomationSignalValueType
import vad.dashing.tbox.automation.AutomationStartupBehavior
import vad.dashing.tbox.automation.AutomationSystemEvent
import vad.dashing.tbox.automation.AutomationThresholdDirection
import vad.dashing.tbox.automation.AutomationTimeOfDay
import vad.dashing.tbox.automation.AutomationTrigger
import vad.dashing.tbox.automation.automationGeofenceRearmRadius
import vad.dashing.tbox.automation.instant
import vad.dashing.tbox.automation.sortedByAutomationLabel
import vad.dashing.tbox.location.GeoCoordinateParse
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
                    text = "Триггер ${index + 1}",
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
                    AutomationButtonLabel("Удалить")
                }
            }
            AutomationDropdown(
                label = "Тип триггера",
                value = triggerUiKind(trigger),
                options = TriggerUiKind.entries.sortedByAutomationLabel { it.label() },
                optionLabel = TriggerUiKind::label,
                onValueChange = { onChange(defaultTrigger(it, trigger.id)) },
            )
            AutomationTextField(
                value = trigger.id,
                onValueChange = { id -> onChange(trigger.withId(id)) },
                label = "ID триггера",
                modifier = Modifier.fillMaxWidth(),
            )
            when (trigger) {
                is AutomationTrigger.SystemEvent -> SystemEventFields(trigger, onChange)
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
        label = "Событие",
        value = trigger.event,
        options = AutomationSystemEvent.entries.sortedByAutomationLabel(::systemEventLabel),
        optionLabel = ::systemEventLabel,
        onValueChange = { onChange(trigger.copy(event = it)) },
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
            label = "Каждые",
            value = value,
            onValueChange = { raw ->
                onChange(trigger.copy(intervalMillis = raw.toLong() * unit.millis))
            },
            modifier = Modifier.weight(1f),
        )
        AutomationDropdown(
            label = "Единицы",
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
        IntervalPresetButton("30 с", 30_000L, AutomationIntervalUnit.SECONDS, trigger, onChange) {
            unit = it
        }
        IntervalPresetButton("1 мин", 60_000L, AutomationIntervalUnit.MINUTES, trigger, onChange) {
            unit = it
        }
        IntervalPresetButton("1 ч", 3_600_000L, AutomationIntervalUnit.HOURS, trigger, onChange) {
            unit = it
        }
    }
    Text(
        text = "Отсчёт начинается после полного запуска фоновой службы. Первый запуск — через " +
            "полный период. После перезапуска отсчёт начинается заново; пропущенные периоды " +
            "не выполняются пачкой.",
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
    val signals = AutomationSignalCatalog.signalsOfType(AutomationSignalValueType.NUMBER)
    AutomationDropdown(
        label = "Сигнал",
        value = trigger.signal,
        options = signals,
        optionLabel = { AutomationSignalCatalog.get(it).label },
        onValueChange = { signal ->
            val sources = AutomationSignalCatalog.get(signal).sources
            onChange(
                trigger.copy(
                    signal = signal,
                    source = trigger.source.takeIf { it in sources }
                        ?: AutomationSignalCatalog.preferredSource(sources),
                ),
            )
        },
    )
    AutomationSignalValueHint(trigger.signal)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AutomationDropdown(
            label = "Источник",
            value = trigger.source,
            options = AutomationSignalCatalog.sourcesForUi(trigger.signal),
            optionLabel = ::automationSourceLabel,
            onValueChange = { onChange(trigger.copy(source = it)) },
            modifier = Modifier.weight(1f),
        )
        AutomationDropdown(
            label = "Направление",
            value = trigger.direction,
            options = AutomationThresholdDirection.entries,
            optionLabel = {
                when (it) {
                    AutomationThresholdDirection.ABOVE -> "Стало больше"
                    AutomationThresholdDirection.BELOW -> "Стало меньше"
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
        text = "Порог повторного взведения",
        description = "",
        enabled = true,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AutomationDoubleField(
            label = "Порог",
            value = trigger.threshold,
            onValueChange = { threshold ->
                val reset = trigger.resetThreshold ?: threshold
                onChange(trigger.copy(threshold = threshold, resetThreshold = reset))
            },
            modifier = Modifier.weight(1f),
        )
        if (trigger.rearmEnabled) {
            AutomationDoubleField(
                label = "Порог повторного взведения",
                value = trigger.resetThreshold ?: trigger.threshold,
                onValueChange = { onChange(trigger.copy(resetThreshold = it)) },
                modifier = Modifier.weight(1f),
            )
        }
        AutomationSecondsField(
            label = "В течение, с",
            valueMillis = trigger.holdMillis,
            onValueChange = { onChange(trigger.copy(holdMillis = it)) },
            modifier = Modifier.weight(1f),
        )
    }
    Text(
        text = if (trigger.rearmEnabled) {
            "«В течение»: после пересечения порога значение должно оставаться выполненным N секунд (0 — сразу). " +
                "Повторное взведение: сигнал должен дойти до порога взведения или уйти за него, не обязательно попасть точно в число."
        } else {
            "Без порога повторного взведения автоматизация запускается на каждое новое значение сигнала, " +
                "пока условие порога выполняется. Повтор той же цифры с датчика не считается событием. " +
                "Мелкая дрожь (9.1 → 9.0 → 9.1) даст много запусков. Для температуры и CAN " +
                "обычно лучше оставить порог взведения включённым."
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
    val signals = AutomationSignalCatalog.signalsOfType(AutomationSignalValueType.STATE)
    AutomationDropdown(
        label = "Сигнал",
        value = trigger.signal,
        options = signals,
        optionLabel = { AutomationSignalCatalog.get(it).label },
        onValueChange = { signal ->
            val descriptor = AutomationSignalCatalog.get(signal)
            onChange(
                trigger.copy(
                    signal = signal,
                    source = trigger.source.takeIf { it in descriptor.sources }
                        ?: AutomationSignalCatalog.preferredSource(descriptor.sources),
                    expectedState = automationExpectedStateForSignal(
                        signal,
                        trigger.expectedState,
                    ),
                ),
            )
        },
    )
    AutomationSignalValueHint(trigger.signal)
    val descriptor = AutomationSignalCatalog.get(trigger.signal)
    val isForegroundApp = trigger.signal == AutomationSignalId.FOREGROUND_APP
    val isWifiSsid = trigger.signal == AutomationSignalId.WIFI_SSID
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AutomationDropdown(
            label = "Источник",
            value = trigger.source,
            options = AutomationSignalCatalog.sourcesForUi(descriptor.sources),
            optionLabel = ::automationSourceLabel,
            onValueChange = { onChange(trigger.copy(source = it)) },
            modifier = Modifier.weight(1f),
        )
        if (!isForegroundApp && !isWifiSsid) {
            if (descriptor.stateOptions.isNotEmpty()) {
                AutomationDropdown(
                    label = "Состояние",
                    value = trigger.expectedState,
                    options = stateOptionsWithCurrent(descriptor.stateOptions, trigger.expectedState),
                    optionLabel = ::automationStateLabel,
                    onValueChange = { onChange(trigger.copy(expectedState = it)) },
                    modifier = Modifier.weight(1f),
                )
            } else {
                AutomationTextField(
                    value = trigger.expectedState,
                    onValueChange = { onChange(trigger.copy(expectedState = it)) },
                    label = "Состояние",
                    modifier = Modifier.weight(1f),
                )
            }
        }
        AutomationSecondsField(
            label = "В течение, с",
            valueMillis = trigger.holdMillis,
            onValueChange = { onChange(trigger.copy(holdMillis = it)) },
            modifier = Modifier.weight(1f),
        )
    }
    if (isForegroundApp) {
        AutomationPackagePicker(
            label = "Приложение",
            packageName = trigger.expectedState,
            apps = apps,
            onValueChange = { onChange(trigger.copy(expectedState = it)) },
        )
    }
    if (isWifiSsid) {
        AutomationWifiSsidPicker(
            label = "Точка доступа",
            ssid = trigger.expectedState,
            includeNone = true,
            onValueChange = { onChange(trigger.copy(expectedState = it)) },
        )
    }
    StartupBehaviorField(trigger.startupBehavior) {
        onChange(trigger.copy(startupBehavior = it))
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
        label = "Координаты или ссылка на точку",
        singleLine = false,
        minLines = 2,
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        text = when {
            parsed != null ->
                "Распознано: ${formatGeofenceCoord(parsed.lat)}, ${formatGeofenceCoord(parsed.lon)}"
            trigger.queryText.isBlank() ->
                "Вставьте координаты или ссылку (Яндекс, Google, 2GIS, geo:, градусы)."
            else -> "Строка не распознана"
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
        label = "Направление",
        value = trigger.direction,
        options = AutomationGeofenceDirection.entries,
        optionLabel = {
            when (it) {
                AutomationGeofenceDirection.ENTER -> "Вошёл в зону"
                AutomationGeofenceDirection.EXIT -> "Выехал из зоны"
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
            label = "Радиус зоны, м",
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
            label = "Радиус повторного взведения, м",
            value = trigger.rearmRadiusMeters,
            onValueChange = { onChange(trigger.copy(rearmRadiusMeters = it)) },
            modifier = Modifier.weight(1f),
        )
        AutomationSecondsField(
            label = "В течение, с",
            valueMillis = trigger.holdMillis,
            onValueChange = { onChange(trigger.copy(holdMillis = it)) },
            modifier = Modifier.weight(1f),
        )
    }
    Text(
        text = when (trigger.direction) {
            AutomationGeofenceDirection.ENTER ->
                "Срабатывает при входе в радиус зоны. Повторно взводится после выхода за больший радиус взведения."
            AutomationGeofenceDirection.EXIT ->
                "Срабатывает при выходе за радиус зоны. Повторно взводится после входа в меньший радиус взведения."
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
        label = "В",
        value = trigger.at,
        onValueChange = { onChange(trigger.copy(at = it)) },
    )
    AutomationWeekdayPicker(
        selected = trigger.weekdays,
        onChange = { onChange(trigger.copy(weekdays = it)) },
        caption = "Ничего не отмечено — каждый день. Сработает один раз в эту минуту по часам ГУ. " +
            "Если служба в эту минуту не работала, запуск пропускается, кроме «Запустить автоматизацию».",
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
                    offsetMinutes = next.offsetMinutes.coerceIn(0, AUTOMATION_SOLAR_MAX_OFFSET_MINUTES),
                    offsetDirection = next.offsetDirection,
                ),
            )
        },
    )
    AutomationWeekdayPicker(
        selected = trigger.weekdays,
        onChange = { onChange(trigger.copy(weekdays = it)) },
        caption = "Ничего не отмечено — каждый день. Момент считается по текущей или последней " +
            "геопозиции и часам ГУ. Нет точки или нет восхода/заката в этот день — не сработает.",
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
        label = "Если условие выполнено после перезапуска службы",
        value = behavior,
        options = AutomationStartupBehavior.entries,
        optionLabel = {
            when (it) {
                AutomationStartupBehavior.INITIALIZE_ONLY -> "Запомнить начальное состояние"
                AutomationStartupBehavior.FIRE_IF_MATCHING -> "Запустить автоматизацию"
            }
        },
        onValueChange = onChange,
    )
    Text(
        text = when (behavior) {
            AutomationStartupBehavior.INITIALIZE_ONLY ->
                "После перезапуска фоновой службы, если условие уже выполнено, правило не запускается — " +
                    "текущее значение запоминается как база. Сработает только после повторного взведения " +
                    "и нового выполнения условия. Включение и правка правила всегда только запоминают базу, " +
                    "без запуска."
            AutomationStartupBehavior.FIRE_IF_MATCHING ->
                "После перезапуска фоновой службы, если условие уже выполнено, правило запускается " +
                    "(с учётом выдержки «в течение»). Затем триггер разряжается до повторного взведения. " +
                    "Включение и правка правила всё равно только запоминают базу, без запуска."
        },
        style = MaterialTheme.typography.tboxCaption,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    )
}

private enum class TriggerUiKind {
    SYSTEM_EVENT,
    INTERVAL,
    NUMERIC_THRESHOLD,
    STATE,
    GEOFENCE,
    TIME,
    SOLAR;

    fun label(): String = when (this) {
        SYSTEM_EVENT -> "Событие программы"
        INTERVAL -> "Периодически"
        NUMERIC_THRESHOLD -> "Числовой порог"
        STATE -> "Состояние"
        GEOFENCE -> "Геопозиция"
        TIME -> "Время"
        SOLAR -> "Восход / закат"
    }
}

private fun triggerUiKind(trigger: AutomationTrigger): TriggerUiKind = when (trigger) {
    is AutomationTrigger.SystemEvent -> TriggerUiKind.SYSTEM_EVENT
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
        SECONDS -> "Секунды"
        MINUTES -> "Минуты"
        HOURS -> "Часы"
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
    AutomationSystemEvent.BACKGROUND_SERVICE_STARTED -> "Фоновая служба полностью запущена"
    AutomationSystemEvent.MAIN_SCREEN_OPENED -> "Открыт главный экран программы"
    AutomationSystemEvent.MENU_OPENED -> "Открыто меню программы"
}
