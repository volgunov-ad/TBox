package vad.dashing.tbox.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.R
import vad.dashing.tbox.ui.theme.tboxCaption
import vad.dashing.tbox.ui.theme.tboxTitle
import vad.dashing.tbox.AUTOMATION_TRIGGER_ID_MAX_CHARS
import vad.dashing.tbox.AUTOMATION_TRIGGER_WIDGET_TOGGLE_INT
import vad.dashing.tbox.AppLauncherLaunchMode
import vad.dashing.tbox.DEFAULT_HTTP_REQUEST_WIDGET_YAML
import vad.dashing.tbox.SettingsViewModel
import vad.dashing.tbox.automation.AUTOMATION_MAX_ACTION_DEPTH
import vad.dashing.tbox.automation.AUTOMATION_MAX_DELAY_MS
import vad.dashing.tbox.automation.AutomationAction
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
                    text = "Действие ${index + 1}: ${actionTitle(action)}",
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
            when (action) {
                is AutomationAction.Delay -> AutomationSecondsField(
                    label = "Задержка, с",
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
        text = "Если",
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
        text = "То",
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
        text = "Иначе",
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
            text = "CAN-команда отсутствует в безопасном каталоге. Выберите другое действие.",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.tboxCaption,
        )
    }
    val selectedEntry = entry ?: catalogEntries.firstOrNull()
    if (selectedEntry == null) {
        Text(
            text = "Для текущего CAN-режима нет доступных команд.",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.tboxCaption,
        )
        return
    }
    AutomationDropdown(
        label = "CAN-действие",
        value = selectedEntry,
        options = catalogEntries,
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
            label = "Операция",
            value = action.operation,
            options = operations,
            optionLabel = {
                when (it) {
                    AutomationCanOperation.SET -> "Установить"
                    AutomationCanOperation.TOGGLE -> "Переключить"
                    AutomationCanOperation.TRUNK_PULSE -> "Импульс открыть/закрыть"
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
                    label = "Значение",
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
        label = "Приложение",
        packageName = action.packageName,
        apps = apps,
        onValueChange = { onChange(action.copy(packageName = it)) },
    )
    AutomationDropdown(
        label = "Режим запуска",
        value = action.launchMode,
        options = AppLauncherLaunchMode.entries,
        optionLabel = {
            when (it) {
                AppLauncherLaunchMode.FULLSCREEN -> "Обычный полноэкранный"
                AppLauncherLaunchMode.FREEFORM -> "Freeform"
                AppLauncherLaunchMode.STOCK_WINDOW -> "Окно штатного лаунчера"
                AppLauncherLaunchMode.VIRTUAL_DISPLAY -> "В виртуальном дисплее (ADB)"
            }
        },
        onValueChange = { onChange(action.copy(launchMode = it)) },
    )
    if (action.launchMode == AppLauncherLaunchMode.FREEFORM) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AutomationDropdown(
                label = "Сторона",
                value = action.freeformSide,
                options = FreeformLaunchSide.entries,
                optionLabel = {
                    when (it) {
                        FreeformLaunchSide.LEFT -> "Слева"
                        FreeformLaunchSide.RIGHT -> "Справа"
                        FreeformLaunchSide.TOP -> "Сверху"
                        FreeformLaunchSide.BOTTOM -> "Снизу"
                    }
                },
                onValueChange = { onChange(action.copy(freeformSide = it)) },
                modifier = Modifier.weight(1f),
            )
            AutomationDropdown(
                label = "Размер, %",
                value = FreeformLaunchBounds.normalizePercent(action.freeformPercent),
                options = FreeformLaunchBounds.percentOptions(),
                optionLabel = { "$it%" },
                onValueChange = { onChange(action.copy(freeformPercent = it)) },
                modifier = Modifier.weight(1f),
            )
            val pages = (0..pageCount).toList()
            val selectedPage = action.freeformOverlayPage?.coerceIn(1, pageCount) ?: 0
            AutomationDropdown(
                label = "Страница рядом",
                value = selectedPage,
                options = pages,
                optionLabel = { if (it == 0) "Не менять" else it.toString() },
                onValueChange = {
                    onChange(action.copy(freeformOverlayPage = it.takeIf { page -> page > 0 }))
                },
                modifier = Modifier.weight(1f),
            )
        }
        AutomationDropdown(
            label = "Главный экран рядом",
            value = action.freeformOverlayCrop,
            options = listOf(false, true),
            optionLabel = { if (it) "Обрезать по окну" else "Уместить целиком" },
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
                add(AutomationVirtualDisplayOption(null, null, null, "— не выбран —"))
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
                            "Дисплей $selected (нет в кэше)$sizeSuffix",
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
            label = "Виртуальный дисплей",
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
                            "Найдено дисплеев: $count"
                        }
                        is vad.dashing.tbox.adb.VirtualDisplayAdb.RefreshOutcome.Failed ->
                            "Не удалось обновить список (${outcome.reason.name})"
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
                if (refreshing) "Обновление…" else "Обновить список дисплеев",
            )
        }
        Text(
            text = "Список общий с ярлыками (без дисплея 0). Id сверяется по размеру после рестарта лаунчера. Refresh возвращает TCP; запуск оставляет TCP включённым.",
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
            label = "Куда открыть",
            value = action.target,
            options = AutomationMainScreenTarget.entries,
            optionLabel = {
                when (it) {
                    AutomationMainScreenTarget.FULLSCREEN -> "TBox Monitor полноэкранно"
                    AutomationMainScreenTarget.CURRENT_WINDOW -> "Текущий freeform-overlay"
                }
            },
            onValueChange = { onChange(action.copy(target = it)) },
            modifier = Modifier.weight(2f),
        )
        val pages = (1..pageCount.coerceAtLeast(1)).toList()
        AutomationDropdown(
            label = "Страница",
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
        label = "HTTP-действие",
        value = action.openBrowser,
        options = listOf(false, true),
        optionLabel = { if (it) "Открыть URL в браузере" else "Выполнить запрос" },
        onValueChange = { onChange(action.copy(openBrowser = it)) },
    )
    AutomationTextField(
        value = action.yaml,
        onValueChange = { onChange(action.copy(yaml = it)) },
        label = "YAML запроса",
        singleLine = false,
        minLines = 3,
        modifier = Modifier.fillMaxWidth(),
    )
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
    AutomationDropdown(
        label = "Действие приложения",
        value = action.type,
        options = AutomationBuiltinActionType.entries.filter {
            it != AutomationBuiltinActionType.ESP_RELAY_SET
        }.sortedByAutomationLabel(::builtinActionLabel),
        optionLabel = ::builtinActionLabel,
        onValueChange = { type ->
            onChange(
                AutomationAction.Builtin(
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
                        else -> 0
                    },
                    boolValue = type == AutomationBuiltinActionType.WIFI_SET_ENABLED ||
                        type == AutomationBuiltinActionType.WIFI_MODEM_SET_DATA ||
                        type == AutomationBuiltinActionType.SET_HU_SCREEN_AUTO_BRIGHTNESS ||
                        type == AutomationBuiltinActionType.SET_AUTOMATION_TRIGGER_WIDGET ||
                        type == AutomationBuiltinActionType.SET_AUTOMATION_ENABLED ||
                        type == AutomationBuiltinActionType.ADB_SET_TCP,
                    stringValue = when {
                        type == AutomationBuiltinActionType.ADB_FORCE_STOP ->
                            apps.firstOrNull()?.packageName.orEmpty()
                        type == AutomationBuiltinActionType.WIFI_CONNECT ->
                            WifiStaController.savedSsids(context).firstOrNull().orEmpty()
                        type == AutomationBuiltinActionType.SET_HU_DAY_NIGHT_THEME -> "auto"
                        type == AutomationBuiltinActionType.SET_HEADREST_SPEAKER -> "assist"
                        type == AutomationBuiltinActionType.SET_AUTOMATION_ENABLED ->
                            peerAutomations.firstOrNull()?.id.orEmpty()
                        type == AutomationBuiltinActionType.CRUISE_ENGAGE_TO_TARGET ||
                            type == AutomationBuiltinActionType.CRUISE_PAUSE ||
                            type == AutomationBuiltinActionType.CRUISE_FULL_OFF ||
                            type == AutomationBuiltinActionType.CRUISE_RESUME ||
                            type == AutomationBuiltinActionType.CRUISE_ACTIVATE_AT_CURRENT_SPEED ||
                            type == AutomationBuiltinActionType.CRUISE_NUDGE ->
                            AutomationCruiseActions.MODE_ACC
                        else -> ""
                    },
                ),
            )
        },
    )
    when (action.type) {
        AutomationBuiltinActionType.ESP_RELAY_SET -> Text(
            text = "Действие больше не поддерживается. Выберите «Переключить ESP-реле» или «Импульс ESP-реле».",
            style = MaterialTheme.typography.tboxCaption,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.fillMaxWidth(),
        )

        AutomationBuiltinActionType.TOGGLE_HIDE_FLOATING_PANELS ->
            FloatingPanelVisibilityActionFields(action, floatingPanels, onChange)

        AutomationBuiltinActionType.TOGGLE_FLOATING_PANELS_ENABLED ->
            FloatingPanelEnabledActionFields(action, floatingPanels, onChange)

        AutomationBuiltinActionType.ESP_RELAY_TOGGLE -> AutomationIntField(
            label = "Канал реле",
            value = action.intValue,
            onValueChange = { onChange(action.copy(intValue = it)) },
            modifier = Modifier.fillMaxWidth(),
        )

        AutomationBuiltinActionType.ESP_RELAY_PULSE -> Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AutomationIntField(
                label = "Канал реле",
                value = action.intValue,
                onValueChange = { onChange(action.copy(intValue = it)) },
                modifier = Modifier.weight(1f),
            )
            AutomationTextField(
                value = action.stringValue,
                onValueChange = { onChange(action.copy(stringValue = it)) },
                label = "Длительность, мс (пусто = стандарт)",
                modifier = Modifier.weight(1f),
            )
        }

        AutomationBuiltinActionType.MEDIA_PREVIOUS,
        AutomationBuiltinActionType.MEDIA_PLAY_PAUSE,
        AutomationBuiltinActionType.MEDIA_PLAY,
        AutomationBuiltinActionType.MEDIA_NEXT,
        AutomationBuiltinActionType.MEDIA_TOGGLE_LIKE,
        -> AutomationPackagePicker(
            label = "Медиаплеер",
            packageName = action.stringValue,
            apps = apps,
            onValueChange = { onChange(action.copy(stringValue = it)) },
            blankLabel = "Активный плеер",
        )

        AutomationBuiltinActionType.SET_MEDIA_VOLUME -> AutomationIntField(
            label = "Громкость медиа (0–31)",
            value = action.intValue,
            onValueChange = { onChange(action.copy(intValue = it)) },
            modifier = Modifier.fillMaxWidth(),
        )

        AutomationBuiltinActionType.SET_PHONE_VOLUME -> AutomationIntField(
            label = "Громкость телефона (1–31)",
            value = action.intValue,
            onValueChange = { onChange(action.copy(intValue = it)) },
            modifier = Modifier.fillMaxWidth(),
        )

        AutomationBuiltinActionType.SET_NAVI_VOLUME -> AutomationIntField(
            label = "Громкость навигатора (0–10)",
            value = action.intValue,
            onValueChange = { onChange(action.copy(intValue = it)) },
            modifier = Modifier.fillMaxWidth(),
        )

        AutomationBuiltinActionType.SET_VOICE_VOLUME -> AutomationIntField(
            label = "Громкость голоса (2–10)",
            value = action.intValue,
            onValueChange = { onChange(action.copy(intValue = it)) },
            modifier = Modifier.fillMaxWidth(),
        )

        AutomationBuiltinActionType.SET_HEADREST_SPEAKER -> AutomationDropdown(
            label = "Динамик подголовника",
            value = action.stringValue.trim().lowercase().ifEmpty { "assist" },
            options = listOf("only", "assist", "off"),
            optionLabel = AutomationSignalStateEncoding::stateOptionLabel,
            onValueChange = { onChange(action.copy(stringValue = it)) },
        )

        AutomationBuiltinActionType.SET_HU_DAY_NIGHT_THEME -> AutomationDropdown(
            label = "Тема день/ночь ГУ",
            value = action.stringValue.trim().lowercase().ifEmpty { "auto" },
            options = listOf("light", "dark", "auto"),
            optionLabel = { key ->
                when (key) {
                    "light" -> "Светлая"
                    "dark" -> "Тёмная"
                    else -> "Авто"
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
                "Запись"
            } else {
                "Симуляция потери источника"
            },
            value = action.boolValue,
            options = listOf(true, false),
            optionLabel = {
                if (action.type == AutomationBuiltinActionType.SET_GEO_DEBUG_LOG) {
                    if (it) "Запустить" else "Остановить"
                } else {
                    if (it) "Включить" else "Выключить"
                }
            },
            onValueChange = { onChange(action.copy(boolValue = it)) },
        )

        AutomationBuiltinActionType.WIFI_SET_ENABLED -> AutomationDropdown(
            label = "Wi-Fi",
            value = action.boolValue,
            options = listOf(true, false),
            optionLabel = { if (it) "Включить" else "Выключить" },
            onValueChange = { onChange(action.copy(boolValue = it)) },
        )

        AutomationBuiltinActionType.WIFI_CONNECT -> AutomationWifiSsidPicker(
            label = "Сохранённая сеть",
            ssid = action.stringValue,
            includeNone = false,
            onValueChange = { onChange(action.copy(stringValue = it)) },
        )

        AutomationBuiltinActionType.WIFI_DISCONNECT -> Text(
            text = "Снимает ассоциацию с текущей сетью, радио остаётся включённым. " +
                "Иначе ГУ сразу подключится к той же сети снова.",
            style = MaterialTheme.typography.tboxCaption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )

        AutomationBuiltinActionType.WIFI_MODEM_SET_DATA -> AutomationDropdown(
            label = "Данные Wi‑Fi модема",
            value = action.boolValue,
            options = listOf(true, false),
            optionLabel = { if (it) "Включить" else "Выключить" },
            onValueChange = { onChange(action.copy(boolValue = it)) },
        )

        AutomationBuiltinActionType.WIFI_MODEM_REBOOT -> Text(
            text = "Перезагружает текущий Wi‑Fi модем (нужен источник «Wi‑Fi HTTP»).",
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
                optionLabel = { if (it) "Включить" else "Выключить" },
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
            label = "Яркость экрана ГУ (1–10)",
            value = action.intValue,
            onValueChange = { raw ->
                // Skip empty draft; do not clamp here or the field fights typing.
                if (raw == Int.MIN_VALUE) return@AutomationIntField
                onChange(action.copy(intValue = raw))
            },
        )

        AutomationBuiltinActionType.SET_HU_SCREEN_AUTO_BRIGHTNESS -> AutomationDropdown(
            label = "Автояркость экрана ГУ",
            value = action.boolValue,
            options = listOf(true, false),
            optionLabel = { if (it) "Включить" else "Выключить" },
            onValueChange = { onChange(action.copy(boolValue = it)) },
        )

        AutomationBuiltinActionType.SET_AUTOMATION_TRIGGER_WIDGET -> Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AutomationDropdown(
                label = "Режим",
                value = builtinActionTriggerWidgetCommand(action),
                options = AutomationTriggerWidgetCommand.entries.toList(),
                optionLabel = { command ->
                    when (command) {
                        AutomationTriggerWidgetCommand.ACTIVATE -> "Активировать"
                        AutomationTriggerWidgetCommand.DEACTIVATE -> "Деактивировать"
                        AutomationTriggerWidgetCommand.TOGGLE -> "Переключить"
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
                label = "ID триггера виджета",
                modifier = Modifier.fillMaxWidth(),
            )
        }

        AutomationBuiltinActionType.SET_AUTOMATION_ENABLED -> Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AutomationRulePicker(
                label = "Автоматизация",
                selectedId = action.stringValue,
                peerAutomations = peerAutomations,
                onIdChange = { onChange(action.copy(stringValue = it)) },
            )
            AutomationDropdown(
                label = "Операция",
                value = action.boolValue,
                options = listOf(true, false),
                optionLabel = { if (it) "Включить" else "Выключить" },
                onValueChange = { onChange(action.copy(boolValue = it)) },
            )
            Text(
                text = "Без переключения: если правило уже в нужном состоянии — no-op. " +
                    "Правило может выключить само себя (текущий прогон отменяется).",
                style = MaterialTheme.typography.tboxCaption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        AutomationBuiltinActionType.SHOW_TOAST -> AutomationTextField(
            value = action.stringValue,
            onValueChange = { onChange(action.copy(stringValue = it)) },
            label = "Текст",
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
                label = "Текст",
                singleLine = false,
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            AutomationSecondsField(
                label = "Автозакрытие, с (0 — только «Закрыть»)",
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
                text = "Нужно разрешение «Поверх других окон»: Настройки → Разрешения. Само по себе не выдаётся.",
                style = MaterialTheme.typography.tboxCaption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        AutomationBuiltinActionType.CRUISE_ENGAGE_TO_TARGET -> Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CruiseModeDropdown(action, onChange)
            AutomationIntField(
                label = "Уставка, км/ч (30–150)",
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
                text = "Как виджет «Уставка круиз-контроля»: включение и доведение до цели " +
                    "(интервалы шагов — как у виджета по умолчанию). Уже на уставке → пауза (212). " +
                    "Действие ждёт окончания converge.",
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
            CruiseModeDropdown(action, onChange)
            Text(
                text = when (action.type) {
                    AutomationBuiltinActionType.CRUISE_PAUSE ->
                        "Пауза Active/Override (Cancel 212), как статус-плитка."
                    AutomationBuiltinActionType.CRUISE_FULL_OFF ->
                        "Полное выключение (210), как двойное нажатие виджета."
                    AutomationBuiltinActionType.CRUISE_ACTIVATE_AT_CURRENT_SPEED ->
                        "Активация на текущей скорости (SET−), как свайп вниз на статус-плитке в Standby; из Off — enable+SET−."
                    else ->
                        "Возобновление из Standby (RES+), как свайп вверх на статус-плитке."
                },
                style = MaterialTheme.typography.tboxCaption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        AutomationBuiltinActionType.CRUISE_NUDGE -> Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CruiseModeDropdown(action, onChange)
            AutomationDropdown(
                label = "Шаг уставки",
                value = if (action.intValue == -1) -1 else 1,
                options = listOf(1, -1),
                optionLabel = { if (it > 0) "+1 км/ч (RES+)" else "−1 км/ч (SET−)" },
                onValueChange = { onChange(action.copy(intValue = it)) },
            )
            Text(
                text = "Только в Active/Override; как свайпы статус-плитки.",
                style = MaterialTheme.typography.tboxCaption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        else -> Unit
    }
}

@Composable
private fun CruiseModeDropdown(
    action: AutomationAction.Builtin,
    onChange: (AutomationAction) -> Unit,
) {
    val modeKey = AutomationCruiseActions.parseForcedMode(action.stringValue)
        ?.let { if (it == vad.dashing.tbox.CruiseControlType.CCS) {
            AutomationCruiseActions.MODE_CCS
        } else {
            AutomationCruiseActions.MODE_ACC
        } }
        ?: AutomationCruiseActions.MODE_ACC
    AutomationDropdown(
        label = "Режим круиза",
        value = modeKey,
        options = AutomationCruiseActions.MODE_OPTIONS,
        optionLabel = AutomationCruiseActions::modeLabel,
        onValueChange = { key ->
            onChange(action.copy(stringValue = AutomationCruiseActions.encodeMode(
                when (key) {
                    AutomationCruiseActions.MODE_CCS -> vad.dashing.tbox.CruiseControlType.CCS
                    else -> vad.dashing.tbox.CruiseControlType.ACC
                },
            )))
        },
    )
}

@Composable
private fun AddAutomationActionRow(
    apps: List<LaunchableAppEntry>,
    floatingPanels: List<FloatingDashboardConfig>,
    peerAutomations: List<AutomationDefinition>,
    onAdd: (AutomationAction) -> Unit,
) {
    var kind by remember { mutableStateOf(ActionUiKind.CAN) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        AutomationDropdown(
            label = "Новое действие",
            value = kind,
            options = ActionUiKind.entries.sortedByAutomationLabel { it.label() },
            optionLabel = ActionUiKind::label,
            onValueChange = { kind = it },
            modifier = Modifier.weight(1f),
        )
        OutlinedButton(
            onClick = rememberWrappedOnClick {
                onAdd(defaultAction(kind, apps))
            },
        ) {
            AutomationButtonLabel("Добавить")
        }
    }
}

private enum class ActionUiKind {
    CAN,
    BUILTIN,
    LAUNCH_APP,
    OPEN_MAIN_SCREEN,
    HTTP,
        DELAY,
        SHOW_TOAST,
        SHOW_ALERT,
        IF_THEN_ELSE;

    fun label(): String = when (this) {
        CAN -> "CAN"
        BUILTIN -> "Действие TBox Monitor"
        LAUNCH_APP -> "Запустить приложение"
        OPEN_MAIN_SCREEN -> "Открыть страницу главного экрана"
        HTTP -> "HTTP"
        DELAY -> "Задержка"
        SHOW_TOAST -> "Toast"
        SHOW_ALERT -> "Сообщение на экране"
        IF_THEN_ELSE -> "Если — То — Иначе"
    }
}

private fun defaultAction(
    kind: ActionUiKind,
    apps: List<LaunchableAppEntry>,
): AutomationAction = when (kind) {
    ActionUiKind.CAN -> {
        val canMode = UniversalCanRepository.mode.value
        val entry = AutomationCanCatalog.pickerEntries().firstOrNull { it.supports(canMode) }
            ?: AutomationCanCatalog.pickerEntries().first()
        AutomationAction.CanCommand(
            bus = entry.bus,
            propertyId = entry.propertyId,
            operation = if (AutomationCanOperation.SET in entry.allowedOperations) {
                AutomationCanOperation.SET
            } else {
                entry.allowedOperations.first()
            },
            value = entry.defaultValueFor(canMode),
        ).let(AutomationCanValueCodec::withPortableKey)
    }

    ActionUiKind.BUILTIN ->
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
    is AutomationAction.Delay -> "Задержка"
    is AutomationAction.IfThenElse -> "Если — То — Иначе"
    is AutomationAction.CanCommand ->
        AutomationCanCatalog.get(action.bus, action.propertyId)?.label ?: "CAN"

    is AutomationAction.LaunchApplication -> "Запустить приложение"
    is AutomationAction.OpenMainScreen -> "Открыть главный экран"
    is AutomationAction.HttpRequest -> "HTTP"
    is AutomationAction.Builtin -> builtinActionLabel(action.type)
}

internal fun builtinActionLabel(type: AutomationBuiltinActionType): String = when (type) {
    AutomationBuiltinActionType.OPEN_MENU -> "Открыть меню программы"
    AutomationBuiltinActionType.FINISH_AND_START_TRIP -> "Завершить поездку и начать новую"
    AutomationBuiltinActionType.RESET_MOTOR_HOURS -> "Сбросить моточасы"
    AutomationBuiltinActionType.RESTART_TBOX -> "Перезагрузить TBox"
    AutomationBuiltinActionType.TOGGLE_APP_DAY_NIGHT_THEME -> "Переключить день/ночь"
    AutomationBuiltinActionType.ENABLE_HEAD_UNIT_AUTO_THEME -> "Включить автоматическую тему ГУ"
    AutomationBuiltinActionType.SET_HU_DAY_NIGHT_THEME -> "Тема день/ночь ГУ (светлая/тёмная/авто)"
    AutomationBuiltinActionType.TOGGLE_MIRROR_ADJUST_MODE -> "Переключить регулировку зеркал"
    AutomationBuiltinActionType.TOGGLE_HIDE_FLOATING_PANELS -> "Плавающие панели — видимость"
    AutomationBuiltinActionType.TOGGLE_FLOATING_PANELS_ENABLED -> "Плавающие панели — включение"
    AutomationBuiltinActionType.ESP_RELAY_SET -> "Установить маску ESP-реле"
    AutomationBuiltinActionType.ESP_RELAY_TOGGLE -> "Переключить ESP-реле"
    AutomationBuiltinActionType.ESP_RELAY_PULSE -> "Импульс ESP-реле"
    AutomationBuiltinActionType.MEDIA_PREVIOUS -> "Предыдущий трек"
    AutomationBuiltinActionType.MEDIA_PLAY_PAUSE -> "Воспроизведение/пауза"
    AutomationBuiltinActionType.MEDIA_PLAY -> "Воспроизведение"
    AutomationBuiltinActionType.MEDIA_NEXT -> "Следующий трек"
    AutomationBuiltinActionType.MEDIA_TOGGLE_LIKE -> "Поставить/снять «Нравится»"
    AutomationBuiltinActionType.SET_MEDIA_VOLUME -> "Установить громкость медиа"
    AutomationBuiltinActionType.SET_PHONE_VOLUME -> "Установить громкость телефона"
    AutomationBuiltinActionType.SET_NAVI_VOLUME -> "Установить громкость навигатора"
    AutomationBuiltinActionType.SET_VOICE_VOLUME -> "Установить громкость голоса"
    AutomationBuiltinActionType.SET_HEADREST_SPEAKER -> "Динамик подголовника"
    AutomationBuiltinActionType.CYCLE_MOCK_LOCATION_MODE -> "Следующий режим подмены геопозиции"
    AutomationBuiltinActionType.GNSS_MODULE_REBOOT -> "Перезапустить GNSS-модуль"
    AutomationBuiltinActionType.SET_SIMULATED_LOCATION_SOURCE_LOSS ->
        "Симулировать потерю геоисточника"
    AutomationBuiltinActionType.SET_GEO_DEBUG_LOG -> "Запись гео-журнала"
    AutomationBuiltinActionType.WIFI_SET_ENABLED -> "Wi-Fi: включить / выключить"
    AutomationBuiltinActionType.WIFI_CONNECT -> "Wi-Fi: подключиться к сети"
    AutomationBuiltinActionType.WIFI_DISCONNECT -> "Wi-Fi: отключиться от сети"
    AutomationBuiltinActionType.WIFI_MODEM_SET_DATA -> "Wi‑Fi модем: данные вкл/выкл"
    AutomationBuiltinActionType.WIFI_MODEM_REBOOT -> "Wi‑Fi модем: перезагрузка"
    AutomationBuiltinActionType.ADB_SET_TCP -> "ADB TCP: включить / выключить"
    AutomationBuiltinActionType.ADB_SHELL -> "ADB: выполнить shell-команду"
    AutomationBuiltinActionType.ADB_FORCE_STOP -> "ADB: остановить приложение"
    AutomationBuiltinActionType.SET_HU_SCREEN_BRIGHTNESS -> "Яркость экрана ГУ"
    AutomationBuiltinActionType.SET_HU_SCREEN_AUTO_BRIGHTNESS -> "Автояркость экрана ГУ"
    AutomationBuiltinActionType.SHOW_TOAST -> "Toast"
    AutomationBuiltinActionType.SHOW_ALERT -> "Сообщение на экране"
    AutomationBuiltinActionType.SET_AUTOMATION_TRIGGER_WIDGET -> "Триггер автоматизации (виджет)"
    AutomationBuiltinActionType.SET_AUTOMATION_ENABLED -> "Включить / выключить автоматизацию"
    AutomationBuiltinActionType.CRUISE_ENGAGE_TO_TARGET ->
        "Круиз: включить и довести до уставки"
    AutomationBuiltinActionType.CRUISE_PAUSE -> "Круиз: пауза (Cancel)"
    AutomationBuiltinActionType.CRUISE_FULL_OFF -> "Круиз: полностью выключить"
    AutomationBuiltinActionType.CRUISE_RESUME -> "Круиз: возобновить (RES+)"
    AutomationBuiltinActionType.CRUISE_ACTIVATE_AT_CURRENT_SPEED ->
        "Круиз: активировать на текущей скорости (SET−)"
    AutomationBuiltinActionType.CRUISE_NUDGE -> "Круиз: уставка ±1"
    AutomationBuiltinActionType.START_VAD_VOICE -> "VAD Voice: слушать"
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
        label = "Операция",
        value = action.floatingPanelVisibilityOp(),
        options = AutomationFloatingPanelVisibilityOp.entries.toList(),
        optionLabel = ::floatingPanelVisibilityOpLabel,
        onValueChange = { op ->
            onChange(action.copy(intValue = floatingPanelVisibilityOpToInt(op)))
        },
    )
    Text(
        text = "Скрытие и показ временные: настройку «Показывать плавающую панель» не меняют. " +
            "После перезапуска приложения или службы панели снова появятся, если для них " +
            "включено «Показывать плавающую панель» и нет других ограничений (правила " +
            "скрытия по приложениям, разрешение «Поверх других окон»).",
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
        label = "Операция",
        value = action.floatingPanelEnabledOp(),
        options = AutomationFloatingPanelEnabledOp.entries.toList(),
        optionLabel = ::floatingPanelEnabledOpLabel,
        onValueChange = { op ->
            onChange(action.copy(intValue = floatingPanelEnabledOpToInt(op)))
        },
    )
    Text(
        text = "Включение и выключение сохраняются в настройках (переключатель «Показывать " +
            "плавающую панель») и действуют после перезагрузки. При выполнении сбрасывается " +
            "временное скрытие всех панелей.",
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
        label = "Панели",
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
                text = "Нет плавающих панелей. Создайте панель в настройках.",
                style = MaterialTheme.typography.tboxCaption,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            AutomationDropdown(
                label = "Панель",
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
