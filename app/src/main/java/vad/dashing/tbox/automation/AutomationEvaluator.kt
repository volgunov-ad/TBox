package vad.dashing.tbox.automation

data class AutomationTriggerFire(
    val triggerId: String,
    val oldValue: AutomationSignalValue?,
    val newValue: AutomationSignalValue?,
)

/**
 * Pure per-automation trigger state machine.
 *
 * Multiple triggers use OR semantics. A single input event produces at most one result: the first
 * matching trigger in definition order. Top-level conditions are an AND-group; a single
 * [AutomationDefinition.conditionWaitMillis] window may wait until trigger and conditions
 * are true together before actions start.
 */
class AutomationEvaluator(
    private val definition: AutomationDefinition,
    private val allowStartupFire: Boolean,
    private val clock: AutomationClock = AutomationClock.System,
) {
    private data class RuntimeState(
        var initialized: Boolean = false,
        var armed: Boolean = true,
        var matchingSinceElapsedMillis: Long? = null,
        var currentValue: AutomationSignalValue? = null,
        var previousValue: AutomationSignalValue? = null,
        var lastNumericValue: Double? = null,
        var pendingInitialFire: Boolean = false,
        var initialFireAllowed: Boolean = false,
    )

    private data class ReadyCandidate(
        val triggerIndex: Int,
        val fire: AutomationTriggerFire,
        val readyAtElapsedMillis: Long,
        val initialFire: Boolean,
    )

    private val triggerStates = definition.triggers.associate {
        it.id to RuntimeState(initialFireAllowed = allowStartupFire)
    }
    private val latestSamples = mutableMapOf<AutomationSignalKey, AutomationSignalValue>()
    private var startupFireClaimed = false
    private var lastSeenMinuteKey: String = clock.wallTime().minuteKey

    fun onSystemEvent(event: AutomationSystemEvent): AutomationTriggerFire? {
        val trigger = definition.triggers.firstOrNull {
            it is AutomationTrigger.SystemEvent && it.event == event
        } ?: return null
        if (event == AutomationSystemEvent.BACKGROUND_SERVICE_STARTED) {
            startupFireClaimed = true
        }
        return AutomationTriggerFire(trigger.id, oldValue = null, newValue = null)
    }

    fun onSignalSample(sample: AutomationSignalSample): AutomationTriggerFire? {
        if (sample.value == AutomationSignalValue.Unavailable) {
            latestSamples.remove(sample.key)
            definition.triggers.forEach { trigger ->
                if (trigger.signalKeyOrNull() == sample.key) {
                    invalidate(triggerStates.getValue(trigger.id))
                }
            }
            return null
        }
        latestSamples[sample.key] = sample.value
        val candidates = mutableListOf<ReadyCandidate>()
        definition.triggers.forEachIndexed { index, trigger ->
            if (trigger.signalKeyOrNull() != sample.key) return@forEachIndexed
            val state = triggerStates.getValue(trigger.id)
            val candidate = updateTrigger(
                trigger = trigger,
                state = state,
                value = sample.value,
                nowElapsedMillis = sample.observedAtElapsedMillis,
            )
            if (candidate != null) {
                candidates += candidate.copy(triggerIndex = index)
            }
        }
        return chooseCandidate(candidates)
    }

    /**
     * Completes configurable `for`/hold durations even when the source does not re-emit a stable
     * value. The current value must still satisfy the trigger.
     */
    fun onTick(nowElapsedMillis: Long): AutomationTriggerFire? {
        val candidates = mutableListOf<ReadyCandidate>()
        addTimeTriggerCandidates(nowElapsedMillis, candidates)
        definition.triggers.forEachIndexed { index, trigger ->
            val state = triggerStates.getValue(trigger.id)
            val since = state.matchingSinceElapsedMillis ?: return@forEachIndexed
            val value = state.currentValue ?: return@forEachIndexed
            if (!trigger.matches(value)) {
                state.matchingSinceElapsedMillis = null
                state.pendingInitialFire = false
                return@forEachIndexed
            }
            val readyAt = since + trigger.holdMillis()
            if (nowElapsedMillis < readyAt) return@forEachIndexed
            val initialFire = state.pendingInitialFire
            state.matchingSinceElapsedMillis = null
            state.pendingInitialFire = false
            state.armed = false
            candidates += ReadyCandidate(
                triggerIndex = index,
                fire = AutomationTriggerFire(
                    triggerId = trigger.id,
                    oldValue = state.previousValue,
                    newValue = value,
                ),
                readyAtElapsedMillis = readyAt,
                initialFire = initialFire,
            )
        }
        return chooseCandidate(candidates)
    }

    fun conditionsPass(
        context: AutomationTriggerContext,
        conditions: List<AutomationCondition> = definition.conditions,
    ): Boolean = conditions.all {
        evaluateCondition(it, context, latestSamples, clock.wallTime())
    }

    /**
     * Ready to start actions: every top-level condition is true **and** the firing trigger
     * still matches (system events stay satisfied after they fire).
     */
    fun isReadyToRun(context: AutomationTriggerContext): Boolean =
        conditionsPass(context) && triggerStillMatching(context.triggerId)

    fun triggerStillMatching(triggerId: String): Boolean {
        val trigger = definition.triggers.firstOrNull { it.id == triggerId } ?: return false
        return when (trigger) {
            is AutomationTrigger.SystemEvent,
            is AutomationTrigger.Time,
            -> true
            else -> {
                val key = trigger.signalKeyOrNull() ?: return false
                val value = latestSamples[key] ?: return false
                trigger.matches(value)
            }
        }
    }

    fun snapshot(): Map<AutomationSignalKey, AutomationSignalValue> = latestSamples.toMap()

    private fun updateTrigger(
        trigger: AutomationTrigger,
        state: RuntimeState,
        value: AutomationSignalValue,
        nowElapsedMillis: Long,
    ): ReadyCandidate? {
        val old = state.currentValue
        state.previousValue = old
        state.currentValue = value
        val numberChanged = numericValueChanged(state.lastNumericValue, value)

        if (!state.initialized) {
            state.initialized = true
            val matches = trigger.matches(value)
            rememberNumeric(state, value)
            if (!matches) {
                state.armed = true
                return null
            }
            val shouldFire = state.initialFireAllowed &&
                trigger.startupBehavior() == AutomationStartupBehavior.FIRE_IF_MATCHING
            state.initialFireAllowed = false
            if (!shouldFire) {
                // Baseline only: the current matching level is not an edge.
                state.armed = false
                return null
            }
            state.pendingInitialFire = true
            return beginOrFire(trigger, state, old, value, nowElapsedMillis)
        }

        if (!state.armed) {
            if (trigger.usesRearmHysteresis()) {
                if (trigger.isRearmedBy(value)) {
                    state.armed = true
                    state.matchingSinceElapsedMillis = null
                    state.pendingInitialFire = false
                }
                rememberNumeric(state, value)
                return null
            }
            if (!trigger.matches(value)) {
                state.armed = true
                state.matchingSinceElapsedMillis = null
                state.pendingInitialFire = false
                rememberNumeric(state, value)
                return null
            }
            if (!numberChanged) {
                rememberNumeric(state, value)
                return null
            }
            state.armed = true
            state.matchingSinceElapsedMillis = null
            state.pendingInitialFire = false
        }

        if (!trigger.matches(value)) {
            state.matchingSinceElapsedMillis = null
            state.pendingInitialFire = false
            rememberNumeric(state, value)
            return null
        }

        if (!trigger.usesRearmHysteresis() && numberChanged) {
            state.matchingSinceElapsedMillis = null
        }
        rememberNumeric(state, value)
        return beginOrFire(trigger, state, old, value, nowElapsedMillis)
    }

    private fun invalidate(state: RuntimeState) {
        if (state.initialized) {
            state.initialFireAllowed = false
        }
        // Keep initialized/armed. A brief Unavailable must break the hold timer, not treat the
        // next matching sample as a cold-start baseline (that would disarm while still matching).
        // lastNumericValue is kept so "no hysteresis" can tell a repeated number from a new one.
        state.matchingSinceElapsedMillis = null
        state.currentValue = null
        state.previousValue = null
        state.pendingInitialFire = false
    }

    private fun rememberNumeric(state: RuntimeState, value: AutomationSignalValue) {
        val number = (value as? AutomationSignalValue.Number)?.value ?: return
        state.lastNumericValue = number
    }

    private fun beginOrFire(
        trigger: AutomationTrigger,
        state: RuntimeState,
        oldValue: AutomationSignalValue?,
        newValue: AutomationSignalValue,
        nowElapsedMillis: Long,
    ): ReadyCandidate? {
        val since = state.matchingSinceElapsedMillis ?: nowElapsedMillis.also {
            state.matchingSinceElapsedMillis = it
        }
        val hold = trigger.holdMillis()
        if (hold > 0L && nowElapsedMillis < since + hold) return null
        val initialFire = state.pendingInitialFire
        state.matchingSinceElapsedMillis = null
        state.pendingInitialFire = false
        state.armed = false
        return ReadyCandidate(
            triggerIndex = 0,
            fire = AutomationTriggerFire(trigger.id, oldValue, newValue),
            readyAtElapsedMillis = since + hold,
            initialFire = initialFire,
        )
    }

    private fun chooseCandidate(candidates: List<ReadyCandidate>): AutomationTriggerFire? {
        if (candidates.isEmpty()) return null
        val eligible = if (startupFireClaimed) {
            candidates.filterNot { it.initialFire }
        } else {
            candidates
        }
        val selected = eligible.minWithOrNull(
            compareBy<ReadyCandidate> { it.readyAtElapsedMillis }
                .thenBy { it.triggerIndex },
        ) ?: return null
        if (selected.initialFire) {
            startupFireClaimed = true
        }
        return selected.fire
    }

    companion object {
        fun evaluateCondition(
            condition: AutomationCondition,
            context: AutomationTriggerContext,
            snapshot: Map<AutomationSignalKey, AutomationSignalValue>,
            wallTime: AutomationWallTime = AutomationClock.System.wallTime(),
        ): Boolean {
            return when (condition) {
                AutomationCondition.Always -> true
                is AutomationCondition.Numeric -> {
                    val actual = (
                        snapshot[AutomationSignalKey(condition.signal, condition.source)]
                            as? AutomationSignalValue.Number
                        )?.value ?: return false
                    when (condition.comparison) {
                        AutomationComparison.ABOVE -> actual > condition.expectedValue
                        AutomationComparison.BELOW -> actual < condition.expectedValue
                        AutomationComparison.AT_LEAST -> actual >= condition.expectedValue
                        AutomationComparison.AT_MOST -> actual <= condition.expectedValue
                        AutomationComparison.EQUAL -> actual == condition.expectedValue
                        AutomationComparison.NOT_EQUAL -> actual != condition.expectedValue
                    }
                }

                is AutomationCondition.State -> {
                    val actual = (
                        snapshot[AutomationSignalKey(condition.signal, condition.source)]
                            as? AutomationSignalValue.State
                        )?.value ?: return false
                    actual.equals(condition.expectedState.trim(), ignoreCase = true)
                }

                is AutomationCondition.TriggeredBy -> context.triggerId in condition.triggerIds
                is AutomationCondition.All ->
                    condition.conditions.all {
                        evaluateCondition(it, context, snapshot, wallTime)
                    }

                is AutomationCondition.Any ->
                    condition.conditions.any {
                        evaluateCondition(it, context, snapshot, wallTime)
                    }

                is AutomationCondition.Not ->
                    !evaluateCondition(condition.condition, context, snapshot, wallTime)

                is AutomationCondition.Time -> AutomationTimeLogic.conditionMatches(
                    after = condition.after,
                    before = condition.before,
                    weekdays = condition.weekdays,
                    wall = wallTime,
                )
            }
        }
    }

    private fun addTimeTriggerCandidates(
        nowElapsedMillis: Long,
        candidates: MutableList<ReadyCandidate>,
    ) {
        val wall = clock.wallTime()
        val minuteKey = wall.minuteKey
        val previous = lastSeenMinuteKey
        lastSeenMinuteKey = minuteKey
        if (previous == minuteKey) return
        definition.triggers.forEachIndexed { index, trigger ->
            val time = trigger as? AutomationTrigger.Time ?: return@forEachIndexed
            if (!AutomationTimeLogic.triggerMatches(time.at, time.weekdays, wall)) {
                return@forEachIndexed
            }
            candidates += ReadyCandidate(
                triggerIndex = index,
                fire = AutomationTriggerFire(time.id, oldValue = null, newValue = null),
                readyAtElapsedMillis = nowElapsedMillis,
                initialFire = false,
            )
        }
    }
}

private fun AutomationTrigger.usesRearmHysteresis(): Boolean = when (this) {
    is AutomationTrigger.NumericThreshold -> rearmEnabled
    else -> true
}

private fun numericValueChanged(lastNumeric: Double?, value: AutomationSignalValue): Boolean {
    val number = (value as? AutomationSignalValue.Number)?.value ?: return true
    return lastNumeric == null || lastNumeric != number
}

private fun AutomationTrigger.signalKeyOrNull(): AutomationSignalKey? = when (this) {
    is AutomationTrigger.SystemEvent,
    is AutomationTrigger.Time,
    -> null
    is AutomationTrigger.NumericThreshold -> AutomationSignalKey(signal, source)
    is AutomationTrigger.StateEquals -> AutomationSignalKey(signal, source)
    is AutomationTrigger.Geofence -> AUTOMATION_GEO_DISPLAY_KEY
}

private fun AutomationTrigger.matches(value: AutomationSignalValue): Boolean {
    return when (this) {
        is AutomationTrigger.SystemEvent,
        is AutomationTrigger.Time,
        -> false
        is AutomationTrigger.NumericThreshold -> {
            val number = (value as? AutomationSignalValue.Number)?.value ?: return false
            when (direction) {
                AutomationThresholdDirection.ABOVE -> number > threshold
                AutomationThresholdDirection.BELOW -> number < threshold
            }
        }

        is AutomationTrigger.StateEquals -> {
            val state = (value as? AutomationSignalValue.State)?.value ?: return false
            state.equals(expectedState.trim(), ignoreCase = true)
        }

        is AutomationTrigger.Geofence -> geofenceDistance(this, value)?.let { distance ->
            when (direction) {
                AutomationGeofenceDirection.ENTER -> distance <= zoneRadiusMeters
                AutomationGeofenceDirection.EXIT -> distance > zoneRadiusMeters
            }
        } ?: false
    }
}

private fun AutomationTrigger.isRearmedBy(value: AutomationSignalValue): Boolean {
    return when (this) {
        is AutomationTrigger.SystemEvent,
        is AutomationTrigger.Time,
        -> true
        is AutomationTrigger.NumericThreshold -> {
            val number = (value as? AutomationSignalValue.Number)?.value ?: return false
            val reset = resetThreshold ?: threshold
            when (direction) {
                AutomationThresholdDirection.ABOVE -> number <= reset
                AutomationThresholdDirection.BELOW -> number >= reset
            }
        }

        is AutomationTrigger.StateEquals -> {
            val state = (value as? AutomationSignalValue.State)?.value ?: return false
            !state.equals(expectedState.trim(), ignoreCase = true)
        }

        is AutomationTrigger.Geofence -> geofenceDistance(this, value)?.let { distance ->
            when (direction) {
                AutomationGeofenceDirection.ENTER -> distance > rearmRadiusMeters
                AutomationGeofenceDirection.EXIT -> distance <= rearmRadiusMeters
            }
        } ?: false
    }
}

private fun AutomationTrigger.holdMillis(): Long = when (this) {
    is AutomationTrigger.SystemEvent,
    is AutomationTrigger.Time,
    -> 0L
    is AutomationTrigger.NumericThreshold -> holdMillis
    is AutomationTrigger.StateEquals -> holdMillis
    is AutomationTrigger.Geofence -> holdMillis
}

private fun AutomationTrigger.startupBehavior(): AutomationStartupBehavior = when (this) {
    is AutomationTrigger.SystemEvent,
    is AutomationTrigger.Time,
    -> AutomationStartupBehavior.INITIALIZE_ONLY
    is AutomationTrigger.NumericThreshold -> startupBehavior
    is AutomationTrigger.StateEquals -> startupBehavior
    is AutomationTrigger.Geofence -> startupBehavior
}

private fun geofenceDistance(
    trigger: AutomationTrigger.Geofence,
    value: AutomationSignalValue,
): Double? {
    val position = value as? AutomationSignalValue.Position ?: return null
    return vad.dashing.tbox.location.ConstantDrMath.distanceMeters(
        trigger.latitude,
        trigger.longitude,
        position.latitude,
        position.longitude,
    ).takeIf { it.isFinite() }
}
