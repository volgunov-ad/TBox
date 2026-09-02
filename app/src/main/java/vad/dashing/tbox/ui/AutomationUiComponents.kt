package vad.dashing.tbox.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import vad.dashing.tbox.location.GeoCoordinateParse
import vad.dashing.tbox.ui.theme.tboxBody
import vad.dashing.tbox.ui.theme.tboxButton
import vad.dashing.tbox.ui.theme.tboxCaption
import vad.dashing.tbox.ui.theme.tboxTitle
import vad.dashing.tbox.automation.AutomationComparison
import vad.dashing.tbox.automation.AutomationCondition
import vad.dashing.tbox.automation.AutomationGeofencePresence
import vad.dashing.tbox.automation.AutomationUiState
import vad.dashing.tbox.automation.AUTOMATION_MAX_CONDITION_DEPTH
import vad.dashing.tbox.automation.AutomationSignalCatalog
import vad.dashing.tbox.automation.AutomationSignalId
import vad.dashing.tbox.automation.AutomationSignalSource
import vad.dashing.tbox.automation.AutomationSignalValueType
import vad.dashing.tbox.automation.AUTOMATION_SOLAR_MAX_OFFSET_MINUTES
import vad.dashing.tbox.automation.AutomationClock
import vad.dashing.tbox.automation.AutomationSolarEvent
import vad.dashing.tbox.automation.AutomationSolarInstant
import vad.dashing.tbox.automation.AutomationSolarLogic
import vad.dashing.tbox.automation.AutomationSolarOffsetDirection
import vad.dashing.tbox.automation.AutomationTimeOfDay
import vad.dashing.tbox.automation.AutomationWeekday
import vad.dashing.tbox.automation.automationWeekdayShortLabel
import vad.dashing.tbox.automation.WifiStaController
import vad.dashing.tbox.automation.WifiStaSsid
import vad.dashing.tbox.automation.sortedByAutomationLabel
import vad.dashing.tbox.location.GeoDisplayRepository
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
internal fun <T> AutomationDropdown(
    label: String,
    value: T,
    options: List<T>,
    optionLabel: (T) -> String,
    onValueChange: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    optionSupportingLabel: ((T) -> String)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    var menuWidth by remember { mutableStateOf(0.dp) }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val hideIme = remember(focusManager, keyboardController) {
        {
            keyboardController?.hide()
            focusManager.clearFocus(force = true)
        }
    }
    // Popup restores the previous TextField after dismiss, which reopens the IME.
    // Skip the initial expanded=false so a newly composed dropdown does not steal
    // focus from a field the user just tapped.
    var menuHadOpened by remember { mutableStateOf(false) }
    LaunchedEffect(expanded) {
        if (expanded) {
            menuHadOpened = true
            hideIme()
            return@LaunchedEffect
        }
        if (!menuHadOpened) return@LaunchedEffect
        hideIme()
        delay(64)
        hideIme()
    }
    val openMenu = rememberWrappedOnClick {
        hideIme()
        expanded = true
    }
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.tboxCaption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = openMenu,
                enabled = enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { size ->
                        menuWidth = with(density) { size.width.toDp() }
                    },
            ) {
                Text(
                    text = optionLabel(value),
                    style = MaterialTheme.typography.tboxTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Start,
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = {
                    hideIme()
                    expanded = false
                },
                modifier = Modifier.width(menuWidth.coerceAtLeast(280.dp)),
            ) {
                options.forEach { option ->
                    key(option) {
                        val menuItemClick = rememberWrappedOnClick {
                            hideIme()
                            expanded = false
                            onValueChange(option)
                        }
                        DropdownMenuItem(
                            text = {
                                val supporting = optionSupportingLabel?.invoke(option).orEmpty()
                                if (supporting.isBlank()) {
                                    Text(
                                        text = optionLabel(option),
                                        style = MaterialTheme.typography.tboxTitle,
                                    )
                                } else {
                                    Column {
                                        Text(
                                            text = optionLabel(option),
                                            style = MaterialTheme.typography.tboxTitle,
                                        )
                                        Text(
                                            text = supporting,
                                            style = MaterialTheme.typography.tboxCaption,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            },
                            onClick = menuItemClick,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun AutomationCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

@Composable
internal fun AutomationButtonLabel(text: String) {
    Text(text = text, style = MaterialTheme.typography.tboxButton)
}

@Composable
internal fun AutomationBodyText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.tboxBody,
        color = color,
    )
}

@Composable
internal fun AutomationTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.tboxCaption,
            )
        },
        singleLine = singleLine,
        minLines = minLines,
        textStyle = MaterialTheme.typography.tboxTitle.copy(
            color = MaterialTheme.colorScheme.onSurface,
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            focusedBorderColor = MaterialTheme.colorScheme.outline,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            cursorColor = MaterialTheme.colorScheme.primary,
        ),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier,
    )
}

@Composable
internal fun AutomationDoubleField(
    label: String,
    value: Double,
    onValueChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by remember { mutableStateOf(formatAutomationNumber(value)) }
    LaunchedEffect(value) {
        if (value.isFinite() && draft.replace(',', '.').toDoubleOrNull() != value) {
            draft = formatAutomationNumber(value)
        }
    }
    AutomationTextField(
        value = draft,
        onValueChange = { next ->
            draft = next
            onValueChange(
                next.replace(',', '.').toDoubleOrNull()?.takeIf(Double::isFinite)
                    ?: Double.NaN,
            )
        },
        label = label,
        modifier = modifier,
    )
}

@Composable
internal fun AutomationIntField(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by remember { mutableStateOf(value.toString()) }
    LaunchedEffect(value) {
        if (value != Int.MIN_VALUE && draft.toIntOrNull() != value) {
            draft = value.toString()
        }
    }
    AutomationTextField(
        value = draft,
        onValueChange = { next ->
            draft = next
            onValueChange(next.toIntOrNull() ?: Int.MIN_VALUE)
        },
        label = label,
        modifier = modifier,
    )
}

@Composable
internal fun AutomationSecondsField(
    label: String,
    valueMillis: Long,
    onValueChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val seconds = valueMillis / 1_000.0
    var draft by remember { mutableStateOf(formatAutomationNumber(seconds)) }
    LaunchedEffect(valueMillis) {
        if (valueMillis >= 0L) {
            val parsed = draft.replace(',', '.').toDoubleOrNull()
            if (parsed == null || (parsed * 1_000.0).toLong() != valueMillis) {
                draft = formatAutomationNumber(seconds)
            }
        }
    }
    AutomationTextField(
        value = draft,
        onValueChange = { next ->
            draft = next
            val millis = next.replace(',', '.').toDoubleOrNull()
                ?.takeIf { it.isFinite() && it >= 0.0 }
                ?.let { (it * 1_000.0).toLong() }
                ?: -1L
            onValueChange(millis)
        },
        label = label,
        modifier = modifier,
    )
}

@Composable
internal fun AutomationConditionEditor(
    condition: AutomationCondition,
    triggerIds: List<String>,
    apps: List<LaunchableAppEntry>,
    onChange: (AutomationCondition) -> Unit,
    modifier: Modifier = Modifier,
    depth: Int = 0,
) {
    val kind = conditionKind(condition)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AutomationDropdown(
            label = "Тип условия",
            value = kind,
            options = ConditionUiKind.entries.sortedByAutomationLabel { it.label() },
            optionLabel = ConditionUiKind::label,
            onValueChange = { selected ->
                onChange(defaultCondition(selected, triggerIds))
            },
        )
        when (condition) {
            AutomationCondition.Always -> AutomationBodyText("Всегда истинно")
            is AutomationCondition.Numeric -> NumericConditionFields(condition, onChange)
            is AutomationCondition.State -> StateConditionFields(condition, apps, onChange)
            is AutomationCondition.Time -> TimeConditionFields(condition, onChange)
            is AutomationCondition.Solar -> SolarConditionFields(condition, onChange)
            is AutomationCondition.TriggeredBy -> {
                if (triggerIds.isEmpty()) {
                    AutomationBodyText("Нет триггеров")
                } else {
                    Text(
                        text = "Подходящие ID триггеров",
                        style = MaterialTheme.typography.tboxTitle,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    triggerIds.forEach { triggerId ->
                        key(triggerId) {
                            val selected = triggerId in condition.triggerIds
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = selected,
                                    onCheckedChange = rememberWrappedOnCheckedChange { checked ->
                                        val ids = if (checked) {
                                            condition.triggerIds + triggerId
                                        } else {
                                            condition.triggerIds - triggerId
                                        }
                                        onChange(AutomationCondition.TriggeredBy(ids))
                                    },
                                )
                                AutomationBodyText(triggerId)
                            }
                        }
                    }
                }
            }

            is AutomationCondition.All -> ConditionGroupFields(
                title = "Все условия",
                conditions = condition.conditions,
                triggerIds = triggerIds,
                apps = apps,
                onChange = { onChange(AutomationCondition.All(it)) },
                depth = depth,
            )

            is AutomationCondition.Any -> ConditionGroupFields(
                title = "Любое условие",
                conditions = condition.conditions,
                triggerIds = triggerIds,
                apps = apps,
                onChange = { onChange(AutomationCondition.Any(it)) },
                depth = depth,
            )

            is AutomationCondition.Not -> {
                if (depth < AUTOMATION_MAX_CONDITION_DEPTH) {
                    AutomationConditionEditor(
                        condition = condition.condition,
                        triggerIds = triggerIds,
                        apps = apps,
                        onChange = { onChange(AutomationCondition.Not(it)) },
                        modifier = Modifier.padding(start = 12.dp),
                        depth = depth + 1,
                    )
                }
            }

            is AutomationCondition.Geofence -> GeofenceConditionFields(condition, onChange)
            is AutomationCondition.UiState -> UiStateConditionFields(condition, onChange)
        }
    }
}

@Composable
private fun NumericConditionFields(
    condition: AutomationCondition.Numeric,
    onChange: (AutomationCondition) -> Unit,
) {
    val signals = AutomationSignalCatalog.signalsOfType(AutomationSignalValueType.NUMBER)
    AutomationDropdown(
        label = "Сигнал",
        value = condition.signal,
        options = signals,
        optionLabel = { AutomationSignalCatalog.get(it).label },
        onValueChange = { signal ->
            val sources = AutomationSignalCatalog.get(signal).sources
            onChange(
                condition.copy(
                    signal = signal,
                    source = condition.source.takeIf { it in sources }
                        ?: AutomationSignalCatalog.preferredSource(sources),
                ),
            )
        },
    )
    AutomationSignalValueHint(condition.signal)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AutomationDropdown(
            label = "Источник",
            value = condition.source,
            options = AutomationSignalCatalog.sourcesForUi(condition.signal),
            optionLabel = ::automationSourceLabel,
            onValueChange = { onChange(condition.copy(source = it)) },
            modifier = Modifier.weight(1f),
        )
        AutomationDropdown(
            label = "Сравнение",
            value = condition.comparison,
            options = AutomationComparison.entries,
            optionLabel = ::automationComparisonLabel,
            onValueChange = { onChange(condition.copy(comparison = it)) },
            modifier = Modifier.weight(1f),
        )
        AutomationDoubleField(
            label = "Значение",
            value = condition.expectedValue,
            onValueChange = { onChange(condition.copy(expectedValue = it)) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StateConditionFields(
    condition: AutomationCondition.State,
    apps: List<LaunchableAppEntry>,
    onChange: (AutomationCondition) -> Unit,
) {
    val signals = AutomationSignalCatalog.signalsOfType(AutomationSignalValueType.STATE)
    AutomationDropdown(
        label = "Сигнал",
        value = condition.signal,
        options = signals,
        optionLabel = { AutomationSignalCatalog.get(it).label },
        onValueChange = { signal ->
            val descriptor = AutomationSignalCatalog.get(signal)
            onChange(
                condition.copy(
                    signal = signal,
                    source = condition.source.takeIf { it in descriptor.sources }
                        ?: AutomationSignalCatalog.preferredSource(descriptor.sources),
                    expectedState = automationExpectedStateForSignal(
                        signal,
                        condition.expectedState,
                    ),
                ),
            )
        },
    )
    AutomationSignalValueHint(condition.signal)
    val descriptor = AutomationSignalCatalog.get(condition.signal)
    val isForegroundApp = condition.signal == AutomationSignalId.FOREGROUND_APP
    val isWifiSsid = condition.signal == AutomationSignalId.WIFI_SSID
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AutomationDropdown(
            label = "Источник",
            value = condition.source,
            options = AutomationSignalCatalog.sourcesForUi(descriptor.sources),
            optionLabel = ::automationSourceLabel,
            onValueChange = { onChange(condition.copy(source = it)) },
            modifier = Modifier.weight(1f),
        )
        if (!isForegroundApp && !isWifiSsid) {
            if (descriptor.stateOptions.isNotEmpty()) {
                AutomationDropdown(
                    label = "Состояние",
                    value = condition.expectedState,
                    options = stateOptionsWithCurrent(descriptor.stateOptions, condition.expectedState),
                    optionLabel = ::automationStateLabel,
                    onValueChange = { onChange(condition.copy(expectedState = it)) },
                    modifier = Modifier.weight(1f),
                )
            } else {
                AutomationTextField(
                    value = condition.expectedState,
                    onValueChange = { onChange(condition.copy(expectedState = it)) },
                    label = "Состояние",
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
    if (isForegroundApp) {
        AutomationPackagePicker(
            label = "Приложение",
            packageName = condition.expectedState,
            apps = apps,
            onValueChange = { onChange(condition.copy(expectedState = it)) },
        )
    }
    if (isWifiSsid) {
        AutomationWifiSsidPicker(
            label = "Точка доступа",
            ssid = condition.expectedState,
            includeNone = true,
            onValueChange = { onChange(condition.copy(expectedState = it)) },
        )
    }
}

@Composable
private fun TimeConditionFields(
    condition: AutomationCondition.Time,
    onChange: (AutomationCondition) -> Unit,
) {
    SettingSwitch(
        isChecked = condition.after != null,
        onCheckedChange = { enabled ->
            onChange(
                condition.copy(
                    after = if (enabled) {
                        condition.after ?: AutomationTimeOfDay.DEFAULT
                    } else {
                        null
                    },
                ),
            )
        },
        text = "После",
        description = "",
        enabled = true,
    )
    if (condition.after != null) {
        AutomationTimeOfDayPicker(
            label = "После",
            value = condition.after,
            onValueChange = { onChange(condition.copy(after = it)) },
        )
    }
    SettingSwitch(
        isChecked = condition.before != null,
        onCheckedChange = { enabled ->
            onChange(
                condition.copy(
                    before = if (enabled) {
                        condition.before ?: AutomationTimeOfDay(6, 0)
                    } else {
                        null
                    },
                ),
            )
        },
        text = "До",
        description = "",
        enabled = true,
    )
    if (condition.before != null) {
        AutomationTimeOfDayPicker(
            label = "До",
            value = condition.before,
            onValueChange = { onChange(condition.copy(before = it)) },
        )
    }
    AutomationWeekdayPicker(
        selected = condition.weekdays,
        onChange = { onChange(condition.copy(weekdays = it)) },
        caption = "Ничего не отмечено — любой день. Если «после» позже «до», окно идёт через полночь.",
    )
}

@Composable
private fun SolarConditionFields(
    condition: AutomationCondition.Solar,
    onChange: (AutomationCondition) -> Unit,
) {
    SettingSwitch(
        isChecked = condition.after != null,
        onCheckedChange = { enabled ->
            onChange(
                condition.copy(
                    after = if (enabled) {
                        condition.after ?: AutomationSolarInstant()
                    } else {
                        null
                    },
                ),
            )
        },
        text = "После",
        description = "",
        enabled = true,
    )
    condition.after?.let { after ->
        AutomationSolarInstantFields(
            instant = after,
            onChange = { onChange(condition.copy(after = it)) },
        )
    }
    SettingSwitch(
        isChecked = condition.before != null,
        onCheckedChange = { enabled ->
            onChange(
                condition.copy(
                    before = if (enabled) {
                        condition.before ?: AutomationSolarInstant(
                            event = AutomationSolarEvent.SUNRISE,
                        )
                    } else {
                        null
                    },
                ),
            )
        },
        text = "До",
        description = "",
        enabled = true,
    )
    condition.before?.let { before ->
        AutomationSolarInstantFields(
            instant = before,
            onChange = { onChange(condition.copy(before = it)) },
        )
    }
    AutomationWeekdayPicker(
        selected = condition.weekdays,
        onChange = { onChange(condition.copy(weekdays = it)) },
        caption = "Ничего не отмечено — любой день. Если «после» позже «до», окно идёт через полночь. " +
            "Нет геопозиции или нет восхода/заката — условие ложно.",
    )
}

@Composable
internal fun AutomationSolarInstantFields(
    instant: AutomationSolarInstant,
    onChange: (AutomationSolarInstant) -> Unit,
) {
    val geo by GeoDisplayRepository.state.collectAsStateWithLifecycle()
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AutomationDropdown(
            label = "Событие",
            value = instant.event,
            options = AutomationSolarEvent.entries,
            optionLabel = { event ->
                when (event) {
                    AutomationSolarEvent.SUNRISE -> "Восход"
                    AutomationSolarEvent.SUNSET -> "Закат"
                }
            },
            onValueChange = { onChange(instant.copy(event = it)) },
            modifier = Modifier.weight(1f),
        )
        AutomationDropdown(
            label = "Смещение",
            value = instant.offsetDirection,
            options = AutomationSolarOffsetDirection.entries,
            optionLabel = { direction ->
                when (direction) {
                    AutomationSolarOffsetDirection.BEFORE -> "До"
                    AutomationSolarOffsetDirection.AFTER -> "После"
                }
            },
            onValueChange = { onChange(instant.copy(offsetDirection = it)) },
            modifier = Modifier.weight(1f),
        )
        AutomationIntField(
            label = "Минуты, 0–$AUTOMATION_SOLAR_MAX_OFFSET_MINUTES",
            value = instant.offsetMinutes,
            onValueChange = { raw ->
                if (raw == Int.MIN_VALUE) return@AutomationIntField
                onChange(
                    instant.copy(
                        offsetMinutes = raw.coerceIn(0, AUTOMATION_SOLAR_MAX_OFFSET_MINUTES),
                    ),
                )
            },
            modifier = Modifier.weight(1f),
        )
    }
    val hint = solarInstantHint(instant, geo.latitude, geo.longitude)
    Text(
        text = hint,
        style = MaterialTheme.typography.tboxCaption,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun solarInstantHint(
    instant: AutomationSolarInstant,
    latitude: Double,
    longitude: Double,
): String {
    if (!latitude.isFinite() || !longitude.isFinite() || latitude == 0.0 && longitude == 0.0) {
        return "Сегодняшнее время появится, когда будет геопозиция."
    }
    val wall = AutomationClock.System.wallTime()
    val minutes = AutomationSolarLogic.clockMinutesOnWallDate(
        instant,
        latitude,
        longitude,
        wall,
    ) ?: return "Сегодня нет этого восхода или заката."
    val hour = minutes / 60
    val minute = minutes % 60
    return String.format("Сегодня %02d:%02d по часам ГУ.", hour, minute)
}

@Composable
private fun ConditionGroupFields(
    title: String,
    conditions: List<AutomationCondition>,
    triggerIds: List<String>,
    apps: List<LaunchableAppEntry>,
    onChange: (List<AutomationCondition>) -> Unit,
    depth: Int,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.tboxTitle,
        color = MaterialTheme.colorScheme.onSurface,
    )
    if (depth >= AUTOMATION_MAX_CONDITION_DEPTH) {
        AutomationBodyText("Достигнута максимальная вложенность")
        return
    }
    conditions.forEachIndexed { index, nested ->
        key(index) {
            AutomationConditionEditor(
                condition = nested,
                triggerIds = triggerIds,
                apps = apps,
                onChange = { changed ->
                    onChange(conditions.toMutableList().also { it[index] = changed })
                },
                modifier = Modifier.padding(start = 12.dp),
                depth = depth + 1,
            )
            OutlinedButton(
                onClick = rememberWrappedOnClick {
                    onChange(conditions.filterIndexed { i, _ -> i != index })
                },
            ) {
                AutomationButtonLabel("Удалить условие")
            }
        }
    }
    OutlinedButton(
        onClick = rememberWrappedOnClick { onChange(conditions + defaultNumericCondition()) },
    ) {
        AutomationButtonLabel("Добавить условие")
    }
}

internal fun defaultNumericCondition(): AutomationCondition.Numeric {
    val signal = AutomationSignalId.ENGINE_RPM
    return AutomationCondition.Numeric(
        signal = signal,
        source = AutomationSignalCatalog.preferredSource(signal),
        comparison = AutomationComparison.ABOVE,
        expectedValue = 1_000.0,
    )
}

private enum class ConditionUiKind {
    ALWAYS,
    NUMERIC,
    STATE,
    GEOFENCE,
    UI_STATE,
    TRIGGERED_BY,
    TIME,
    SOLAR,
    ALL,
    ANY,
    NOT;

    fun label(): String = when (this) {
        ALWAYS -> "Всегда"
        NUMERIC -> "Числовое сравнение"
        STATE -> "Состояние"
        GEOFENCE -> "Геозона"
        UI_STATE -> "Состояние приложения"
        TRIGGERED_BY -> "Сработал триггер"
        TIME -> "Время"
        SOLAR -> "Восход / закат"
        ALL -> "И — все"
        ANY -> "ИЛИ — любое"
        NOT -> "НЕ"
    }
}

private fun conditionKind(condition: AutomationCondition): ConditionUiKind = when (condition) {
    AutomationCondition.Always -> ConditionUiKind.ALWAYS
    is AutomationCondition.Numeric -> ConditionUiKind.NUMERIC
    is AutomationCondition.State -> ConditionUiKind.STATE
    is AutomationCondition.Geofence -> ConditionUiKind.GEOFENCE
    is AutomationCondition.UiState -> ConditionUiKind.UI_STATE
    is AutomationCondition.TriggeredBy -> ConditionUiKind.TRIGGERED_BY
    is AutomationCondition.Time -> ConditionUiKind.TIME
    is AutomationCondition.Solar -> ConditionUiKind.SOLAR
    is AutomationCondition.All -> ConditionUiKind.ALL
    is AutomationCondition.Any -> ConditionUiKind.ANY
    is AutomationCondition.Not -> ConditionUiKind.NOT
}

private fun defaultCondition(
    kind: ConditionUiKind,
    triggerIds: List<String>,
): AutomationCondition = when (kind) {
    ConditionUiKind.ALWAYS -> AutomationCondition.Always
    ConditionUiKind.NUMERIC -> defaultNumericCondition()
    ConditionUiKind.STATE -> {
        val signal = AutomationSignalId.GEAR_MODE
        AutomationCondition.State(
            signal = signal,
            source = AutomationSignalCatalog.preferredSource(signal),
            expectedState = "P",
        )
    }

    ConditionUiKind.GEOFENCE -> AutomationCondition.Geofence()
    ConditionUiKind.UI_STATE -> AutomationCondition.UiState(
        state = AutomationUiState.SERVICE_RUNNING,
    )

    ConditionUiKind.TRIGGERED_BY ->
        AutomationCondition.TriggeredBy(triggerIds.firstOrNull()?.let(::setOf).orEmpty())

    ConditionUiKind.TIME -> AutomationCondition.Time(
        after = AutomationTimeOfDay(22, 0),
        before = AutomationTimeOfDay(6, 0),
    )

    ConditionUiKind.SOLAR -> AutomationCondition.Solar(
        after = AutomationSolarInstant(event = AutomationSolarEvent.SUNSET),
        before = AutomationSolarInstant(event = AutomationSolarEvent.SUNRISE),
    )

    ConditionUiKind.ALL -> AutomationCondition.All(listOf(defaultNumericCondition()))
    ConditionUiKind.ANY -> AutomationCondition.Any(listOf(defaultNumericCondition()))
    ConditionUiKind.NOT -> AutomationCondition.Not(defaultNumericCondition())
}

internal fun automationSourceLabel(source: AutomationSignalSource): String = when (source) {
    AutomationSignalSource.TBOX -> "TBox"
    AutomationSignalSource.HEAD_UNIT -> "mbCAN/VHAL"
    AutomationSignalSource.APP -> "Приложение"
}

internal fun automationComparisonLabel(comparison: AutomationComparison): String = when (comparison) {
    AutomationComparison.ABOVE -> ">"
    AutomationComparison.BELOW -> "<"
    AutomationComparison.AT_LEAST -> "≥"
    AutomationComparison.AT_MOST -> "≤"
    AutomationComparison.EQUAL -> "="
    AutomationComparison.NOT_EQUAL -> "≠"
}

internal fun automationStateLabel(value: String): String =
    AutomationSignalCatalog.stateOptionLabel(value)

@Composable
private fun GeofenceConditionFields(
    condition: AutomationCondition.Geofence,
    onChange: (AutomationCondition) -> Unit,
) {
    val parsed = GeoCoordinateParse.parse(condition.queryText)
    AutomationTextField(
        value = condition.queryText,
        onValueChange = { text ->
            val point = GeoCoordinateParse.parse(text)
            onChange(
                condition.copy(
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
                "Распознано: ${formatAutomationGeofenceCoord(parsed.lat)}, " +
                    formatAutomationGeofenceCoord(parsed.lon)
            condition.queryText.isBlank() ->
                "Текущая геопозиция сравнивается с этой точкой и радиусом."
            else -> "Строка не распознана"
        },
        style = MaterialTheme.typography.tboxCaption,
        color = if (parsed != null || condition.queryText.isBlank()) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.error
        },
        modifier = Modifier.fillMaxWidth(),
    )
    AutomationDropdown(
        label = "Положение",
        value = condition.presence,
        options = AutomationGeofencePresence.entries,
        optionLabel = {
            when (it) {
                AutomationGeofencePresence.INSIDE -> "Внутри зоны"
                AutomationGeofencePresence.OUTSIDE -> "Снаружи зоны"
            }
        },
        onValueChange = { onChange(condition.copy(presence = it)) },
    )
    AutomationDoubleField(
        label = "Радиус зоны, м",
        value = condition.zoneRadiusMeters,
        onValueChange = { onChange(condition.copy(zoneRadiusMeters = it)) },
    )
}

@Composable
private fun UiStateConditionFields(
    condition: AutomationCondition.UiState,
    onChange: (AutomationCondition) -> Unit,
) {
    AutomationDropdown(
        label = "Состояние приложения",
        value = condition.state,
        options = AutomationUiState.entries,
        optionLabel = {
            when (it) {
                AutomationUiState.SERVICE_RUNNING -> "Фоновая служба запущена"
                AutomationUiState.MAIN_SCREEN_OPEN -> "Открыт главный экран"
                AutomationUiState.MENU_OPEN -> "Открыто меню"
            }
        },
        onValueChange = { onChange(condition.copy(state = it)) },
    )
}

private fun formatAutomationGeofenceCoord(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

@Composable
internal fun AutomationTimeOfDayPicker(
    label: String,
    value: AutomationTimeOfDay,
    onValueChange: (AutomationTimeOfDay) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hours = (0..23).toList()
    val minutes = (0..59).toList()
    val safe = if (value.isValid()) value else AutomationTimeOfDay.DEFAULT
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        AutomationDropdown(
            label = "$label, ч",
            value = safe.hour,
            options = hours,
            optionLabel = { it.toString().padStart(2, '0') },
            onValueChange = { onValueChange(AutomationTimeOfDay(it, safe.minute)) },
            modifier = Modifier.weight(1f),
        )
        AutomationDropdown(
            label = "мин",
            value = safe.minute,
            options = minutes,
            optionLabel = { it.toString().padStart(2, '0') },
            onValueChange = { onValueChange(AutomationTimeOfDay(safe.hour, it)) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
internal fun AutomationWeekdayPicker(
    selected: Set<AutomationWeekday>,
    onChange: (Set<AutomationWeekday>) -> Unit,
    caption: String,
) {
    Text(
        text = "Дни недели",
        style = MaterialTheme.typography.tboxTitle,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        AutomationWeekday.entries.forEach { day ->
            val checked = day in selected
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Checkbox(
                    checked = checked,
                    onCheckedChange = rememberWrappedOnCheckedChange { enabled ->
                        onChange(if (enabled) selected + day else selected - day)
                    },
                )
                Text(
                    text = automationWeekdayShortLabel(day),
                    style = MaterialTheme.typography.tboxCaption,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
    Text(
        text = caption,
        style = MaterialTheme.typography.tboxCaption,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
internal fun AutomationSignalValueHint(signal: AutomationSignalId) {
    val text = AutomationSignalCatalog.get(signal).valueHint()
    if (text.isBlank()) return
    Text(
        text = text,
        style = MaterialTheme.typography.tboxCaption,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

internal fun stateOptionsWithCurrent(options: List<String>, current: String): List<String> {
    if (current.isBlank()) return options
    return if (options.any { it.equals(current, ignoreCase = true) }) {
        options
    } else {
        listOf(current) + options
    }
}

internal fun automationExpectedStateForSignal(
    signal: AutomationSignalId,
    previous: String,
): String {
    if (signal == AutomationSignalId.FOREGROUND_APP) return ""
    if (signal == AutomationSignalId.WIFI_SSID) return WifiStaSsid.NONE
    return AutomationSignalCatalog.get(signal).stateOptions.firstOrNull() ?: previous
}

@Composable
internal fun AutomationWifiSsidPicker(
    label: String,
    ssid: String,
    includeNone: Boolean,
    onValueChange: (String) -> Unit,
) {
    val context = LocalContext.current
    val saved = remember(context) { WifiStaController.savedSsids(context) }
    val options = buildList {
        if (includeNone) add(WifiStaSsid.NONE)
        add("")
        if (
            ssid.isNotBlank() &&
            !ssid.equals(WifiStaSsid.NONE, ignoreCase = true) &&
            saved.none { it.equals(ssid, ignoreCase = true) }
        ) {
            add(ssid)
        }
        addAll(saved)
    }.distinct()
    AutomationDropdown(
        label = label,
        value = ssid,
        options = options,
        optionLabel = { value ->
            when {
                value.isBlank() -> "Выберите…"
                value.equals(WifiStaSsid.NONE, ignoreCase = true) -> "Нет сети"
                else -> value
            }
        },
        onValueChange = onValueChange,
    )
}

@Composable
internal fun AutomationPackagePicker(
    label: String,
    packageName: String,
    apps: List<LaunchableAppEntry>,
    onValueChange: (String) -> Unit,
) {
    val known = apps
        .filter { it.packageName.isNotBlank() }
        .distinctBy { it.packageName }
        .sortedByAutomationLabel { it.label }
        .map { it.packageName }
    val options = buildList {
        add("")
        if (packageName.isNotBlank() && packageName !in known) add(packageName)
        addAll(known)
    }
    AutomationDropdown(
        label = label,
        value = packageName,
        options = options,
        optionLabel = { pkg ->
            when {
                pkg.isBlank() -> "Выберите…"
                else -> apps.firstOrNull { it.packageName == pkg }?.label
                    ?.let { "$it ($pkg)" } ?: pkg
            }
        },
        onValueChange = onValueChange,
    )
    AutomationTextField(
        value = packageName,
        onValueChange = onValueChange,
        label = "Пакет приложения",
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun formatAutomationNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
