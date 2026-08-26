package vad.dashing.tbox.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import vad.dashing.tbox.automation.AutomationComparison
import vad.dashing.tbox.automation.AutomationCondition
import vad.dashing.tbox.automation.AutomationSignalCatalog
import vad.dashing.tbox.automation.AutomationSignalId
import vad.dashing.tbox.automation.AutomationSignalSource
import vad.dashing.tbox.automation.AutomationSignalValueType

@Composable
internal fun <T> AutomationDropdown(
    label: String,
    value: T,
    options: List<T>,
    optionLabel: (T) -> String,
    onValueChange: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = { expanded = true },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = optionLabel(value),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        expanded = false
                        onValueChange(option)
                    },
                )
            }
        }
    }
}

@Composable
internal fun AutomationDoubleField(
    label: String,
    value: Double,
    onValueChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by remember(value) { mutableStateOf(formatAutomationNumber(value)) }
    OutlinedTextField(
        value = draft,
        onValueChange = { next ->
            draft = next
            next.replace(',', '.').toDoubleOrNull()?.takeIf(Double::isFinite)?.let(onValueChange)
        },
        label = { Text(label) },
        singleLine = true,
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
    var draft by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = draft,
        onValueChange = { next ->
            draft = next
            next.toIntOrNull()?.let(onValueChange)
        },
        label = { Text(label) },
        singleLine = true,
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
    var draft by remember(valueMillis) { mutableStateOf(formatAutomationNumber(seconds)) }
    OutlinedTextField(
        value = draft,
        onValueChange = { next ->
            draft = next
            next.replace(',', '.').toDoubleOrNull()
                ?.takeIf { it.isFinite() && it >= 0.0 }
                ?.let { onValueChange((it * 1_000.0).toLong()) }
        },
        label = { Text(label) },
        singleLine = true,
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
            options = ConditionUiKind.entries,
            optionLabel = ConditionUiKind::label,
            onValueChange = { selected ->
                onChange(defaultCondition(selected, triggerIds))
            },
        )
        when (condition) {
            AutomationCondition.Always -> Text("Всегда истинно")
            is AutomationCondition.Numeric -> NumericConditionFields(condition, onChange)
            is AutomationCondition.State -> StateConditionFields(condition, onChange)
            is AutomationCondition.TriggeredBy -> {
                val options = triggerIds.ifEmpty { listOf("") }
                val selected = condition.triggerIds.firstOrNull()?.takeIf { it in options }
                    ?: options.first()
                AutomationDropdown(
                    label = "ID триггера",
                    value = selected,
                    options = options,
                    optionLabel = { it.ifBlank { "Нет триггеров" } },
                    onValueChange = {
                        onChange(AutomationCondition.TriggeredBy(setOf(it)))
                    },
                    enabled = triggerIds.isNotEmpty(),
                )
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
                if (depth < 6) {
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
    val signals = AutomationSignalCatalog.entries
        .filter { it.id.valueType == AutomationSignalValueType.NUMBER }
        .map { it.id }
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
    val signals = AutomationSignalCatalog.entries
        .filter { it.id.valueType == AutomationSignalValueType.STATE }
        .map { it.id }
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
            val selected = condition.expectedState.takeIf { it in descriptor.stateOptions }
                ?: descriptor.stateOptions.first()
            AutomationDropdown(
                label = "Состояние",
                value = selected,
                options = descriptor.stateOptions,
                optionLabel = ::automationStateLabel,
                onValueChange = { onChange(condition.copy(expectedState = it)) },
                modifier = Modifier.weight(1f),
            )
        } else {
            OutlinedTextField(
                value = condition.expectedState,
                onValueChange = { onChange(condition.copy(expectedState = it)) },
                label = { Text("Состояние") },
                singleLine = true,
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
    Text(title, style = MaterialTheme.typography.titleSmall)
    if (depth >= 6) {
        Text("Достигнута максимальная вложенность")
        return
    }
    conditions.forEachIndexed { index, nested ->
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
            onClick = { onChange(conditions.filterIndexed { i, _ -> i != index }) },
        ) {
            Text("Удалить условие")
        }
    }
    OutlinedButton(
        onClick = { onChange(conditions + defaultNumericCondition()) },
    ) {
        Text("Добавить условие")
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
        AutomationCondition.TriggeredBy(setOf(triggerIds.firstOrNull().orEmpty()))

    ConditionUiKind.ALL -> AutomationCondition.All(listOf(defaultNumericCondition()))
    ConditionUiKind.ANY -> AutomationCondition.Any(listOf(defaultNumericCondition()))
    ConditionUiKind.NOT -> AutomationCondition.Not(defaultNumericCondition())
}

internal fun automationSourceLabel(source: AutomationSignalSource): String = when (source) {
    AutomationSignalSource.TBOX -> "TBox"
    AutomationSignalSource.HEAD_UNIT -> "mbCAN/VHAL"
}

internal fun automationComparisonLabel(comparison: AutomationComparison): String = when (comparison) {
    AutomationComparison.ABOVE -> ">"
    AutomationComparison.BELOW -> "<"
    AutomationComparison.AT_LEAST -> "≥"
    AutomationComparison.AT_MOST -> "≤"
    AutomationComparison.EQUAL -> "="
    AutomationComparison.NOT_EQUAL -> "≠"
}

internal fun automationStateLabel(value: String): String = when (value.lowercase()) {
    "on" -> "Включено"
    "off" -> "Выключено"
    else -> value
}

private fun formatAutomationNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
