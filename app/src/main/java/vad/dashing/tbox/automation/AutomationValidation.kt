package vad.dashing.tbox.automation

import vad.dashing.tbox.SettingsManager

data class AutomationValidationIssue(
    val path: String,
    val message: String,
)

object AutomationValidator {
    private const val MAX_CONDITION_DEPTH = 8
    private const val MAX_ACTION_DEPTH = 8
    private const val MAX_ACTION_COUNT = 200
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
        if (actionCounter[0] > MAX_ACTION_COUNT) {
            issues += AutomationValidationIssue(
                "$path.actions",
                "Слишком много действий: максимум $MAX_ACTION_COUNT",
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
        if (depth > MAX_CONDITION_DEPTH) {
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
        if (depth > MAX_ACTION_DEPTH) {
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
                }
            }

            is AutomationAction.Builtin -> validateBuiltin(action, path, issues)
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
            -> if (action.intValue !in 0..31) {
                issues += AutomationValidationIssue("$path.intValue", "Недопустимый канал реле")
            }

            AutomationBuiltinActionType.ESP_RELAY_SET -> if (action.intValue < 0) {
                issues += AutomationValidationIssue("$path.intValue", "Маска реле не может быть отрицательной")
            }

            AutomationBuiltinActionType.SET_MEDIA_VOLUME -> if (action.intValue !in 0..31) {
                issues += AutomationValidationIssue("$path.intValue", "Громкость должна быть 0–31")
            }

            else -> Unit
        }
    }
}
