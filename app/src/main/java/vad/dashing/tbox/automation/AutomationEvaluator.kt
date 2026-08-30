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
    private var lastCatchUpDateKey: String? = null
    private var timeCatchUpAttempted = false
    private var solarCatchUpAttempted = false
    private var lastKnownLatitude: Double? = null
    private var lastKnownLongitude: Double? = null

    /** Clock / hold completion — skip the 250 ms engine tick when nothing is waiting. */
    fun needsPeriodicTick(): Boolean {
        if (definition.triggers.any { it is AutomationTrigger.Time || it is AutomationTrigger.Solar }) {
            return true
        }
        return triggerStates.values.any { it.matchingSinceElapsedMillis != null }
    }

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
                    invalidate(
                        triggerStates.getValue(trigger.id),
                        rearm = trigger.rearmsOnUnavailable(),
                    )
                }
            }
            return null
        }
        latestSamples[sample.key] = sample.value
        rememberSolarPosition(sample.value)
        val candidates = mutableListOf<ReadyCandidate>()
        addSolarCatchUpCandidates(sample.observedAtElapsedMillis, candidates)
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
        addTimeCatchUpCandidates(nowElapsedMillis, candidates)
        addSolarCatchUpCandidates(nowElapsedMillis, candidates)
        val minuteChanged = consumeMinuteChange()
        if (minuteChanged) {
            addTimeTriggerCandidates(nowElapsedMillis, candidates)
            addSolarTriggerCandidates(nowElapsedMillis, candidates)
        }
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
        evaluateCondition(
            it,
            context,
            latestSamples,
            clock.wallTime(),
            lastKnownLatitude,
            lastKnownLongitude,
        )
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
            is AutomationTrigger.Solar,
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

    private fun invalidate(state: RuntimeState, rearm: Boolean = false) {
        if (state.initialized) {
            state.initialFireAllowed = false
        }
        // Keep initialized. A brief Unavailable must break the hold timer, not treat the
        // next matching sample as a cold-start baseline (that would disarm while still matching).
        // lastNumericValue is kept so "no hysteresis" can tell a repeated number from a new one.
        // StateEquals: no value means "not this state" — rearm so app-gone → app-back can fire.
        if (rearm) {
            state.armed = true
        }
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
            latitude: Double? = null,
            longitude: Double? = null,
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
                        evaluateCondition(it, context, snapshot, wallTime, latitude, longitude)
                    }

                is AutomationCondition.Any ->
                    condition.conditions.any {
                        evaluateCondition(it, context, snapshot, wallTime, latitude, longitude)
                    }

                is AutomationCondition.Not ->
                    !evaluateCondition(
                        condition.condition,
                        context,
                        snapshot,
                        wallTime,
                        latitude,
                        longitude,
                    )

                is AutomationCondition.Time -> AutomationTimeLogic.conditionMatches(
                    after = condition.after,
                    before = condition.before,
                    weekdays = condition.weekdays,
                    wall = wallTime,
                )

                is AutomationCondition.Solar -> {
                    val geo = snapshot[AUTOMATION_GEO_DISPLAY_KEY] as? AutomationSignalValue.Position
                    AutomationSolarLogic.conditionMatches(
                        after = condition.after,
                        before = condition.before,
                        weekdays = condition.weekdays,
                        latitude = geo?.latitude ?: latitude,
                        longitude = geo?.longitude ?: longitude,
                        wall = wallTime,
                    )
                }
            }
        }
    }

    private fun addTimeCatchUpCandidates(
        nowElapsedMillis: Long,
        candidates: MutableList<ReadyCandidate>,
    ) {
        if (!allowStartupFire) return
        val wall = clock.wallTime()
        resetCatchUpIfDateChanged(wall)
        if (timeCatchUpAttempted) return
        timeCatchUpAttempted = true
        definition.triggers.forEachIndexed { index, trigger ->
            val time = trigger as? AutomationTrigger.Time ?: return@forEachIndexed
            if (!AutomationTimeLogic.triggerCatchUp(time.at, time.weekdays, time.startupBehavior, wall)) {
                return@forEachIndexed
            }
            addClockCandidate(index, time.id, nowElapsedMillis, initialFire = true, candidates)
        }
    }

    private fun addSolarCatchUpCandidates(
        nowElapsedMillis: Long,
        candidates: MutableList<ReadyCandidate>,
    ) {
        if (!allowStartupFire) return
        val latitude = lastKnownLatitude ?: return
        val longitude = lastKnownLongitude ?: return
        val wall = clock.wallTime()
        resetCatchUpIfDateChanged(wall)
        if (solarCatchUpAttempted) return
        solarCatchUpAttempted = true
        definition.triggers.forEachIndexed { index, trigger ->
            val solar = trigger as? AutomationTrigger.Solar ?: return@forEachIndexed
            if (!AutomationSolarLogic.triggerCatchUp(solar, latitude, longitude, wall)) {
                return@forEachIndexed
            }
            addClockCandidate(index, solar.id, nowElapsedMillis, initialFire = true, candidates)
        }
    }

    private fun consumeMinuteChange(): Boolean {
        val minuteKey = clock.wallTime().minuteKey
        val changed = lastSeenMinuteKey != minuteKey
        lastSeenMinuteKey = minuteKey
        return changed
    }

    private fun addTimeTriggerCandidates(
        nowElapsedMillis: Long,
        candidates: MutableList<ReadyCandidate>,
    ) {
        val wall = clock.wallTime()
        definition.triggers.forEachIndexed { index, trigger ->
            val time = trigger as? AutomationTrigger.Time ?: return@forEachIndexed
            if (!AutomationTimeLogic.triggerMatches(time.at, time.weekdays, wall)) {
                return@forEachIndexed
            }
            addClockCandidate(index, time.id, nowElapsedMillis, initialFire = false, candidates)
        }
    }

    private fun addSolarTriggerCandidates(
        nowElapsedMillis: Long,
        candidates: MutableList<ReadyCandidate>,
    ) {
        val latitude = lastKnownLatitude ?: return
        val longitude = lastKnownLongitude ?: return
        val wall = clock.wallTime()
        definition.triggers.forEachIndexed { index, trigger ->
            val solar = trigger as? AutomationTrigger.Solar ?: return@forEachIndexed
            if (!AutomationSolarLogic.triggerExact(solar, latitude, longitude, wall)) {
                return@forEachIndexed
            }
            addClockCandidate(index, solar.id, nowElapsedMillis, initialFire = false, candidates)
        }
    }

    private fun addClockCandidate(
        index: Int,
        triggerId: String,
        nowElapsedMillis: Long,
        initialFire: Boolean,
        candidates: MutableList<ReadyCandidate>,
    ) {
        if (candidates.any { it.fire.triggerId == triggerId }) return
        candidates += ReadyCandidate(
            triggerIndex = index,
            fire = AutomationTriggerFire(triggerId, oldValue = null, newValue = null),
            readyAtElapsedMillis = nowElapsedMillis,
            initialFire = initialFire,
        )
    }

    private fun resetCatchUpIfDateChanged(wall: AutomationWallTime) {
        val dateKey = "${wall.year}-${wall.month}-${wall.dayOfMonth}"
        if (lastCatchUpDateKey == dateKey) return
        lastCatchUpDateKey = dateKey
        timeCatchUpAttempted = false
        solarCatchUpAttempted = false
    }

    private fun rememberSolarPosition(value: AutomationSignalValue) {
        val position = value as? AutomationSignalValue.Position ?: return
        if (!position.latitude.isFinite() || !position.longitude.isFinite()) return
        lastKnownLatitude = position.latitude
        lastKnownLongitude = position.longitude
    }
}

private fun AutomationTrigger.usesRearmHysteresis(): Boolean = when (this) {
    is AutomationTrigger.NumericThreshold -> rearmEnabled
    else -> true
}

private fun AutomationTrigger.rearmsOnUnavailable(): Boolean = this is AutomationTrigger.StateEquals

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
    is AutomationTrigger.Geofence,
    is AutomationTrigger.Solar,
    -> AUTOMATION_GEO_DISPLAY_KEY
}

private fun AutomationTrigger.matches(value: AutomationSignalValue): Boolean {
    return when (this) {
        is AutomationTrigger.SystemEvent,
        is AutomationTrigger.Time,
        is AutomationTrigger.Solar,
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
        is AutomationTrigger.Solar,
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
    is AutomationTrigger.Solar,
    -> 0L
    is AutomationTrigger.NumericThreshold -> holdMillis
    is AutomationTrigger.StateEquals -> holdMillis
    is AutomationTrigger.Geofence -> holdMillis
}

private fun AutomationTrigger.startupBehavior(): AutomationStartupBehavior = when (this) {
    is AutomationTrigger.SystemEvent -> AutomationStartupBehavior.INITIALIZE_ONLY
    is AutomationTrigger.NumericThreshold -> startupBehavior
    is AutomationTrigger.StateEquals -> startupBehavior
    is AutomationTrigger.Geofence -> startupBehavior
    is AutomationTrigger.Time -> startupBehavior
    is AutomationTrigger.Solar -> startupBehavior
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
