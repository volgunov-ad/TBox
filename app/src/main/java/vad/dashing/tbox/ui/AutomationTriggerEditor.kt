package vad.dashing.tbox.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import vad.dashing.tbox.ui.theme.tboxCaption
import vad.dashing.tbox.ui.theme.tboxTitle
import vad.dashing.tbox.automation.AutomationGeofenceDirection
import vad.dashing.tbox.automation.AutomationSignalCatalog
import vad.dashing.tbox.automation.AutomationSignalId
import vad.dashing.tbox.automation.AutomationSignalSource
import vad.dashing.tbox.automation.AutomationSignalValueType
import vad.dashing.tbox.automation.AutomationStartupBehavior
import vad.dashing.tbox.automation.AutomationSystemEvent
import vad.dashing.tbox.automation.AutomationThresholdDirection
import vad.dashing.tbox.automation.AutomationTimeOfDay
import vad.dashing.tbox.automation.AutomationTrigger
import vad.dashing.tbox.automation.automationGeofenceRearmRadius
import vad.dashing.tbox.automation.sortedByAutomationLabel
import vad.dashing.tbox.location.GeoCoordinateParse

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
                is AutomationTrigger.NumericThreshold -> NumericTriggerFields(trigger, onChange)
                is AutomationTrigger.StateEquals -> StateTriggerFields(trigger, apps, onChange)
                is AutomationTrigger.Geofence -> GeofenceTriggerFields(trigger, onChange)
                is AutomationTrigger.Time -> TimeTriggerFields(trigger, onChange)
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
            val sources = AutomationSignalCatalog.get(signal).sources.toList()
            onChange(
                trigger.copy(
                    signal = signal,
                    source = trigger.source.takeIf { it in sources } ?: sources.first(),
                ),
            )
        },
    )
    AutomationSignalValueHint(trigger.signal)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val sources = AutomationSignalCatalog.get(trigger.signal).sources.toList()
        AutomationDropdown(
            label = "Источник",
            value = trigger.source,
            options = sources,
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
                        ?: descriptor.sources.first(),
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
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AutomationDropdown(
            label = "Источник",
            value = trigger.source,
            options = descriptor.sources.toList(),
            optionLabel = ::automationSourceLabel,
            onValueChange = { onChange(trigger.copy(source = it)) },
            modifier = Modifier.weight(1f),
        )
        if (!isForegroundApp) {
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
            "Если служба в эту минуту не работала, запуск пропускается.",
    )
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
    NUMERIC_THRESHOLD,
    STATE,
    GEOFENCE,
    TIME;

    fun label(): String = when (this) {
        SYSTEM_EVENT -> "Событие программы"
        NUMERIC_THRESHOLD -> "Числовой порог"
        STATE -> "Состояние"
        GEOFENCE -> "Геопозиция"
        TIME -> "Время"
    }
}

private fun triggerUiKind(trigger: AutomationTrigger): TriggerUiKind = when (trigger) {
    is AutomationTrigger.SystemEvent -> TriggerUiKind.SYSTEM_EVENT
    is AutomationTrigger.NumericThreshold -> TriggerUiKind.NUMERIC_THRESHOLD
    is AutomationTrigger.StateEquals -> TriggerUiKind.STATE
    is AutomationTrigger.Geofence -> TriggerUiKind.GEOFENCE
    is AutomationTrigger.Time -> TriggerUiKind.TIME
}

private fun defaultTrigger(kind: TriggerUiKind, id: String): AutomationTrigger = when (kind) {
    TriggerUiKind.SYSTEM_EVENT -> AutomationTrigger.SystemEvent(
        id = id,
        event = AutomationSystemEvent.BACKGROUND_SERVICE_STARTED,
    )

    TriggerUiKind.NUMERIC_THRESHOLD -> AutomationTrigger.NumericThreshold(
        id = id,
        signal = AutomationSignalId.ENGINE_RPM,
        source = AutomationSignalSource.TBOX,
        direction = AutomationThresholdDirection.ABOVE,
        threshold = 1_000.0,
        resetThreshold = 1_000.0,
    )

    TriggerUiKind.STATE -> AutomationTrigger.StateEquals(
        id = id,
        signal = AutomationSignalId.GEAR_MODE,
        source = AutomationSignalSource.TBOX,
        expectedState = "P",
    )

    TriggerUiKind.GEOFENCE -> AutomationTrigger.Geofence(id = id)

    TriggerUiKind.TIME -> AutomationTrigger.Time(
        id = id,
        at = AutomationTimeOfDay.DEFAULT,
    )
}

private fun AutomationTrigger.withId(id: String): AutomationTrigger = when (this) {
    is AutomationTrigger.SystemEvent -> copy(id = id)
    is AutomationTrigger.NumericThreshold -> copy(id = id)
    is AutomationTrigger.StateEquals -> copy(id = id)
    is AutomationTrigger.Geofence -> copy(id = id)
    is AutomationTrigger.Time -> copy(id = id)
}

private fun formatGeofenceCoord(value: Double): String =
    String.format(java.util.Locale.US, "%.6f", value)

internal fun systemEventLabel(event: AutomationSystemEvent): String = when (event) {
    AutomationSystemEvent.BACKGROUND_SERVICE_STARTED -> "Фоновая служба полностью запущена"
    AutomationSystemEvent.MAIN_SCREEN_OPENED -> "Открыт главный экран программы"
    AutomationSystemEvent.MENU_OPENED -> "Открыто меню программы"
}
