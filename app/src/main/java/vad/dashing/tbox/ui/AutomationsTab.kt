package vad.dashing.tbox.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vad.dashing.tbox.BackgroundService
import vad.dashing.tbox.R
import vad.dashing.tbox.SettingsViewModel
import vad.dashing.tbox.ui.theme.tboxBody
import vad.dashing.tbox.ui.theme.tboxCaption
import vad.dashing.tbox.ui.theme.tboxHeadline
import vad.dashing.tbox.ui.theme.tboxTitle
import vad.dashing.tbox.automation.AutomationDefinition
import vad.dashing.tbox.automation.AutomationExecutionState
import vad.dashing.tbox.automation.AutomationRunMode
import vad.dashing.tbox.automation.AutomationRuntimeStatus
import vad.dashing.tbox.automation.AutomationSystemEvent
import vad.dashing.tbox.automation.AutomationTrigger
import vad.dashing.tbox.automation.AutomationValidator
import vad.dashing.tbox.automation.AutomationViewModel
import vad.dashing.tbox.automation.nextAutomationTriggerId

@Composable
fun AutomationsTab(
    settingsViewModel: SettingsViewModel,
    onServiceCommand: (String, String, String) -> Unit,
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
    var pendingExport by remember { mutableStateOf<AutomationDefinition?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        input.bufferedReader().readText()
                    }.orEmpty()
                }.getOrElse { "" }
            }
            if (text.isBlank()) {
                Toast.makeText(
                    context,
                    context.getString(R.string.toast_automations_import_read_error),
                    Toast.LENGTH_LONG,
                ).show()
                return@launch
            }
            val result = automationViewModel.importFromJson(text)
            if (result.isSuccess) {
                Toast.makeText(
                    context,
                    context.getString(
                        R.string.toast_automations_imported,
                        result.getOrNull() ?: 0,
                    ),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

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
                onImport = {
                    importLauncher.launch(
                        arrayOf("application/json", "text/plain", "application/*", "*/*"),
                    )
                },
                onEdit = automationViewModel::edit,
                onDuplicate = automationViewModel::duplicate,
                onEnabledChange = automationViewModel::setEnabled,
                onDelete = { pendingDelete = it },
                onExport = { pendingExport = it },
                onRunNow = { definition ->
                    onServiceCommand(
                        BackgroundService.ACTION_AUTOMATION_RUN_NOW,
                        BackgroundService.EXTRA_AUTOMATION_ID,
                        definition.id,
                    )
                },
            )
        }
    } else {
        AutomationDefinitionEditor(
            definition = requireNotNull(draft),
            apps = apps,
            pageCount = pageCount,
            isSaved = snapshot.document.automations.any { it.id == draft?.id },
            onChange = automationViewModel::updateDraft,
            onCancel = automationViewModel::closeEditor,
            onSave = { definition ->
                automationViewModel.save(definition)
            },
            onRunNow = { definition ->
                onServiceCommand(
                    BackgroundService.ACTION_AUTOMATION_RUN_NOW,
                    BackgroundService.EXTRA_AUTOMATION_ID,
                    definition.id,
                )
            },
        )
    }

    pendingExport?.let { definition ->
        AlertDialog(
            onDismissRequest = { pendingExport = null },
            title = { AppAlertDialogTitle(stringResource(R.string.dialog_file_saving_title)) },
            text = { AppAlertDialogText(stringResource(R.string.dialog_save_automation_json_downloads)) },
            confirmButton = {
                Button(
                    onClick = rememberWrappedOnClick {
                        val toExport = definition
                        pendingExport = null
                        scope.launch {
                            val result = automationViewModel.exportToDownloads(toExport)
                            if (result.isSuccess) {
                                Toast.makeText(
                                    context,
                                    context.getString(
                                        R.string.toast_saved_to,
                                        result.getOrNull().orEmpty(),
                                    ),
                                    Toast.LENGTH_LONG,
                                ).show()
                            } else {
                                Toast.makeText(
                                    context,
                                    context.getString(
                                        R.string.toast_save_error,
                                        result.exceptionOrNull()?.message.orEmpty(),
                                    ),
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                        }
                    },
                ) {
                    AppAlertDialogButtonLabel(stringResource(R.string.automations_export_json))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = rememberWrappedOnClick { pendingExport = null }) {
                    AppAlertDialogButtonLabel("Отмена")
                }
            },
        )
    }

    pendingDelete?.let { definition ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { AppAlertDialogTitle("Удалить автоматизацию?") },
            text = { AppAlertDialogText(definition.name) },
            confirmButton = {
                Button(
                    onClick = rememberWrappedOnClick {
                        automationViewModel.delete(definition.id)
                        pendingDelete = null
                    },
                ) {
                    AppAlertDialogButtonLabel("Удалить")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = rememberWrappedOnClick { pendingDelete = null }) {
                    AppAlertDialogButtonLabel("Отмена")
                }
            },
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { AppAlertDialogTitle("Сбросить повреждённую конфигурацию?") },
            text = {
                AppAlertDialogText(
                    "Сохранённый JSON будет удалён. Восстановить его можно только из backup.",
                )
            },
            confirmButton = {
                Button(
                    onClick = rememberWrappedOnClick {
                        automationViewModel.resetInvalidConfiguration()
                        confirmReset = false
                    },
                ) {
                    AppAlertDialogButtonLabel("Сбросить")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = rememberWrappedOnClick { confirmReset = false }) {
                    AppAlertDialogButtonLabel("Отмена")
                }
            },
        )
    }

    lastError?.let { error ->
        AlertDialog(
            onDismissRequest = automationViewModel::clearError,
            title = { AppAlertDialogTitle(stringResource(R.string.automations_error_title)) },
            text = { AppAlertDialogText(error) },
            confirmButton = {
                Button(onClick = rememberWrappedOnClick(automationViewModel::clearError)) {
                    AppAlertDialogButtonLabel("OK")
                }
            },
        )
    }
}

@Composable
private fun AutomationsList(
    automations: List<AutomationDefinition>,
    statuses: Map<String, AutomationRuntimeStatus>,
    onAdd: () -> Unit,
    onImport: () -> Unit,
    onEdit: (AutomationDefinition) -> Unit,
    onDuplicate: (AutomationDefinition) -> Unit,
    onEnabledChange: (String, Boolean) -> Unit,
    onDelete: (AutomationDefinition) -> Unit,
    onExport: (AutomationDefinition) -> Unit,
    onRunNow: (AutomationDefinition) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Автоматизации",
                style = MaterialTheme.typography.tboxHeadline,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = rememberWrappedOnClick(onImport)) {
                AutomationButtonLabel(stringResource(R.string.automations_import))
            }
            Button(onClick = rememberWrappedOnClick(onAdd)) {
                AutomationButtonLabel("Добавить")
            }
        }
        Text(
            text = "Автоматизации выполняются фоновой службой, даже когда этот пункт меню скрыт. " +
                AUTOMATION_LIMITS_HINT,
            style = MaterialTheme.typography.tboxBody,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        if (automations.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Автоматизаций пока нет",
                    style = MaterialTheme.typography.tboxTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Нажмите «Добавить» или «Импорт».",
                    style = MaterialTheme.typography.tboxBody,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
                        onDuplicate = { onDuplicate(definition) },
                        onEnabledChange = { onEnabledChange(definition.id, it) },
                        onDelete = { onDelete(definition) },
                        onExport = { onExport(definition) },
                        onRunNow = { onRunNow(definition) },
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
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Конфигурация автоматизаций повреждена",
            style = MaterialTheme.typography.tboxHeadline,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Правила не выполняются, а редактирование заблокировано, чтобы не потерять исходный JSON.",
            style = MaterialTheme.typography.tboxBody,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = error,
            style = MaterialTheme.typography.tboxBody,
            color = MaterialTheme.colorScheme.error,
        )
        OutlinedButton(onClick = rememberWrappedOnClick(onReset)) {
            AutomationButtonLabel("Сбросить конфигурацию")
        }
    }
}

@Composable
private fun AutomationListCard(
    definition: AutomationDefinition,
    status: AutomationRuntimeStatus?,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit,
    onRunNow: () -> Unit,
) {
    val issues = remember(definition) { AutomationValidator.validate(definition) }
    AutomationCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = definition.name,
                    style = MaterialTheme.typography.tboxTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = automationSummary(definition),
                    style = MaterialTheme.typography.tboxCaption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = runtimeStatusText(status),
                    style = MaterialTheme.typography.tboxCaption,
                    color = runtimeStatusColor(status),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (issues.isNotEmpty()) {
                    Text(
                        text = stringResource(
                            R.string.automations_invalid_rule_hint,
                            issues.first().message,
                        ),
                        style = MaterialTheme.typography.tboxCaption,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        SettingSwitch(
            isChecked = definition.enabled,
            onCheckedChange = onEnabledChange,
            text = "Включена",
            description = "",
            enabled = true,
        )
        Button(
            onClick = rememberWrappedOnClick(onRunNow),
            modifier = Modifier.fillMaxWidth(),
            enabled = issues.isEmpty(),
        ) {
            AutomationButtonLabel("Выполнить сейчас")
        }
        OutlinedButton(
            onClick = rememberWrappedOnClick(onExport),
            modifier = Modifier.fillMaxWidth(),
        ) {
            AutomationButtonLabel(stringResource(R.string.automations_export_json))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = rememberWrappedOnClick(onEdit),
                modifier = Modifier.weight(1f),
            ) {
                AutomationButtonLabel("Изменить")
            }
            OutlinedButton(
                onClick = rememberWrappedOnClick(onDuplicate),
                modifier = Modifier.weight(1f),
            ) {
                AutomationButtonLabel("Дублировать")
            }
            OutlinedButton(
                onClick = rememberWrappedOnClick(onDelete),
                modifier = Modifier.weight(1f),
            ) {
                AutomationButtonLabel("Удалить")
            }
        }
    }
}

@Composable
private fun AutomationDefinitionEditor(
    definition: AutomationDefinition,
    apps: List<LaunchableAppEntry>,
    pageCount: Int,
    isSaved: Boolean,
    onChange: (AutomationDefinition) -> Unit,
    onCancel: () -> Unit,
    onSave: (AutomationDefinition) -> Unit,
    onRunNow: (AutomationDefinition) -> Unit,
) {
    val issues = remember(definition) { AutomationValidator.validate(definition) }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (definition.name.isBlank()) {
                        "Новая автоматизация"
                    } else {
                        definition.name
                    },
                    style = MaterialTheme.typography.tboxHeadline,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                OutlinedButton(onClick = rememberWrappedOnClick(onCancel)) {
                    AutomationButtonLabel("Отмена")
                }
                OutlinedButton(
                    onClick = rememberWrappedOnClick { onRunNow(definition) },
                    enabled = isSaved && issues.isEmpty(),
                ) {
                    AutomationButtonLabel("Выполнить сейчас")
                }
                Button(
                    onClick = rememberWrappedOnClick { onSave(definition) },
                    enabled = issues.isEmpty(),
                ) {
                    AutomationButtonLabel("Сохранить")
                }
            }
        }
        item {
            Text(
                text = AUTOMATION_LIMITS_HINT,
                style = MaterialTheme.typography.tboxCaption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AutomationTextField(
                    value = definition.name,
                    onValueChange = { onChange(definition.copy(name = it)) },
                    label = "Название",
                    modifier = Modifier.fillMaxWidth(),
                )
                AutomationTextField(
                    value = definition.description,
                    onValueChange = { onChange(definition.copy(description = it)) },
                    label = "Описание",
                    singleLine = false,
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                SettingsTitle("Режим выполнения")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
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
                SettingSwitch(
                    isChecked = definition.enabled,
                    onCheckedChange = { onChange(definition.copy(enabled = it)) },
                    text = "Включена",
                    description = "",
                    enabled = true,
                )
            }
        }
        item {
            SettingsTitle("Когда")
            Text(
                text = "Несколько триггеров объединяются через ИЛИ. При одновременном совпадении используется первый по порядку.",
                style = MaterialTheme.typography.tboxCaption,
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
                apps = apps,
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
                onClick = rememberWrappedOnClick {
                    onChange(
                        definition.copy(
                            triggers = definition.triggers + AutomationTrigger.SystemEvent(
                                id = nextAutomationTriggerId(definition.triggers.map { it.id }),
                                event = AutomationSystemEvent.BACKGROUND_SERVICE_STARTED,
                            ),
                        ),
                    )
                },
            ) {
                AutomationButtonLabel("Добавить триггер")
            }
        }
        item {
            SettingsTitle("При условиях")
            Text(
                text = "Условия этого раздела объединяются через И. " +
                    "Одно время ожидания на всю группу: 0 — сразу пропустить, если не выполнено.",
                style = MaterialTheme.typography.tboxCaption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AutomationSecondsField(
                label = "Ждать условие, с",
                valueMillis = definition.conditionWaitMillis,
                onValueChange = { onChange(definition.copy(conditionWaitMillis = it)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        items(definition.conditions.size) { index ->
            val condition = definition.conditions[index]
            AutomationCard {
                AutomationConditionEditor(
                    condition = condition,
                    triggerIds = definition.triggers.map { it.id },
                    apps = apps,
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
                    onClick = rememberWrappedOnClick {
                        onChange(
                            definition.copy(
                                conditions = definition.conditions.filterIndexed { i, _ ->
                                    i != index
                                },
                            ),
                        )
                    },
                ) {
                    AutomationButtonLabel("Удалить условие")
                }
            }
        }
        item {
            OutlinedButton(
                onClick = rememberWrappedOnClick {
                    onChange(definition.copy(conditions = definition.conditions + defaultNumericCondition()))
                },
            ) {
                AutomationButtonLabel("Добавить условие")
            }
        }
        item {
            SettingsTitle("Выполнить")
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
        if (issues.isNotEmpty()) {
            item {
                Text(
                    text = issues.joinToString("\n") { "• ${it.message}" },
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.tboxCaption,
                )
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

private fun automationSummary(definition: AutomationDefinition): String =
    "${definition.triggers.size} триггер(а) → ${definition.actions.size} действие(я)"

private fun runtimeStatusText(status: AutomationRuntimeStatus?): String {
    val time = formatAutomationRunTime(status)
    return when (status?.state) {
        null,
        AutomationExecutionState.IDLE,
        -> "Ещё не запускалась"

        AutomationExecutionState.RUNNING ->
            if (time != null) "Выполняется с $time" else "Выполняется"

        AutomationExecutionState.SUCCESS ->
            if (time != null) "Последний запуск выполнен $time" else "Последний запуск выполнен"

        AutomationExecutionState.ERROR -> {
            val prefix = if (time != null) "Ошибка $time" else "Ошибка"
            val message = status.lastMessage
            if (message.isBlank()) prefix else "$prefix: $message"
        }
    }
}

private const val AUTOMATION_LIMITS_HINT =
    "Одно правило не стартует чаще чем раз в 2 с. После 5 ошибок подряд оно выключается до ручного включения. " +
        "«Выполнить сейчас» сразу запускает сохранённые действия, без триггера и общих условий."

private fun formatAutomationRunTime(status: AutomationRuntimeStatus?): String? {
    if (status == null) return null
    val epochMillis = when (status.state) {
        AutomationExecutionState.RUNNING -> status.lastStartedAtEpochMillis
        AutomationExecutionState.SUCCESS,
        AutomationExecutionState.ERROR,
        -> status.lastFinishedAtEpochMillis ?: status.lastStartedAtEpochMillis
        else -> null
    }
    if (epochMillis == null || epochMillis <= 0L) return null
    return SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault()).format(Date(epochMillis))
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
