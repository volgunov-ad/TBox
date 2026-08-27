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
import vad.dashing.tbox.ui.theme.tboxTitle
import vad.dashing.tbox.automation.AutomationSignalCatalog
import vad.dashing.tbox.automation.AutomationSignalId
import vad.dashing.tbox.automation.AutomationSignalSource
import vad.dashing.tbox.automation.AutomationSignalValueType
import vad.dashing.tbox.automation.AutomationStartupBehavior
import vad.dashing.tbox.automation.AutomationSystemEvent
import vad.dashing.tbox.automation.AutomationThresholdDirection
import vad.dashing.tbox.automation.AutomationTrigger

@Composable
internal fun AutomationTriggerEditor(
    trigger: AutomationTrigger,
    index: Int,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
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
                options = TriggerUiKind.entries,
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
                is AutomationTrigger.StateEquals -> StateTriggerFields(trigger, onChange)
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
        options = AutomationSystemEvent.entries,
        optionLabel = ::systemEventLabel,
        onValueChange = { onChange(trigger.copy(event = it)) },
    )
}

@Composable
private fun NumericTriggerFields(
    trigger: AutomationTrigger.NumericThreshold,
    onChange: (AutomationTrigger) -> Unit,
) {
    val signals = AutomationSignalCatalog.entries
        .filter { it.id.valueType == AutomationSignalValueType.NUMBER }
        .map { it.id }
    AutomationDropdown(
        label = "Сигнал",
        value = trigger.signal,
        options = signals,
        optionLabel = { AutomationSignalCatalog.get(it).label },
        optionSupportingLabel = { AutomationSignalCatalog.get(it).valueHint() },
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
        AutomationDoubleField(
            label = "Порог повторного взведения",
            value = trigger.resetThreshold ?: trigger.threshold,
            onValueChange = { onChange(trigger.copy(resetThreshold = it)) },
            modifier = Modifier.weight(1f),
        )
        AutomationSecondsField(
            label = "Удерживать, с",
            valueMillis = trigger.holdMillis,
            onValueChange = { onChange(trigger.copy(holdMillis = it)) },
            modifier = Modifier.weight(1f),
        )
    }
    StartupBehaviorField(trigger.startupBehavior) {
        onChange(trigger.copy(startupBehavior = it))
    }
}

@Composable
private fun StateTriggerFields(
    trigger: AutomationTrigger.StateEquals,
    onChange: (AutomationTrigger) -> Unit,
) {
    val signals = AutomationSignalCatalog.entries
        .filter { it.id.valueType == AutomationSignalValueType.STATE }
        .map { it.id }
    AutomationDropdown(
        label = "Сигнал",
        value = trigger.signal,
        options = signals,
        optionLabel = { AutomationSignalCatalog.get(it).label },
        optionSupportingLabel = { AutomationSignalCatalog.get(it).valueHint() },
        onValueChange = { signal ->
            val descriptor = AutomationSignalCatalog.get(signal)
            onChange(
                trigger.copy(
                    signal = signal,
                    source = trigger.source.takeIf { it in descriptor.sources }
                        ?: descriptor.sources.first(),
                    expectedState = descriptor.stateOptions.firstOrNull()
                        ?: trigger.expectedState,
                ),
            )
        },
    )
    AutomationSignalValueHint(trigger.signal)
    val descriptor = AutomationSignalCatalog.get(trigger.signal)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AutomationDropdown(
            label = "Источник",
            value = trigger.source,
            options = descriptor.sources.toList(),
            optionLabel = ::automationSourceLabel,
            onValueChange = { onChange(trigger.copy(source = it)) },
            modifier = Modifier.weight(1f),
        )
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
        AutomationSecondsField(
            label = "Удерживать, с",
            valueMillis = trigger.holdMillis,
            onValueChange = { onChange(trigger.copy(holdMillis = it)) },
            modifier = Modifier.weight(1f),
        )
    }
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
}

private enum class TriggerUiKind {
    SYSTEM_EVENT,
    NUMERIC_THRESHOLD,
    STATE;

    fun label(): String = when (this) {
        SYSTEM_EVENT -> "Событие программы"
        NUMERIC_THRESHOLD -> "Числовой порог"
        STATE -> "Состояние"
    }
}

private fun triggerUiKind(trigger: AutomationTrigger): TriggerUiKind = when (trigger) {
    is AutomationTrigger.SystemEvent -> TriggerUiKind.SYSTEM_EVENT
    is AutomationTrigger.NumericThreshold -> TriggerUiKind.NUMERIC_THRESHOLD
    is AutomationTrigger.StateEquals -> TriggerUiKind.STATE
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
}

private fun AutomationTrigger.withId(id: String): AutomationTrigger = when (this) {
    is AutomationTrigger.SystemEvent -> copy(id = id)
    is AutomationTrigger.NumericThreshold -> copy(id = id)
    is AutomationTrigger.StateEquals -> copy(id = id)
}

internal fun systemEventLabel(event: AutomationSystemEvent): String = when (event) {
    AutomationSystemEvent.BACKGROUND_SERVICE_STARTED -> "Фоновая служба полностью запущена"
    AutomationSystemEvent.MAIN_SCREEN_OPENED -> "Открыт главный экран программы"
    AutomationSystemEvent.MENU_OPENED -> "Открыто меню программы"
}
