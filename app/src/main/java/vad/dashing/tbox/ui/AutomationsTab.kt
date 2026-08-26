package vad.dashing.tbox.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import vad.dashing.tbox.SettingsViewModel
import vad.dashing.tbox.automation.AutomationDefinition
import vad.dashing.tbox.automation.AutomationExecutionState
import vad.dashing.tbox.automation.AutomationRunMode
import vad.dashing.tbox.automation.AutomationRuntimeStatus
import vad.dashing.tbox.automation.AutomationSystemEvent
import vad.dashing.tbox.automation.AutomationTrigger
import vad.dashing.tbox.automation.AutomationValidator
import vad.dashing.tbox.automation.AutomationViewModel

@Composable
fun AutomationsTab(
    settingsViewModel: SettingsViewModel,
    automationViewModel: AutomationViewModel = viewModel(),
) {
    val snapshot by automationViewModel.storeSnapshot.collectAsStateWithLifecycle()
    val statuses by automationViewModel.runtimeStatuses.collectAsStateWithLifecycle()
    val lastError by automationViewModel.lastError.collectAsStateWithLifecycle()
    val draft by automationViewModel.editorDraft.collectAsStateWithLifecycle()
    val pageCount by settingsViewModel.mainScreenPageCount.collectAsStateWithLifecycle()
    val launcherIconRevision by
        settingsViewModel.launcherAppIconRevision.collectAsStateWithLifecycle()
    val apps = rememberLaunchableAppEntries(settingsViewModel, launcherIconRevision)
    var pendingDelete by remember { mutableStateOf<AutomationDefinition?>(null) }
    var confirmReset by remember { mutableStateOf(false) }

    BackHandler(enabled = draft != null) {
        automationViewModel.closeEditor()
    }

    if (draft == null) {
        if (snapshot.loadError != null) {
            InvalidAutomationConfiguration(
                error = requireNotNull(snapshot.loadError),
                onReset = { confirmReset = true },
            )
        } else {
            AutomationsList(
                automations = snapshot.document.automations,
                statuses = statuses,
                onAdd = { automationViewModel.edit(AutomationDefinition.newDraft()) },
                onEdit = automationViewModel::edit,
                onEnabledChange = automationViewModel::setEnabled,
                onDelete = { pendingDelete = it },
            )
        }
    } else {
        AutomationDefinitionEditor(
            definition = requireNotNull(draft),
            apps = apps,
            pageCount = pageCount,
            onChange = automationViewModel::updateDraft,
            onCancel = automationViewModel::closeEditor,
            onSave = { definition ->
                automationViewModel.save(definition)
            },
        )
    }

    pendingDelete?.let { definition ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Удалить автоматизацию?") },
            text = { Text(definition.name) },
            confirmButton = {
                Button(
                    onClick = {
                        automationViewModel.delete(definition.id)
                        pendingDelete = null
                    },
                ) {
                    Text("Удалить")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Отмена") }
            },
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Сбросить повреждённую конфигурацию?") },
            text = {
                Text("Сохранённый JSON будет удалён. Восстановить его можно только из backup.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        automationViewModel.resetInvalidConfiguration()
                        confirmReset = false
                    },
                ) {
                    Text("Сбросить")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text("Отмена") }
            },
        )
    }

    lastError?.let { error ->
        AlertDialog(
            onDismissRequest = automationViewModel::clearError,
            title = { Text("Автоматизация не сохранена") },
            text = { Text(error) },
            confirmButton = {
                Button(onClick = automationViewModel::clearError) { Text("OK") }
            },
        )
    }
}

@Composable
private fun AutomationsList(
    automations: List<AutomationDefinition>,
    statuses: Map<String, AutomationRuntimeStatus>,
    onAdd: () -> Unit,
    onEdit: (AutomationDefinition) -> Unit,
    onEnabledChange: (String, Boolean) -> Unit,
    onDelete: (AutomationDefinition) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Автоматизации",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = onAdd) {
                Text("Добавить")
            }
        }
        Text(
            text = "Автоматизации выполняются фоновой службой, даже когда этот пункт меню скрыт.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        HorizontalDivider()
        if (automations.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Автоматизаций пока нет", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text("Нажмите «Добавить», чтобы создать первую.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(automations, key = AutomationDefinition::id) { definition ->
                    AutomationListCard(
                        definition = definition,
                        status = statuses[definition.id],
                        onEdit = { onEdit(definition) },
                        onEnabledChange = { onEnabledChange(definition.id, it) },
                        onDelete = { onDelete(definition) },
                    )
                }
            }
        }
    }
}

@Composable
private fun InvalidAutomationConfiguration(
    error: String,
    onReset: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Конфигурация автоматизаций повреждена", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Правила не выполняются, а редактирование заблокировано, чтобы не потерять исходный JSON.",
        )
        Text(error, color = MaterialTheme.colorScheme.error)
        OutlinedButton(onClick = onReset) {
            Text("Сбросить конфигурацию")
        }
    }
}

@Composable
private fun AutomationListCard(
    definition: AutomationDefinition,
    status: AutomationRuntimeStatus?,
    onEdit: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = definition.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = automationSummary(definition),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = runtimeStatusText(status),
                    style = MaterialTheme.typography.bodySmall,
                    color = runtimeStatusColor(status),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Switch(
                checked = definition.enabled,
                onCheckedChange = onEnabledChange,
            )
            OutlinedButton(onClick = onEdit) { Text("Изменить") }
            OutlinedButton(onClick = onDelete) { Text("Удалить") }
        }
    }
}

@Composable
private fun AutomationDefinitionEditor(
    definition: AutomationDefinition,
    apps: List<LaunchableAppEntry>,
    pageCount: Int,
    onChange: (AutomationDefinition) -> Unit,
    onCancel: () -> Unit,
    onSave: (AutomationDefinition) -> Unit,
) {
    val issues = remember(definition) { AutomationValidator.validate(definition) }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (definition.name.isBlank()) {
                        "Новая автоматизация"
                    } else {
                        definition.name
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = onCancel) { Text("Отмена") }
                Button(
                    onClick = { onSave(definition) },
                    enabled = issues.isEmpty(),
                ) {
                    Text("Сохранить")
                }
            }
        }
        item {
            OutlinedTextField(
                value = definition.name,
                onValueChange = { onChange(definition.copy(name = it)) },
                label = { Text("Название") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = definition.description,
                onValueChange = { onChange(definition.copy(description = it)) },
                label = { Text("Описание") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Включена")
                Switch(
                    checked = definition.enabled,
                    onCheckedChange = { onChange(definition.copy(enabled = it)) },
                )
            }
        }
        item {
            Text("Когда", style = MaterialTheme.typography.titleLarge)
            Text(
                "Несколько триггеров объединяются через ИЛИ. При одновременном совпадении используется первый по порядку.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(definition.triggers.size) { index ->
            val trigger = definition.triggers[index]
            AutomationTriggerEditor(
                trigger = trigger,
                index = index,
                canMoveUp = index > 0,
                canMoveDown = index < definition.triggers.lastIndex,
                onChange = { changed ->
                    onChange(
                        definition.copy(
                            triggers = definition.triggers.toMutableList().also {
                                it[index] = changed
                            },
                        ),
                    )
                },
                onDelete = {
                    onChange(
                        definition.copy(
                            triggers = definition.triggers.filterIndexed { i, _ -> i != index },
                        ),
                    )
                },
                onMoveUp = {
                    onChange(definition.copy(triggers = definition.triggers.moved(index, index - 1)))
                },
                onMoveDown = {
                    onChange(definition.copy(triggers = definition.triggers.moved(index, index + 1)))
                },
            )
        }
        item {
            OutlinedButton(
                onClick = {
                    onChange(
                        definition.copy(
                            triggers = definition.triggers + AutomationTrigger.SystemEvent(
                                event = AutomationSystemEvent.BACKGROUND_SERVICE_STARTED,
                            ),
                        ),
                    )
                },
            ) {
                Text("Добавить триггер")
            }
        }
        item {
            HorizontalDivider()
            Text("При условиях", style = MaterialTheme.typography.titleLarge)
            Text(
                "Условия этого раздела объединяются через И.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(definition.conditions.size) { index ->
            val condition = definition.conditions[index]
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AutomationConditionEditor(
                        condition = condition,
                        triggerIds = definition.triggers.map { it.id },
                        onChange = { changed ->
                            onChange(
                                definition.copy(
                                    conditions = definition.conditions.toMutableList().also {
                                        it[index] = changed
                                    },
                                ),
                            )
                        },
                    )
                    OutlinedButton(
                        onClick = {
                            onChange(
                                definition.copy(
                                    conditions = definition.conditions.filterIndexed { i, _ ->
                                        i != index
                                    },
                                ),
                            )
                        },
                    ) {
                        Text("Удалить условие")
                    }
                }
            }
        }
        item {
            OutlinedButton(
                onClick = {
                    onChange(definition.copy(conditions = definition.conditions + defaultNumericCondition()))
                },
            ) {
                Text("Добавить условие")
            }
        }
        item {
            HorizontalDivider()
            Text("Выполнить", style = MaterialTheme.typography.titleLarge)
        }
        item {
            AutomationActionListEditor(
                actions = definition.actions,
                triggerIds = definition.triggers.map { it.id },
                apps = apps,
                pageCount = pageCount,
                onChange = { onChange(definition.copy(actions = it)) },
            )
        }
        item {
            HorizontalDivider()
            Text("Режим выполнения", style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AutomationDropdown(
                    label = "Повторный запуск",
                    value = definition.runMode,
                    options = AutomationRunMode.entries,
                    optionLabel = {
                        when (it) {
                            AutomationRunMode.SINGLE -> "Игнорировать новый"
                            AutomationRunMode.RESTART -> "Перезапустить сценарий"
                            AutomationRunMode.QUEUED -> "Поставить в очередь"
                            AutomationRunMode.PARALLEL -> "Запустить параллельно"
                        }
                    },
                    onValueChange = { mode ->
                        onChange(
                            definition.copy(
                                runMode = mode,
                                maxRuns = if (mode == AutomationRunMode.SINGLE) 1 else {
                                    definition.maxRuns.coerceAtLeast(2)
                                },
                            ),
                        )
                    },
                    modifier = Modifier.weight(2f),
                )
                if (definition.runMode != AutomationRunMode.SINGLE) {
                    AutomationIntField(
                        label = "Максимум запусков",
                        value = definition.maxRuns,
                        onValueChange = { onChange(definition.copy(maxRuns = it)) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        if (issues.isNotEmpty()) {
            item {
                Text(
                    text = issues.joinToString("\n") { "• ${it.message}" },
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

private fun automationSummary(definition: AutomationDefinition): String =
    "${definition.triggers.size} триггер(а) → ${definition.actions.size} действие(я)"

private fun runtimeStatusText(status: AutomationRuntimeStatus?): String = when (status?.state) {
    null,
    AutomationExecutionState.IDLE,
    -> "Ещё не запускалась"

    AutomationExecutionState.RUNNING -> "Выполняется"
    AutomationExecutionState.SUCCESS -> "Последний запуск выполнен"
    AutomationExecutionState.ERROR -> "Ошибка: ${status.lastMessage}"
}

private fun runtimeStatusColor(status: AutomationRuntimeStatus?): Color = when (status?.state) {
    AutomationExecutionState.RUNNING -> Color(0xFF1565C0)
    AutomationExecutionState.SUCCESS -> Color(0xFF2E7D32)
    AutomationExecutionState.ERROR -> Color(0xFFB3261E)
    else -> Color.Unspecified
}

private fun <T> List<T>.moved(from: Int, to: Int): List<T> {
    if (from !in indices || to !in indices || from == to) return this
    return toMutableList().also { list ->
        val item = list.removeAt(from)
        list.add(to, item)
    }
}
