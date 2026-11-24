package vad.dashing.tbox.ui

import vad.dashing.tbox.ruEn

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import vad.dashing.tbox.FloatingDashboardConfig
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
    val floatingPanels by settingsViewModel.floatingDashboards.collectAsStateWithLifecycle()
    val launcherIconRevision by
        settingsViewModel.launcherAppIconRevision.collectAsStateWithLifecycle()
    val apps = rememberLaunchableAppEntries(settingsViewModel, launcherIconRevision)
    var pendingDelete by remember { mutableStateOf<AutomationDefinition?>(null) }
    var pendingExport by remember { mutableStateOf<AutomationDefinition?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val importReadErrorToast = stringResource(R.string.toast_automations_import_read_error)
    val importNoPickerToast = stringResource(R.string.toast_automations_import_no_picker)
    val importedTemplate = stringResource(R.string.toast_automations_imported)
    val savedToTemplate = stringResource(R.string.toast_saved_to)
    val saveErrorTemplate = stringResource(R.string.toast_save_error)
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
                    importReadErrorToast,
                    Toast.LENGTH_LONG,
                ).show()
                return@launch
            }
            val result = automationViewModel.importFromJson(text)
            if (result.isSuccess) {
                Toast.makeText(
                    context,
                    importedTemplate.format(result.getOrNull() ?: 0),
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
                    val openDocumentIntent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "*/*"
                    }
                    if (openDocumentIntent.resolveActivity(context.packageManager) == null) {
                        Toast.makeText(context, importNoPickerToast, Toast.LENGTH_LONG).show()
                        return@AutomationsList
                    }
                    runCatching {
                        importLauncher.launch(
                            arrayOf("application/json", "text/plain", "application/*", "*/*"),
                        )
                    }.onFailure {
                        Toast.makeText(context, importNoPickerToast, Toast.LENGTH_LONG).show()
                    }
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
            floatingPanels = floatingPanels,
            peerAutomations = peerAutomationsForEditor(snapshot.document.automations, draft),
            pageCount = pageCount,
            settingsViewModel = settingsViewModel,
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
                                    savedToTemplate.format(result.getOrNull().orEmpty()),
                                    Toast.LENGTH_LONG,
                                ).show()
                            } else {
                                Toast.makeText(
                                    context,
                                    saveErrorTemplate.format(
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
                    AppAlertDialogButtonLabel(ruEn("Отмена", "Cancel"))
                }
            },
        )
    }

    pendingDelete?.let { definition ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { AppAlertDialogTitle(ruEn("Удалить автоматизацию?", "Delete automation?")) },
            text = { AppAlertDialogText(definition.name) },
            confirmButton = {
                Button(
                    onClick = rememberWrappedOnClick {
                        automationViewModel.delete(definition.id)
                        pendingDelete = null
                    },
                ) {
                    AppAlertDialogButtonLabel(ruEn("Удалить", "Delete"))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = rememberWrappedOnClick { pendingDelete = null }) {
                    AppAlertDialogButtonLabel(ruEn("Отмена", "Cancel"))
                }
            },
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { AppAlertDialogTitle(ruEn("Сбросить повреждённую конфигурацию?", "Reset corrupted configuration?")) },
            text = {
                AppAlertDialogText(
                    ruEn("Сохранённый JSON будет удалён. Восстановить его можно только из backup.", "The saved JSON will be deleted. It can be restored only from a backup."),
                )
            },
            confirmButton = {
                Button(
                    onClick = rememberWrappedOnClick {
                        automationViewModel.resetInvalidConfiguration()
                        confirmReset = false
                    },
                ) {
                    AppAlertDialogButtonLabel(ruEn("Сбросить", "Reset"))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = rememberWrappedOnClick { confirmReset = false }) {
                    AppAlertDialogButtonLabel(ruEn("Отмена", "Cancel"))
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
                text = ruEn("Автоматизации", "Automations"),
                style = MaterialTheme.typography.tboxHeadline,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = rememberWrappedOnClick(onImport)) {
                AutomationButtonLabel(stringResource(R.string.automations_import))
            }
            Button(onClick = rememberWrappedOnClick(onAdd)) {
                AutomationButtonLabel(ruEn("Добавить", "Add"))
            }
        }
        Text(
            text = ruEn("Автоматизации выполняются фоновой службой, даже когда этот пункт меню скрыт. ", "Automations run in the background service, even when this menu item is hidden. ") +
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
                    text = ruEn("Автоматизаций пока нет", "No automations yet"),
                    style = MaterialTheme.typography.tboxTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = ruEn("Нажмите «Добавить» или «Импорт».", "Tap “Add” or “Import”."),
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
            text = ruEn("Конфигурация автоматизаций повреждена", "Automation configuration is damaged"),
            style = MaterialTheme.typography.tboxHeadline,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = ruEn("Правила не выполняются, а редактирование заблокировано, чтобы не потерять исходный JSON.", "Rules do not run, and editing is locked so the original JSON is not lost."),
            style = MaterialTheme.typography.tboxBody,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = error,
            style = MaterialTheme.typography.tboxBody,
            color = MaterialTheme.colorScheme.error,
        )
        OutlinedButton(onClick = rememberWrappedOnClick(onReset)) {
            AutomationButtonLabel(ruEn("Сбросить конфигурацию", "Reset configuration"))
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
            text = ruEn("Включена", "On"),
            description = "",
            enabled = true,
        )
        Button(
            onClick = rememberWrappedOnClick(onRunNow),
            modifier = Modifier.fillMaxWidth(),
            enabled = issues.isEmpty(),
        ) {
            AutomationButtonLabel(ruEn("Выполнить сейчас", "Run now"))
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
                AutomationButtonLabel(ruEn("Изменить", "Edit"))
            }
            OutlinedButton(
                onClick = rememberWrappedOnClick(onDuplicate),
                modifier = Modifier.weight(1f),
            ) {
                AutomationButtonLabel(ruEn("Дублировать", "Duplicate"))
            }
            OutlinedButton(
                onClick = rememberWrappedOnClick(onDelete),
                modifier = Modifier.weight(1f),
            ) {
                AutomationButtonLabel(ruEn("Удалить", "Delete"))
            }
        }
    }
}

@Composable
private fun AutomationDefinitionEditor(
    definition: AutomationDefinition,
    apps: List<LaunchableAppEntry>,
    floatingPanels: List<FloatingDashboardConfig>,
    peerAutomations: List<AutomationDefinition>,
    pageCount: Int,
    settingsViewModel: SettingsViewModel,
    isSaved: Boolean,
    onChange: (AutomationDefinition) -> Unit,
    onCancel: () -> Unit,
    onSave: (AutomationDefinition) -> Unit,
    onRunNow: (AutomationDefinition) -> Unit,
) {
    val issues = remember(definition) { AutomationValidator.validate(definition) }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 18.dp)
                .padding(top = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (definition.name.isBlank()) {
                    ruEn("Новая автоматизация", "New automation")
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
                AutomationButtonLabel(ruEn("Отмена", "Cancel"))
            }
            OutlinedButton(
                onClick = rememberWrappedOnClick { onRunNow(definition) },
                enabled = isSaved && issues.isEmpty(),
            ) {
                AutomationButtonLabel(ruEn("Выполнить сейчас", "Run now"))
            }
            Button(
                onClick = rememberWrappedOnClick { onSave(definition) },
                enabled = issues.isEmpty(),
            ) {
                AutomationButtonLabel(ruEn("Сохранить", "Save"))
            }
        }
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
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
                    label = ruEn("Название", "Name"),
                    modifier = Modifier.fillMaxWidth(),
                )
                AutomationTextField(
                    value = definition.description,
                    onValueChange = { onChange(definition.copy(description = it)) },
                    label = ruEn("Описание", "Description"),
                    singleLine = false,
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                SettingsTitle(ruEn("Режим выполнения", "Run mode"))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AutomationDropdown(
                        label = ruEn("Повторный запуск", "Re-trigger"),
                        value = definition.runMode,
                        options = AutomationRunMode.entries,
                        optionLabel = {
                            when (it) {
                                AutomationRunMode.SINGLE -> ruEn("Игнорировать новый", "Ignore new")
                                AutomationRunMode.RESTART -> ruEn("Перезапустить сценарий", "Restart")
                                AutomationRunMode.QUEUED -> ruEn("Поставить в очередь", "Queue")
                                AutomationRunMode.PARALLEL -> ruEn("Запустить параллельно", "Run in parallel")
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
                            label = ruEn("Максимум запусков", "Maximum runs"),
                            value = definition.maxRuns,
                            onValueChange = { onChange(definition.copy(maxRuns = it)) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                SettingSwitch(
                    isChecked = definition.enabled,
                    onCheckedChange = { onChange(definition.copy(enabled = it)) },
                    text = ruEn("Включена", "On"),
                    description = "",
                    enabled = true,
                )
            }
        }
        item {
            SettingsTitle(ruEn("Когда", "When"))
            Text(
                text = ruEn("Несколько триггеров объединяются через ИЛИ. При одновременном совпадении используется первый по порядку.", "Several triggers are combined with OR. If several match at once, the first one in order is used."),
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
                AutomationButtonLabel(ruEn("Добавить триггер", "Add trigger"))
            }
        }
        item {
            SettingsTitle(ruEn("При условиях", "Under conditions"))
            Text(
                text = ruEn("Условия этого раздела объединяются через И. ", "Conditions in this section are combined with AND. ") +
                    ruEn("Одно время ожидания на всю группу: 0 — сразу пропустить, если не выполнено.", "One wait time for the whole group: 0 — skip immediately if it is not met."),
                style = MaterialTheme.typography.tboxCaption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AutomationSecondsField(
                label = ruEn("Ждать условие, с", "Wait for condition, s"),
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
                    peerAutomations = peerAutomations,
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
                    AutomationButtonLabel(ruEn("Удалить условие", "Delete condition"))
                }
            }
        }
        item {
            OutlinedButton(
                onClick = rememberWrappedOnClick {
                    onChange(definition.copy(conditions = definition.conditions + defaultNumericCondition()))
                },
            ) {
                AutomationButtonLabel(ruEn("Добавить условие", "Add condition"))
            }
        }
        item {
            SettingsTitle(ruEn("Выполнить", "Run"))
        }
        item {
            AutomationActionListEditor(
                actions = definition.actions,
                triggerIds = definition.triggers.map { it.id },
                apps = apps,
                floatingPanels = floatingPanels,
                peerAutomations = peerAutomations,
                pageCount = pageCount,
                settingsViewModel = settingsViewModel,
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
}

private fun automationSummary(definition: AutomationDefinition): String =
    ruEn("${definition.triggers.size} триггер(а) → ${definition.actions.size} действие(я)", "${definition.triggers.size} trigger(s) → ${definition.actions.size} action(s)")

/** Document rules plus the draft under edit (so self / new rules appear in pickers). */
private fun peerAutomationsForEditor(
    saved: List<AutomationDefinition>,
    draft: AutomationDefinition?,
): List<AutomationDefinition> {
    if (draft == null) return saved
    val withoutDraft = saved.filterNot { it.id == draft.id }
    return withoutDraft + draft
}

private fun runtimeStatusText(status: AutomationRuntimeStatus?): String {
    val time = formatAutomationRunTime(status)
    return when (status?.state) {
        null,
        AutomationExecutionState.IDLE,
        -> ruEn("Ещё не запускалась", "Has not run yet")

        AutomationExecutionState.RUNNING ->
            if (time != null) ruEn("Выполняется с $time", "Running since $time") else ruEn("Выполняется", "Running")

        AutomationExecutionState.SUCCESS ->
            if (time != null) ruEn("Последний запуск выполнен $time", "Last run succeeded $time") else ruEn("Последний запуск выполнен", "Last run succeeded")

        AutomationExecutionState.ERROR -> {
            val prefix = if (time != null) ruEn("Ошибка $time", "Error $time") else ruEn("Ошибка", "Error")
            val message = status.lastMessage
            if (message.isBlank()) prefix else "$prefix: $message"
        }
    }
}

private val AUTOMATION_LIMITS_HINT =
    ruEn("Одно правило не стартует чаще чем раз в 2 с. После 5 ошибок подряд оно выключается до ручного включения. ", "One rule does not start more often than once every 2 s. After 5 errors in a row it turns off until it is turned on manually. ") +
        ruEn("«Выполнить сейчас» сразу запускает сохранённые действия, без триггера и общих условий.", "“Run now” runs the saved actions immediately, without a trigger or shared conditions.")

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
