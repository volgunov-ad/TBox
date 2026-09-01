package vad.dashing.tbox.automation

import org.json.JSONArray
import org.json.JSONObject
import vad.dashing.tbox.AppLauncherLaunchMode
import vad.dashing.tbox.DEFAULT_HTTP_REQUEST_WIDGET_YAML
import vad.dashing.tbox.freeform.FreeformLaunchBounds
import vad.dashing.tbox.freeform.FreeformLaunchSide

object AutomationCodec {
    private const val KEY_FORMAT_VERSION = "formatVersion"
    private const val KEY_AUTOMATIONS = "automations"
    private const val KEY_TYPE = "type"

    fun encode(document: AutomationDocument, pretty: Boolean = false): String {
        val root = JSONObject()
            .put(KEY_FORMAT_VERSION, AUTOMATION_FORMAT_VERSION)
            .put(
                KEY_AUTOMATIONS,
                JSONArray().also { array ->
                    document.automations.forEach { array.put(encodeDefinition(it)) }
                },
            )
        return if (pretty) root.toString(2) else root.toString()
    }

    fun encodeDefinitionDocument(definition: AutomationDefinition, pretty: Boolean = true): String =
        encode(AutomationDocument(automations = listOf(definition)), pretty = pretty)

    /**
     * Import payload: store document, a single definition object, or a JSON array of
     * definitions. Empty list is an error.
     */
    fun decodeImport(raw: String): Result<List<AutomationDefinition>> = runCatching {
        val trimmed = raw.trim()
        require(trimmed.isNotEmpty()) { "Пустой JSON" }
        val definitions = when {
            trimmed.startsWith("[") ->
                JSONArray(trimmed).mapObjects(::decodeDefinition)
            else -> {
                val root = JSONObject(trimmed)
                if (root.has(KEY_AUTOMATIONS)) {
                    decode(trimmed).getOrThrow().automations
                } else {
                    listOf(decodeDefinition(root))
                }
            }
        }
        require(definitions.isNotEmpty()) { "В файле нет автоматизаций" }
        definitions
    }

    fun decode(raw: String): Result<AutomationDocument> = runCatching {
        if (raw.isBlank()) return@runCatching AutomationDocument()
        val root = JSONObject(raw)
        val version = root.requireInt(KEY_FORMAT_VERSION)
        require(version == AUTOMATION_FORMAT_VERSION) {
            "Unsupported automation format version: $version"
        }
        val array = root.optJSONArray(KEY_AUTOMATIONS)
            ?: throw IllegalArgumentException("Missing automations array")
        val definitions = buildList {
            for (index in 0 until array.length()) {
                add(decodeDefinition(array.requireObject(index)))
            }
        }
        AutomationDocument(formatVersion = version, automations = definitions)
    }

    private fun encodeDefinition(definition: AutomationDefinition): JSONObject =
        JSONObject()
            .put("id", definition.id)
            .put("name", definition.name)
            .put("description", definition.description)
            .put("enabled", definition.enabled)
            .put(
                "triggers",
                JSONArray().also { array ->
                    definition.triggers.forEach { array.put(encodeTrigger(it)) }
                },
            )
            .put(
                "conditions",
                JSONArray().also { array ->
                    definition.conditions.forEach { array.put(encodeCondition(it)) }
                },
            )
            .put(
                "actions",
                JSONArray().also { array ->
                    definition.actions.forEach { array.put(encodeAction(it)) }
                },
            )
            .put("runMode", definition.runMode.storageKey)
            .put("maxRuns", definition.maxRuns)
            .put("conditionWaitMillis", definition.conditionWaitMillis)

    private fun decodeDefinition(json: JSONObject): AutomationDefinition {
        val triggers = json.requireArray("triggers").mapObjects(::decodeTrigger)
        val conditions = json.optJSONArray("conditions")?.mapObjects(::decodeCondition).orEmpty()
        val actions = json.requireArray("actions").mapObjects(::decodeAction)
        return AutomationDefinition(
            id = json.requireNonBlankString("id"),
            name = json.requireString("name"),
            description = json.requireString("description"),
            enabled = json.requireBoolean("enabled"),
            triggers = triggers,
            conditions = conditions,
            actions = actions,
            runMode = json.requireStorageEnum("runMode", AutomationRunMode.entries) {
                it.storageKey
            },
            maxRuns = json.requireInt("maxRuns"),
            conditionWaitMillis = json.optLongOrDefault("conditionWaitMillis", 0L),
        )
    }

    private fun encodeTrigger(trigger: AutomationTrigger): JSONObject =
        when (trigger) {
            is AutomationTrigger.SystemEvent -> JSONObject()
                .put(KEY_TYPE, "system_event")
                .put("id", trigger.id)
                .put("event", trigger.event.storageKey)

            is AutomationTrigger.NumericThreshold -> JSONObject()
                .put(KEY_TYPE, "numeric_threshold")
                .put("id", trigger.id)
                .put("signal", trigger.signal.storageKey)
                .put("source", trigger.source.storageKey)
                .put("direction", trigger.direction.storageKey)
                .put("threshold", trigger.threshold)
                .putNullable("resetThreshold", trigger.resetThreshold)
                .put("rearmEnabled", trigger.rearmEnabled)
                .put("holdMillis", trigger.holdMillis)
                .put("startupBehavior", trigger.startupBehavior.storageKey)

            is AutomationTrigger.StateEquals -> JSONObject()
                .put(KEY_TYPE, "state_equals")
                .put("id", trigger.id)
                .put("signal", trigger.signal.storageKey)
                .put("source", trigger.source.storageKey)
                .put("expectedState", trigger.expectedState)
                .put("holdMillis", trigger.holdMillis)
                .put("startupBehavior", trigger.startupBehavior.storageKey)

            is AutomationTrigger.Geofence -> JSONObject()
                .put(KEY_TYPE, "geofence")
                .put("id", trigger.id)
                .put("queryText", trigger.queryText)
                .put("latitude", trigger.latitude)
                .put("longitude", trigger.longitude)
                .put("direction", trigger.direction.storageKey)
                .put("zoneRadiusMeters", trigger.zoneRadiusMeters)
                .put("rearmRadiusMeters", trigger.rearmRadiusMeters)
                .put("holdMillis", trigger.holdMillis)
                .put("startupBehavior", trigger.startupBehavior.storageKey)

            is AutomationTrigger.Time -> JSONObject()
                .put(KEY_TYPE, "time")
                .put("id", trigger.id)
                .put("at", trigger.at.toStorageKey())
                .put("weekdays", encodeWeekdays(trigger.weekdays))
                .put("startupBehavior", trigger.startupBehavior.storageKey)

            is AutomationTrigger.Solar -> JSONObject()
                .put(KEY_TYPE, "solar")
                .put("id", trigger.id)
                .put("event", trigger.event.storageKey)
                .put("offsetMinutes", trigger.offsetMinutes)
                .put("offsetDirection", trigger.offsetDirection.storageKey)
                .put("weekdays", encodeWeekdays(trigger.weekdays))
                .put("startupBehavior", trigger.startupBehavior.storageKey)
        }

    private fun decodeTrigger(json: JSONObject): AutomationTrigger =
        when (json.requireNonBlankString(KEY_TYPE)) {
            "system_event" -> AutomationTrigger.SystemEvent(
                id = json.requireNonBlankString("id"),
                event = AutomationSystemEvent.fromStorageKey(json.requireNonBlankString("event"))
                    ?: throw IllegalArgumentException("Unknown system event"),
            )

            "numeric_threshold" -> AutomationTrigger.NumericThreshold(
                id = json.requireNonBlankString("id"),
                signal = AutomationSignalId.fromStorageKey(json.requireNonBlankString("signal"))
                    ?: throw IllegalArgumentException("Unknown numeric signal"),
                source = AutomationSignalSource.fromStorageKey(json.requireNonBlankString("source"))
                    ?: throw IllegalArgumentException("Unknown signal source"),
                direction = AutomationThresholdDirection.fromStorageKey(
                    json.requireNonBlankString("direction"),
                )
                    ?: throw IllegalArgumentException("Unknown threshold direction"),
                threshold = json.requireFiniteDouble("threshold"),
                resetThreshold = json.optFiniteDouble("resetThreshold"),
                rearmEnabled = json.optBooleanOrDefault("rearmEnabled", true),
                holdMillis = json.requireLong("holdMillis"),
                startupBehavior = json.requireStorageEnum(
                    "startupBehavior",
                    AutomationStartupBehavior.entries,
                ) { it.storageKey },
            )

            "state_equals" -> {
                val signal = AutomationSignalId.fromStorageKey(json.requireNonBlankString("signal"))
                    ?: throw IllegalArgumentException("Unknown state signal")
                val source = AutomationSignalSource.fromStorageKey(json.requireNonBlankString("source"))
                    ?: throw IllegalArgumentException("Unknown signal source")
                val expectedState = json.requireNonBlankString("expectedState")
                val migratedState = AutomationSignalStateEncoding.migrateLegacyStateValue(signal, expectedState)
                AutomationTrigger.StateEquals(
                    id = json.requireNonBlankString("id"),
                    signal = signal,
                    source = source,
                    expectedState = migratedState,
                    holdMillis = json.requireLong("holdMillis"),
                    startupBehavior = json.requireStorageEnum(
                        "startupBehavior",
                        AutomationStartupBehavior.entries,
                    ) { it.storageKey },
                )
            }

            "geofence" -> AutomationTrigger.Geofence(
                id = json.requireNonBlankString("id"),
                queryText = json.requireString("queryText"),
                latitude = json.requireFiniteDouble("latitude"),
                longitude = json.requireFiniteDouble("longitude"),
                direction = AutomationGeofenceDirection.fromStorageKey(
                    json.requireNonBlankString("direction"),
                ) ?: throw IllegalArgumentException("Unknown geofence direction"),
                zoneRadiusMeters = json.requireFiniteDouble("zoneRadiusMeters"),
                rearmRadiusMeters = json.requireFiniteDouble("rearmRadiusMeters"),
                holdMillis = json.requireLong("holdMillis"),
                startupBehavior = json.requireStorageEnum(
                    "startupBehavior",
                    AutomationStartupBehavior.entries,
                ) { it.storageKey },
            )

            "time" -> AutomationTrigger.Time(
                id = json.requireNonBlankString("id"),
                at = AutomationTimeOfDay.fromStorageKey(json.requireNonBlankString("at"))
                    ?: throw IllegalArgumentException("Unknown time of day"),
                weekdays = json.decodeWeekdays(),
                startupBehavior = json.optStartupBehavior(),
            )

            "solar" -> AutomationTrigger.Solar(
                id = json.requireNonBlankString("id"),
                event = AutomationSolarEvent.fromStorageKey(json.requireNonBlankString("event"))
                    ?: throw IllegalArgumentException("Unknown solar event"),
                offsetMinutes = json.requireInt("offsetMinutes"),
                offsetDirection = AutomationSolarOffsetDirection.fromStorageKey(
                    json.requireNonBlankString("offsetDirection"),
                ) ?: throw IllegalArgumentException("Unknown solar offset direction"),
                weekdays = json.decodeWeekdays(),
                startupBehavior = json.optStartupBehavior(),
            )

            else -> throw IllegalArgumentException("Unknown trigger type")
        }

    private fun encodeCondition(condition: AutomationCondition): JSONObject =
        when (condition) {
            AutomationCondition.Always -> JSONObject().put(KEY_TYPE, "always")
            is AutomationCondition.Numeric -> JSONObject()
                .put(KEY_TYPE, "numeric")
                .put("signal", condition.signal.storageKey)
                .put("source", condition.source.storageKey)
                .put("comparison", condition.comparison.storageKey)
                .put("expectedValue", condition.expectedValue)

            is AutomationCondition.State -> JSONObject()
                .put(KEY_TYPE, "state")
                .put("signal", condition.signal.storageKey)
                .put("source", condition.source.storageKey)
                .put("expectedState", condition.expectedState)

            is AutomationCondition.TriggeredBy -> JSONObject()
                .put(KEY_TYPE, "triggered_by")
                .put(
                    "triggerIds",
                    JSONArray().also { array ->
                        condition.triggerIds.sorted().forEach(array::put)
                    },
                )

            is AutomationCondition.All -> JSONObject()
                .put(KEY_TYPE, "all")
                .put("conditions", encodeConditions(condition.conditions))

            is AutomationCondition.Any -> JSONObject()
                .put(KEY_TYPE, "any")
                .put("conditions", encodeConditions(condition.conditions))

            is AutomationCondition.Not -> JSONObject()
                .put(KEY_TYPE, "not")
                .put("condition", encodeCondition(condition.condition))

            is AutomationCondition.Time -> JSONObject()
                .put(KEY_TYPE, "time")
                .putNullable("after", condition.after?.toStorageKey())
                .putNullable("before", condition.before?.toStorageKey())
                .put("weekdays", encodeWeekdays(condition.weekdays))

            is AutomationCondition.Solar -> JSONObject()
                .put(KEY_TYPE, "solar")
                .put("after", condition.after?.let(::encodeSolarInstant) ?: JSONObject.NULL)
                .put("before", condition.before?.let(::encodeSolarInstant) ?: JSONObject.NULL)
                .put("weekdays", encodeWeekdays(condition.weekdays))

            is AutomationCondition.Geofence -> JSONObject()
                .put(KEY_TYPE, "geofence")
                .put("queryText", condition.queryText)
                .put("latitude", condition.latitude)
                .put("longitude", condition.longitude)
                .put("presence", condition.presence.storageKey)
                .put("zoneRadiusMeters", condition.zoneRadiusMeters)

            is AutomationCondition.UiState -> JSONObject()
                .put(KEY_TYPE, "ui_state")
                .put("state", condition.state.storageKey)
        }

    private fun decodeCondition(json: JSONObject): AutomationCondition =
        when (json.requireNonBlankString(KEY_TYPE)) {
            "always" -> AutomationCondition.Always
            "state" -> {
                val signal = AutomationSignalId.fromStorageKey(json.requireNonBlankString("signal"))
                    ?: throw IllegalArgumentException("Unknown state condition signal")
                val source = AutomationSignalSource.fromStorageKey(json.requireNonBlankString("source"))
                    ?: throw IllegalArgumentException("Unknown condition source")
                val expectedState = json.requireNonBlankString("expectedState")
                migrateLegacySignalCondition(
                    AutomationCondition.State(
                        signal = signal,
                        source = source,
                        expectedState = expectedState,
                    ),
                )
            }

            "numeric" -> {
                val signal = AutomationSignalId.fromStorageKey(json.requireNonBlankString("signal"))
                    ?: throw IllegalArgumentException("Unknown numeric condition signal")
                val source = AutomationSignalSource.fromStorageKey(json.requireNonBlankString("source"))
                    ?: throw IllegalArgumentException("Unknown condition source")
                val comparison = AutomationComparison.fromStorageKey(
                    json.requireNonBlankString("comparison"),
                ) ?: throw IllegalArgumentException("Unknown numeric comparison")
                val expectedValue = json.requireFiniteDouble("expectedValue")
                AutomationSignalStateEncoding.migrateLegacyNumericCondition(
                    signal,
                    source,
                    expectedValue,
                ) ?: AutomationCondition.Numeric(
                    signal = signal,
                    source = source,
                    comparison = comparison,
                    expectedValue = expectedValue,
                )
            }

            "geofence" -> AutomationCondition.Geofence(
                queryText = json.requireString("queryText"),
                latitude = json.requireFiniteDouble("latitude"),
                longitude = json.requireFiniteDouble("longitude"),
                presence = AutomationGeofencePresence.fromStorageKey(
                    json.requireNonBlankString("presence"),
                ) ?: throw IllegalArgumentException("Unknown geofence presence"),
                zoneRadiusMeters = json.requireFiniteDouble("zoneRadiusMeters"),
            )

            "ui_state" -> AutomationCondition.UiState(
                state = AutomationUiState.fromStorageKey(json.requireNonBlankString("state"))
                    ?: throw IllegalArgumentException("Unknown UI state"),
            )

            "triggered_by" -> AutomationCondition.TriggeredBy(
                triggerIds = json.requireArray("triggerIds").mapStrings().toSet(),
            )

            "all" -> AutomationCondition.All(
                json.requireArray("conditions").mapObjects(::decodeCondition),
            )

            "any" -> AutomationCondition.Any(
                json.requireArray("conditions").mapObjects(::decodeCondition),
            )

            "not" -> AutomationCondition.Not(
                decodeCondition(json.requireObject("condition")),
            )

            "time" -> AutomationCondition.Time(
                after = json.optTimeOfDay("after"),
                before = json.optTimeOfDay("before"),
                weekdays = json.decodeWeekdays(),
            )

            "solar" -> AutomationCondition.Solar(
                after = json.optSolarInstant("after"),
                before = json.optSolarInstant("before"),
                weekdays = json.decodeWeekdays(),
            )

            else -> throw IllegalArgumentException("Unknown condition type")
        }

    private fun encodeConditions(conditions: List<AutomationCondition>): JSONArray =
        JSONArray().also { array -> conditions.forEach { array.put(encodeCondition(it)) } }

    private fun encodeActions(actions: List<AutomationAction>): JSONArray =
        JSONArray().also { array -> actions.forEach { array.put(encodeAction(it)) } }

    private fun encodeAction(action: AutomationAction): JSONObject =
        when (action) {
            is AutomationAction.Delay -> JSONObject()
                .put(KEY_TYPE, "delay")
                .put("durationMillis", action.durationMillis)

            is AutomationAction.IfThenElse -> JSONObject()
                .put(KEY_TYPE, "if_then_else")
                .put("condition", encodeCondition(action.condition))
                .put("thenActions", encodeActions(action.thenActions))
                .put("elseActions", encodeActions(action.elseActions))

            is AutomationAction.CanCommand -> JSONObject()
                .put(KEY_TYPE, "can_command")
                .put("bus", action.bus.storageKey)
                .put("propertyId", action.propertyId)
                .put("operation", action.operation.storageKey)
                .put("value", action.value)

            is AutomationAction.LaunchApplication -> JSONObject()
                .put(KEY_TYPE, "launch_application")
                .put("packageName", action.packageName)
                .put("launchMode", action.launchMode.storageKey)
                .put("freeformSide", action.freeformSide.storageKey)
                .put("freeformPercent", action.freeformPercent)
                .putNullable("freeformOverlayPage", action.freeformOverlayPage)
                .put("freeformOverlayCrop", action.freeformOverlayCrop)

            is AutomationAction.OpenMainScreen -> JSONObject()
                .put(KEY_TYPE, "open_main_screen")
                .put("page", action.page)
                .put("target", action.target.storageKey)

            is AutomationAction.HttpRequest -> JSONObject()
                .put(KEY_TYPE, "http_request")
                .put("yaml", action.yaml)
                .put("openBrowser", action.openBrowser)

            is AutomationAction.Builtin -> JSONObject()
                .put(KEY_TYPE, "builtin")
                .put("actionType", action.type.storageKey)
                .put("intValue", action.intValue)
                .put("stringValue", action.stringValue)
                .put("boolValue", action.boolValue)
        }

    private fun decodeAction(json: JSONObject): AutomationAction =
        when (json.requireNonBlankString(KEY_TYPE)) {
            "delay" -> AutomationAction.Delay(
                durationMillis = json.requireLong("durationMillis"),
            )

            "if_then_else" -> AutomationAction.IfThenElse(
                condition = decodeCondition(json.requireObject("condition")),
                thenActions = json.requireArray("thenActions").mapObjects(::decodeAction),
                elseActions = json.optJSONArray("elseActions")?.mapObjects(::decodeAction).orEmpty(),
            )

            "can_command" -> AutomationAction.CanCommand(
                bus = json.requireStorageEnum("bus", AutomationCanBus.entries) { it.storageKey },
                propertyId = json.requireInt("propertyId"),
                operation = json.requireStorageEnum(
                    "operation",
                    AutomationCanOperation.entries,
                ) { it.storageKey },
                value = json.requireInt("value"),
            )

            "launch_application" -> AutomationAction.LaunchApplication(
                packageName = json.requireNonBlankString("packageName"),
                launchMode = json.requireStorageEnum(
                    "launchMode",
                    AppLauncherLaunchMode.entries,
                ) { it.storageKey },
                freeformSide = json.requireStorageEnum(
                    "freeformSide",
                    FreeformLaunchSide.entries,
                ) { it.storageKey },
                freeformPercent = FreeformLaunchBounds.normalizePercent(
                    json.requireInt("freeformPercent"),
                ),
                freeformOverlayPage = json.optNullableInt("freeformOverlayPage"),
                freeformOverlayCrop = json.requireBoolean("freeformOverlayCrop"),
            )

            "open_main_screen" -> AutomationAction.OpenMainScreen(
                page = json.requireInt("page"),
                target = json.requireStorageEnum(
                    "target",
                    AutomationMainScreenTarget.entries,
                ) { it.storageKey },
            )

            "http_request" -> AutomationAction.HttpRequest(
                yaml = json.requireString("yaml"),
                openBrowser = json.requireBoolean("openBrowser"),
            )

            "builtin" -> AutomationAction.Builtin(
                type = AutomationBuiltinActionType.fromStorageKey(
                    json.requireNonBlankString("actionType"),
                )
                    ?: throw IllegalArgumentException("Unknown builtin action"),
                intValue = json.requireInt("intValue"),
                stringValue = json.requireString("stringValue"),
                boolValue = json.requireBoolean("boolValue"),
            )

            else -> throw IllegalArgumentException("Unknown action type")
        }

    private fun JSONObject.requireObject(key: String): JSONObject =
        optJSONObject(key) ?: throw IllegalArgumentException("Missing object: $key")

    private fun JSONObject.requireArray(key: String): JSONArray =
        optJSONArray(key) ?: throw IllegalArgumentException("Missing array: $key")

    private fun JSONArray.requireObject(index: Int): JSONObject =
        optJSONObject(index) ?: throw IllegalArgumentException("Expected object at index $index")

    private fun JSONObject.requireNonBlankString(key: String): String =
        requireString(key).trim().takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("Missing string: $key")

    private fun JSONObject.requireString(key: String): String {
        val value = opt(key)
        require(value is String) { "Expected string: $key" }
        return value
    }

    private fun JSONObject.requireBoolean(key: String): Boolean {
        val value = opt(key)
        require(value is Boolean) { "Expected boolean: $key" }
        return value
    }

    private fun JSONObject.requireInt(key: String): Int {
        val value = opt(key)
        require(value is Number) { "Expected integer: $key" }
        val long = value.toLong()
        require(value.toDouble().isFinite() && value.toDouble() == long.toDouble()) {
            "Expected integer: $key"
        }
        require(long in Int.MIN_VALUE..Int.MAX_VALUE) { "Integer out of range: $key" }
        return long.toInt()
    }

    private fun JSONObject.requireLong(key: String): Long {
        val value = opt(key)
        require(value is Number) { "Expected long: $key" }
        val long = value.toLong()
        require(value.toDouble().isFinite() && value.toDouble() == long.toDouble()) {
            "Expected long: $key"
        }
        return long
    }

    private fun JSONObject.optLongOrDefault(key: String, default: Long): Long {
        if (!has(key) || isNull(key)) return default
        return requireLong(key)
    }

    private fun JSONObject.optBooleanOrDefault(key: String, default: Boolean): Boolean {
        if (!has(key) || isNull(key)) return default
        return requireBoolean(key)
    }

    private fun JSONObject.requireFiniteDouble(key: String): Double =
        optFiniteDouble(key) ?: throw IllegalArgumentException("Missing finite number: $key")

    private fun JSONObject.optFiniteDouble(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        val value = optDouble(key, Double.NaN)
        return value.takeIf { it.isFinite() }
    }

    private fun JSONObject.optNullableInt(key: String): Int? {
        if (!has(key) || isNull(key)) return null
        return requireInt(key)
    }

    private fun JSONObject.putNullable(key: String, value: Any?): JSONObject =
        put(key, value ?: JSONObject.NULL)

    private fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> =
        buildList {
            for (index in 0 until length()) {
                add(transform(requireObject(index)))
            }
        }

    private fun JSONArray.mapStrings(): List<String> =
        buildList {
            for (index in 0 until length()) {
                val value = optString(index).trim()
                require(value.isNotEmpty()) { "Expected non-blank string at index $index" }
                add(value)
            }
        }

    private fun encodeWeekdays(weekdays: Set<AutomationWeekday>): JSONArray =
        JSONArray().also { array ->
            AutomationWeekday.entries.filter { it in weekdays }.forEach { array.put(it.storageKey) }
        }

    private fun JSONObject.decodeWeekdays(): Set<AutomationWeekday> {
        if (!has("weekdays") || isNull("weekdays")) return emptySet()
        return requireArray("weekdays").mapStrings().map { raw ->
            AutomationWeekday.fromStorageKey(raw)
                ?: throw IllegalArgumentException("Unknown weekday: $raw")
        }.toSet()
    }

    private fun JSONObject.optTimeOfDay(key: String): AutomationTimeOfDay? {
        if (!has(key) || isNull(key)) return null
        return AutomationTimeOfDay.fromStorageKey(requireNonBlankString(key))
            ?: throw IllegalArgumentException("Unknown time of day: $key")
    }

    private fun JSONObject.optStartupBehavior(): AutomationStartupBehavior {
        if (!has("startupBehavior") || isNull("startupBehavior")) {
            return AutomationStartupBehavior.INITIALIZE_ONLY
        }
        return AutomationStartupBehavior.fromStorageKey(requireNonBlankString("startupBehavior"))
    }

    private fun encodeSolarInstant(instant: AutomationSolarInstant): JSONObject =
        JSONObject()
            .put("event", instant.event.storageKey)
            .put("offsetMinutes", instant.offsetMinutes)
            .put("offsetDirection", instant.offsetDirection.storageKey)

    private fun JSONObject.optSolarInstant(key: String): AutomationSolarInstant? {
        if (!has(key) || isNull(key)) return null
        val raw = requireObject(key)
        return AutomationSolarInstant(
            event = AutomationSolarEvent.fromStorageKey(raw.requireNonBlankString("event"))
                ?: throw IllegalArgumentException("Unknown solar event"),
            offsetMinutes = raw.requireInt("offsetMinutes"),
            offsetDirection = AutomationSolarOffsetDirection.fromStorageKey(
                raw.requireNonBlankString("offsetDirection"),
            ) ?: throw IllegalArgumentException("Unknown solar offset direction"),
        )
    }

    private fun migrateLegacySignalCondition(condition: AutomationCondition.State): AutomationCondition.State {
        if (condition.signal != AutomationSignalId.DRIVE_MODE &&
            condition.signal != AutomationSignalId.HEADLIGHT_MODE
        ) {
            return condition
        }
        return condition.copy(
            expectedState = AutomationSignalStateEncoding.migrateLegacyStateValue(
                condition.signal,
                condition.expectedState,
            ),
        )
    }

    private fun <T> JSONObject.requireStorageEnum(
        key: String,
        entries: List<T>,
        storageKey: (T) -> String,
    ): T {
        val raw = requireNonBlankString(key)
        return entries.firstOrNull { storageKey(it) == raw }
            ?: throw IllegalArgumentException("Unknown $key: $raw")
    }
}
