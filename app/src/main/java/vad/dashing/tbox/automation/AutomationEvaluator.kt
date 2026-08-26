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
 * matching trigger in definition order. Conditions are evaluated separately immediately before
 * actions start.
 */
class AutomationEvaluator(
    private val definition: AutomationDefinition,
    private val allowStartupFire: Boolean,
) {
    private data class RuntimeState(
        var initialized: Boolean = false,
        var armed: Boolean = true,
        var matchingSinceElapsedMillis: Long? = null,
        var currentValue: AutomationSignalValue? = null,
        var previousValue: AutomationSignalValue? = null,
        var pendingInitialFire: Boolean = false,
    )

    private val triggerStates = definition.triggers.associate { it.id to RuntimeState() }
    private val latestSamples = mutableMapOf<AutomationSignalKey, AutomationSignalValue>()
    private var startupFireClaimed = false

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
        latestSamples[sample.key] = sample.value
        definition.triggers.forEach { trigger ->
            if (trigger.signalKeyOrNull() != sample.key) return@forEach
            val state = triggerStates.getValue(trigger.id)
            val fire = updateTrigger(
                trigger = trigger,
                state = state,
                value = sample.value,
                nowElapsedMillis = sample.observedAtElapsedMillis,
            )
            if (fire != null) return fire
        }
        return null
    }

    /**
     * Completes configurable `for`/hold durations even when the source does not re-emit a stable
     * value. The current value must still satisfy the trigger.
     */
    fun onTick(nowElapsedMillis: Long): AutomationTriggerFire? {
        definition.triggers.forEach { trigger ->
            val state = triggerStates.getValue(trigger.id)
            val since = state.matchingSinceElapsedMillis ?: return@forEach
            val value = state.currentValue ?: return@forEach
            if (!trigger.matches(value)) {
                state.matchingSinceElapsedMillis = null
                state.pendingInitialFire = false
                return@forEach
            }
            if (nowElapsedMillis - since < trigger.holdMillis()) return@forEach
            if (state.pendingInitialFire && startupFireClaimed) {
                state.matchingSinceElapsedMillis = null
                state.pendingInitialFire = false
                state.armed = false
                return@forEach
            }
            if (state.pendingInitialFire) {
                startupFireClaimed = true
            }
            state.matchingSinceElapsedMillis = null
            state.pendingInitialFire = false
            state.armed = false
            return AutomationTriggerFire(
                triggerId = trigger.id,
                oldValue = state.previousValue,
                newValue = value,
            )
        }
        return null
    }

    fun conditionsPass(
        context: AutomationTriggerContext,
        conditions: List<AutomationCondition> = definition.conditions,
    ): Boolean = conditions.all { evaluateCondition(it, context, latestSamples) }

    fun snapshot(): Map<AutomationSignalKey, AutomationSignalValue> = latestSamples.toMap()

    private fun updateTrigger(
        trigger: AutomationTrigger,
        state: RuntimeState,
        value: AutomationSignalValue,
        nowElapsedMillis: Long,
    ): AutomationTriggerFire? {
        val old = state.currentValue
        state.previousValue = old
        state.currentValue = value

        if (!state.initialized) {
            state.initialized = true
            val matches = trigger.matches(value)
            if (!matches) {
                state.armed = true
                return null
            }
            val shouldFire = allowStartupFire &&
                trigger.startupBehavior() == AutomationStartupBehavior.FIRE_IF_MATCHING
            if (!shouldFire) {
                state.armed = false
                return null
            }
            state.pendingInitialFire = true
            return beginOrFire(trigger, state, old, value, nowElapsedMillis)
        }

        if (!state.armed) {
            if (trigger.isRearmedBy(value)) {
                state.armed = true
                state.matchingSinceElapsedMillis = null
                state.pendingInitialFire = false
            }
            return null
        }

        if (!trigger.matches(value)) {
            state.matchingSinceElapsedMillis = null
            state.pendingInitialFire = false
            return null
        }

        return beginOrFire(trigger, state, old, value, nowElapsedMillis)
    }

    private fun beginOrFire(
        trigger: AutomationTrigger,
        state: RuntimeState,
        oldValue: AutomationSignalValue?,
        newValue: AutomationSignalValue,
        nowElapsedMillis: Long,
    ): AutomationTriggerFire? {
        if (state.matchingSinceElapsedMillis == null) {
            state.matchingSinceElapsedMillis = nowElapsedMillis
        }
        if (trigger.holdMillis() > 0L) return null
        if (state.pendingInitialFire && startupFireClaimed) {
            state.matchingSinceElapsedMillis = null
            state.pendingInitialFire = false
            state.armed = false
            return null
        }
        if (state.pendingInitialFire) {
            startupFireClaimed = true
        }
        state.matchingSinceElapsedMillis = null
        state.pendingInitialFire = false
        state.armed = false
        return AutomationTriggerFire(trigger.id, oldValue, newValue)
    }

    companion object {
        fun evaluateCondition(
            condition: AutomationCondition,
            context: AutomationTriggerContext,
            snapshot: Map<AutomationSignalKey, AutomationSignalValue>,
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
                    condition.conditions.all { evaluateCondition(it, context, snapshot) }

                is AutomationCondition.Any ->
                    condition.conditions.any { evaluateCondition(it, context, snapshot) }

                is AutomationCondition.Not -> !evaluateCondition(condition.condition, context, snapshot)
            }
        }
    }
}

private fun AutomationTrigger.signalKeyOrNull(): AutomationSignalKey? = when (this) {
    is AutomationTrigger.SystemEvent -> null
    is AutomationTrigger.NumericThreshold -> AutomationSignalKey(signal, source)
    is AutomationTrigger.StateEquals -> AutomationSignalKey(signal, source)
}

private fun AutomationTrigger.matches(value: AutomationSignalValue): Boolean {
    return when (this) {
        is AutomationTrigger.SystemEvent -> false
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
    }
}

private fun AutomationTrigger.isRearmedBy(value: AutomationSignalValue): Boolean {
    return when (this) {
        is AutomationTrigger.SystemEvent -> true
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
    }
}

private fun AutomationTrigger.holdMillis(): Long = when (this) {
    is AutomationTrigger.SystemEvent -> 0L
    is AutomationTrigger.NumericThreshold -> holdMillis
    is AutomationTrigger.StateEquals -> holdMillis
}

private fun AutomationTrigger.startupBehavior(): AutomationStartupBehavior = when (this) {
    is AutomationTrigger.SystemEvent -> AutomationStartupBehavior.INITIALIZE_ONLY
    is AutomationTrigger.NumericThreshold -> startupBehavior
    is AutomationTrigger.StateEquals -> startupBehavior
}
