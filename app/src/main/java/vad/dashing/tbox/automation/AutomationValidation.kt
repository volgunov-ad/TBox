package vad.dashing.tbox.automation

import vad.dashing.tbox.ruEn

import vad.dashing.tbox.AUTOMATION_TRIGGER_ID_MAX_CHARS
import vad.dashing.tbox.AppLauncherLaunchMode
import vad.dashing.tbox.CAMD_ACTION_CLOSE
import vad.dashing.tbox.CAMD_ACTION_CLOSE_ALL
import vad.dashing.tbox.CAMD_ACTION_OPEN
import vad.dashing.tbox.CAMD_WINDOW_CAMERA_IDS
import vad.dashing.tbox.camdPresetNameIssue
import vad.dashing.tbox.camdWindowCropIssue
import vad.dashing.tbox.camdWindowGeometryIssue
import vad.dashing.tbox.parseCamdAutomationWindowArgs
import vad.dashing.tbox.SettingsManager
import vad.dashing.tbox.browserUrlFromHttpRequestYaml
import vad.dashing.tbox.esp.EspBleRelayChannel
import vad.dashing.tbox.esp.EspBleRelayIds
import vad.dashing.tbox.mbcan.UniversalCanRepository
import vad.dashing.tbox.parseHttpRequestWidgetYaml

data class AutomationValidationIssue(
    val path: String,
    val message: String,
)

object AutomationValidator {
    private const val MAX_PARALLEL_RUNS = 10

    fun validate(document: AutomationDocument): List<AutomationValidationIssue> {
        val issues = integrityIssues(document).toMutableList()
        document.automations.forEachIndexed { index, definition ->
            validateDefinition(definition, "automations[$index]", issues)
        }
        return issues
    }

    /** JSON / format problems that block the whole document. Per-rule issues are not here. */
    fun integrityIssues(document: AutomationDocument): List<AutomationValidationIssue> {
        val issues = mutableListOf<AutomationValidationIssue>()
        if (document.formatVersion != AUTOMATION_FORMAT_VERSION) {
            issues += AutomationValidationIssue(
                "formatVersion",
                ruEn("Неподдерживаемая версия формата", "Unsupported format version"),
            )
        }
        val duplicateIds = document.automations.groupingBy { it.id }.eachCount()
            .filterValues { it > 1 }
            .keys
        duplicateIds.forEach {
            issues += AutomationValidationIssue("automations", ruEn("Повторяется id автоматизации: $it", "Duplicate automation id: $it"))
        }
        return issues
    }

    fun validate(definition: AutomationDefinition): List<AutomationValidationIssue> {
        val issues = mutableListOf<AutomationValidationIssue>()
        validateDefinition(definition, "automation", issues)
        return issues
    }

    /** Ad-hoc action lists (External API invoke) have no triggers to reference. */
    fun validateActions(actions: List<AutomationAction>): List<AutomationValidationIssue> {
        val issues = mutableListOf<AutomationValidationIssue>()
        val actionCounter = IntArray(1)
        actions.forEachIndexed { index, action ->
            validateAction(action, emptySet(), "actions[$index]", 0, actionCounter, issues)
        }
        return issues
    }

    fun isRunnable(definition: AutomationDefinition): Boolean =
        definition.enabled && validate(definition).isEmpty()

    /**
     * Turns off rules that cannot run, so one stale command (e.g. window 5 %)
     * does not block the rest of the document.
     */
    fun withInvalidDisabled(
        document: AutomationDocument,
    ): Pair<AutomationDocument, List<String>> {
        val disabledIds = mutableListOf<String>()
        val automations = document.automations.map { definition ->
            if (definition.enabled && validate(definition).isNotEmpty()) {
                disabledIds += definition.id
                definition.copy(enabled = false)
            } else {
                definition
            }
        }
        return document.copy(automations = automations) to disabledIds
    }

    private fun validateDefinition(
        definition: AutomationDefinition,
        path: String,
        issues: MutableList<AutomationValidationIssue>,
    ) {
        if (definition.id.isBlank()) {
            issues += AutomationValidationIssue("$path.id", ruEn("Отсутствует id", "Missing id"))
        }
        if (definition.name.isBlank()) {
            issues += AutomationValidationIssue("$path.name", ruEn("Укажите название", "Enter a name"))
        }
        if (definition.triggers.isEmpty()) {
            issues += AutomationValidationIssue("$path.triggers", ruEn("Добавьте хотя бы один триггер", "Add at least one trigger"))
        }
        if (definition.actions.isEmpty()) {
            issues += AutomationValidationIssue("$path.actions", ruEn("Добавьте хотя бы одно действие", "Add at least one action"))
        }
        val triggerIds = definition.triggers.map { it.id }
        if (triggerIds.any { it.isBlank() }) {
            issues += AutomationValidationIssue("$path.triggers", ruEn("У каждого триггера должен быть id", "Every trigger must have an id"))
        }
        if (triggerIds.toSet().size != triggerIds.size) {
            issues += AutomationValidationIssue("$path.triggers", ruEn("ID триггеров должны быть уникальны", "Trigger IDs must be unique"))
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
                ruEn("Недопустимое время ожидания условия", "Invalid condition wait time"),
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
                ruEn("Слишком много действий: максимум $AUTOMATION_MAX_ACTION_COUNT", "Too many actions: maximum $AUTOMATION_MAX_ACTION_COUNT"),
            )
        }
        val maxRunsAllowed = if (definition.runMode == AutomationRunMode.SINGLE) 1 else MAX_PARALLEL_RUNS
        if (definition.maxRuns !in 1..maxRunsAllowed) {
            issues += AutomationValidationIssue(
                "$path.maxRuns",
                ruEn("Недопустимое число одновременных запусков", "Invalid number of concurrent runs"),
            )
        }
    }

    private fun validateTrigger(
        trigger: AutomationTrigger,
        path: String,
        issues: MutableList<AutomationValidationIssue>,
    ) {
        when (trigger) {
            is AutomationTrigger.SystemEvent ->
                validateHold(trigger.holdMillis, "$path.holdMillis", issues)
            is AutomationTrigger.WidgetPressed -> {
                val widgetTriggerId = trigger.triggerId.trim()
                if (widgetTriggerId.isEmpty()) {
                    issues += AutomationValidationIssue("$path.triggerId", ruEn("Введите ID триггера", "Enter a trigger ID"))
                } else if (widgetTriggerId.length > AUTOMATION_TRIGGER_ID_MAX_CHARS) {
                    issues += AutomationValidationIssue(
                        "$path.triggerId",
                        ruEn("ID триггера длиннее $AUTOMATION_TRIGGER_ID_MAX_CHARS символов", "Trigger ID is longer than $AUTOMATION_TRIGGER_ID_MAX_CHARS characters"),
                    )
                }
            }
            is AutomationTrigger.HardKey -> {
                if (trigger.keyCode !in AUTOMATION_HARD_KEY_MIN_CODE..AUTOMATION_HARD_KEY_MAX_CODE) {
                    issues += AutomationValidationIssue(
                        "$path.keyCode",
                        ruEn("Код кнопки должен быть от $AUTOMATION_HARD_KEY_MIN_CODE ", "Button code must be from $AUTOMATION_HARD_KEY_MIN_CODE ") +
                            ruEn("до $AUTOMATION_HARD_KEY_MAX_CODE", "to $AUTOMATION_HARD_KEY_MAX_CODE"),
                    )
                }
            }
            is AutomationTrigger.EspBleBtn -> {
                if (trigger.btn !in AUTOMATION_ESP_BLE_BTN_MIN..AUTOMATION_ESP_BLE_BTN_MAX) {
                    issues += AutomationValidationIssue(
                        "$path.btn",
                        ruEn("Номер кнопки должен быть от $AUTOMATION_ESP_BLE_BTN_MIN ", "Button number must be from $AUTOMATION_ESP_BLE_BTN_MIN ") +
                            ruEn("до $AUTOMATION_ESP_BLE_BTN_MAX", "to $AUTOMATION_ESP_BLE_BTN_MAX"),
                    )
                }
                if (trigger.mac.isBlank()) {
                    issues += AutomationValidationIssue(
                        "$path.mac",
                        ruEn("Выберите устройство Shelly Blu (MAC)", "Choose a Shelly Blu device (MAC)"),
                    )
                }
            }
            is AutomationTrigger.EspGpioBtn -> {
                if (trigger.channel !in 0..3) {
                    issues += AutomationValidationIssue(
                        "$path.channel",
                        ruEn("ESP-вход должен быть 0…3", "ESP input must be 0…3"),
                    )
                }
            }
            is AutomationTrigger.Interval -> {
                if (trigger.intervalMillis !in AUTOMATION_MIN_INTERVAL_MS..AUTOMATION_MAX_INTERVAL_MS) {
                    issues += AutomationValidationIssue(
                        "$path.intervalMillis",
                        ruEn("Период должен быть от ${AUTOMATION_MIN_INTERVAL_MS / 1_000L} с ", "The period must be from ${AUTOMATION_MIN_INTERVAL_MS / 1_000L} s ") +
                            ruEn("до ${AUTOMATION_MAX_INTERVAL_MS / 3_600_000L} ч", "to ${AUTOMATION_MAX_INTERVAL_MS / 3_600_000L} h"),
                    )
                } else if (trigger.intervalMillis % 1_000L != 0L) {
                    issues += AutomationValidationIssue(
                        "$path.intervalMillis",
                        ruEn("Период должен быть целым числом секунд", "The period must be a whole number of seconds"),
                    )
                }
            }

            is AutomationTrigger.NumericThreshold -> {
                validateSignal(
                    trigger.signal,
                    trigger.source,
                    AutomationSignalValueType.NUMBER,
                    path,
                    issues,
                )
                validateEspBleBatteryMac(trigger.signal, trigger.mac, "$path.mac", issues)
                if (!trigger.threshold.isFinite()) {
                    issues += AutomationValidationIssue("$path.threshold", ruEn("Порог должен быть числом", "The threshold must be a number"))
                }
                if (trigger.rearmEnabled) {
                    trigger.resetThreshold?.let { reset ->
                        if (!reset.isFinite()) {
                            issues += AutomationValidationIssue(
                                "$path.resetThreshold",
                                ruEn("Порог возврата должен быть числом", "The return threshold must be a number"),
                            )
                        } else {
                            val valid = when (trigger.direction) {
                                AutomationThresholdDirection.ABOVE -> reset <= trigger.threshold
                                AutomationThresholdDirection.BELOW -> reset >= trigger.threshold
                            }
                            if (!valid) {
                                issues += AutomationValidationIssue(
                                    "$path.resetThreshold",
                                    ruEn("Порог возврата расположен с неверной стороны основного порога", "The return threshold is on the wrong side of the main threshold"),
                                )
                            }
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
                        ruEn("Укажите ожидаемое состояние", "Enter the expected state"),
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
                validateBleRelaySignal(trigger.signal, trigger.relayId, trigger.relayChannel, path, issues)
            }

            is AutomationTrigger.Geofence -> {
                if (!trigger.latitude.isFinite() || trigger.latitude !in -90.0..90.0) {
                    issues += AutomationValidationIssue(
                        "$path.latitude",
                        ruEn("Вставьте распознаваемую точку (координаты или ссылку)", "Paste a recognizable point (coordinates or a link)"),
                    )
                }
                if (!trigger.longitude.isFinite() || trigger.longitude !in -180.0..180.0) {
                    issues += AutomationValidationIssue(
                        "$path.longitude",
                        ruEn("Вставьте распознаваемую точку (координаты или ссылку)", "Paste a recognizable point (coordinates or a link)"),
                    )
                }
                val zone = trigger.zoneRadiusMeters
                val rearm = trigger.rearmRadiusMeters
                if (!zone.isFinite() || zone < 0.0 || zone > AUTOMATION_GEOFENCE_MAX_RADIUS_M) {
                    issues += AutomationValidationIssue(
                        "$path.zoneRadiusMeters",
                        ruEn("Радиус зоны должен быть от 0 до ${AUTOMATION_GEOFENCE_MAX_RADIUS_M.toInt()} м", "The zone radius must be from 0 to ${AUTOMATION_GEOFENCE_MAX_RADIUS_M.toInt()} m"),
                    )
                }
                if (!rearm.isFinite() || rearm < 0.0 || rearm > AUTOMATION_GEOFENCE_MAX_RADIUS_M) {
                    issues += AutomationValidationIssue(
                        "$path.rearmRadiusMeters",
                        ruEn("Радиус взведения должен быть от 0 до ${AUTOMATION_GEOFENCE_MAX_RADIUS_M.toInt()} м", "The rearm radius must be from 0 to ${AUTOMATION_GEOFENCE_MAX_RADIUS_M.toInt()} m"),
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
                                    ruEn("Радиус взведения должен быть больше радиуса зоны", "The rearm radius must be greater than the zone radius")
                                AutomationGeofenceDirection.EXIT ->
                                    ruEn("Радиус взведения должен быть меньше радиуса зоны", "The rearm radius must be less than the zone radius")
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
                        ruEn("Для «выехал» радиус зоны должен быть больше 0", "For “left”, the zone radius must be greater than 0"),
                    )
                }
                validateHold(trigger.holdMillis, "$path.holdMillis", issues)
            }

            is AutomationTrigger.Time -> {
                if (!trigger.at.isValid()) {
                    issues += AutomationValidationIssue("$path.at", ruEn("Укажите время ЧЧ:ММ", "Enter the time as HH:MM"))
                }
            }

            is AutomationTrigger.Solar -> {
                if (trigger.offsetMinutes !in 0..AUTOMATION_SOLAR_MAX_OFFSET_MINUTES) {
                    issues += AutomationValidationIssue(
                        "$path.offsetMinutes",
                        ruEn("Смещение должно быть 0–$AUTOMATION_SOLAR_MAX_OFFSET_MINUTES мин", "The offset must be 0–$AUTOMATION_SOLAR_MAX_OFFSET_MINUTES min"),
                    )
                }
            }
        }
    }

    private fun validateHold(
        holdMillis: Long,
        path: String,
        issues: MutableList<AutomationValidationIssue>,
    ) {
        if (holdMillis !in 0..AUTOMATION_MAX_HOLD_MS) {
            issues += AutomationValidationIssue(path, ruEn("Недопустимая выдержка", "Invalid hold"))
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
            issues += AutomationValidationIssue(path, ruEn("Тип сигнала не подходит", "The signal type does not match"))
        }
        if (!AutomationSignalCatalog.supports(signal, source)) {
            issues += AutomationValidationIssue(path, ruEn("Выбранный источник не поддерживает сигнал", "The selected source does not support this signal"))
        }
    }

    private fun validateEspBleBatteryMac(
        signal: AutomationSignalId,
        mac: String,
        path: String,
        issues: MutableList<AutomationValidationIssue>,
    ) {
        if (signal != AutomationSignalId.ESP_BLE_BATTERY) return
        if (mac.isBlank()) {
            issues += AutomationValidationIssue(
                path,
                ruEn("Выберите устройство Shelly Blu (MAC) для сигнала батареи", "Choose a Shelly Blu device (MAC) for the battery signal"),
            )
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
            issues += AutomationValidationIssue(path, ruEn("Слишком глубокая вложенность условий", "Condition nesting is too deep"))
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
                validateEspBleBatteryMac(condition.signal, condition.mac, "$path.mac", issues)
                if (!condition.expectedValue.isFinite()) {
                    issues += AutomationValidationIssue("$path.expectedValue", ruEn("Ожидается число", "A number is expected"))
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
                        ruEn("Укажите ожидаемое состояние", "Enter the expected state"),
                    )
                } else {
                    validateStateValue(
                        condition.signal,
                        condition.expectedState,
                        "$path.expectedState",
                        issues,
                    )
                }
                validateBleRelaySignal(condition.signal, condition.relayId, condition.relayChannel, path, issues)
            }

            is AutomationCondition.TriggeredBy -> {
                if (condition.triggerIds.isEmpty() || !triggerIds.containsAll(condition.triggerIds)) {
                    issues += AutomationValidationIssue(
                        "$path.triggerIds",
                        ruEn("Условие ссылается на неизвестный триггер", "The condition refers to an unknown trigger"),
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

            is AutomationCondition.Time -> {
                val afterValid = condition.after == null || condition.after.isValid()
                val beforeValid = condition.before == null || condition.before.isValid()
                if (!afterValid) {
                    issues += AutomationValidationIssue("$path.after", ruEn("Укажите время «после» как ЧЧ:ММ", "Enter the “after” time as HH:MM"))
                }
                if (!beforeValid) {
                    issues += AutomationValidationIssue("$path.before", ruEn("Укажите время «до» как ЧЧ:ММ", "Enter the “before” time as HH:MM"))
                }
                if (
                    afterValid &&
                    beforeValid &&
                    condition.after == null &&
                    condition.before == null &&
                    condition.weekdays.isEmpty()
                ) {
                    issues += AutomationValidationIssue(
                        path,
                        ruEn("Задайте окно времени или дни недели", "Set a time window or days of the week"),
                    )
                }
            }

            is AutomationCondition.Solar -> {
                val afterValid = condition.after == null || condition.after.isValid()
                val beforeValid = condition.before == null || condition.before.isValid()
                if (!afterValid) {
                    issues += AutomationValidationIssue(
                        "$path.after",
                        ruEn("Смещение «после» должно быть 0–$AUTOMATION_SOLAR_MAX_OFFSET_MINUTES мин", "The “after” offset must be 0–$AUTOMATION_SOLAR_MAX_OFFSET_MINUTES min"),
                    )
                }
                if (!beforeValid) {
                    issues += AutomationValidationIssue(
                        "$path.before",
                        ruEn("Смещение «до» должно быть 0–$AUTOMATION_SOLAR_MAX_OFFSET_MINUTES мин", "The “before” offset must be 0–$AUTOMATION_SOLAR_MAX_OFFSET_MINUTES min"),
                    )
                }
                if (
                    afterValid &&
                    beforeValid &&
                    condition.after == null &&
                    condition.before == null &&
                    condition.weekdays.isEmpty()
                ) {
                    issues += AutomationValidationIssue(
                        path,
                        ruEn("Задайте окно восход/закат или дни недели", "Set a sunrise/sunset window or days of the week"),
                    )
                }
            }

            is AutomationCondition.Geofence -> {
                if (!condition.latitude.isFinite() || condition.latitude !in -90.0..90.0) {
                    issues += AutomationValidationIssue(
                        "$path.latitude",
                        ruEn("Вставьте распознаваемую точку (координаты или ссылку)", "Paste a recognizable point (coordinates or a link)"),
                    )
                }
                if (!condition.longitude.isFinite() || condition.longitude !in -180.0..180.0) {
                    issues += AutomationValidationIssue(
                        "$path.longitude",
                        ruEn("Вставьте распознаваемую точку (координаты или ссылку)", "Paste a recognizable point (coordinates or a link)"),
                    )
                }
                val zone = condition.zoneRadiusMeters
                if (!zone.isFinite() || zone < 0.0 || zone > AUTOMATION_GEOFENCE_MAX_RADIUS_M) {
                    issues += AutomationValidationIssue(
                        "$path.zoneRadiusMeters",
                        ruEn("Радиус зоны должен быть от 0 до ${AUTOMATION_GEOFENCE_MAX_RADIUS_M.toInt()} м", "The zone radius must be from 0 to ${AUTOMATION_GEOFENCE_MAX_RADIUS_M.toInt()} m"),
                    )
                }
                if (condition.presence == AutomationGeofencePresence.OUTSIDE &&
                    zone.isFinite() &&
                    zone <= 0.0
                ) {
                    issues += AutomationValidationIssue(
                        "$path.zoneRadiusMeters",
                        ruEn("Для «снаружи» радиус зоны должен быть больше 0", "For “outside”, the zone radius must be greater than 0"),
                    )
                }
            }

            is AutomationCondition.UiState -> Unit

            is AutomationCondition.TriggerWidget -> {
                val triggerId = condition.triggerId.trim()
                if (triggerId.isEmpty()) {
                    issues += AutomationValidationIssue(
                        "$path.triggerId",
                        ruEn("Укажите ID триггера виджета", "Enter the widget trigger ID"),
                    )
                } else if (triggerId.length > AUTOMATION_TRIGGER_ID_MAX_CHARS) {
                    issues += AutomationValidationIssue(
                        "$path.triggerId",
                        ruEn("ID триггера — не более $AUTOMATION_TRIGGER_ID_MAX_CHARS символов", "Trigger ID — at most $AUTOMATION_TRIGGER_ID_MAX_CHARS characters"),
                    )
                }
            }

            is AutomationCondition.AdbTcp -> Unit
            is AutomationCondition.AutomationEnabled -> {
                if (condition.automationId.trim().isEmpty()) {
                    issues += AutomationValidationIssue(
                        "$path.automationId",
                        ruEn("Укажите ID автоматизации", "Enter the automation ID"),
                    )
                }
            }
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
            issues += AutomationValidationIssue(path, ruEn("Слишком глубокая вложенность действий", "Action nesting is too deep"))
            return
        }
        when (action) {
            is AutomationAction.Delay -> {
                if (action.durationMillis !in 0..AUTOMATION_MAX_DELAY_MS) {
                    issues += AutomationValidationIssue(path, ruEn("Недопустимая задержка", "Invalid delay"))
                }
            }

            is AutomationAction.IfThenElse -> {
                validateCondition(action.condition, triggerIds, "$path.condition", 0, issues)
                if (action.thenActions.isEmpty()) {
                    issues += AutomationValidationIssue("$path.thenActions", ruEn("Ветка «То» пуста", "The “Then” branch is empty"))
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
                    issues += AutomationValidationIssue(path, ruEn("CAN-команда отсутствует в безопасном каталоге", "CAN command is not in the safe catalog"))
                } else {
                    val canMode = UniversalCanRepository.mode.value
                    val entry = AutomationCanCatalog.get(action.bus, action.propertyId)
                    if (entry != null && !entry.supports(canMode)) {
                        issues += AutomationValidationIssue(
                            path,
                            ruEn("CAN-действие не подтверждено для текущего backend ГУ", "CAN action is not confirmed for the current HU backend"),
                        )
                    } else if (!AutomationCanValueCodec.isResolvable(action, canMode)) {
                        issues += AutomationValidationIssue(
                            path,
                            ruEn("Значение CAN недоступно на текущем backend ГУ", "CAN value is unavailable on the current HU backend"),
                        )
                    }
                }
            }

            is AutomationAction.LaunchApplication -> {
                if (action.packageName.isBlank()) {
                    issues += AutomationValidationIssue("$path.packageName", ruEn("Выберите приложение", "Choose an app"))
                }
                if (action.launchMode == AppLauncherLaunchMode.VIRTUAL_DISPLAY) {
                    val displayId = action.virtualDisplayId
                    if (displayId == null || displayId <= 0) {
                        issues += AutomationValidationIssue(
                            "$path.virtualDisplayId",
                            ruEn("Выберите виртуальный дисплей", "Choose a virtual display"),
                        )
                    }
                }
            }

            is AutomationAction.OpenMainScreen -> {
                if (action.page !in
                    SettingsManager.MIN_MAIN_SCREEN_PAGE_COUNT..SettingsManager.MAX_MAIN_SCREEN_PAGE_COUNT
                ) {
                    issues += AutomationValidationIssue("$path.page", ruEn("Недопустимый номер страницы", "Invalid page number"))
                }
            }

            is AutomationAction.HttpRequest -> {
                if (action.yaml.isBlank()) {
                    issues += AutomationValidationIssue("$path.yaml", ruEn("Настройка HTTP пуста", "The HTTP setting is empty"))
                } else {
                    val parsed = if (action.openBrowser) {
                        browserUrlFromHttpRequestYaml(action.yaml)
                    } else {
                        parseHttpRequestWidgetYaml(action.yaml)
                    }
                    parsed.exceptionOrNull()?.let {
                        issues += AutomationValidationIssue(
                            "$path.yaml",
                            it.message ?: ruEn("Некорректная настройка HTTP", "Invalid HTTP setting"),
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
            issues += AutomationValidationIssue(path, ruEn("Состояние отсутствует в каталоге", "State is not in the catalog"))
        }
    }

    private fun validateBleRelaySignal(
        signal: AutomationSignalId,
        relayId: String,
        relayChannel: Int,
        path: String,
        issues: MutableList<AutomationValidationIssue>,
    ) {
        if (!isBleRelaySignal(signal)) return
        if (relayId.isNotBlank() && EspBleRelayIds.normalize(relayId).isEmpty()) {
            issues += AutomationValidationIssue(
                "$path.relayId",
                ruEn("Id BLE-реле — 8 hex-символов", "BLE relay id is 8 hex characters"),
            )
        }
        if (!EspBleRelayChannel.inRange(relayChannel)) {
            issues += AutomationValidationIssue(
                "$path.relayChannel",
                ruEn("Канал BLE-реле должен быть 0–7", "BLE relay channel must be 0–7"),
            )
        }
    }

    private fun validateBleRelayAction(
        action: AutomationAction.Builtin,
        path: String,
        issues: MutableList<AutomationValidationIssue>,
    ) {
        val id = EspBleRelayActionEncoding.idOf(action)
        if (id.isEmpty() && action.intValue !in 0..1) {
            issues += AutomationValidationIssue(
                "$path.intValue",
                ruEn("Недопустимый индекс BLE-реле (0 или 1)", "Invalid BLE relay index (0 or 1)"),
            )
        }
        val raw = action.stringValue.trim()
        if (raw.isNotEmpty() && id.isEmpty() && EspBleRelayActionEncoding.pulseText(action).toLongOrNull() == null &&
            EspBleRelayActionEncoding.channelField(action) == null
        ) {
            issues += AutomationValidationIssue(
                "$path.stringValue",
                ruEn("Id BLE-реле — 8 hex-символов", "BLE relay id is 8 hex characters"),
            )
        }
        val channel = EspBleRelayActionEncoding.channelField(action)
        if (channel != null && !EspBleRelayChannel.inRange(channel)) {
            issues += AutomationValidationIssue(
                "$path.stringValue",
                ruEn("Канал BLE-реле должен быть 0–7", "BLE relay channel must be 0–7"),
            )
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
                issues += AutomationValidationIssue("$path.intValue", ruEn("Недопустимый канал реле", "Invalid relay channel"))
            }

            AutomationBuiltinActionType.ESP_BLE_RELAY_TOGGLE,
            AutomationBuiltinActionType.ESP_BLE_RELAY_PULSE,
            AutomationBuiltinActionType.ESP_BLE_RELAY_SET,
            -> validateBleRelayAction(action, path, issues)

            AutomationBuiltinActionType.ESP_RELAY_SET ->
                issues += AutomationValidationIssue(
                    path,
                    ruEn("Действие «установить маску ESP-реле» больше не поддерживается", "The “set ESP relay mask” action is no longer supported"),
                )

            AutomationBuiltinActionType.SET_MEDIA_VOLUME -> if (action.intValue !in 0..31) {
                issues += AutomationValidationIssue("$path.intValue", ruEn("Громкость должна быть 0–31", "Volume must be 0–31"))
            }

            AutomationBuiltinActionType.SET_PHONE_VOLUME -> if (action.intValue !in 1..31) {
                issues += AutomationValidationIssue(
                    "$path.intValue",
                    ruEn("Громкость телефона должна быть 1–31", "Phone volume must be 1–31"),
                )
            }

            AutomationBuiltinActionType.SET_NAVI_VOLUME -> if (action.intValue !in 0..10) {
                issues += AutomationValidationIssue(
                    "$path.intValue",
                    ruEn("Громкость навигатора должна быть 0–10", "Navigation volume must be 0–10"),
                )
            }

            AutomationBuiltinActionType.SET_VOICE_VOLUME -> if (action.intValue !in 2..10) {
                issues += AutomationValidationIssue(
                    "$path.intValue",
                    ruEn("Громкость голоса должна быть 2–10", "Voice volume must be 2–10"),
                )
            }

            AutomationBuiltinActionType.SET_HU_DAY_NIGHT_THEME -> {
                val key = action.stringValue.trim().lowercase()
                if (key !in setOf("light", "dark", "auto")) {
                    issues += AutomationValidationIssue(
                        "$path.stringValue",
                        ruEn("Тема: light / dark / auto", "Theme: light / dark / auto"),
                    )
                }
            }

            AutomationBuiltinActionType.SET_HEADREST_SPEAKER -> {
                val key = action.stringValue.trim().lowercase()
                if (key !in setOf("only", "assist", "off")) {
                    issues += AutomationValidationIssue(
                        "$path.stringValue",
                        ruEn("Подголовник: only / assist / off", "Headrest: only / assist / off"),
                    )
                }
            }

            AutomationBuiltinActionType.SET_HU_SCREEN_BRIGHTNESS -> if (action.intValue !in 1..10) {
                issues += AutomationValidationIssue(
                    "$path.intValue",
                    ruEn("Яркость экрана ГУ должна быть 1–10", "HU screen brightness must be 1–10"),
                )
            }

            AutomationBuiltinActionType.CRUISE_ENGAGE_TO_TARGET -> {
                // Mode prefix optional (legacy acc/ccs still accepted); runtime uses global type.
                val modePart = action.stringValue.trim().lowercase()
                    .substringBefore(':').substringBefore(',')
                if (
                    modePart.isNotEmpty() &&
                    AutomationCruiseActions.parseForcedMode(action.stringValue) == null
                ) {
                    issues += AutomationValidationIssue(
                        "$path.stringValue",
                        ruEn("Круиз: режим ACC или CCS (acc / ccs), либо пусто (глобальный тип)", "Cruise: ACC or CCS mode (acc / ccs), or empty (global type)"),
                    )
                }
                val target = action.intValue
                if (
                    target != 0 &&
                    target !in vad.dashing.tbox.ACC_CRUISE_TARGET_KMH_MIN..
                        vad.dashing.tbox.ACC_CRUISE_TARGET_KMH_MAX
                ) {
                    issues += AutomationValidationIssue(
                        "$path.intValue",
                        ruEn("Уставка круиза должна быть ", "The cruise setpoint must be ") +
                            "${vad.dashing.tbox.ACC_CRUISE_TARGET_KMH_MIN}–" +
                            ruEn("${vad.dashing.tbox.ACC_CRUISE_TARGET_KMH_MAX} км/ч", "${vad.dashing.tbox.ACC_CRUISE_TARGET_KMH_MAX} km/h"),
                    )
                }
                val parts = action.stringValue.trim().lowercase().split(':', ',')
                parts.getOrNull(1)?.toIntOrNull()?.let { increase ->
                    if (
                        increase !in vad.dashing.tbox.ACC_CRUISE_STEP_INTERVAL_MS_MIN..
                            vad.dashing.tbox.ACC_CRUISE_STEP_INTERVAL_MS_MAX
                    ) {
                        issues += AutomationValidationIssue(
                            "$path.stringValue",
                            ruEn("Интервал +1 должен быть ", "The +1 interval must be ") +
                                "${vad.dashing.tbox.ACC_CRUISE_STEP_INTERVAL_MS_MIN}–" +
                                ruEn("${vad.dashing.tbox.ACC_CRUISE_STEP_INTERVAL_MS_MAX} мс", "${vad.dashing.tbox.ACC_CRUISE_STEP_INTERVAL_MS_MAX} ms"),
                        )
                    }
                }
                parts.getOrNull(2)?.toIntOrNull()?.let { decrease ->
                    if (
                        decrease !in vad.dashing.tbox.ACC_CRUISE_STEP_INTERVAL_MS_MIN..
                            vad.dashing.tbox.ACC_CRUISE_STEP_INTERVAL_MS_MAX
                    ) {
                        issues += AutomationValidationIssue(
                            "$path.stringValue",
                            ruEn("Интервал −1 должен быть ", "The −1 interval must be ") +
                                "${vad.dashing.tbox.ACC_CRUISE_STEP_INTERVAL_MS_MIN}–" +
                                ruEn("${vad.dashing.tbox.ACC_CRUISE_STEP_INTERVAL_MS_MAX} мс", "${vad.dashing.tbox.ACC_CRUISE_STEP_INTERVAL_MS_MAX} ms"),
                        )
                    }
                }
            }

            AutomationBuiltinActionType.CRUISE_PAUSE,
            AutomationBuiltinActionType.CRUISE_FULL_OFF,
            AutomationBuiltinActionType.CRUISE_RESUME,
            AutomationBuiltinActionType.CRUISE_ACTIVATE_AT_CURRENT_SPEED,
            -> {
                val modePart = action.stringValue.trim().lowercase()
                    .substringBefore(':').substringBefore(',')
                if (
                    modePart.isNotEmpty() &&
                    AutomationCruiseActions.parseForcedMode(action.stringValue) == null
                ) {
                    issues += AutomationValidationIssue(
                        "$path.stringValue",
                        ruEn("Круиз: режим ACC или CCS (acc / ccs), либо пусто (глобальный тип)", "Cruise: ACC or CCS mode (acc / ccs), or empty (global type)"),
                    )
                }
            }

            AutomationBuiltinActionType.CRUISE_NUDGE -> {
                val modePart = action.stringValue.trim().lowercase()
                    .substringBefore(':').substringBefore(',')
                if (
                    modePart.isNotEmpty() &&
                    AutomationCruiseActions.parseForcedMode(action.stringValue) == null
                ) {
                    issues += AutomationValidationIssue(
                        "$path.stringValue",
                        ruEn("Круиз: режим ACC или CCS (acc / ccs), либо пусто (глобальный тип)", "Cruise: ACC or CCS mode (acc / ccs), or empty (global type)"),
                    )
                }
                if (action.intValue != 1 && action.intValue != -1) {
                    issues += AutomationValidationIssue(
                        "$path.intValue",
                        ruEn("Шаг уставки круиза: +1 или −1", "Cruise setpoint step: +1 or −1"),
                    )
                }
            }

            AutomationBuiltinActionType.WIFI_CONNECT -> {
                if (WifiStaSsid.normalize(action.stringValue) == null) {
                    issues += AutomationValidationIssue(
                        "$path.stringValue",
                        ruEn("Выберите сохранённую сеть Wi-Fi", "Choose a saved Wi-Fi network"),
                    )
                }
            }

            AutomationBuiltinActionType.ADB_SHELL -> {
                val command = action.stringValue.trim()
                if (command.isEmpty()) {
                    issues += AutomationValidationIssue(
                        "$path.stringValue",
                        ruEn("Введите shell-команду", "Enter a shell command"),
                    )
                } else if (command.length > vad.dashing.tbox.adb.AdbAutomationActions.MAX_SHELL_COMMAND_CHARS) {
                    issues += AutomationValidationIssue(
                        "$path.stringValue",
                        ruEn("Команда длиннее ${vad.dashing.tbox.adb.AdbAutomationActions.MAX_SHELL_COMMAND_CHARS} символов", "Command is longer than ${vad.dashing.tbox.adb.AdbAutomationActions.MAX_SHELL_COMMAND_CHARS} characters"),
                    )
                }
            }

            AutomationBuiltinActionType.ADB_FORCE_STOP -> {
                if (action.stringValue.trim().isEmpty()) {
                    issues += AutomationValidationIssue(
                        "$path.stringValue",
                        ruEn("Выберите приложение", "Choose an app"),
                    )
                }
            }

            AutomationBuiltinActionType.CAMD_WINDOW -> {
                val args = parseCamdAutomationWindowArgs(action.stringValue)
                if (args == null) {
                    issues += AutomationValidationIssue(
                        "$path.stringValue",
                        ruEn("Окно камеры: open, close или close_all", "Camera window: open, close, or close_all"),
                    )
                } else if (args.mode != CAMD_ACTION_CLOSE_ALL &&
                    action.intValue !in CAMD_WINDOW_CAMERA_IDS
                ) {
                    issues += AutomationValidationIssue(
                        "$path.intValue",
                        ruEn("Камера: 4, 5, 8, 9, 0, 1 или 3", "Camera: 4, 5, 8, 9, 0, 1, or 3"),
                    )
                } else if (args.mode == CAMD_ACTION_OPEN) {
                    val geometry = camdWindowGeometryIssue(args.x, args.y, args.w, args.h)
                    if (geometry != null) {
                        issues += AutomationValidationIssue("$path.stringValue", geometry)
                    }
                    val preset = args.presetName
                    if (preset != null) {
                        val presetIssue = camdPresetNameIssue(preset)
                        if (presetIssue != null) {
                            issues += AutomationValidationIssue("$path.stringValue", presetIssue)
                        }
                    }
                    val crop = args.crop
                    if (crop != null) {
                        val cropIssue = camdWindowCropIssue(crop.x, crop.y, crop.w, crop.h)
                        if (cropIssue != null) {
                            issues += AutomationValidationIssue("$path.stringValue", cropIssue)
                        }
                    }
                } else if (args.mode != CAMD_ACTION_CLOSE && args.mode != CAMD_ACTION_CLOSE_ALL) {
                    issues += AutomationValidationIssue(
                        "$path.stringValue",
                        ruEn("Окно камеры: open, close или close_all", "Camera window: open, close, or close_all"),
                    )
                }
            }

            AutomationBuiltinActionType.PLACE_CALL -> {
                if (vad.dashing.tbox.normalizeDialPhoneNumber(action.stringValue).isEmpty()) {
                    issues += AutomationValidationIssue(
                        "$path.stringValue",
                        ruEn("Укажите номер телефона", "Enter a phone number"),
                    )
                }
            }

            AutomationBuiltinActionType.TOGGLE_HIDE_FLOATING_PANELS,
            AutomationBuiltinActionType.TOGGLE_FLOATING_PANELS_ENABLED,
            -> {
                if (action.intValue !in 0..2) {
                    issues += AutomationValidationIssue(
                        "$path.intValue",
                        ruEn("Недопустимая операция для плавающих панелей", "Invalid operation for floating panels"),
                    )
                }
                if (
                    action.floatingPanelScope() == AutomationFloatingPanelScope.SELECTED &&
                    action.stringValue.isBlank()
                ) {
                    issues += AutomationValidationIssue(
                        "$path.stringValue",
                        ruEn("Выберите плавающую панель", "Choose a floating panel"),
                    )
                }
            }

            else -> Unit
        }
        if (action.type == AutomationBuiltinActionType.SET_AUTOMATION_TRIGGER_WIDGET) {
            val triggerId = action.stringValue.trim()
            if (triggerId.isEmpty()) {
                issues += AutomationValidationIssue("$path.stringValue", ruEn("Введите ID триггера", "Enter a trigger ID"))
            } else if (triggerId.length > AUTOMATION_TRIGGER_ID_MAX_CHARS) {
                issues += AutomationValidationIssue(
                    "$path.stringValue",
                    ruEn("ID триггера длиннее $AUTOMATION_TRIGGER_ID_MAX_CHARS символов", "Trigger ID is longer than $AUTOMATION_TRIGGER_ID_MAX_CHARS characters"),
                )
            }
        }
        if (action.type == AutomationBuiltinActionType.SET_AUTOMATION_ENABLED) {
            if (action.stringValue.trim().isEmpty()) {
                issues += AutomationValidationIssue(
                    "$path.stringValue",
                    ruEn("Укажите ID автоматизации", "Enter the automation ID"),
                )
            }
        }
        if (action.type == AutomationBuiltinActionType.ESP_RELAY_PULSE ||
            action.type == AutomationBuiltinActionType.ESP_BLE_RELAY_PULSE
        ) {
            val duration = if (action.type == AutomationBuiltinActionType.ESP_BLE_RELAY_PULSE) {
                EspBleRelayActionEncoding.pulseText(action)
            } else {
                action.stringValue.trim()
            }
            val parsedDuration = duration.toLongOrNull()
            if (
                duration.isNotEmpty() &&
                (parsedDuration == null || parsedDuration !in 1L..60_000L)
            ) {
                issues += AutomationValidationIssue(
                    "$path.stringValue",
                    ruEn("Длительность импульса должна быть 1–60000 мс", "Pulse duration must be 1–60000 ms"),
                )
            }
        }
        if (action.type in USER_MESSAGE_ACTIONS) {
            val text = action.stringValue.trim()
            if (text.isEmpty()) {
                issues += AutomationValidationIssue("$path.stringValue", ruEn("Введите текст сообщения", "Enter the message text"))
            } else if (text.length > AUTOMATION_MAX_USER_MESSAGE_CHARS) {
                issues += AutomationValidationIssue(
                    "$path.stringValue",
                    ruEn("Текст длиннее $AUTOMATION_MAX_USER_MESSAGE_CHARS символов", "Text is longer than $AUTOMATION_MAX_USER_MESSAGE_CHARS characters"),
                )
            }
        }
        if (action.type == AutomationBuiltinActionType.SHOW_ALERT) {
            if (action.intValue.toLong() !in 0L..AUTOMATION_MAX_DELAY_MS) {
                issues += AutomationValidationIssue(
                    "$path.intValue",
                    ruEn("Автозакрытие должно быть 0–${AUTOMATION_MAX_DELAY_MS / 1_000L} с", "Auto-close must be 0–${AUTOMATION_MAX_DELAY_MS / 1_000L} s"),
                )
            }
        }
    }


    private val USER_MESSAGE_ACTIONS = setOf(
        AutomationBuiltinActionType.SHOW_TOAST,
        AutomationBuiltinActionType.SHOW_ALERT,
    )
}
