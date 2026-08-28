package vad.dashing.tbox.automation

import vad.dashing.tbox.SettingsManager
import vad.dashing.tbox.browserUrlFromHttpRequestYaml
import vad.dashing.tbox.parseHttpRequestWidgetYaml

data class AutomationValidationIssue(
    val path: String,
    val message: String,
)

object AutomationValidator {
    private const val MAX_PARALLEL_RUNS = 10

    fun validate(document: AutomationDocument): List<AutomationValidationIssue> {
        val issues = mutableListOf<AutomationValidationIssue>()
        if (document.formatVersion != AUTOMATION_FORMAT_VERSION) {
            issues += AutomationValidationIssue(
                "formatVersion",
                "Неподдерживаемая версия формата",
            )
        }
        val duplicateIds = document.automations.groupingBy { it.id }.eachCount()
            .filterValues { it > 1 }
            .keys
        duplicateIds.forEach {
            issues += AutomationValidationIssue("automations", "Повторяется id автоматизации: $it")
        }
        document.automations.forEachIndexed { index, definition ->
            validateDefinition(definition, "automations[$index]", issues)
        }
        return issues
    }

    fun validate(definition: AutomationDefinition): List<AutomationValidationIssue> {
        val issues = mutableListOf<AutomationValidationIssue>()
        validateDefinition(definition, "automation", issues)
        return issues
    }

    private fun validateDefinition(
        definition: AutomationDefinition,
        path: String,
        issues: MutableList<AutomationValidationIssue>,
    ) {
        if (definition.id.isBlank()) {
            issues += AutomationValidationIssue("$path.id", "Отсутствует id")
        }
        if (definition.name.isBlank()) {
            issues += AutomationValidationIssue("$path.name", "Укажите название")
        }
        if (definition.triggers.isEmpty()) {
            issues += AutomationValidationIssue("$path.triggers", "Добавьте хотя бы один триггер")
        }
        if (definition.actions.isEmpty()) {
            issues += AutomationValidationIssue("$path.actions", "Добавьте хотя бы одно действие")
        }
        val triggerIds = definition.triggers.map { it.id }
        if (triggerIds.any { it.isBlank() }) {
            issues += AutomationValidationIssue("$path.triggers", "У каждого триггера должен быть id")
        }
        if (triggerIds.toSet().size != triggerIds.size) {
            issues += AutomationValidationIssue("$path.triggers", "ID триггеров должны быть уникальны")
        }
        definition.triggers.forEachIndexed { index, trigger ->
            validateTrigger(trigger, "$path.triggers[$index]", issues)
        }
        definition.conditions.forEachIndexed { index, condition ->
            validateCondition(
                condition = condition,
                triggerIds = triggerIds.toSet(),
                path = "$path.conditions[$index]",
                depth = 0,
                issues = issues,
            )
        }
        if (definition.conditionWaitMillis !in 0..AUTOMATION_MAX_CONDITION_WAIT_MS) {
            issues += AutomationValidationIssue(
                "$path.conditionWaitMillis",
                "Недопустимое время ожидания условия",
            )
        }
        val actionCounter = intArrayOf(0)
        definition.actions.forEachIndexed { index, action ->
            validateAction(
                action = action,
                triggerIds = triggerIds.toSet(),
                path = "$path.actions[$index]",
                depth = 0,
                actionCounter = actionCounter,
                issues = issues,
            )
        }
        if (actionCounter[0] > AUTOMATION_MAX_ACTION_COUNT) {
            issues += AutomationValidationIssue(
                "$path.actions",
                "Слишком много действий: максимум $AUTOMATION_MAX_ACTION_COUNT",
            )
        }
        val maxRunsAllowed = if (definition.runMode == AutomationRunMode.SINGLE) 1 else MAX_PARALLEL_RUNS
        if (definition.maxRuns !in 1..maxRunsAllowed) {
            issues += AutomationValidationIssue(
                "$path.maxRuns",
                "Недопустимое число одновременных запусков",
            )
        }
    }

    private fun validateTrigger(
        trigger: AutomationTrigger,
        path: String,
        issues: MutableList<AutomationValidationIssue>,
    ) {
        when (trigger) {
            is AutomationTrigger.SystemEvent -> Unit
            is AutomationTrigger.NumericThreshold -> {
                validateSignal(
                    trigger.signal,
                    trigger.source,
                    AutomationSignalValueType.NUMBER,
                    path,
                    issues,
                )
                if (!trigger.threshold.isFinite()) {
                    issues += AutomationValidationIssue("$path.threshold", "Порог должен быть числом")
                }
                trigger.resetThreshold?.let { reset ->
                    if (!reset.isFinite()) {
                        issues += AutomationValidationIssue(
                            "$path.resetThreshold",
                            "Порог возврата должен быть числом",
                        )
                    } else {
                        val valid = when (trigger.direction) {
                            AutomationThresholdDirection.ABOVE -> reset <= trigger.threshold
                            AutomationThresholdDirection.BELOW -> reset >= trigger.threshold
                        }
                        if (!valid) {
                            issues += AutomationValidationIssue(
                                "$path.resetThreshold",
                                "Порог возврата расположен с неверной стороны основного порога",
                            )
                        }
                    }
                }
                validateHold(trigger.holdMillis, "$path.holdMillis", issues)
            }

            is AutomationTrigger.StateEquals -> {
                validateSignal(
                    trigger.signal,
                    trigger.source,
                    AutomationSignalValueType.STATE,
                    path,
                    issues,
                )
                if (trigger.expectedState.isBlank()) {
                    issues += AutomationValidationIssue(
                        "$path.expectedState",
                        "Укажите ожидаемое состояние",
                    )
                } else {
                    validateStateValue(
                        trigger.signal,
                        trigger.expectedState,
                        "$path.expectedState",
                        issues,
                    )
                }
                validateHold(trigger.holdMillis, "$path.holdMillis", issues)
            }

            is AutomationTrigger.Geofence -> {
                if (!trigger.latitude.isFinite() || trigger.latitude !in -90.0..90.0) {
                    issues += AutomationValidationIssue(
                        "$path.latitude",
                        "Вставьте распознаваемую точку (координаты или ссылку)",
                    )
                }
                if (!trigger.longitude.isFinite() || trigger.longitude !in -180.0..180.0) {
                    issues += AutomationValidationIssue(
                        "$path.longitude",
                        "Вставьте распознаваемую точку (координаты или ссылку)",
                    )
                }
                val zone = trigger.zoneRadiusMeters
                val rearm = trigger.rearmRadiusMeters
                if (!zone.isFinite() || zone < 0.0 || zone > AUTOMATION_GEOFENCE_MAX_RADIUS_M) {
                    issues += AutomationValidationIssue(
                        "$path.zoneRadiusMeters",
                        "Радиус зоны должен быть от 0 до ${AUTOMATION_GEOFENCE_MAX_RADIUS_M.toInt()} м",
                    )
                }
                if (!rearm.isFinite() || rearm < 0.0 || rearm > AUTOMATION_GEOFENCE_MAX_RADIUS_M) {
                    issues += AutomationValidationIssue(
                        "$path.rearmRadiusMeters",
                        "Радиус взведения должен быть от 0 до ${AUTOMATION_GEOFENCE_MAX_RADIUS_M.toInt()} м",
                    )
                } else if (zone.isFinite() && zone >= 0.0) {
                    val valid = when (trigger.direction) {
                        AutomationGeofenceDirection.ENTER -> rearm > zone
                        AutomationGeofenceDirection.EXIT -> rearm < zone
                    }
                    if (!valid) {
                        issues += AutomationValidationIssue(
                            "$path.rearmRadiusMeters",
                            when (trigger.direction) {
                                AutomationGeofenceDirection.ENTER ->
                                    "Радиус взведения должен быть больше радиуса зоны"
                                AutomationGeofenceDirection.EXIT ->
                                    "Радиус взведения должен быть меньше радиуса зоны"
                            },
                        )
                    }
                }
                if (trigger.direction == AutomationGeofenceDirection.EXIT &&
                    zone.isFinite() &&
                    zone <= 0.0
                ) {
                    issues += AutomationValidationIssue(
                        "$path.zoneRadiusMeters",
                        "Для «выехал» радиус зоны должен быть больше 0",
                    )
                }
                validateHold(trigger.holdMillis, "$path.holdMillis", issues)
            }
        }
    }

    private fun validateHold(
        holdMillis: Long,
        path: String,
        issues: MutableList<AutomationValidationIssue>,
    ) {
        if (holdMillis !in 0..AUTOMATION_MAX_HOLD_MS) {
            issues += AutomationValidationIssue(path, "Недопустимая выдержка")
        }
    }

    private fun validateSignal(
        signal: AutomationSignalId,
        source: AutomationSignalSource,
        expectedType: AutomationSignalValueType,
        path: String,
        issues: MutableList<AutomationValidationIssue>,
    ) {
        if (signal.valueType != expectedType) {
            issues += AutomationValidationIssue(path, "Тип сигнала не подходит")
        }
        if (!AutomationSignalCatalog.supports(signal, source)) {
            issues += AutomationValidationIssue(path, "Выбранный источник не поддерживает сигнал")
        }
    }

    private fun validateCondition(
        condition: AutomationCondition,
        triggerIds: Set<String>,
        path: String,
        depth: Int,
        issues: MutableList<AutomationValidationIssue>,
    ) {
        if (depth > AUTOMATION_MAX_CONDITION_DEPTH) {
            issues += AutomationValidationIssue(path, "Слишком глубокая вложенность условий")
            return
        }
        when (condition) {
            AutomationCondition.Always -> Unit
            is AutomationCondition.Numeric -> {
                validateSignal(
                    condition.signal,
                    condition.source,
                    AutomationSignalValueType.NUMBER,
                    path,
                    issues,
                )
                if (!condition.expectedValue.isFinite()) {
                    issues += AutomationValidationIssue("$path.expectedValue", "Ожидается число")
                }
            }

            is AutomationCondition.State -> {
                validateSignal(
                    condition.signal,
                    condition.source,
                    AutomationSignalValueType.STATE,
                    path,
                    issues,
                )
                if (condition.expectedState.isBlank()) {
                    issues += AutomationValidationIssue(
                        "$path.expectedState",
                        "Укажите ожидаемое состояние",
                    )
                } else {
                    validateStateValue(
                        condition.signal,
                        condition.expectedState,
                        "$path.expectedState",
                        issues,
                    )
                }
            }

            is AutomationCondition.TriggeredBy -> {
                if (condition.triggerIds.isEmpty() || !triggerIds.containsAll(condition.triggerIds)) {
                    issues += AutomationValidationIssue(
                        "$path.triggerIds",
                        "Условие ссылается на неизвестный триггер",
                    )
                }
            }

            is AutomationCondition.All -> condition.conditions.forEachIndexed { index, nested ->
                validateCondition(nested, triggerIds, "$path.conditions[$index]", depth + 1, issues)
            }

            is AutomationCondition.Any -> condition.conditions.forEachIndexed { index, nested ->
                validateCondition(nested, triggerIds, "$path.conditions[$index]", depth + 1, issues)
            }

            is AutomationCondition.Not ->
                validateCondition(condition.condition, triggerIds, "$path.condition", depth + 1, issues)
        }
    }

    private fun validateAction(
        action: AutomationAction,
        triggerIds: Set<String>,
        path: String,
        depth: Int,
        actionCounter: IntArray,
        issues: MutableList<AutomationValidationIssue>,
    ) {
        actionCounter[0] += 1
        if (depth > AUTOMATION_MAX_ACTION_DEPTH) {
            issues += AutomationValidationIssue(path, "Слишком глубокая вложенность действий")
            return
        }
        when (action) {
            is AutomationAction.Delay -> {
                if (action.durationMillis !in 0..AUTOMATION_MAX_DELAY_MS) {
                    issues += AutomationValidationIssue(path, "Недопустимая задержка")
                }
            }

            is AutomationAction.IfThenElse -> {
                validateCondition(action.condition, triggerIds, "$path.condition", 0, issues)
                if (action.thenActions.isEmpty()) {
                    issues += AutomationValidationIssue("$path.thenActions", "Ветка «То» пуста")
                }
                action.thenActions.forEachIndexed { index, nested ->
                    validateAction(
                        nested,
                        triggerIds,
                        "$path.thenActions[$index]",
                        depth + 1,
                        actionCounter,
                        issues,
                    )
                }
                action.elseActions.forEachIndexed { index, nested ->
                    validateAction(
                        nested,
                        triggerIds,
                        "$path.elseActions[$index]",
                        depth + 1,
                        actionCounter,
                        issues,
                    )
                }
            }

            is AutomationAction.CanCommand -> {
                if (!AutomationCanCatalog.isAllowed(action)) {
                    issues += AutomationValidationIssue(path, "CAN-команда отсутствует в безопасном каталоге")
                }
            }

            is AutomationAction.LaunchApplication -> {
                if (action.packageName.isBlank()) {
                    issues += AutomationValidationIssue("$path.packageName", "Выберите приложение")
                }
            }

            is AutomationAction.OpenMainScreen -> {
                if (action.page !in
                    SettingsManager.MIN_MAIN_SCREEN_PAGE_COUNT..SettingsManager.MAX_MAIN_SCREEN_PAGE_COUNT
                ) {
                    issues += AutomationValidationIssue("$path.page", "Недопустимый номер страницы")
                }
            }

            is AutomationAction.HttpRequest -> {
                if (action.yaml.isBlank()) {
                    issues += AutomationValidationIssue("$path.yaml", "Настройка HTTP пуста")
                } else {
                    val parsed = if (action.openBrowser) {
                        browserUrlFromHttpRequestYaml(action.yaml)
                    } else {
                        parseHttpRequestWidgetYaml(action.yaml)
                    }
                    parsed.exceptionOrNull()?.let {
                        issues += AutomationValidationIssue(
                            "$path.yaml",
                            it.message ?: "Некорректная настройка HTTP",
                        )
                    }
                }
            }

            is AutomationAction.Builtin -> validateBuiltin(action, path, issues)
        }
    }

    private fun validateStateValue(
        signal: AutomationSignalId,
        value: String,
        path: String,
        issues: MutableList<AutomationValidationIssue>,
    ) {
        val options = AutomationSignalCatalog.get(signal).stateOptions
        if (
            options.isNotEmpty() &&
            options.none { it.equals(value.trim(), ignoreCase = true) }
        ) {
            issues += AutomationValidationIssue(path, "Состояние отсутствует в каталоге")
        }
    }

    private fun validateBuiltin(
        action: AutomationAction.Builtin,
        path: String,
        issues: MutableList<AutomationValidationIssue>,
    ) {
        when (action.type) {
            AutomationBuiltinActionType.ESP_RELAY_TOGGLE,
            AutomationBuiltinActionType.ESP_RELAY_PULSE,
            -> if (action.intValue !in 0..7) {
                issues += AutomationValidationIssue("$path.intValue", "Недопустимый канал реле")
            }

            AutomationBuiltinActionType.ESP_RELAY_SET ->
                issues += AutomationValidationIssue(
                    path,
                    "Действие «установить маску ESP-реле» больше не поддерживается",
                )

            AutomationBuiltinActionType.SET_MEDIA_VOLUME -> if (action.intValue !in 0..31) {
                issues += AutomationValidationIssue("$path.intValue", "Громкость должна быть 0–31")
            }

            else -> Unit
        }
        if (action.type == AutomationBuiltinActionType.ESP_RELAY_PULSE) {
            val duration = action.stringValue.trim()
            val parsedDuration = duration.toLongOrNull()
            if (
                duration.isNotEmpty() &&
                (parsedDuration == null || parsedDuration !in 1L..60_000L)
            ) {
                issues += AutomationValidationIssue(
                    "$path.stringValue",
                    "Длительность импульса должна быть 1–60000 мс",
                )
            }
        }
        if (
            action.type in MEDIA_PACKAGE_ACTIONS &&
            action.stringValue.isBlank()
        ) {
            issues += AutomationValidationIssue(
                "$path.stringValue",
                "Выберите медиаплеер",
            )
        }
        if (action.type in USER_MESSAGE_ACTIONS) {
            val text = action.stringValue.trim()
            if (text.isEmpty()) {
                issues += AutomationValidationIssue("$path.stringValue", "Введите текст сообщения")
            } else if (text.length > AUTOMATION_MAX_USER_MESSAGE_CHARS) {
                issues += AutomationValidationIssue(
                    "$path.stringValue",
                    "Текст длиннее $AUTOMATION_MAX_USER_MESSAGE_CHARS символов",
                )
            }
        }
        if (action.type == AutomationBuiltinActionType.SHOW_ALERT) {
            if (action.intValue.toLong() !in 0L..AUTOMATION_MAX_DELAY_MS) {
                issues += AutomationValidationIssue(
                    "$path.intValue",
                    "Автозакрытие должно быть 0–${AUTOMATION_MAX_DELAY_MS / 1_000L} с",
                )
            }
        }
    }

    private val MEDIA_PACKAGE_ACTIONS = setOf(
        AutomationBuiltinActionType.MEDIA_PREVIOUS,
        AutomationBuiltinActionType.MEDIA_PLAY_PAUSE,
        AutomationBuiltinActionType.MEDIA_PLAY,
        AutomationBuiltinActionType.MEDIA_NEXT,
        AutomationBuiltinActionType.MEDIA_TOGGLE_LIKE,
    )

    private val USER_MESSAGE_ACTIONS = setOf(
        AutomationBuiltinActionType.SHOW_TOAST,
        AutomationBuiltinActionType.SHOW_ALERT,
    )
}
