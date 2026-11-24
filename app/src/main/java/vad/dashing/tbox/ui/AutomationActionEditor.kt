package vad.dashing.tbox.ui

import vad.dashing.tbox.ruEn

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.AppPermissions
import vad.dashing.tbox.AUTOMATION_TRIGGER_ID_MAX_CHARS
import vad.dashing.tbox.CAMD_ACTION_CLOSE
import vad.dashing.tbox.CAMD_ACTION_CLOSE_ALL
import vad.dashing.tbox.CAMD_ACTION_OPEN
import vad.dashing.tbox.CamdAutomationWindowArgs
import vad.dashing.tbox.CamdWindowCrop
import vad.dashing.tbox.CAMD_FRAME_MODE_AS_IS
import vad.dashing.tbox.CAMD_FRAME_MODE_CUSTOM
import vad.dashing.tbox.CAMD_FRAME_MODE_PRESET
import vad.dashing.tbox.isValidCamdPresetName
import vad.dashing.tbox.CAMD_WINDOW_CAMERA_IDS
import vad.dashing.tbox.CAMD_WINDOW_DEFAULT_CAMERA
import vad.dashing.tbox.CAMD_WINDOW_DEFAULT_H
import vad.dashing.tbox.CAMD_WINDOW_DEFAULT_W
import vad.dashing.tbox.CAMD_WINDOW_DEFAULT_X
import vad.dashing.tbox.CAMD_WINDOW_DEFAULT_Y
import vad.dashing.tbox.AUTOMATION_TRIGGER_WIDGET_TOGGLE_INT
import vad.dashing.tbox.AppLauncherLaunchMode
import vad.dashing.tbox.DEFAULT_HTTP_REQUEST_WIDGET_YAML
import vad.dashing.tbox.R
import vad.dashing.tbox.SettingsViewModel
import vad.dashing.tbox.phone.PhoneDialCallActivity
import vad.dashing.tbox.ui.theme.tboxCaption
import vad.dashing.tbox.ui.theme.tboxTitle
import vad.dashing.tbox.automation.AUTOMATION_MAX_ACTION_DEPTH
import vad.dashing.tbox.automation.AUTOMATION_MAX_DELAY_MS
import vad.dashing.tbox.automation.AutomationAction
import vad.dashing.tbox.automation.AutomationActionGroup
import vad.dashing.tbox.automation.AutomationBuiltinActionType
import vad.dashing.tbox.automation.AutomationCanCatalog
import vad.dashing.tbox.automation.AutomationCanCatalogEntry
import vad.dashing.tbox.automation.AutomationCanOperation
import vad.dashing.tbox.automation.AutomationCanValueCodec
import vad.dashing.tbox.automation.AutomationCondition
import vad.dashing.tbox.automation.AutomationCruiseActions
import vad.dashing.tbox.automation.AutomationDefinition
import vad.dashing.tbox.automation.AutomationFloatingPanelEnabledOp
import vad.dashing.tbox.automation.AutomationFloatingPanelScope
import vad.dashing.tbox.automation.AutomationFloatingPanelVisibilityOp
import vad.dashing.tbox.automation.AutomationMainScreenTarget
import vad.dashing.tbox.automation.AutomationSignalStateEncoding
import vad.dashing.tbox.automation.AutomationTriggerWidgetCommand
import vad.dashing.tbox.automation.builtinActionTriggerWidgetCommand
import vad.dashing.tbox.automation.builtinActionGroup
import vad.dashing.tbox.automation.builtinActionKeywords
import vad.dashing.tbox.automation.canActionGroup
import vad.dashing.tbox.automation.canActionKeywords
import vad.dashing.tbox.formatCamdAutomationWindowArgs
import vad.dashing.tbox.parseCamdAutomationWindowArgs
import vad.dashing.tbox.automation.floatingPanelEnabledOp
import vad.dashing.tbox.automation.floatingPanelEnabledOpLabel
import vad.dashing.tbox.automation.floatingPanelEnabledOpToInt
import vad.dashing.tbox.automation.floatingPanelScope
import vad.dashing.tbox.automation.floatingPanelScopeLabel
import vad.dashing.tbox.automation.floatingPanelVisibilityOp
import vad.dashing.tbox.automation.floatingPanelVisibilityOpLabel
import vad.dashing.tbox.automation.floatingPanelVisibilityOpToInt
import vad.dashing.tbox.automation.WifiStaController
import vad.dashing.tbox.automation.sortedByAutomationLabel
import vad.dashing.tbox.FloatingDashboardConfig
import vad.dashing.tbox.freeform.FreeformLaunchBounds
import vad.dashing.tbox.freeform.FreeformLaunchSide
import vad.dashing.tbox.mbcan.UniversalCanRepository
@Composable
internal fun AutomationActionListEditor(
    actions: List<AutomationAction>,
    triggerIds: List<String>,
    apps: List<LaunchableAppEntry>,
    floatingPanels: List<FloatingDashboardConfig>,
    pageCount: Int,
    settingsViewModel: SettingsViewModel,
    onChange: (List<AutomationAction>) -> Unit,
    modifier: Modifier = Modifier,
    depth: Int = 0,
    peerAutomations: List<AutomationDefinition> = emptyList(),
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        actions.forEachIndexed { index, action ->
            AutomationActionEditor(
                action = action,
                index = index,
                triggerIds = triggerIds,
                apps = apps,
                floatingPanels = floatingPanels,
                peerAutomations = peerAutomations,
                pageCount = pageCount,
                settingsViewModel = settingsViewModel,
                canMoveUp = index > 0,
                canMoveDown = index < actions.lastIndex,
                onChange = { changed ->
                    onChange(actions.toMutableList().also { it[index] = changed })
                },
                onDelete = {
                    onChange(actions.filterIndexed { actionIndex, _ -> actionIndex != index })
                },
                onMoveUp = {
                    onChange(actions.moved(index, index - 1))
                },
                onMoveDown = {
                    onChange(actions.moved(index, index + 1))
                },
                depth = depth,
            )
        }
        if (depth < AUTOMATION_MAX_ACTION_DEPTH) {
            AddAutomationActionRow(
                apps = apps,
                floatingPanels = floatingPanels,
                peerAutomations = peerAutomations,
                onAdd = { onChange(actions + it) },
            )
        }
    }
}

@Composable
private fun AutomationActionEditor(
    action: AutomationAction,
    index: Int,
    triggerIds: List<String>,
    apps: List<LaunchableAppEntry>,
    floatingPanels: List<FloatingDashboardConfig>,
    peerAutomations: List<AutomationDefinition>,
    pageCount: Int,
    settingsViewModel: SettingsViewModel,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onChange: (AutomationAction) -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    depth: Int,
) {
    AutomationCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = ruEn("Действие ${index + 1}: ${actionTitle(action)}", "Action ${index + 1}: ${actionTitle(action)}"),
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
            when (action) {
                is AutomationAction.Delay -> AutomationSecondsField(
                    label = ruEn("Задержка, с", "Delay, s"),
                    valueMillis = action.durationMillis,
                    onValueChange = { onChange(action.copy(durationMillis = it)) },
                    modifier = Modifier.fillMaxWidth(),
                )

                is AutomationAction.IfThenElse -> IfThenElseFields(
                    action = action,
                    triggerIds = triggerIds,
                    apps = apps,
                    floatingPanels = floatingPanels,
                    peerAutomations = peerAutomations,
                    pageCount = pageCount,
                    settingsViewModel = settingsViewModel,
                    onChange = onChange,
                    depth = depth,
                )

                is AutomationAction.CanCommand -> CanCommandFields(action, onChange)
                is AutomationAction.LaunchApplication -> LaunchApplicationFields(
                    action = action,
                    apps = apps,
                    pageCount = pageCount,
                    settingsViewModel = settingsViewModel,
                    onChange = onChange,
                )

                is AutomationAction.OpenMainScreen -> OpenMainScreenFields(
                    action,
                    pageCount,
                    onChange,
                )

                is AutomationAction.HttpRequest -> HttpRequestFields(action, onChange)
                is AutomationAction.Builtin -> BuiltinActionFields(
                    action,
                    apps,
                    floatingPanels,
                    peerAutomations,
                    onChange,
                )
            }
    }
}

@Composable
private fun IfThenElseFields(
    action: AutomationAction.IfThenElse,
    triggerIds: List<String>,
    apps: List<LaunchableAppEntry>,
    floatingPanels: List<FloatingDashboardConfig>,
    peerAutomations: List<AutomationDefinition>,
    pageCount: Int,
    settingsViewModel: SettingsViewModel,
    onChange: (AutomationAction) -> Unit,
    depth: Int,
) {
    Text(
        text = ruEn("Если", "If"),
        style = MaterialTheme.typography.tboxTitle,
        color = MaterialTheme.colorScheme.onSurface,
    )
    AutomationConditionEditor(
        condition = action.condition,
        triggerIds = triggerIds,
        apps = apps,
        peerAutomations = peerAutomations,
        onChange = { onChange(action.copy(condition = it)) },
        modifier = Modifier.padding(start = 12.dp),
    )
    Text(
        text = ruEn("То", "Then"),
        style = MaterialTheme.typography.tboxTitle,
        color = MaterialTheme.colorScheme.onSurface,
    )
    AutomationActionListEditor(
        actions = action.thenActions,
        triggerIds = triggerIds,
        apps = apps,
        floatingPanels = floatingPanels,
        peerAutomations = peerAutomations,
        pageCount = pageCount,
        settingsViewModel = settingsViewModel,
        onChange = { onChange(action.copy(thenActions = it)) },
        modifier = Modifier.padding(start = 12.dp),
        depth = depth + 1,
    )
    Text(
        text = ruEn("Иначе", "Else"),
        style = MaterialTheme.typography.tboxTitle,
        color = MaterialTheme.colorScheme.onSurface,
    )
    AutomationActionListEditor(
        actions = action.elseActions,
        triggerIds = triggerIds,
        apps = apps,
        floatingPanels = floatingPanels,
        peerAutomations = peerAutomations,
        pageCount = pageCount,
        settingsViewModel = settingsViewModel,
        onChange = { onChange(action.copy(elseActions = it)) },
        modifier = Modifier.padding(start = 12.dp),
        depth = depth + 1,
    )
}

@Composable
private fun CanCommandFields(
    action: AutomationAction.CanCommand,
    onChange: (AutomationAction) -> Unit,
) {
    val canMode by UniversalCanRepository.mode.collectAsState()
    val entry = AutomationCanCatalog.get(action.bus, action.propertyId)
    val catalogEntries = AutomationCanCatalog.pickerEntries()
        .filter { it.supports(canMode) }
        .sortedByAutomationLabel { it.label }
    if (entry == null) {
        Text(
            text = ruEn("CAN-команда отсутствует в безопасном каталоге. Выберите другое действие.", "CAN command is not in the safe catalog. Choose another action."),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.tboxCaption,
        )
    }
    val selectedEntry = entry ?: catalogEntries.firstOrNull()
    if (selectedEntry == null) {
        Text(
            text = ruEn("Для текущего CAN-режима нет доступных команд.", "No commands are available for the current CAN mode."),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.tboxCaption,
        )
        return
    }
    val canOptions = remember(catalogEntries) {
        catalogEntries.map { catalogueEntry ->
            AutomationPickerOption(
                value = catalogueEntry,
                label = catalogueEntry.label,
                group = catalogueEntry.group.label,
                keywords = canActionKeywords(catalogueEntry.bus, catalogueEntry.propertyId),
                key = "can:${catalogueEntry.bus}:${catalogueEntry.propertyId}",
            )
        }
    }
        .sortedGroupedConsecutive()
    AutomationSearchablePickerField(
        label = ruEn("Действие", "Action"),
        value = selectedEntry,
        options = canOptions,
        optionLabel = { it.label },
        onValueChange = { selected ->
            val operation = if (
                AutomationCanOperation.SET in selected.allowedOperations
            ) {
                AutomationCanOperation.SET
            } else {
                selected.allowedOperations.first()
            }
            onChange(
                AutomationAction.CanCommand(
                    bus = selected.bus,
                    propertyId = selected.propertyId,
                    operation = operation,
                    value = selected.defaultValueFor(canMode),
                ).let(AutomationCanValueCodec::withPortableKey),
            )
        },
    )
    if (entry == null) return
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val operations = if (action.operation in entry.allowedOperations) {
            entry.allowedOperations.toList()
        } else {
            listOf(action.operation) + entry.allowedOperations
        }
        AutomationDropdown(
            label = ruEn("Операция", "Operation"),
            value = action.operation,
            options = operations,
            optionLabel = {
                when (it) {
                    AutomationCanOperation.SET -> ruEn("Установить", "Set")
                    AutomationCanOperation.TOGGLE -> ruEn("Переключить", "Toggle")
                    AutomationCanOperation.TRUNK_PULSE -> ruEn("Импульс открыть/закрыть", "Open/close pulse")
                }
            },
            onValueChange = {
                onChange(
                    AutomationCanValueCodec.withPortableKey(action.copy(operation = it, valueKey = null)),
                )
            },
            modifier = Modifier.weight(1f),
        )
        if (action.operation != AutomationCanOperation.TOGGLE) {
            val values = if (
                action.operation == AutomationCanOperation.TRUNK_PULSE
            ) {
                listOf(1, 2)
            } else {
                entry.allowedValuesFor(canMode)
            }
            if (values.isNotEmpty()) {
                val currentValue = AutomationCanValueCodec.resolveWriteValue(action, canMode)
                    ?: action.value
                val options = if (currentValue in values) values else listOf(currentValue) + values
                AutomationDropdown(
                    label = ruEn("Значение", "Value"),
                    value = currentValue,
                    options = options,
                    optionLabel = { entry.valueLabel(it, canMode) },
                    onValueChange = { selectedValue ->
                        onChange(
                            AutomationCanValueCodec.withPortableKey(
                                action.copy(value = selectedValue, valueKey = null),
                            ),
                        )
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun LaunchApplicationFields(
    action: AutomationAction.LaunchApplication,
    apps: List<LaunchableAppEntry>,
    pageCount: Int,
    settingsViewModel: SettingsViewModel,
    onChange: (AutomationAction) -> Unit,
) {
    val context = LocalContext.current
    AutomationPackagePicker(
        label = ruEn("Приложение", "App"),
        packageName = action.packageName,
        apps = apps,
        onValueChange = { onChange(action.copy(packageName = it)) },
    )
    val dclEnvEnabled by
        settingsViewModel.dclLauncherEnvironmentEnabled.collectAsStateWithLifecycle()
    val stockWindowAvailable = remember(context) {
        vad.dashing.tbox.AdayoStockAppWindow.isAvailable(context)
    }
    val launchModeOptions = remember(dclEnvEnabled, action.launchMode, stockWindowAvailable) {
        vad.dashing.tbox.dcl.DclLauncherEnvironmentRouting.selectableLaunchModes(
            envEnabled = dclEnvEnabled,
            currentMode = action.launchMode,
            showStockWindowMode = stockWindowAvailable,
        )
    }
    AutomationDropdown(
        label = ruEn("Режим запуска", "Launch mode"),
        value = action.launchMode,
        options = launchModeOptions,
        optionLabel = {
            when (it) {
                AppLauncherLaunchMode.FULLSCREEN -> ruEn("Обычный", "Normal")
                AppLauncherLaunchMode.DCL_FULL_WIDTH -> ruEn("На всю ширину (DCL)", "Full width (DCL)")
                AppLauncherLaunchMode.DCL_FULLSCREEN -> ruEn("На весь экран (DCL)", "Full screen (DCL)")
                AppLauncherLaunchMode.FREEFORM -> "Freeform"
                AppLauncherLaunchMode.STOCK_WINDOW -> ruEn("Окно штатного лаунчера", "Stock launcher window")
                AppLauncherLaunchMode.VIRTUAL_DISPLAY -> ruEn("В виртуальном дисплее (ADB)", "On a virtual display (ADB)")
            }
        },
        onValueChange = { onChange(action.copy(launchMode = it)) },
    )
    if (dclEnvEnabled &&
        action.launchMode in
        vad.dashing.tbox.dcl.DclLauncherEnvironmentRouting.BULK_REMAP_SOURCE_MODES
    ) {
        Text(
            text = stringResource(R.string.widget_app_launcher_dcl_env_legacy_mode_hint),
            style = MaterialTheme.typography.tboxCaption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (action.launchMode == AppLauncherLaunchMode.FREEFORM) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AutomationDropdown(
                label = ruEn("Сторона", "Side"),
                value = action.freeformSide,
                options = FreeformLaunchSide.entries,
                optionLabel = {
                    when (it) {
                        FreeformLaunchSide.LEFT -> ruEn("Слева", "Left")
                        FreeformLaunchSide.RIGHT -> ruEn("Справа", "Right")
                        FreeformLaunchSide.TOP -> ruEn("Сверху", "Top")
                        FreeformLaunchSide.BOTTOM -> ruEn("Снизу", "Bottom")
                    }
                },
                onValueChange = { onChange(action.copy(freeformSide = it)) },
                modifier = Modifier.weight(1f),
            )
            AutomationDropdown(
                label = ruEn("Размер, %", "Size, %"),
                value = FreeformLaunchBounds.normalizePercent(action.freeformPercent),
                options = FreeformLaunchBounds.percentOptions(),
                optionLabel = { "$it%" },
                onValueChange = { onChange(action.copy(freeformPercent = it)) },
                modifier = Modifier.weight(1f),
            )
            val pages = (0..pageCount).toList()
            val selectedPage = action.freeformOverlayPage?.coerceIn(1, pageCount) ?: 0
            AutomationDropdown(
                label = ruEn("Страница рядом", "Page beside"),
                value = selectedPage,
                options = pages,
                optionLabel = { if (it == 0) ruEn("Не менять", "Do not change") else it.toString() },
                onValueChange = {
                    onChange(action.copy(freeformOverlayPage = it.takeIf { page -> page > 0 }))
                },
                modifier = Modifier.weight(1f),
            )
        }
        AutomationDropdown(
            label = ruEn("Главный экран рядом", "Home screen beside"),
            value = action.freeformOverlayCrop,
            options = listOf(false, true),
            optionLabel = { if (it) ruEn("Обрезать по окну", "Crop to the window") else ruEn("Уместить целиком", "Fit entirely") },
            onValueChange = { onChange(action.copy(freeformOverlayCrop = it)) },
        )
    } else if (action.launchMode == AppLauncherLaunchMode.VIRTUAL_DISPLAY) {
        val displaysJson by settingsViewModel.huVirtualDisplaysJson.collectAsStateWithLifecycle()
        val refreshing by settingsViewModel.huVirtualDisplaysRefreshing.collectAsStateWithLifecycle()
        val cachedDisplays = remember(displaysJson) {
            vad.dashing.tbox.adb.HuDisplayInfo.listFromJson(displaysJson)
        }
        val pickerDisplays = remember(cachedDisplays) {
            vad.dashing.tbox.adb.VirtualDisplayTargetResolver.forPicker(cachedDisplays)
        }
        val options = remember(
            pickerDisplays,
            action.virtualDisplayId,
            action.virtualDisplayWidthPx,
            action.virtualDisplayHeightPx,
        ) {
            buildList {
                add(AutomationVirtualDisplayOption(null, null, null, ruEn("— не выбран —", "— not selected —")))
                val ids = pickerDisplays.map { it.displayId }.toSet()
                pickerDisplays.forEach { info ->
                    add(
                        AutomationVirtualDisplayOption(
                            info.displayId,
                            info.widthPx,
                            info.heightPx,
                            info.label(),
                        ),
                    )
                }
                val selected = action.virtualDisplayId
                if (selected != null && selected > 0 && selected !in ids) {
                    val w = action.virtualDisplayWidthPx
                    val h = action.virtualDisplayHeightPx
                    val sizeSuffix = if (w != null && h != null) " · ${w}×${h}" else ""
                    add(
                        AutomationVirtualDisplayOption(
                            selected,
                            w,
                            h,
                            ruEn("Дисплей $selected (нет в кэше)$sizeSuffix", "Display $selected (not in cache)$sizeSuffix"),
                        ),
                    )
                }
            }
        }
        val selectedOption = options.firstOrNull { it.id == action.virtualDisplayId }
            ?: options.first()
        // Remap selected id when the shared display cache refreshes (same as widget picker).
        LaunchedEffect(
            pickerDisplays,
            action.virtualDisplayId,
            action.virtualDisplayWidthPx,
            action.virtualDisplayHeightPx,
        ) {
            val preferredId = action.virtualDisplayId ?: return@LaunchedEffect
            val resolved = vad.dashing.tbox.adb.VirtualDisplayTargetResolver.resolve(
                preferredId = preferredId,
                preferredWidth = action.virtualDisplayWidthPx,
                preferredHeight = action.virtualDisplayHeightPx,
                catalog = cachedDisplays,
            )
            if (resolved is vad.dashing.tbox.adb.VirtualDisplayTargetResolver.ResolveResult.Matched &&
                resolved.remapped
            ) {
                onChange(
                    action.copy(
                        virtualDisplayId = resolved.display.displayId,
                        virtualDisplayWidthPx = resolved.display.widthPx,
                        virtualDisplayHeightPx = resolved.display.heightPx,
                    ),
                )
            }
        }
        AutomationDropdown(
            label = ruEn("Виртуальный дисплей", "Virtual display"),
            value = selectedOption,
            options = options,
            optionLabel = { it.label },
            onValueChange = {
                onChange(
                    action.copy(
                        virtualDisplayId = it.id,
                        virtualDisplayWidthPx = it.widthPx,
                        virtualDisplayHeightPx = it.heightPx,
                    ),
                )
            },
        )
        OutlinedButton(
            onClick = rememberWrappedOnClick {
                settingsViewModel.refreshHuVirtualDisplays(context) { outcome ->
                    val msg = when (outcome) {
                        is vad.dashing.tbox.adb.VirtualDisplayAdb.RefreshOutcome.Success -> {
                            val count = vad.dashing.tbox.adb.VirtualDisplayTargetResolver
                                .forPicker(outcome.displays)
                                .size
                            ruEn("Найдено дисплеев: $count", "Displays found: $count")
                        }
                        is vad.dashing.tbox.adb.VirtualDisplayAdb.RefreshOutcome.Failed ->
                            ruEn("Не удалось обновить список (${
                                vad.dashing.tbox.adb.LocalhostAdbReasons.forVirtualDisplay(
                                    context,
                                    outcome.reason,
                                    outcome.detail,
                                )
                            })", "Could not refresh the list (${
                                vad.dashing.tbox.adb.LocalhostAdbReasons.forVirtualDisplay(
                                    context,
                                    outcome.reason,
                                    outcome.detail,
                                )
                            })")
                    }
                    android.widget.Toast.makeText(
                        context,
                        msg,
                        android.widget.Toast.LENGTH_LONG,
                    ).show()
                }
            },
            enabled = !refreshing,
            modifier = Modifier.fillMaxWidth(),
        ) {
            AutomationButtonLabel(
                if (refreshing) ruEn("Обновление…", "Refreshing…") else ruEn("Обновить список дисплеев", "Refresh the display list"),
            )
        }
        Text(
            text = ruEn("Список общий с ярлыками (без дисплея 0). Id сверяется по размеру после рестарта лаунчера. Refresh возвращает TCP; запуск оставляет TCP включённым.", "The list is shared with shortcuts (display 0 excluded). The id is matched by size after the launcher restarts. Refresh turns TCP back on; a launch leaves TCP on."),
            style = MaterialTheme.typography.tboxCaption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun OpenMainScreenFields(
    action: AutomationAction.OpenMainScreen,
    pageCount: Int,
    onChange: (AutomationAction) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AutomationDropdown(
            label = ruEn("Куда открыть", "Where to open"),
            value = action.target,
            options = AutomationMainScreenTarget.entries,
            optionLabel = {
                when (it) {
                    AutomationMainScreenTarget.FULLSCREEN -> ruEn("TBox Monitor полноэкранно", "TBox Monitor fullscreen")
                    AutomationMainScreenTarget.CURRENT_WINDOW -> ruEn("Текущий freeform-overlay", "Current freeform overlay")
                }
            },
            onValueChange = { onChange(action.copy(target = it)) },
            modifier = Modifier.weight(2f),
        )
        val pages = (1..pageCount.coerceAtLeast(1)).toList()
        AutomationDropdown(
            label = ruEn("Страница", "Page"),
            value = action.page.coerceIn(1, pages.last()),
            options = pages,
            optionLabel = Int::toString,
            onValueChange = { onChange(action.copy(page = it)) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun HttpRequestFields(
    action: AutomationAction.HttpRequest,
    onChange: (AutomationAction) -> Unit,
) {
    AutomationDropdown(
        label = ruEn("HTTP-действие", "HTTP action"),
        value = action.openBrowser,
        options = listOf(false, true),
        optionLabel = { if (it) ruEn("Открыть URL в браузере", "Open a URL in the browser") else ruEn("Выполнить запрос", "Run request") },
        onValueChange = { onChange(action.copy(openBrowser = it)) },
    )
    AutomationTextField(
        value = action.yaml,
        onValueChange = { onChange(action.copy(yaml = it)) },
        label = ruEn("YAML запроса", "Request YAML"),
        singleLine = false,
        minLines = 3,
        modifier = Modifier.fillMaxWidth(),
    )
}

internal fun defaultBuiltinAction(
    type: AutomationBuiltinActionType,
    apps: List<LaunchableAppEntry>,
    savedWifiSsid: String,
    peerAutomations: List<AutomationDefinition>,
): AutomationAction.Builtin = AutomationAction.Builtin(
    type = type,
    intValue = when (type) {
        AutomationBuiltinActionType.SET_HU_SCREEN_BRIGHTNESS -> 8
        AutomationBuiltinActionType.SET_MEDIA_VOLUME -> 10
        AutomationBuiltinActionType.SET_PHONE_VOLUME -> 10
        AutomationBuiltinActionType.SET_NAVI_VOLUME -> 5
        AutomationBuiltinActionType.SET_VOICE_VOLUME -> 5
        AutomationBuiltinActionType.CRUISE_ENGAGE_TO_TARGET ->
            vad.dashing.tbox.ACC_CRUISE_TARGET_KMH_DEFAULT
        AutomationBuiltinActionType.CRUISE_NUDGE -> 1
        AutomationBuiltinActionType.CAMD_WINDOW -> CAMD_WINDOW_DEFAULT_CAMERA
        else -> 0
    },
    boolValue = type == AutomationBuiltinActionType.WIFI_SET_ENABLED ||
        type == AutomationBuiltinActionType.WIFI_MODEM_SET_DATA ||
        type == AutomationBuiltinActionType.SET_HU_SCREEN_AUTO_BRIGHTNESS ||
        type == AutomationBuiltinActionType.SET_AUTOMATION_TRIGGER_WIDGET ||
        type == AutomationBuiltinActionType.SET_AUTOMATION_ENABLED ||
        type == AutomationBuiltinActionType.ADB_SET_TCP ||
        type == AutomationBuiltinActionType.CAMD_RECORDER ||
        type == AutomationBuiltinActionType.DCL_SET_SMART_ACC ||
        type == AutomationBuiltinActionType.ESP_BLE_RELAY_SET,
    stringValue = when {
        type == AutomationBuiltinActionType.ADB_FORCE_STOP ->
            apps.firstOrNull()?.packageName.orEmpty()
        type == AutomationBuiltinActionType.WIFI_CONNECT -> savedWifiSsid
        type == AutomationBuiltinActionType.SET_HU_DAY_NIGHT_THEME -> "auto"
        type == AutomationBuiltinActionType.SET_HEADREST_SPEAKER -> "assist"
        type == AutomationBuiltinActionType.SET_AUTOMATION_ENABLED ->
            peerAutomations.firstOrNull()?.id.orEmpty()
        // Cruise mode comes from the global Settings → Car type; no UI kind.
        type == AutomationBuiltinActionType.CRUISE_ENGAGE_TO_TARGET ||
            type == AutomationBuiltinActionType.CRUISE_PAUSE ||
            type == AutomationBuiltinActionType.CRUISE_FULL_OFF ||
            type == AutomationBuiltinActionType.CRUISE_RESUME ||
            type == AutomationBuiltinActionType.CRUISE_ACTIVATE_AT_CURRENT_SPEED ||
            type == AutomationBuiltinActionType.CRUISE_NUDGE ->
            ""
        type == AutomationBuiltinActionType.CAMD_WINDOW ->
            formatCamdAutomationWindowArgs(
                CAMD_ACTION_OPEN,
                CAMD_WINDOW_DEFAULT_X,
                CAMD_WINDOW_DEFAULT_Y,
                CAMD_WINDOW_DEFAULT_W,
                CAMD_WINDOW_DEFAULT_H,
            )
        else -> ""
    },
)

@Composable
private fun BleRelayTargetFields(
    action: AutomationAction.Builtin,
    onChange: (AutomationAction) -> Unit,
    showPulse: Boolean,
) {
    AutomationIntField(
        label = ruEn("Реле (0 или 1)", "Relay (0 or 1)"),
        value = action.intValue,
        onValueChange = { onChange(action.copy(intValue = it)) },
        modifier = Modifier.fillMaxWidth(),
    )
    AutomationTextField(
        value = EspBleRelayActionEncoding.idOf(action),
        onValueChange = { id ->
            onChange(
                action.copy(
                    stringValue = EspBleRelayActionEncoding.pack(
                        action.type,
                        id,
                        EspBleRelayActionEncoding.pulseText(action),
                    ),
                ),
            )
        },
        label = ruEn("Id платы (пусто = индекс)", "Board id (empty = index)"),
        modifier = Modifier.fillMaxWidth(),
    )
    if (showPulse) {
        AutomationTextField(
            value = EspBleRelayActionEncoding.pulseText(action),
            onValueChange = { ms ->
                onChange(
                    action.copy(
                        stringValue = EspBleRelayActionEncoding.pack(
                            action.type,
                            EspBleRelayActionEncoding.idOf(action),
                            ms,
                        ),
                    ),
                )
            },
            label = ruEn("Длительность, мс (пусто = стандарт)", "Duration, ms (empty = default)"),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun BuiltinActionFields(
    action: AutomationAction.Builtin,
    apps: List<LaunchableAppEntry>,
    floatingPanels: List<FloatingDashboardConfig>,
    peerAutomations: List<AutomationDefinition>,
    onChange: (AutomationAction) -> Unit,
) {
    val context = LocalContext.current
    val builtinOptions = remember {
        AutomationBuiltinActionType.entries.filter {
            it != AutomationBuiltinActionType.ESP_RELAY_SET
        }.map { type ->
            AutomationPickerOption(
                value = type,
                label = builtinActionLabel(type),
                group = builtinActionGroup(type).label,
                keywords = builtinActionKeywords(type),
                key = "builtin:${type.storageKey}",
            )
        }
        .sortedGroupedConsecutive()
    }
    AutomationSearchablePickerField(
        label = ruEn("Действие", "Action"),
        value = action.type,
        options = builtinOptions,
        optionLabel = ::builtinActionLabel,
        onValueChange = { type ->
            onChange(defaultBuiltinAction(type, apps, WifiStaController.savedSsids(context).firstOrNull().orEmpty(), peerAutomations))
        },
    )
    when (action.type) {
        AutomationBuiltinActionType.ESP_RELAY_SET -> Text(
            text = ruEn("Действие больше не поддерживается. Выберите «Переключить ESP-реле» или «Импульс ESP-реле».", "This action is no longer supported. Choose “Toggle ESP relay” or “Pulse ESP relay”."),
            style = MaterialTheme.typography.tboxCaption,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.fillMaxWidth(),
        )

        AutomationBuiltinActionType.TOGGLE_HIDE_FLOATING_PANELS ->
            FloatingPanelVisibilityActionFields(action, floatingPanels, onChange)

        AutomationBuiltinActionType.TOGGLE_FLOATING_PANELS_ENABLED ->
            FloatingPanelEnabledActionFields(action, floatingPanels, onChange)

        AutomationBuiltinActionType.ESP_RELAY_TOGGLE -> AutomationIntField(
            label = ruEn("Канал реле", "Relay channel"),
            value = action.intValue,
            onValueChange = { onChange(action.copy(intValue = it)) },
            modifier = Modifier.fillMaxWidth(),
        )

        AutomationBuiltinActionType.ESP_RELAY_PULSE -> Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AutomationIntField(
                label = ruEn("Канал реле", "Relay channel"),
                value = action.intValue,
                onValueChange = { onChange(action.copy(intValue = it)) },
                modifier = Modifier.weight(1f),
            )
            AutomationTextField(
                value = action.stringValue,
                onValueChange = { onChange(action.copy(stringValue = it)) },
                label = ruEn("Длительность, мс (пусто = стандарт)", "Duration, ms (empty = default)"),
                modifier = Modifier.weight(1f),
            )
        }

        AutomationBuiltinActionType.ESP_BLE_RELAY_SET -> Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AutomationDropdown(
                label = ruEn("Канал BLE-реле", "BLE relay channel"),
                value = action.boolValue,
                options = listOf(true, false),
                optionLabel = { if (it) ruEn("Замкнуть", "Energize") else ruEn("Разомкнуть", "De-energize") },
                onValueChange = { onChange(action.copy(boolValue = it)) },
            )
            BleRelayTargetFields(action, onChange, showPulse = false)
        }

        AutomationBuiltinActionType.ESP_BLE_RELAY_TOGGLE ->
            BleRelayTargetFields(action, onChange, showPulse = false)

        AutomationBuiltinActionType.ESP_BLE_RELAY_PULSE ->
            BleRelayTargetFields(action, onChange, showPulse = true)

        AutomationBuiltinActionType.MEDIA_PREVIOUS,
        AutomationBuiltinActionType.MEDIA_PLAY_PAUSE,
        AutomationBuiltinActionType.MEDIA_PLAY,
        AutomationBuiltinActionType.MEDIA_NEXT,
        AutomationBuiltinActionType.MEDIA_TOGGLE_LIKE,
        -> AutomationPackagePicker(
            label = ruEn("Медиаплеер", "Media player"),
            packageName = action.stringValue,
            apps = apps,
            onValueChange = { onChange(action.copy(stringValue = it)) },
            blankLabel = ruEn("Активный плеер", "Active player"),
        )

        AutomationBuiltinActionType.SET_MEDIA_VOLUME -> AutomationIntField(
            label = ruEn("Громкость медиа (0–31)", "Media volume (0–31)"),
            value = action.intValue,
            onValueChange = { onChange(action.copy(intValue = it)) },
            modifier = Modifier.fillMaxWidth(),
        )

        AutomationBuiltinActionType.SET_PHONE_VOLUME -> AutomationIntField(
            label = ruEn("Громкость телефона (1–31)", "Phone volume (1–31)"),
            value = action.intValue,
            onValueChange = { onChange(action.copy(intValue = it)) },
            modifier = Modifier.fillMaxWidth(),
        )

        AutomationBuiltinActionType.SET_NAVI_VOLUME -> AutomationIntField(
            label = ruEn("Громкость навигатора (0–10)", "Navigation volume (0–10)"),
            value = action.intValue,
            onValueChange = { onChange(action.copy(intValue = it)) },
            modifier = Modifier.fillMaxWidth(),
        )

        AutomationBuiltinActionType.SET_VOICE_VOLUME -> AutomationIntField(
            label = ruEn("Громкость голоса (2–10)", "Voice volume (2–10)"),
            value = action.intValue,
            onValueChange = { onChange(action.copy(intValue = it)) },
            modifier = Modifier.fillMaxWidth(),
        )

        AutomationBuiltinActionType.SET_HEADREST_SPEAKER -> AutomationDropdown(
            label = ruEn("Динамик подголовника", "Headrest speaker"),
            value = action.stringValue.trim().lowercase().ifEmpty { "assist" },
            options = listOf("only", "assist", "off"),
            optionLabel = AutomationSignalStateEncoding::stateOptionLabel,
            onValueChange = { onChange(action.copy(stringValue = it)) },
        )

        AutomationBuiltinActionType.SET_HU_DAY_NIGHT_THEME -> AutomationDropdown(
            label = ruEn("Тема день/ночь ГУ", "HU day/night theme"),
            value = action.stringValue.trim().lowercase().ifEmpty { "auto" },
            options = listOf("light", "dark", "auto"),
            optionLabel = { key ->
                when (key) {
                    "light" -> ruEn("Светлая", "Light")
                    "dark" -> ruEn("Тёмная", "Dark")
                    else -> ruEn("Авто", "Auto")
                }
            },
            onValueChange = { onChange(action.copy(stringValue = it)) },
        )

        AutomationBuiltinActionType.SET_GEO_DEBUG_LOG,
        AutomationBuiltinActionType.SET_SIMULATED_LOCATION_SOURCE_LOSS,
        -> AutomationDropdown(
            label = if (
                action.type == AutomationBuiltinActionType.SET_GEO_DEBUG_LOG
            ) {
                ruEn("Запись", "Recording")
            } else {
                ruEn("Симуляция потери источника", "Source-loss simulation")
            },
            value = action.boolValue,
            options = listOf(true, false),
            optionLabel = {
                if (action.type == AutomationBuiltinActionType.SET_GEO_DEBUG_LOG) {
                    if (it) ruEn("Запустить", "Start") else ruEn("Остановить", "Stop")
                } else {
                    if (it) ruEn("Включить", "On") else ruEn("Выключить", "Off")
                }
            },
            onValueChange = { onChange(action.copy(boolValue = it)) },
        )

        AutomationBuiltinActionType.WIFI_SET_ENABLED -> AutomationDropdown(
            label = "Wi-Fi",
            value = action.boolValue,
            options = listOf(true, false),
            optionLabel = { if (it) ruEn("Включить", "On") else ruEn("Выключить", "Off") },
            onValueChange = { onChange(action.copy(boolValue = it)) },
        )

        AutomationBuiltinActionType.WIFI_CONNECT -> AutomationWifiSsidPicker(
            label = ruEn("Сохранённая сеть", "Saved network"),
            ssid = action.stringValue,
            includeNone = false,
            onValueChange = { onChange(action.copy(stringValue = it)) },
        )

        AutomationBuiltinActionType.WIFI_DISCONNECT -> Text(
            text = ruEn("Снимает ассоциацию с текущей сетью, радио остаётся включённым. ", "Clears the association with the current network; the radio stays on. ") +
                ruEn("Иначе ГУ сразу подключится к той же сети снова.", "Otherwise the HU connects to the same network again right away."),
            style = MaterialTheme.typography.tboxCaption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )

        AutomationBuiltinActionType.WIFI_MODEM_SET_DATA -> AutomationDropdown(
            label = ruEn("Данные Wi‑Fi модема", "Wi‑Fi modem data"),
            value = action.boolValue,
            options = listOf(true, false),
            optionLabel = { if (it) ruEn("Включить", "On") else ruEn("Выключить", "Off") },
            onValueChange = { onChange(action.copy(boolValue = it)) },
        )

        AutomationBuiltinActionType.WIFI_MODEM_REBOOT -> Text(
            text = ruEn("Перезагружает текущий Wi‑Fi модем (нужен источник «Wi‑Fi HTTP»).", "Reboots the current Wi‑Fi modem (the “Wi‑Fi HTTP” source is required)."),
            style = MaterialTheme.typography.tboxCaption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )

        AutomationBuiltinActionType.ADB_SET_TCP -> Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AutomationDropdown(
                label = "ADB TCP (:5555)",
                value = action.boolValue,
                options = listOf(true, false),
                optionLabel = { if (it) ruEn("Включить", "On") else ruEn("Выключить", "Off") },
                onValueChange = { onChange(action.copy(boolValue = it)) },
            )
            Text(
                text = stringResource(R.string.automation_adb_set_tcp_hint),
                style = MaterialTheme.typography.tboxCaption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        AutomationBuiltinActionType.ADB_SHELL -> Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AutomationTextField(
                value = action.stringValue,
                onValueChange = {
                    onChange(
                        action.copy(
                            stringValue = it.take(
                                vad.dashing.tbox.adb.AdbAutomationActions.MAX_SHELL_COMMAND_CHARS,
                            ),
                        ),
                    )
                },
                label = stringResource(R.string.automation_adb_shell_command_label),
                singleLine = false,
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(R.string.automation_adb_shell_hint),
                style = MaterialTheme.typography.tboxCaption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        AutomationBuiltinActionType.ADB_FORCE_STOP -> Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AutomationPackagePicker(
                label = stringResource(R.string.automation_adb_force_stop_package_label),
                packageName = action.stringValue,
                apps = apps,
                onValueChange = { onChange(action.copy(stringValue = it)) },
            )
            Text(
                text = stringResource(R.string.automation_adb_force_stop_hint),
                style = MaterialTheme.typography.tboxCaption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        AutomationBuiltinActionType.SET_HU_SCREEN_BRIGHTNESS -> AutomationIntField(
            label = ruEn("Яркость экрана ГУ (1–10)", "HU screen brightness (1–10)"),
            value = action.intValue,
            onValueChange = { raw ->
                // Skip empty draft; do not clamp here or the field fights typing.
                if (raw == Int.MIN_VALUE) return@AutomationIntField
                onChange(action.copy(intValue = raw))
            },
        )

        AutomationBuiltinActionType.SET_HU_SCREEN_AUTO_BRIGHTNESS -> AutomationDropdown(
            label = ruEn("Автояркость экрана ГУ", "HU screen auto-brightness"),
            value = action.boolValue,
            options = listOf(true, false),
            optionLabel = { if (it) ruEn("Включить", "On") else ruEn("Выключить", "Off") },
            onValueChange = { onChange(action.copy(boolValue = it)) },
        )

        AutomationBuiltinActionType.SET_AUTOMATION_TRIGGER_WIDGET -> Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AutomationDropdown(
                label = ruEn("Режим", "Mode"),
                value = builtinActionTriggerWidgetCommand(action),
                options = AutomationTriggerWidgetCommand.entries.toList(),
                optionLabel = { command ->
                    when (command) {
                        AutomationTriggerWidgetCommand.ACTIVATE -> ruEn("Активировать", "Activate")
                        AutomationTriggerWidgetCommand.DEACTIVATE -> ruEn("Деактивировать", "Deactivate")
                        AutomationTriggerWidgetCommand.TOGGLE -> ruEn("Переключить", "Toggle")
                    }
                },
                onValueChange = { command ->
                    onChange(
                        action.copy(
                            boolValue = command != AutomationTriggerWidgetCommand.DEACTIVATE,
                            intValue = if (command == AutomationTriggerWidgetCommand.TOGGLE) {
                                AUTOMATION_TRIGGER_WIDGET_TOGGLE_INT
                            } else {
                                0
                            },
                        ),
                    )
                },
            )
            AutomationTextField(
                value = action.stringValue,
                onValueChange = { raw ->
                    onChange(action.copy(stringValue = raw.take(AUTOMATION_TRIGGER_ID_MAX_CHARS)))
                },
                label = ruEn("ID триггера виджета", "Widget trigger ID"),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        AutomationBuiltinActionType.SET_AUTOMATION_ENABLED -> Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AutomationRulePicker(
                label = ruEn("Автоматизация", "Automation"),
                selectedId = action.stringValue,
                peerAutomations = peerAutomations,
                onIdChange = { onChange(action.copy(stringValue = it)) },
            )
            AutomationDropdown(
                label = ruEn("Операция", "Operation"),
                value = action.boolValue,
                options = listOf(true, false),
                optionLabel = { if (it) ruEn("Включить", "On") else ruEn("Выключить", "Off") },
                onValueChange = { onChange(action.copy(boolValue = it)) },
            )
            Text(
                text = ruEn("Без переключения: если правило уже в нужном состоянии — no-op. ", "No toggle: if the rule is already in the desired state — no-op. ") +
                    ruEn("Правило может выключить само себя (текущий прогон отменяется).", "The rule can turn itself off (the current run is cancelled)."),
                style = MaterialTheme.typography.tboxCaption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        AutomationBuiltinActionType.SHOW_TOAST -> AutomationTextField(
            value = action.stringValue,
            onValueChange = { onChange(action.copy(stringValue = it)) },
            label = ruEn("Текст", "Text"),
            singleLine = false,
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )

        AutomationBuiltinActionType.SHOW_ALERT -> Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AutomationTextField(
                value = action.stringValue,
                onValueChange = { onChange(action.copy(stringValue = it)) },
                label = ruEn("Текст", "Text"),
                singleLine = false,
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            AutomationSecondsField(
                label = ruEn("Автозакрытие, с (0 — только «Закрыть»)", "Auto-close, s (0 — “Close” only)"),
                valueMillis = action.intValue.toLong().coerceAtLeast(0L),
                onValueChange = { millis ->
                    if (millis >= 0L) {
                        onChange(
                            action.copy(
                                intValue = millis.coerceAtMost(AUTOMATION_MAX_DELAY_MS).toInt(),
                            ),
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = ruEn("Нужно разрешение «Поверх других окон»: Настройки → Разрешения. Само по себе не выдаётся.", "The “Display over other apps” permission is required: Settings → Permissions. It is not granted on its own."),
                style = MaterialTheme.typography.tboxCaption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        AutomationBuiltinActionType.CRUISE_ENGAGE_TO_TARGET -> Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AutomationIntField(
                label = ruEn("Уставка, км/ч (30–150)", "Setpoint, km/h (30–150)"),
                value = action.intValue
                    .takeIf { it != 0 }
                    ?: vad.dashing.tbox.ACC_CRUISE_TARGET_KMH_DEFAULT,
                onValueChange = { raw ->
                    // Skip empty draft; do not clamp here or the field fights typing.
                    if (raw == Int.MIN_VALUE) return@AutomationIntField
                    onChange(action.copy(intValue = raw))
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = ruEn("Как виджет «Уставка круиз-контроля»: включение и доведение до цели ", "Like the “Cruise control setpoint” widget: turn on and bring it to the target ") +
                    ruEn("(интервалы шагов — как у виджета по умолчанию). Уже на уставке → пауза (212). ", "(step intervals — same as the default widget). Already at the setpoint → pause (212). ") +
                    ruEn("Действие ждёт окончания converge. Путь ACC/CCS — из Настройки → Автомобиль.", "The action waits until converge finishes. The ACC/CCS path is Settings → Vehicle."),
                style = MaterialTheme.typography.tboxCaption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        AutomationBuiltinActionType.CRUISE_PAUSE,
        AutomationBuiltinActionType.CRUISE_FULL_OFF,
        AutomationBuiltinActionType.CRUISE_RESUME,
        AutomationBuiltinActionType.CRUISE_ACTIVATE_AT_CURRENT_SPEED,
        -> Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = when (action.type) {
                    AutomationBuiltinActionType.CRUISE_PAUSE ->
                        ruEn("Пауза Active/Override (Cancel 212), как статус-плитка. Путь ACC/CCS — из Настройки → Автомобиль.", "Pause Active/Override (Cancel 212), like the status tile. The ACC/CCS path is Settings → Vehicle.")
                    AutomationBuiltinActionType.CRUISE_FULL_OFF ->
                        ruEn("Полное выключение (210), как двойное нажатие виджета. Путь ACC/CCS — из Настройки → Автомобиль.", "Full off (210), like a double tap on the widget. The ACC/CCS path is Settings → Vehicle.")
                    AutomationBuiltinActionType.CRUISE_ACTIVATE_AT_CURRENT_SPEED ->
                        ruEn("Активация на текущей скорости (SET−), как свайп вниз на статус-плитке в Standby; из Off — enable+SET−. Путь ACC/CCS — из Настройки → Автомобиль.", "Activate at the current speed (SET−), like a swipe down on the status tile in Standby; from Off — enable+SET−. The ACC/CCS path is Settings → Vehicle.")
                    else ->
                        ruEn("Возобновление из Standby (RES+), как свайп вверх на статус-плитке. Путь ACC/CCS — из Настройки → Автомобиль.", "Resume from Standby (RES+), like a swipe up on the status tile. The ACC/CCS path is Settings → Vehicle.")
                },
                style = MaterialTheme.typography.tboxCaption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        AutomationBuiltinActionType.CRUISE_NUDGE -> Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AutomationDropdown(
                label = ruEn("Шаг уставки", "Setpoint step"),
                value = if (action.intValue == -1) -1 else 1,
                options = listOf(1, -1),
                optionLabel = { if (it > 0) ruEn("+1 км/ч (RES+)", "+1 km/h (RES+)") else ruEn("−1 км/ч (SET−)", "−1 km/h (SET−)") },
                onValueChange = { onChange(action.copy(intValue = it)) },
            )
            Text(
                text = ruEn("Только в Active/Override; как свайпы статус-плитки. Путь ACC/CCS — из Настройки → Автомобиль.", "Only in Active/Override; like swipes on the status tile. The ACC/CCS path is Settings → Vehicle."),
                style = MaterialTheme.typography.tboxCaption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        AutomationBuiltinActionType.PLACE_CALL -> Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AutomationTextField(
                value = action.stringValue,
                onValueChange = { onChange(action.copy(stringValue = it)) },
                label = ruEn("Номер телефона", "Phone number"),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = ruEn("Исходящий звонок через Telecom ACTION_CALL → HFP. Нужны разрешение ", "Outgoing call via Telecom ACTION_CALL → HFP. Requires the ") +
                    ruEn("CALL_PHONE и активный Bluetooth HFP. Без HFP — ошибка, клавиатура не открывается.", "CALL_PHONE permission and an active Bluetooth HFP. Without HFP — an error, and the dialer does not open."),
                style = MaterialTheme.typography.tboxCaption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PhoneCallPermissionGrantButton()
        }

        AutomationBuiltinActionType.CAMD_WINDOW -> CamdWindowActionFields(action, onChange)

        AutomationBuiltinActionType.DCL_SET_SMART_ACC -> Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AutomationDropdown(
                label = ruEn("Умный ACC", "Smart ACC"),
                value = action.boolValue,
                options = listOf(true, false),
                optionLabel = { if (it) ruEn("Включить", "On") else ruEn("Выключить", "Off") },
                onValueChange = { onChange(action.copy(boolValue = it)) },
            )
            AutomationDropdown(
                label = ruEn("АСС по ограничению (настройка)", "ACC by speed limit (setting)"),
                value = action.stringValue.trim().lowercase().let { raw ->
                    when (raw) {
                        "true", "1", "yes", "on" -> true
                        "false", "0", "no", "off" -> false
                        else -> null
                    }
                },
                options = listOf(null, true, false),
                optionLabel = {
                    when (it) {
                        true -> ruEn("Включить", "On")
                        false -> ruEn("Выключить", "Off")
                        null -> ruEn("Не менять", "Do not change")
                    }
                },
                onValueChange = { next ->
                    onChange(
                        action.copy(
                            stringValue = when (next) {
                                true -> "true"
                                false -> "false"
                                null -> ""
                            },
                        ),
                    )
                },
            )
            AutomationIntField(
                label = ruEn("Целевая скорость, км/ч (0 = не задавать)", "Target speed, km/h (0 = do not set)"),
                value = action.intValue,
                onValueChange = { onChange(action.copy(intValue = it.coerceAtLeast(0))) },
            )
            Text(
                text = ruEn("Управляет Smart ACC в DCL Launcher (setSmartAcc). Не меняет CAN-круиз.", "Controls Smart ACC in DCL Launcher (setSmartAcc). It does not change CAN cruise."),
                style = MaterialTheme.typography.tboxCaption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        AutomationBuiltinActionType.DCL_TOGGLE_SMART_ACC -> Text(
            text = ruEn("Переключает Умный ACC в DCL (toggleSmartAcc).", "Toggles Smart ACC in DCL (toggleSmartAcc)."),
            style = MaterialTheme.typography.tboxCaption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )

        AutomationBuiltinActionType.CAMD_KEEP_AVM -> Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AutomationDropdown(
                label = ruEn("Держать штатный AVM отключенным", "Keep stock AVM disabled"),
                value = action.boolValue,
                options = listOf(true, false),
                optionLabel = { if (it) ruEn("Включить", "On") else ruEn("Выключить", "Off") },
                onValueChange = { onChange(action.copy(boolValue = it)) },
            )
            Text(
                text = ruEn("Включено — штатный AVM остановлен и не запускается после перезагрузки. ", "On — stock AVM is stopped and does not start after a reboot. ") +
                    ruEn("Выключено — сырые окна закрываются и AVM сразу запускается. ", "Off — the raw windows close and AVM starts immediately. ") +
                    ruEn("Если служба не слушает порт, команда попробует её запустить.", "If the service is not listening on the port, the command will try to start it."),
                style = MaterialTheme.typography.tboxCaption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        AutomationBuiltinActionType.CAMD_RECORDER -> Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AutomationDropdown(
                label = ruEn("Регистратор Камеры ГУ", "HU Cameras recorder"),
                value = action.boolValue,
                options = listOf(true, false),
                optionLabel = { if (it) ruEn("Включить", "On") else ruEn("Выключить", "Off") },
                onValueChange = { onChange(action.copy(boolValue = it)) },
            )
            Text(
                text = ruEn("Мастер-переключатель регистратора (REC ON/OFF). Папка и камеры задаются ", "Recorder master switch (REC ON/OFF). The folder and cameras are set ") +
                    ruEn("в приложении «Камеры ГУ». Это не то же самое, что «запись идёт» ", "in the “HU Cameras” app. This is not the same as “recording” ") +
                    ruEn("(сигнал camd_recording_active). Если служба не слушает порт, команда ", "(camd_recording_active signal). If the service is not listening on the port, the command ") +
                    ruEn("попробует её запустить.", "will try to start it."),
                style = MaterialTheme.typography.tboxCaption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        AutomationBuiltinActionType.CAMD_RECORDER_SOS -> Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = ruEn("Разовая команда REC SOS (camd 0.8.6+): сохраняет текущий сегмент записи ", "One-shot REC SOS command (camd 0.8.6+): saves the current recording segment ") +
                    ruEn("в папку SOS. Без параметров; регистратор должен быть включен.", "to the SOS folder. No parameters; the recorder must be on."),
                style = MaterialTheme.typography.tboxCaption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        AutomationBuiltinActionType.ANSWER_CALL,
        AutomationBuiltinActionType.HANGUP_CALL,
        -> Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = ruEn("Best-effort через BluetoothHeadsetClient / Telecom. Успех — стек принял ", "Best-effort via BluetoothHeadsetClient / Telecom. Success — the stack accepted ") +
                    ruEn("команду, не подтверждение разговора; отказ стека или нет прав — ошибка.", "the command, not confirmation of the call; a stack rejection or missing permission is an error."),
                style = MaterialTheme.typography.tboxCaption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PhoneCallPermissionGrantButton()
        }

        else -> Unit
    }
}

@Composable
private fun CamdWindowActionFields(
    action: AutomationAction.Builtin,
    onChange: (AutomationAction) -> Unit,
) {
    val args = parseCamdAutomationWindowArgs(action.stringValue) ?: CamdAutomationWindowArgs(
        mode = CAMD_ACTION_OPEN,
        x = CAMD_WINDOW_DEFAULT_X,
        y = CAMD_WINDOW_DEFAULT_Y,
        w = CAMD_WINDOW_DEFAULT_W,
        h = CAMD_WINDOW_DEFAULT_H,
    )
    val camera = if (action.intValue in CAMD_WINDOW_CAMERA_IDS) {
        action.intValue
    } else {
        CAMD_WINDOW_DEFAULT_CAMERA
    }
    fun openLine(
        mode: String = args.mode,
        x: Int = args.x,
        y: Int = args.y,
        w: Int = args.w,
        h: Int = args.h,
        rotation: Int = args.rotation,
        flipH: Boolean = args.flipH,
        flipV: Boolean = args.flipV,
        crop: CamdWindowCrop? = args.crop,
        presetName: String? = args.presetName,
        circle: Boolean = args.circle,
    ): String = formatCamdAutomationWindowArgs(mode, x, y, w, h, rotation, flipH, flipV, crop, presetName, circle)
    // Frame mode lives inside the command line (tokens decide), the dropdown just edits it.
    var frameMode by remember {
        mutableStateOf(
            when {
                args.presetName != null -> CAMD_FRAME_MODE_PRESET
                args.rotation != 0 || args.flipH || args.flipV || args.crop != null -> CAMD_FRAME_MODE_CUSTOM
                else -> CAMD_FRAME_MODE_AS_IS
            }
        )
    }
    // Free draft: invalid names must be typable; the line updates only once the name is valid.
    var presetDraft by remember { mutableStateOf(args.presetName ?: "") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AutomationDropdown(
            label = ruEn("Окно", "Window"),
            value = args.mode,
            options = listOf(CAMD_ACTION_OPEN, CAMD_ACTION_CLOSE, CAMD_ACTION_CLOSE_ALL),
            optionLabel = { mode ->
                when (mode) {
                    CAMD_ACTION_CLOSE -> ruEn("Закрыть эту камеру", "Close this camera")
                    CAMD_ACTION_CLOSE_ALL -> ruEn("Закрыть все", "Close all")
                    else -> ruEn("Открыть", "Open")
                }
            },
            onValueChange = { mode ->
                onChange(
                    action.copy(
                        intValue = camera,
                        stringValue = openLine(mode = mode),
                    ),
                )
            },
        )
        if (args.mode != CAMD_ACTION_CLOSE_ALL) {
            val cameraLabels = CAMD_WINDOW_CAMERA_IDS.associateWith { id ->
                stringResource(camdWindowCameraTitle(id))
            }
            AutomationDropdown(
                label = ruEn("Камера", "Camera"),
                value = camera,
                options = CAMD_WINDOW_CAMERA_IDS,
                optionLabel = { id -> cameraLabels[id] ?: id.toString() },
                onValueChange = { onChange(action.copy(intValue = it)) },
            )
        }
        if (args.mode == CAMD_ACTION_OPEN) {
            AutomationDropdown(
                label = ruEn("Кадр", "Frame"),
                value = frameMode,
                options = listOf(CAMD_FRAME_MODE_AS_IS, CAMD_FRAME_MODE_CUSTOM, CAMD_FRAME_MODE_PRESET),
                optionLabel = { mode ->
                    when (mode) {
                        CAMD_FRAME_MODE_CUSTOM -> ruEn("Свои настройки", "Custom settings")
                        CAMD_FRAME_MODE_PRESET -> ruEn("Пресет", "Preset")
                        else -> ruEn("Как есть", "As is")
                    }
                },
                onValueChange = { mode ->
                    frameMode = mode
                    if (mode == CAMD_FRAME_MODE_PRESET) {
                        presetDraft = args.presetName ?: presetDraft
                    }
                    onChange(
                        action.copy(
                            stringValue = when (mode) {
                                CAMD_FRAME_MODE_CUSTOM -> openLine(rotation = args.rotation, flipH = args.flipH, flipV = args.flipV, crop = args.crop, presetName = null)
                                CAMD_FRAME_MODE_PRESET -> openLine(rotation = 0, flipH = false, flipV = false, crop = null, presetName = args.presetName)
                                else -> openLine(rotation = 0, flipH = false, flipV = false, crop = null, presetName = null)
                            },
                        ),
                    )
                },
            )
            if (frameMode == CAMD_FRAME_MODE_PRESET) {
                AutomationTextField(
                    value = presetDraft,
                    onValueChange = { name ->
                        presetDraft = name
                        if (isValidCamdPresetName(name)) {
                            onChange(action.copy(stringValue = openLine(presetName = name)))
                        }
                    },
                    label = ruEn("Пресет (a-z 0-9 _)", "Preset (a-z 0-9 _)"),
                )
            }
            if (frameMode == CAMD_FRAME_MODE_CUSTOM) {
                AutomationDropdown(
                    label = ruEn("Поворот", "Rotation"),
                    value = args.rotation,
                    options = listOf(0, 90, 180, 270),
                    optionLabel = { "$it°" },
                    onValueChange = { degrees ->
                        onChange(action.copy(stringValue = openLine(rotation = degrees)))
                    },
                )
                AutomationDropdown(
                    label = ruEn("Зеркало H", "Mirror H"),
                    value = args.flipH,
                    options = listOf(false, true),
                    optionLabel = { if (it) ruEn("Да", "Yes") else ruEn("Нет", "No") },
                    onValueChange = { onChange(action.copy(stringValue = openLine(flipH = it))) },
                )
                AutomationDropdown(
                    label = ruEn("Зеркало V", "Mirror V"),
                    value = args.flipV,
                    options = listOf(false, true),
                    optionLabel = { if (it) ruEn("Да", "Yes") else ruEn("Нет", "No") },
                    onValueChange = { onChange(action.copy(stringValue = openLine(flipV = it))) },
                )
                AutomationDropdown(
                    label = ruEn("Вырезка", "Crop"),
                    value = args.crop != null,
                    options = listOf(false, true),
                    optionLabel = { if (it) ruEn("Да", "Yes") else ruEn("Нет", "No") },
                    onValueChange = { enabled ->
                        onChange(
                            action.copy(
                                stringValue = openLine(
                                    crop = if (enabled) CamdWindowCrop(10, 10, 80, 80) else null,
                                ),
                            ),
                        )
                    },
                )
                if (args.crop != null) {
                    val crop = args.crop
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CamdPercentField(ruEn("Вырезка: слева, %", "Crop: left, %"), crop.x, Modifier.weight(1f)) { next ->
                            onChange(action.copy(stringValue = openLine(crop = crop.copy(x = next))))
                        }
                        CamdPercentField(ruEn("Вырезка: сверху, %", "Crop: top, %"), crop.y, Modifier.weight(1f)) { next ->
                            onChange(action.copy(stringValue = openLine(crop = crop.copy(y = next))))
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CamdPercentField(ruEn("Вырезка: ширина, %", "Crop: width, %"), crop.w, Modifier.weight(1f)) { next ->
                            onChange(action.copy(stringValue = openLine(crop = crop.copy(w = next))))
                        }
                        CamdPercentField(ruEn("Вырезка: высота, %", "Crop: height, %"), crop.h, Modifier.weight(1f)) { next ->
                            onChange(action.copy(stringValue = openLine(crop = crop.copy(h = next))))
                        }
                    }
                }
            }
            AutomationDropdown(
                label = ruEn("Круг", "Circle"),
                value = args.circle,
                options = listOf(false, true),
                optionLabel = { if (it) ruEn("Да", "Yes") else ruEn("Нет", "No") },
                onValueChange = { onChange(action.copy(stringValue = openLine(circle = it))) },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CamdPercentField(ruEn("Слева, %", "Left, %"), args.x, Modifier.weight(1f)) { next ->
                    onChange(action.copy(stringValue = openLine(x = next)))
                }
                CamdPercentField(ruEn("Сверху, %", "Top, %"), args.y, Modifier.weight(1f)) { next ->
                    onChange(action.copy(stringValue = openLine(y = next)))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CamdPercentField(ruEn("Ширина, %", "Width, %"), args.w, Modifier.weight(1f)) { next ->
                    onChange(action.copy(stringValue = openLine(w = next)))
                }
                CamdPercentField(ruEn("Высота, %", "Height, %"), args.h, Modifier.weight(1f)) { next ->
                    onChange(action.copy(stringValue = openLine(h = next)))
                }
            }
        }
        Text(
            text = ruEn("Нужно приложение «Камеры ГУ». Если служба не слушает порт, команда попробует её запустить. ", "The “HU Cameras” app is required. If the service is not listening on the port, the command will try to start it. ") +
                ruEn("Если удержание AVM выключено, окно входов 4, 5, 8 и 9 сначала останавливает штатный AVM. ", "If AVM hold is off, a window for inputs 4, 5, 8, and 9 stops stock AVM first. ") +
                ruEn("Настройки пресета задаёт «Камеры ГУ», здесь их поменять нельзя.", "The preset is configured by “HU Cameras”; it cannot be changed here."),
            style = MaterialTheme.typography.tboxCaption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun CamdPercentField(
    label: String,
    value: Int,
    modifier: Modifier,
    onValue: (Int) -> Unit,
) {
    AutomationIntField(
        label = label,
        value = value,
        onValueChange = { raw ->
            if (raw == Int.MIN_VALUE) return@AutomationIntField
            onValue(raw)
        },
        modifier = modifier,
    )
}

/** Settings / first-failure path for API-only users who never tap the dial tile (S9). */
@Composable
private fun PhoneCallPermissionGrantButton() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var refreshTick by remember { mutableStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshTick++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val granted = remember(refreshTick) { AppPermissions.hasPhoneCallAccess(context) }
    if (granted) return
    OutlinedButton(
        onClick = rememberWrappedOnClick {
            PhoneDialCallActivity.startPermissionRequest(context)
        },
    ) {
        AutomationButtonLabel(stringResource(R.string.permissions_call_phone_grant_button))
    }
}

@Composable
private fun AddAutomationActionRow(
    apps: List<LaunchableAppEntry>,
    floatingPanels: List<FloatingDashboardConfig>,
    peerAutomations: List<AutomationDefinition>,
    onAdd: (AutomationAction) -> Unit,
) {
    var kind by remember { mutableStateOf(ActionUiKind.ACTIONS) }
    var showActionsPicker by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val canMode by UniversalCanRepository.mode.collectAsState()
    val mergedActionOptions: List<AutomationPickerOption<Any>> =
        remember(canMode) {
            buildList {
                AutomationBuiltinActionType.entries
                    .filter { it != AutomationBuiltinActionType.ESP_RELAY_SET }
                    .forEach { type ->
                        add(
                            AutomationPickerOption<Any>(
                                value = type,
                                label = builtinActionLabel(type),
                                group = builtinActionGroup(type).label,
                                keywords = builtinActionKeywords(type),
                                key = "builtin:${type.storageKey}",
                            ),
                        )
                    }
                AutomationCanCatalog.pickerEntries()
                    .filter { it.supports(canMode) }
                    .sortedByAutomationLabel { it.label }
                    .forEach { catalogueEntry ->
                        add(
                            AutomationPickerOption<Any>(
                                value = catalogueEntry,
                                label = catalogueEntry.label,
                                group = catalogueEntry.group.label,
                                keywords = canActionKeywords(
                                    catalogueEntry.bus,
                                    catalogueEntry.propertyId,
                                ),
                                key = "can:${catalogueEntry.bus}:${catalogueEntry.propertyId}",
                            ),
                        )
                    }
            }
        }
        .sortedGroupedConsecutive()
    if (showActionsPicker) {
        val keyboardController = LocalSoftwareKeyboardController.current
        AutomationSearchablePickerDialog(
            title = ruEn("Действие", "Action"),
            options = mergedActionOptions,
            onSelect = { selected ->
                keyboardController?.hide()
                showActionsPicker = false
                onAdd(
                    when (selected) {
                        is AutomationBuiltinActionType ->
                            defaultBuiltinAction(selected, apps, WifiStaController.savedSsids(context).firstOrNull().orEmpty(), peerAutomations)

                        is AutomationCanCatalogEntry -> {
                            val entry = selected
                            AutomationAction.CanCommand(
                                bus = entry.bus,
                                propertyId = entry.propertyId,
                                operation = if (
                                    AutomationCanOperation.SET in entry.allowedOperations
                                ) {
                                    AutomationCanOperation.SET
                                } else {
                                    entry.allowedOperations.first()
                                },
                                value = entry.defaultValueFor(canMode),
                            ).let(AutomationCanValueCodec::withPortableKey)
                        }

                        else -> AutomationAction.Builtin(
                            AutomationBuiltinActionType.OPEN_MENU,
                        )
                    },
                )
            },
            optionLabel = { value ->
                when (value) {
                    is AutomationBuiltinActionType -> builtinActionLabel(value)
                    is AutomationCanCatalogEntry -> value.label
                    else -> ""
                }
            },
            onDismiss = { showActionsPicker = false },
            widthDp = 360.dp,
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        AutomationDropdown(
            label = ruEn("Новое действие", "New action"),
            value = kind,
            options = ActionUiKind.entries.sortedByAutomationLabel { it.label() },
            optionLabel = ActionUiKind::label,
            onValueChange = { kind = it },
            modifier = Modifier.weight(1f),
        )
        OutlinedButton(
            onClick = rememberWrappedOnClick {
                if (kind == ActionUiKind.ACTIONS) {
                    showActionsPicker = true
                } else {
                    onAdd(defaultAction(kind, apps))
                }
            },
        ) {
            AutomationButtonLabel(ruEn("Добавить", "Add"))
        }
    }
}

private enum class ActionUiKind {
    ACTIONS,
    LAUNCH_APP,
    OPEN_MAIN_SCREEN,
    HTTP,
        DELAY,
        SHOW_TOAST,
        SHOW_ALERT,
        IF_THEN_ELSE;

    fun label(): String = when (this) {
        ACTIONS -> ruEn("Действия", "Actions")
        LAUNCH_APP -> ruEn("Запустить приложение", "Launch app")
        OPEN_MAIN_SCREEN -> ruEn("Открыть страницу главного экрана", "Open a home screen page")
        HTTP -> "HTTP"
        DELAY -> ruEn("Задержка", "Delay")
        SHOW_TOAST -> "Toast"
        SHOW_ALERT -> ruEn("Сообщение на экране", "On-screen message")
        IF_THEN_ELSE -> ruEn("Если — То — Иначе", "If — Then — Else")
    }
}

private fun defaultAction(
    kind: ActionUiKind,
    apps: List<LaunchableAppEntry>,
): AutomationAction = when (kind) {
    ActionUiKind.ACTIONS ->
        AutomationAction.Builtin(AutomationBuiltinActionType.OPEN_MENU)

    ActionUiKind.LAUNCH_APP -> AutomationAction.LaunchApplication(
        packageName = apps.firstOrNull()?.packageName.orEmpty(),
    )

    ActionUiKind.OPEN_MAIN_SCREEN -> AutomationAction.OpenMainScreen(page = 1)
    ActionUiKind.HTTP -> AutomationAction.HttpRequest(DEFAULT_HTTP_REQUEST_WIDGET_YAML)
    ActionUiKind.DELAY -> AutomationAction.Delay(0L)
    ActionUiKind.SHOW_TOAST -> AutomationAction.Builtin(AutomationBuiltinActionType.SHOW_TOAST)
    ActionUiKind.SHOW_ALERT -> AutomationAction.Builtin(AutomationBuiltinActionType.SHOW_ALERT)
    ActionUiKind.IF_THEN_ELSE -> AutomationAction.IfThenElse(
        condition = defaultNumericCondition(),
        thenActions = emptyList(),
    )
}

private fun actionTitle(action: AutomationAction): String = when (action) {
    is AutomationAction.Delay -> ruEn("Задержка", "Delay")
    is AutomationAction.IfThenElse -> ruEn("Если — То — Иначе", "If — Then — Else")
    is AutomationAction.CanCommand ->
        AutomationCanCatalog.get(action.bus, action.propertyId)?.label ?: "CAN"

    is AutomationAction.LaunchApplication -> ruEn("Запустить приложение", "Launch app")
    is AutomationAction.OpenMainScreen -> ruEn("Открыть главный экран", "Open home screen")
    is AutomationAction.HttpRequest -> "HTTP"
    is AutomationAction.Builtin -> builtinActionLabel(action.type)
}

internal fun builtinActionLabel(type: AutomationBuiltinActionType): String = when (type) {
    AutomationBuiltinActionType.OPEN_MENU -> ruEn("Открыть меню программы", "Open the app menu")
    AutomationBuiltinActionType.FINISH_AND_START_TRIP -> ruEn("Завершить поездку и начать новую", "End the trip and start a new one")
    AutomationBuiltinActionType.RESET_MOTOR_HOURS -> ruEn("Сбросить моточасы", "Reset engine hours")
    AutomationBuiltinActionType.RESTART_TBOX -> ruEn("Перезагрузить TBox", "Reboot TBox")
    AutomationBuiltinActionType.TOGGLE_APP_DAY_NIGHT_THEME -> ruEn("Переключить день/ночь", "Toggle day/night")
    AutomationBuiltinActionType.ENABLE_HEAD_UNIT_AUTO_THEME -> ruEn("Включить автоматическую тему ГУ", "Turn on HU auto theme")
    AutomationBuiltinActionType.SET_HU_DAY_NIGHT_THEME -> ruEn("Тема день/ночь ГУ (светлая/тёмная/авто)", "HU day/night theme (light/dark/auto)")
    AutomationBuiltinActionType.TOGGLE_MIRROR_ADJUST_MODE -> ruEn("Переключить регулировку зеркал", "Toggle mirror adjustment")
    AutomationBuiltinActionType.TOGGLE_HIDE_FLOATING_PANELS -> ruEn("Плавающие панели — видимость", "Floating panels — visibility")
    AutomationBuiltinActionType.TOGGLE_FLOATING_PANELS_ENABLED -> ruEn("Плавающие панели — включение", "Floating panels — on/off")
    AutomationBuiltinActionType.ESP_RELAY_SET -> ruEn("Установить маску ESP-реле", "Set the ESP relay mask")
    AutomationBuiltinActionType.ESP_RELAY_TOGGLE -> ruEn("Переключить ESP-реле", "Toggle ESP relay")
    AutomationBuiltinActionType.ESP_RELAY_PULSE -> ruEn("Импульс ESP-реле", "Pulse ESP relay")
    AutomationBuiltinActionType.ESP_BLE_RELAY_SET -> ruEn("Замкнуть/разомкнуть BLE-реле", "Energize/de-energize BLE relay")
    AutomationBuiltinActionType.ESP_BLE_RELAY_TOGGLE -> ruEn("Переключить BLE-реле", "Toggle BLE relay")
    AutomationBuiltinActionType.ESP_BLE_RELAY_PULSE -> ruEn("Импульс BLE-реле", "Pulse BLE relay")
    AutomationBuiltinActionType.MEDIA_PREVIOUS -> ruEn("Предыдущий трек", "Previous track")
    AutomationBuiltinActionType.MEDIA_PLAY_PAUSE -> ruEn("Воспроизведение/пауза", "Play/pause")
    AutomationBuiltinActionType.MEDIA_PLAY -> ruEn("Воспроизведение", "Playback")
    AutomationBuiltinActionType.MEDIA_NEXT -> ruEn("Следующий трек", "Next track")
    AutomationBuiltinActionType.MEDIA_TOGGLE_LIKE -> ruEn("Поставить/снять «Нравится»", "Toggle “Like”")
    AutomationBuiltinActionType.SET_MEDIA_VOLUME -> ruEn("Установить громкость медиа", "Set media volume")
    AutomationBuiltinActionType.SET_PHONE_VOLUME -> ruEn("Установить громкость телефона", "Set phone volume")
    AutomationBuiltinActionType.SET_NAVI_VOLUME -> ruEn("Установить громкость навигатора", "Set navigation volume")
    AutomationBuiltinActionType.SET_VOICE_VOLUME -> ruEn("Установить громкость голоса", "Set voice volume")
    AutomationBuiltinActionType.SET_HEADREST_SPEAKER -> ruEn("Динамик подголовника", "Headrest speaker")
    AutomationBuiltinActionType.CYCLE_MOCK_LOCATION_MODE -> ruEn("Следующий режим подмены геопозиции", "Next mock-location mode")
    AutomationBuiltinActionType.GNSS_MODULE_REBOOT -> ruEn("Перезапустить GNSS-модуль", "Restart the GNSS module")
    AutomationBuiltinActionType.SET_SIMULATED_LOCATION_SOURCE_LOSS ->
        ruEn("Симулировать потерю геоисточника", "Simulate location-source loss")
    AutomationBuiltinActionType.SET_GEO_DEBUG_LOG -> ruEn("Запись гео-журнала", "Geo log recording")
    AutomationBuiltinActionType.WIFI_SET_ENABLED -> ruEn("Wi-Fi: включить / выключить", "Wi-Fi: on / off")
    AutomationBuiltinActionType.WIFI_CONNECT -> ruEn("Wi-Fi: подключиться к сети", "Wi-Fi: connect to network")
    AutomationBuiltinActionType.WIFI_DISCONNECT -> ruEn("Wi-Fi: отключиться от сети", "Wi-Fi: disconnect from network")
    AutomationBuiltinActionType.WIFI_MODEM_SET_DATA -> ruEn("Wi‑Fi модем: данные вкл/выкл", "Wi‑Fi modem: data on/off")
    AutomationBuiltinActionType.WIFI_MODEM_REBOOT -> ruEn("Wi‑Fi модем: перезагрузка", "Wi‑Fi modem: reboot")
    AutomationBuiltinActionType.ADB_SET_TCP -> ruEn("ADB TCP: включить / выключить", "ADB TCP: on / off")
    AutomationBuiltinActionType.ADB_SHELL -> ruEn("ADB: выполнить shell-команду", "ADB: run shell command")
    AutomationBuiltinActionType.ADB_FORCE_STOP -> ruEn("ADB: остановить приложение", "ADB: stop app")
    AutomationBuiltinActionType.SET_HU_SCREEN_BRIGHTNESS -> ruEn("Яркость экрана ГУ", "HU screen brightness")
    AutomationBuiltinActionType.SET_HU_SCREEN_AUTO_BRIGHTNESS -> ruEn("Автояркость экрана ГУ", "HU screen auto-brightness")
    AutomationBuiltinActionType.SHOW_TOAST -> "Toast"
    AutomationBuiltinActionType.SHOW_ALERT -> ruEn("Сообщение на экране", "On-screen message")
    AutomationBuiltinActionType.SET_AUTOMATION_TRIGGER_WIDGET -> ruEn("Триггер автоматизации (виджет)", "Automation trigger (widget)")
    AutomationBuiltinActionType.SET_AUTOMATION_ENABLED -> ruEn("Включить / выключить автоматизацию", "Turn an automation on / off")
    AutomationBuiltinActionType.CRUISE_ENGAGE_TO_TARGET ->
        ruEn("Круиз: включить и довести до уставки", "Cruise: turn on and reach the setpoint")
    AutomationBuiltinActionType.CRUISE_PAUSE -> ruEn("Круиз: пауза (Cancel)", "Cruise: pause (Cancel)")
    AutomationBuiltinActionType.CRUISE_FULL_OFF -> ruEn("Круиз: полностью выключить", "Cruise: turn fully off")
    AutomationBuiltinActionType.CRUISE_RESUME -> ruEn("Круиз: возобновить (RES+)", "Cruise: resume (RES+)")
    AutomationBuiltinActionType.CRUISE_ACTIVATE_AT_CURRENT_SPEED ->
        ruEn("Круиз: активировать на текущей скорости (SET−)", "Cruise: activate at the current speed (SET−)")
    AutomationBuiltinActionType.CRUISE_NUDGE -> ruEn("Круиз: уставка ±1", "Cruise: setpoint ±1")
    AutomationBuiltinActionType.START_VAD_VOICE -> ruEn("VAD Voice: слушать", "VAD Voice: listen")
    AutomationBuiltinActionType.PLACE_CALL -> ruEn("Позвонить (HFP)", "Place call (HFP)")
    AutomationBuiltinActionType.ANSWER_CALL -> ruEn("Ответить на звонок (HFP)", "Answer call (HFP)")
    AutomationBuiltinActionType.HANGUP_CALL -> ruEn("Сбросить звонок (HFP)", "End call (HFP)")
    AutomationBuiltinActionType.CAMD_WINDOW -> ruEn("Окно камеры", "Camera window")
    AutomationBuiltinActionType.CAMD_KEEP_AVM -> ruEn("Держать штатный AVM отключенным", "Keep stock AVM disabled")
    AutomationBuiltinActionType.CAMD_RECORDER -> ruEn("Регистратор Камеры ГУ", "HU Cameras recorder")
    AutomationBuiltinActionType.CAMD_RECORDER_SOS -> ruEn("SOS регистратора (сохранить кадр)", "Recorder SOS (save frame)")
    AutomationBuiltinActionType.DCL_SET_SMART_ACC -> ruEn("DCL: Умный ACC вкл/выкл", "DCL: Smart ACC on/off")
    AutomationBuiltinActionType.DCL_TOGGLE_SMART_ACC -> ruEn("DCL: переключить Умный ACC", "DCL: toggle Smart ACC")
}

private fun <T> List<T>.moved(from: Int, to: Int): List<T> {
    if (from !in indices || to !in indices || from == to) return this
    return toMutableList().also { list ->
        val item = list.removeAt(from)
        list.add(to, item)
    }
}

@Composable
private fun FloatingPanelVisibilityActionFields(
    action: AutomationAction.Builtin,
    floatingPanels: List<FloatingDashboardConfig>,
    onChange: (AutomationAction) -> Unit,
) {
    FloatingPanelTargetFields(
        action = action,
        floatingPanels = floatingPanels,
        onChange = onChange,
    )
    AutomationDropdown(
        label = ruEn("Операция", "Operation"),
        value = action.floatingPanelVisibilityOp(),
        options = AutomationFloatingPanelVisibilityOp.entries.toList(),
        optionLabel = ::floatingPanelVisibilityOpLabel,
        onValueChange = { op ->
            onChange(action.copy(intValue = floatingPanelVisibilityOpToInt(op)))
        },
    )
    Text(
        text = ruEn("Скрытие и показ временные: настройку «Показывать плавающую панель» не меняют. ", "Hide and show are temporary: they do not change the “Show floating panel” setting. ") +
            ruEn("После перезапуска приложения или службы панели снова появятся, если для них ", "After the app or service restarts, panels appear again if ") +
            ruEn("включено «Показывать плавающую панель» и нет других ограничений (правила ", "“Show floating panel” is on and there are no other limits (rules ") +
            ruEn("скрытия по приложениям, разрешение «Поверх других окон»).", "for hiding by app, the “Display over other apps” permission)."),
        style = MaterialTheme.typography.tboxCaption,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun FloatingPanelEnabledActionFields(
    action: AutomationAction.Builtin,
    floatingPanels: List<FloatingDashboardConfig>,
    onChange: (AutomationAction) -> Unit,
) {
    FloatingPanelTargetFields(
        action = action,
        floatingPanels = floatingPanels,
        onChange = onChange,
    )
    AutomationDropdown(
        label = ruEn("Операция", "Operation"),
        value = action.floatingPanelEnabledOp(),
        options = AutomationFloatingPanelEnabledOp.entries.toList(),
        optionLabel = ::floatingPanelEnabledOpLabel,
        onValueChange = { op ->
            onChange(action.copy(intValue = floatingPanelEnabledOpToInt(op)))
        },
    )
    Text(
        text = ruEn("Включение и выключение сохраняются в настройках (переключатель «Показывать ", "On and off are saved in settings (the “Show ") +
            ruEn("плавающую панель») и действуют после перезагрузки. При выполнении сбрасывается ", "floating panel” switch) and they apply after a reboot. When it runs, this clears ") +
            ruEn("временное скрытие всех панелей.", "the temporary hide of all panels."),
        style = MaterialTheme.typography.tboxCaption,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun FloatingPanelTargetFields(
    action: AutomationAction.Builtin,
    floatingPanels: List<FloatingDashboardConfig>,
    onChange: (AutomationAction) -> Unit,
) {
    val scope = action.floatingPanelScope()
    AutomationDropdown(
        label = ruEn("Панели", "Panels"),
        value = scope,
        options = AutomationFloatingPanelScope.entries.toList(),
        optionLabel = ::floatingPanelScopeLabel,
        onValueChange = { selected ->
            onChange(
                action.copy(
                    stringValue = when (selected) {
                        AutomationFloatingPanelScope.ALL -> ""
                        AutomationFloatingPanelScope.SELECTED ->
                            action.stringValue.ifBlank {
                                floatingPanels.firstOrNull()?.id.orEmpty()
                            }
                    },
                ),
            )
        },
    )
    if (scope == AutomationFloatingPanelScope.SELECTED) {
        if (floatingPanels.isEmpty()) {
            Text(
                text = ruEn("Нет плавающих панелей. Создайте панель в настройках.", "No floating panels. Create a panel in settings."),
                style = MaterialTheme.typography.tboxCaption,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            AutomationDropdown(
                label = ruEn("Панель", "Panel"),
                value = floatingPanels.firstOrNull { it.id == action.stringValue }
                    ?: floatingPanels.first(),
                options = floatingPanels,
                optionLabel = { panel -> panel.name.ifBlank { panel.id } },
                onValueChange = { panel -> onChange(action.copy(stringValue = panel.id)) },
            )
        }
    }
}

private data class AutomationVirtualDisplayOption(
    val id: Int?,
    val widthPx: Int?,
    val heightPx: Int?,
    val label: String,
)
