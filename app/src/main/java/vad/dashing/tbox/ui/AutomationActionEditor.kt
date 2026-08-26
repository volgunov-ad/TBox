package vad.dashing.tbox.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import vad.dashing.tbox.AppLauncherLaunchMode
import vad.dashing.tbox.DEFAULT_HTTP_REQUEST_WIDGET_YAML
import vad.dashing.tbox.automation.AutomationAction
import vad.dashing.tbox.automation.AutomationBuiltinActionType
import vad.dashing.tbox.automation.AutomationCanCatalog
import vad.dashing.tbox.automation.AutomationCanCatalogEntry
import vad.dashing.tbox.automation.AutomationCanOperation
import vad.dashing.tbox.automation.AutomationCondition
import vad.dashing.tbox.automation.AutomationMainScreenTarget
import vad.dashing.tbox.freeform.FreeformLaunchBounds
import vad.dashing.tbox.freeform.FreeformLaunchSide
import vad.dashing.tbox.mbcan.MbCanCommandPolicy

@Composable
internal fun AutomationActionListEditor(
    actions: List<AutomationAction>,
    triggerIds: List<String>,
    apps: List<LaunchableAppEntry>,
    pageCount: Int,
    onChange: (List<AutomationAction>) -> Unit,
    modifier: Modifier = Modifier,
    depth: Int = 0,
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
                pageCount = pageCount,
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
        if (depth < 7) {
            AddAutomationActionRow(
                apps = apps,
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
    pageCount: Int,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onChange: (AutomationAction) -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    depth: Int,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Действие ${index + 1}: ${actionTitle(action)}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = onMoveUp, enabled = canMoveUp) { Text("↑") }
                OutlinedButton(onClick = onMoveDown, enabled = canMoveDown) { Text("↓") }
                OutlinedButton(onClick = onDelete) { Text("Удалить") }
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
                    pageCount = pageCount,
                    onChange = onChange,
                    depth = depth,
                )

                is AutomationAction.CanCommand -> CanCommandFields(action, onChange)
                is AutomationAction.LaunchApplication -> LaunchApplicationFields(
                    action,
                    apps,
                    pageCount,
                    onChange,
                )

                is AutomationAction.OpenMainScreen -> OpenMainScreenFields(
                    action,
                    pageCount,
                    onChange,
                )

                is AutomationAction.HttpRequest -> HttpRequestFields(action, onChange)
                is AutomationAction.Builtin -> BuiltinActionFields(action, apps, onChange)
            }
        }
    }
}

@Composable
private fun IfThenElseFields(
    action: AutomationAction.IfThenElse,
    triggerIds: List<String>,
    apps: List<LaunchableAppEntry>,
    pageCount: Int,
    onChange: (AutomationAction) -> Unit,
    depth: Int,
) {
    Text("Если", style = MaterialTheme.typography.titleSmall)
    AutomationConditionEditor(
        condition = action.condition,
        triggerIds = triggerIds,
        onChange = { onChange(action.copy(condition = it)) },
        modifier = Modifier.padding(start = 12.dp),
    )
    Text("То", style = MaterialTheme.typography.titleSmall)
    AutomationActionListEditor(
        actions = action.thenActions,
        triggerIds = triggerIds,
        apps = apps,
        pageCount = pageCount,
        onChange = { onChange(action.copy(thenActions = it)) },
        modifier = Modifier.padding(start = 12.dp),
        depth = depth + 1,
    )
    Text("Иначе", style = MaterialTheme.typography.titleSmall)
    AutomationActionListEditor(
        actions = action.elseActions,
        triggerIds = triggerIds,
        apps = apps,
        pageCount = pageCount,
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
    val entry = AutomationCanCatalog.get(action.bus, action.propertyId)
        ?: AutomationCanCatalog.entries.first()
    AutomationDropdown(
        label = "CAN-действие",
        value = entry,
        options = AutomationCanCatalog.entries,
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
                    value = selected.defaultValue,
                ),
            )
        },
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AutomationDropdown(
            label = "Операция",
            value = action.operation.takeIf { it in entry.allowedOperations }
                ?: entry.allowedOperations.first(),
            options = entry.allowedOperations.toList(),
            optionLabel = {
                when (it) {
                    AutomationCanOperation.SET -> "Установить"
                    AutomationCanOperation.TOGGLE -> "Переключить"
                    AutomationCanOperation.TRUNK_PULSE -> "Импульс открыть/закрыть"
                }
            },
            onValueChange = { onChange(action.copy(operation = it)) },
            modifier = Modifier.weight(1f),
        )
        if (action.operation != AutomationCanOperation.TOGGLE) {
            val values = if (
                action.operation == AutomationCanOperation.TRUNK_PULSE
            ) {
                listOf(1, 2)
            } else {
                entry.allowedValues
            }
            if (values.isNotEmpty()) {
                val selected = action.value.takeIf { it in values } ?: values.first()
                AutomationDropdown(
                    label = "Значение",
                    value = selected,
                    options = values,
                    optionLabel = { canValueLabel(entry, it) },
                    onValueChange = { onChange(action.copy(value = it)) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
    if (entry.safety.name != "NONE") {
        Text(
            text = "Встроенная защита: действие выполняется только при скорости 0 и режиме P.",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun LaunchApplicationFields(
    action: AutomationAction.LaunchApplication,
    apps: List<LaunchableAppEntry>,
    pageCount: Int,
    onChange: (AutomationAction) -> Unit,
) {
    val packages = buildList {
        action.packageName.takeIf { it.isNotBlank() }?.let(::add)
        apps.mapTo(this) { it.packageName }
    }.distinct()
    if (packages.isEmpty()) {
        OutlinedTextField(
            value = action.packageName,
            onValueChange = { onChange(action.copy(packageName = it)) },
            label = { Text("Пакет приложения") },
            modifier = Modifier.fillMaxWidth(),
        )
    } else {
        val selected = action.packageName.takeIf { it in packages } ?: packages.first()
        AutomationDropdown(
            label = "Приложение",
            value = selected,
            options = packages,
            optionLabel = { pkg ->
                apps.firstOrNull { it.packageName == pkg }?.label?.let { "$it ($pkg)" } ?: pkg
            },
            onValueChange = { onChange(action.copy(packageName = it)) },
        )
    }
    AutomationDropdown(
        label = "Режим запуска",
        value = action.launchMode,
        options = AppLauncherLaunchMode.entries,
        optionLabel = {
            when (it) {
                AppLauncherLaunchMode.FULLSCREEN -> "Обычный полноэкранный"
                AppLauncherLaunchMode.FREEFORM -> "Freeform"
                AppLauncherLaunchMode.STOCK_WINDOW -> "Окно штатного лаунчера"
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
    OutlinedTextField(
        value = action.yaml,
        onValueChange = { onChange(action.copy(yaml = it)) },
        label = { Text("YAML запроса") },
        minLines = 3,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun BuiltinActionFields(
    action: AutomationAction.Builtin,
    apps: List<LaunchableAppEntry>,
    onChange: (AutomationAction) -> Unit,
) {
    AutomationDropdown(
        label = "Действие приложения",
        value = action.type,
        options = AutomationBuiltinActionType.entries,
        optionLabel = ::builtinActionLabel,
        onValueChange = { onChange(AutomationAction.Builtin(type = it)) },
    )
    when (action.type) {
        AutomationBuiltinActionType.ESP_RELAY_SET -> AutomationIntField(
            label = "Маска реле",
            value = action.intValue,
            onValueChange = { onChange(action.copy(intValue = it)) },
            modifier = Modifier.fillMaxWidth(),
        )

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
            OutlinedTextField(
                value = action.stringValue,
                onValueChange = { onChange(action.copy(stringValue = it)) },
                label = { Text("Длительность, мс (пусто = стандарт)") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }

        AutomationBuiltinActionType.MEDIA_PREVIOUS,
        AutomationBuiltinActionType.MEDIA_PLAY_PAUSE,
        AutomationBuiltinActionType.MEDIA_PLAY,
        AutomationBuiltinActionType.MEDIA_NEXT,
        AutomationBuiltinActionType.MEDIA_TOGGLE_LIKE,
        -> {
            val packages = buildList {
                action.stringValue.takeIf { it.isNotBlank() }?.let(::add)
                apps.mapTo(this) { it.packageName }
            }.distinct()
            if (packages.isNotEmpty()) {
                val selected = action.stringValue.takeIf { it in packages } ?: packages.first()
                AutomationDropdown(
                    label = "Медиаплеер",
                    value = selected,
                    options = packages,
                    optionLabel = { pkg ->
                        apps.firstOrNull { it.packageName == pkg }?.label ?: pkg
                    },
                    onValueChange = { onChange(action.copy(stringValue = it)) },
                )
            }
        }

        AutomationBuiltinActionType.SET_MEDIA_VOLUME -> AutomationIntField(
            label = "Громкость",
            value = action.intValue,
            onValueChange = { onChange(action.copy(intValue = it)) },
            modifier = Modifier.fillMaxWidth(),
        )

        AutomationBuiltinActionType.SET_GEO_DEBUG_LOG -> AutomationDropdown(
            label = "Запись",
            value = action.boolValue,
            options = listOf(true, false),
            optionLabel = { if (it) "Запустить" else "Остановить" },
            onValueChange = { onChange(action.copy(boolValue = it)) },
        )

        else -> Unit
    }
}

@Composable
private fun AddAutomationActionRow(
    apps: List<LaunchableAppEntry>,
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
            options = ActionUiKind.entries,
            optionLabel = ActionUiKind::label,
            onValueChange = { kind = it },
            modifier = Modifier.weight(1f),
        )
        OutlinedButton(
            onClick = { onAdd(defaultAction(kind, apps)) },
        ) {
            Text("Добавить")
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
    IF_THEN_ELSE;

    fun label(): String = when (this) {
        CAN -> "CAN"
        BUILTIN -> "Действие TBox Monitor"
        LAUNCH_APP -> "Запустить приложение"
        OPEN_MAIN_SCREEN -> "Открыть страницу главного экрана"
        HTTP -> "HTTP"
        DELAY -> "Задержка"
        IF_THEN_ELSE -> "Если — То — Иначе"
    }
}

private fun defaultAction(
    kind: ActionUiKind,
    apps: List<LaunchableAppEntry>,
): AutomationAction = when (kind) {
    ActionUiKind.CAN -> AutomationCanCatalog.entries.first().let { entry ->
        AutomationAction.CanCommand(
            bus = entry.bus,
            propertyId = entry.propertyId,
            operation = if (AutomationCanOperation.SET in entry.allowedOperations) {
                AutomationCanOperation.SET
            } else {
                entry.allowedOperations.first()
            },
            value = entry.defaultValue,
        )
    }

    ActionUiKind.BUILTIN ->
        AutomationAction.Builtin(AutomationBuiltinActionType.OPEN_MENU)

    ActionUiKind.LAUNCH_APP -> AutomationAction.LaunchApplication(
        packageName = apps.firstOrNull()?.packageName.orEmpty(),
    )

    ActionUiKind.OPEN_MAIN_SCREEN -> AutomationAction.OpenMainScreen(page = 1)
    ActionUiKind.HTTP -> AutomationAction.HttpRequest(DEFAULT_HTTP_REQUEST_WIDGET_YAML)
    ActionUiKind.DELAY -> AutomationAction.Delay(0L)
    ActionUiKind.IF_THEN_ELSE -> AutomationAction.IfThenElse(
        condition = defaultNumericCondition(),
        thenActions = listOf(AutomationAction.Delay(0L)),
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

private fun canValueLabel(entry: AutomationCanCatalogEntry, value: Int): String {
    val policy = entry.policy
    if (policy is MbCanCommandPolicy.ToggleBinary) {
        return when (value) {
            policy.offValue -> "Выключить ($value)"
            policy.onValue -> "Включить ($value)"
            else -> value.toString()
        }
    }
    if (entry.propertyId == vad.dashing.tbox.mbcan.MbCanKnownVehiclePropertyId.TRUNK_PLG_CONTROL) {
        return when (value) {
            1 -> "Открыть"
            2 -> "Закрыть"
            else -> value.toString()
        }
    }
    return value.toString()
}

internal fun builtinActionLabel(type: AutomationBuiltinActionType): String = when (type) {
    AutomationBuiltinActionType.OPEN_MENU -> "Открыть меню программы"
    AutomationBuiltinActionType.FINISH_AND_START_TRIP -> "Завершить поездку и начать новую"
    AutomationBuiltinActionType.RESET_MOTOR_HOURS -> "Сбросить моточасы"
    AutomationBuiltinActionType.RESTART_TBOX -> "Перезагрузить TBox"
    AutomationBuiltinActionType.TOGGLE_APP_DAY_NIGHT_THEME -> "Переключить день/ночь"
    AutomationBuiltinActionType.ENABLE_HEAD_UNIT_AUTO_THEME -> "Включить автоматическую тему ГУ"
    AutomationBuiltinActionType.TOGGLE_MIRROR_ADJUST_MODE -> "Переключить регулировку зеркал"
    AutomationBuiltinActionType.TOGGLE_HIDE_FLOATING_PANELS -> "Скрыть/показать плавающие панели"
    AutomationBuiltinActionType.TOGGLE_FLOATING_PANELS_ENABLED -> "Включить/отключить плавающие панели"
    AutomationBuiltinActionType.ESP_RELAY_SET -> "Установить маску ESP-реле"
    AutomationBuiltinActionType.ESP_RELAY_TOGGLE -> "Переключить ESP-реле"
    AutomationBuiltinActionType.ESP_RELAY_PULSE -> "Импульс ESP-реле"
    AutomationBuiltinActionType.MEDIA_PREVIOUS -> "Предыдущий трек"
    AutomationBuiltinActionType.MEDIA_PLAY_PAUSE -> "Воспроизведение/пауза"
    AutomationBuiltinActionType.MEDIA_PLAY -> "Воспроизведение"
    AutomationBuiltinActionType.MEDIA_NEXT -> "Следующий трек"
    AutomationBuiltinActionType.MEDIA_TOGGLE_LIKE -> "Поставить/снять «Нравится»"
    AutomationBuiltinActionType.SET_MEDIA_VOLUME -> "Установить громкость медиа"
    AutomationBuiltinActionType.CYCLE_MOCK_LOCATION_MODE -> "Следующий режим подмены геопозиции"
    AutomationBuiltinActionType.SET_GEO_DEBUG_LOG -> "Запись гео-журнала"
}

private fun <T> List<T>.moved(from: Int, to: Int): List<T> {
    if (from !in indices || to !in indices || from == to) return this
    return toMutableList().also { list ->
        val item = list.removeAt(from)
        list.add(to, item)
    }
}
