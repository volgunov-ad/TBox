package vad.dashing.tbox.automation

import android.content.Context
import android.os.SystemClock
import java.util.UUID
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vad.dashing.tbox.AppDataManager
import vad.dashing.tbox.SettingsManager
import vad.dashing.tbox.TboxRepository

class AutomationEngine(
    context: Context,
    private val settingsManager: SettingsManager,
    appDataManager: AppDataManager,
    parentCoroutineContext: CoroutineContext,
    private val systemEventBaselineSequence: Long,
    serviceActions: AutomationServiceActions,
) {
    private sealed interface EngineEvent {
        data class Signal(val sample: AutomationSignalSample) : EngineEvent
        data class System(val event: AutomationSystemEvent) : EngineEvent
        data class Definitions(val snapshot: AutomationStoreSnapshot) : EngineEvent
        data class RunFinished(val automationId: String, val runId: String) : EngineEvent
        data class RunNow(val automationId: String) : EngineEvent
        data object Tick : EngineEvent
    }

    private data class ExecutionState(
        val active: MutableMap<String, Job> = linkedMapOf(),
        val queued: ArrayDeque<AutomationTriggerContext> = ArrayDeque(),
    )

    private val appContext = context.applicationContext
    private val engineJob = SupervisorJob(parentCoroutineContext[Job])
    private val scope = CoroutineScope(parentCoroutineContext + engineJob)
    private val store = AutomationStore(appContext)
    private val actionExecutor = AutomationActionExecutor(
        appContext,
        settingsManager,
        appDataManager,
        serviceActions,
    )
    private val events = Channel<EngineEvent>(EVENT_BUFFER_CAPACITY)
    private val signalProvider = AutomationSignalProvider(scope) { sample ->
        events.send(EngineEvent.Signal(sample))
    }
    private val evaluators = linkedMapOf<String, AutomationEvaluator>()
    private val definitions = linkedMapOf<String, AutomationDefinition>()
    private val executionStates = mutableMapOf<String, ExecutionState>()
    private val dispatchGuard = AutomationDispatchGuard()
    private val loopGuard = AutomationGlobalLoopGuard()
    @Volatile
    private var latestSamples: Map<AutomationSignalKey, AutomationSignalValue> = emptyMap()
    private var started = false
    private var serviceReady = false

    suspend fun start() {
        if (started) return
        started = true
        val initial = store.snapshots.first()
        installInitialDefinitions(initial)
        scope.launch { eventLoop() }
        scope.launch {
            store.snapshots.collect { events.send(EngineEvent.Definitions(it)) }
        }
        scope.launch {
            while (true) {
                delay(TICK_MS)
                if (needsPeriodicTick()) {
                    events.send(EngineEvent.Tick)
                }
            }
        }
        scope.launch {
            AutomationSystemEventBus.eventsAfter(systemEventBaselineSequence).collect {
                events.send(EngineEvent.System(it.event))
            }
        }
        if (initial.loadError != null) {
            TboxRepository.addLog("ERROR", LOG_TAG, "Configuration: ${initial.loadError}")
        }
        scope.launch {
            store.disableInvalidEnabled().onSuccess { disabledCount ->
                if (disabledCount > 0) {
                    TboxRepository.addLog(
                        "WARN",
                        LOG_TAG,
                        "Disabled $disabledCount invalid automation(s); others stay active",
                    )
                }
            }
        }
    }

    fun notifyBackgroundServiceStarted() {
        if (started && !serviceReady) {
            serviceReady = true
            events.trySend(EngineEvent.System(AutomationSystemEvent.BACKGROUND_SERVICE_STARTED))
            scope.launch {
                signalProvider.replaceInterests(collectSignalInterests(definitions.values))
            }
        }
    }

    /** Execute saved actions now: skip trigger matching and top-level conditions. */
    fun requestRunNow(automationId: String) {
        if (!started) {
            TboxRepository.addLog("WARN", LOG_TAG, "Run now ignored: engine not started")
            AutomationRuntimeState.markRejected(
                automationId,
                "Фоновая служба ещё не готова",
                System.currentTimeMillis(),
            )
            return
        }
        val sent = events.trySend(EngineEvent.RunNow(automationId))
        if (!sent.isSuccess) {
            TboxRepository.addLog("WARN", LOG_TAG, "Run now dropped: event queue full")
            AutomationRuntimeState.markRejected(
                automationId,
                "Очередь запусков переполнена",
                System.currentTimeMillis(),
            )
        }
    }

    suspend fun stop() {
        withContext(NonCancellable) {
            if (!started && !engineJob.isActive) return@withContext
            requestStop()
            engineJob.join()
            signalProvider.stop()
            dispatchGuard.retain(emptySet())
            loopGuard.clear()
            executionStates.values.forEach { state ->
                state.queued.clear()
            }
            executionStates.clear()
            events.close()
        }
    }

    fun requestStop() {
        started = false
        engineJob.cancel()
    }

    /** Sync CAN / foreground-app release when [stop] cannot run (service [android.app.Service.onDestroy]). */
    fun releaseInterests() {
        signalProvider.stop()
    }

    private fun needsPeriodicTick(): Boolean = evaluators.values.any { it.needsPeriodicTick() }

    private fun installInitialDefinitions(snapshot: AutomationStoreSnapshot) {
        definitions.clear()
        evaluators.clear()
        snapshot.document.automations.forEach { definition ->
            definitions[definition.id] = definition
            if (AutomationValidator.isRunnable(definition)) {
                evaluators[definition.id] = AutomationEvaluator(
                    definition = definition,
                    allowStartupFire = true,
                )
            }
        }
        AutomationRuntimeState.retainAutomationIds(definitions.keys)
        dispatchGuard.retain(definitions.keys)
    }

    private suspend fun eventLoop() {
        for (event in events) {
            try {
                when (event) {
                    is EngineEvent.Signal -> handleSignal(event.sample)
                    is EngineEvent.System -> handleSystemEvent(event.event)
                    is EngineEvent.Definitions -> handleDefinitionUpdate(event.snapshot)
                    is EngineEvent.RunFinished -> handleRunFinished(event.automationId, event.runId)
                    is EngineEvent.RunNow -> handleRunNow(event.automationId)
                    EngineEvent.Tick -> handleTick()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                TboxRepository.addLog(
                    "ERROR",
                    LOG_TAG,
                    "Event loop: ${error.message ?: error.javaClass.simpleName}",
                )
            }
        }
    }

    private suspend fun handleSignal(sample: AutomationSignalSample) {
        latestSamples = if (sample.value == AutomationSignalValue.Unavailable) {
            latestSamples - sample.key
        } else {
            latestSamples + (sample.key to sample.value)
        }
        definitions.values.forEach { definition ->
            val evaluator = evaluators[definition.id] ?: return@forEach
            val fire = evaluator.onSignalSample(sample) ?: return@forEach
            dispatch(definition, evaluator, fire)
        }
    }

    private suspend fun handleSystemEvent(event: AutomationSystemEvent) {
        definitions.values.forEach { definition ->
            val evaluator = evaluators[definition.id] ?: return@forEach
            val fire = evaluator.onSystemEvent(event) ?: return@forEach
            dispatch(definition, evaluator, fire)
        }
    }

    private suspend fun handleTick() {
        val now = SystemClock.elapsedRealtime()
        definitions.values.forEach { definition ->
            val evaluator = evaluators[definition.id] ?: return@forEach
            val fire = evaluator.onTick(now) ?: return@forEach
            dispatch(definition, evaluator, fire)
        }
    }

    private suspend fun handleDefinitionUpdate(snapshot: AutomationStoreSnapshot) {
        if (snapshot.loadError != null) {
            TboxRepository.addLog("ERROR", LOG_TAG, "Configuration update: ${snapshot.loadError}")
        }
        val incoming = snapshot.document.automations.associateBy { it.id }
        val removedOrChanged = definitions.keys.filter { id -> definitions[id] != incoming[id] }.toSet()
        removedOrChanged.forEach { id ->
            cancelExecutions(id)
            dispatchGuard.clear(id)
        }

        val nextEvaluators = linkedMapOf<String, AutomationEvaluator>()
        snapshot.document.automations.forEach { definition ->
            if (!AutomationValidator.isRunnable(definition)) {
                return@forEach
            }
            val previousDefinition = definitions[definition.id]
            val previousEvaluator = evaluators[definition.id]
            if (previousDefinition == definition && previousEvaluator != null) {
                nextEvaluators[definition.id] = previousEvaluator
            } else {
                val evaluator = AutomationEvaluator(
                    definition = definition,
                    // Enabling/editing always establishes a baseline and never runs immediately.
                    allowStartupFire = false,
                )
                latestSamples.forEach { (key, value) ->
                    if (key in definition.signalInterests()) {
                        evaluator.onSignalSample(
                            AutomationSignalSample(
                                key = key,
                                value = value,
                                observedAtElapsedMillis = SystemClock.elapsedRealtime(),
                            ),
                        )
                    }
                }
                nextEvaluators[definition.id] = evaluator
            }
        }

        definitions.clear()
        snapshot.document.automations.forEach { definitions[it.id] = it }
        evaluators.clear()
        evaluators.putAll(nextEvaluators)
        AutomationRuntimeState.retainAutomationIds(definitions.keys)
        dispatchGuard.retain(definitions.keys)

        val interests = collectSignalInterests(definitions.values)
        latestSamples = latestSamples.filterKeys { it in interests }
        if (serviceReady) {
            signalProvider.replaceInterests(interests)
        }
    }

    private suspend fun dispatch(
        definition: AutomationDefinition,
        evaluator: AutomationEvaluator,
        fire: AutomationTriggerFire,
    ) {
        if (!definition.enabled) return
        val context = AutomationTriggerContext(
            automationId = definition.id,
            triggerId = fire.triggerId,
            firedAtEpochMillis = System.currentTimeMillis(),
            oldValue = fire.oldValue,
            newValue = fire.newValue,
        )
        if (!evaluator.isReadyToRun(context) && definition.conditionWaitMillis <= 0L) {
            TboxRepository.addLog(
                "DEBUG",
                LOG_TAG,
                "${definition.name}: conditions rejected trigger=${fire.triggerId}",
            )
            return
        }
        val state = executionStates.getOrPut(definition.id, ::ExecutionState)
        when (
            AutomationRunAdmissionPolicy.decide(
                runMode = definition.runMode,
                activeCount = state.active.size,
                queuedCount = state.queued.size,
                maxRuns = definition.maxRuns,
            )
        ) {
            AutomationRunAdmission.SKIP -> return
            AutomationRunAdmission.LAUNCH ->
                launchRun(definition, evaluator, context, state)
            AutomationRunAdmission.ENQUEUE ->
                state.queued.addLast(context)
            AutomationRunAdmission.CANCEL_ACTIVE_AND_LAUNCH -> {
                state.active.values.forEach(Job::cancel)
                state.active.clear()
                state.queued.clear()
                launchRun(definition, evaluator, context, state)
            }
        }
    }

    private fun handleRunNow(automationId: String) {
        val definition = definitions[automationId]
        val reason = AutomationRunNow.rejection(definition)
        if (reason != null) {
            AutomationRuntimeState.markRejected(
                automationId,
                reason,
                System.currentTimeMillis(),
            )
            TboxRepository.addLog(
                "WARN",
                LOG_TAG,
                "${definition?.name ?: automationId}: $reason",
            )
            return
        }
        val def = requireNotNull(definition)
        if (!loopGuard.tryAcquire(SystemClock.elapsedRealtime())) {
            TboxRepository.addLog("ERROR", LOG_TAG, "Global loop guard blocked ${def.name}")
            AutomationRuntimeState.markRejected(
                automationId,
                "Слишком много запусков, подождите",
                System.currentTimeMillis(),
            )
            return
        }
        val nowElapsed = SystemClock.elapsedRealtime()
        if (!dispatchGuard.tryAcquire(def.id, nowElapsed)) {
            val message = "Подождите ${AUTOMATION_MIN_LAUNCH_INTERVAL_MS / 1000} с после предыдущего запуска"
            TboxRepository.addLog("WARN", LOG_TAG, "${def.name}: $message")
            AutomationRuntimeState.markRejected(automationId, message, System.currentTimeMillis())
            return
        }
        val state = executionStates.getOrPut(def.id, ::ExecutionState)
        state.active.values.forEach(Job::cancel)
        state.active.clear()
        state.queued.clear()
        val context = AutomationTriggerContext(
            automationId = def.id,
            triggerId = AutomationRunNow.triggerId(def),
            firedAtEpochMillis = System.currentTimeMillis(),
        )
        launchRun(
            definition = def,
            evaluator = evaluators[def.id],
            context = context,
            state = state,
            skipGuards = true,
            skipConditions = true,
            countFailures = false,
        )
    }

    private fun launchRun(
        definition: AutomationDefinition,
        evaluator: AutomationEvaluator?,
        context: AutomationTriggerContext,
        state: ExecutionState,
        skipGuards: Boolean = false,
        skipConditions: Boolean = false,
        countFailures: Boolean = true,
    ) {
        if (!skipGuards && !loopGuard.tryAcquire(SystemClock.elapsedRealtime())) {
            TboxRepository.addLog(
                "ERROR",
                LOG_TAG,
                "Global loop guard blocked ${definition.name}",
            )
            return
        }
        val nowElapsed = SystemClock.elapsedRealtime()
        if (!skipGuards && !dispatchGuard.tryAcquire(definition.id, nowElapsed)) {
            TboxRepository.addLog(
                "WARN",
                LOG_TAG,
                "${definition.name}: skipped, cooldown ${AUTOMATION_MIN_LAUNCH_INTERVAL_MS} мс",
            )
            return
        }
        val runId = UUID.randomUUID().toString()
        val job = scope.launch {
            AutomationRuntimeState.markStarted(
                automationId = definition.id,
                triggerId = context.triggerId,
                startedAtEpochMillis = context.firedAtEpochMillis,
            )
            TboxRepository.addLog(
                "INFO",
                LOG_TAG,
                "${definition.name}: started trigger=${context.triggerId}" +
                    if (skipConditions) " (run now)" else "",
            )
            var result: AutomationActionResult? = null
            var cancelled = false
            try {
                val conditionsReady = if (skipConditions) {
                    true
                } else {
                    val readyEvaluator = requireNotNull(evaluator) {
                        "Automation evaluator required unless skipConditions"
                    }
                    awaitAutomationConditionWindow(
                        waitMillis = definition.conditionWaitMillis,
                        isReady = { readyEvaluator.isReadyToRun(context) },
                        nowElapsedMillis = { SystemClock.elapsedRealtime() },
                        delayFor = { delay(it) },
                        pollMillis = TICK_MS,
                    )
                }
                if (!conditionsReady) {
                    TboxRepository.addLog(
                        "DEBUG",
                        LOG_TAG,
                        "${definition.name}: condition wait timed out trigger=${context.triggerId}",
                    )
                    result = AutomationActionResult.ok("Условие не выполнилось за время ожидания")
                } else {
                    result = actionExecutor.execute(
                        actions = definition.actions,
                        context = context,
                        signalSnapshot = { latestSamples.toMap() },
                    )
                    TboxRepository.addLog(
                        if (result.success) "INFO" else "ERROR",
                        LOG_TAG,
                        "${definition.name}: ${result.message}",
                    )
                }
            } catch (cancelledRun: CancellationException) {
                cancelled = true
                throw cancelledRun
            } catch (error: Exception) {
                result = AutomationActionResult.failure(
                    error.message ?: error.javaClass.simpleName,
                )
                TboxRepository.addLog(
                    "ERROR",
                    LOG_TAG,
                    "${definition.name}: ${result.message}",
                )
            } finally {
                val success = result?.success == true
                val shouldDisable = when {
                    cancelled -> false
                    countFailures -> dispatchGuard.recordOutcome(definition.id, success)
                    else -> {
                        if (success) {
                            dispatchGuard.recordOutcome(definition.id, true)
                        }
                        false
                    }
                }
                val message = when {
                    shouldDisable ->
                        "Отключена после ${AUTOMATION_MAX_CONSECUTIVE_FAILURES} ошибок подряд"
                    cancelled -> result?.message ?: "Выполнение отменено"
                    else -> result?.message.orEmpty()
                }
                AutomationRuntimeState.markFinished(
                    automationId = definition.id,
                    success = success,
                    message = message,
                    finishedAtEpochMillis = System.currentTimeMillis(),
                )
                if (shouldDisable) {
                    TboxRepository.addLog("ERROR", LOG_TAG, "${definition.name}: $message")
                    cancelExecutions(definition.id)
                    scope.launch {
                        store.setEnabled(definition.id, false)
                            .onFailure { error ->
                                TboxRepository.addLog(
                                    "ERROR",
                                    LOG_TAG,
                                    "Auto-disable failed: ${error.message ?: error.javaClass.simpleName}",
                                )
                            }
                    }
                }
                events.trySend(EngineEvent.RunFinished(definition.id, runId))
            }
        }
        state.active[runId] = job
    }

    private fun handleRunFinished(automationId: String, runId: String) {
        val state = executionStates[automationId] ?: return
        state.active.remove(runId)
        val definition = definitions[automationId]
        val evaluator = evaluators[automationId]
        if (
            definition != null &&
            evaluator != null &&
            AutomationRunAdmissionPolicy.shouldLaunchQueuedNext(
                runMode = definition.runMode,
                enabled = definition.enabled,
                activeCount = state.active.size,
                queuedCount = state.queued.size,
            )
        ) {
            val context = state.queued.removeFirst()
            launchRun(definition, evaluator, context, state)
        }
        if (state.active.isEmpty() && state.queued.isEmpty()) {
            executionStates.remove(automationId)
        }
    }

    private fun cancelExecutions(automationId: String) {
        val state = executionStates.remove(automationId) ?: return
        state.active.values.forEach(Job::cancel)
        state.queued.clear()
    }

    private fun collectSignalInterests(
        definitions: Collection<AutomationDefinition>,
    ): Set<AutomationSignalKey> =
        definitions.asSequence()
            .filter { AutomationValidator.isRunnable(it) }
            .flatMap { it.signalInterests().asSequence() }
            .toSet()

    companion object {
        private const val LOG_TAG = "Automation"
        private const val TICK_MS = 250L
        private const val EVENT_BUFFER_CAPACITY = 256
    }
}

private fun AutomationDefinition.signalInterests(): Set<AutomationSignalKey> = buildSet {
    triggers.forEach { trigger ->
        when (trigger) {
            is AutomationTrigger.SystemEvent -> Unit
            is AutomationTrigger.NumericThreshold ->
                add(AutomationSignalKey(trigger.signal, trigger.source))

            is AutomationTrigger.StateEquals ->
                add(AutomationSignalKey(trigger.signal, trigger.source))

            is AutomationTrigger.Geofence,
            is AutomationTrigger.Solar,
            -> add(AUTOMATION_GEO_DISPLAY_KEY)
            is AutomationTrigger.Time -> Unit
        }
    }
    conditions.forEach { addConditionInterests(it) }
    actions.forEach { addActionInterests(it) }
}

private fun MutableSet<AutomationSignalKey>.addConditionInterests(
    condition: AutomationCondition,
) {
    when (condition) {
        AutomationCondition.Always,
        is AutomationCondition.TriggeredBy,
        is AutomationCondition.Time,
        -> Unit

        is AutomationCondition.Solar -> add(AUTOMATION_GEO_DISPLAY_KEY)

        is AutomationCondition.Numeric ->
            add(AutomationSignalKey(condition.signal, condition.source))

        is AutomationCondition.State ->
            add(AutomationSignalKey(condition.signal, condition.source))

        is AutomationCondition.All -> condition.conditions.forEach(::addConditionInterests)
        is AutomationCondition.Any -> condition.conditions.forEach(::addConditionInterests)
        is AutomationCondition.Not -> addConditionInterests(condition.condition)
    }
}

private fun MutableSet<AutomationSignalKey>.addActionInterests(action: AutomationAction) {
    when (action) {
        is AutomationAction.IfThenElse -> {
            addConditionInterests(action.condition)
            action.thenActions.forEach(::addActionInterests)
            action.elseActions.forEach(::addActionInterests)
        }

        else -> Unit
    }
}
