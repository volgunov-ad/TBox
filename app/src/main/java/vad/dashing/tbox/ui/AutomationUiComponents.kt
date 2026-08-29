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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import vad.dashing.tbox.ui.theme.tboxBody
import vad.dashing.tbox.ui.theme.tboxButton
import vad.dashing.tbox.ui.theme.tboxCaption
import vad.dashing.tbox.ui.theme.tboxTitle
import vad.dashing.tbox.automation.AutomationComparison
import vad.dashing.tbox.automation.AutomationCondition
import vad.dashing.tbox.automation.AUTOMATION_MAX_CONDITION_DEPTH
import vad.dashing.tbox.automation.AutomationSignalCatalog
import vad.dashing.tbox.automation.AutomationSignalId
import vad.dashing.tbox.automation.AutomationSignalSource
import vad.dashing.tbox.automation.AutomationSignalValueType
import vad.dashing.tbox.automation.sortedByAutomationLabel

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
            is AutomationCondition.State -> StateConditionFields(condition, onChange)
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
                onChange = { onChange(AutomationCondition.All(it)) },
                depth = depth,
            )

            is AutomationCondition.Any -> ConditionGroupFields(
                title = "Любое условие",
                conditions = condition.conditions,
                triggerIds = triggerIds,
                onChange = { onChange(AutomationCondition.Any(it)) },
                depth = depth,
            )

            is AutomationCondition.Not -> {
                if (depth < AUTOMATION_MAX_CONDITION_DEPTH) {
                    AutomationConditionEditor(
                        condition = condition.condition,
                        triggerIds = triggerIds,
                        onChange = { onChange(AutomationCondition.Not(it)) },
                        modifier = Modifier.padding(start = 12.dp),
                        depth = depth + 1,
                    )
                }
            }
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
            val sources = AutomationSignalCatalog.get(signal).sources.toList()
            onChange(
                condition.copy(
                    signal = signal,
                    source = condition.source.takeIf { it in sources } ?: sources.first(),
                ),
            )
        },
    )
    AutomationSignalValueHint(condition.signal)
    val sources = AutomationSignalCatalog.get(condition.signal).sources.toList()
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AutomationDropdown(
            label = "Источник",
            value = condition.source,
            options = sources,
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
                        ?: descriptor.sources.first(),
                    expectedState = descriptor.stateOptions.firstOrNull()
                        ?: condition.expectedState,
                ),
            )
        },
    )
    AutomationSignalValueHint(condition.signal)
    val descriptor = AutomationSignalCatalog.get(condition.signal)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AutomationDropdown(
            label = "Источник",
            value = condition.source,
            options = descriptor.sources.toList(),
            optionLabel = ::automationSourceLabel,
            onValueChange = { onChange(condition.copy(source = it)) },
            modifier = Modifier.weight(1f),
        )
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

@Composable
private fun ConditionGroupFields(
    title: String,
    conditions: List<AutomationCondition>,
    triggerIds: List<String>,
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

internal fun defaultNumericCondition(): AutomationCondition.Numeric =
    AutomationCondition.Numeric(
        signal = AutomationSignalId.ENGINE_RPM,
        source = AutomationSignalSource.TBOX,
        comparison = AutomationComparison.ABOVE,
        expectedValue = 1_000.0,
    )

private enum class ConditionUiKind {
    ALWAYS,
    NUMERIC,
    STATE,
    TRIGGERED_BY,
    ALL,
    ANY,
    NOT;

    fun label(): String = when (this) {
        ALWAYS -> "Всегда"
        NUMERIC -> "Числовое сравнение"
        STATE -> "Состояние"
        TRIGGERED_BY -> "Сработал триггер"
        ALL -> "И — все"
        ANY -> "ИЛИ — любое"
        NOT -> "НЕ"
    }
}

private fun conditionKind(condition: AutomationCondition): ConditionUiKind = when (condition) {
    AutomationCondition.Always -> ConditionUiKind.ALWAYS
    is AutomationCondition.Numeric -> ConditionUiKind.NUMERIC
    is AutomationCondition.State -> ConditionUiKind.STATE
    is AutomationCondition.TriggeredBy -> ConditionUiKind.TRIGGERED_BY
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
    ConditionUiKind.STATE -> AutomationCondition.State(
        signal = AutomationSignalId.GEAR_MODE,
        source = AutomationSignalSource.TBOX,
        expectedState = "P",
    )

    ConditionUiKind.TRIGGERED_BY ->
        AutomationCondition.TriggeredBy(triggerIds.firstOrNull()?.let(::setOf).orEmpty())

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
