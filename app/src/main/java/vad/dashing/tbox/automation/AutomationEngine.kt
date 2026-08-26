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
    private val recentDispatchElapsedMillis = ArrayDeque<Long>()
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
                events.send(EngineEvent.Tick)
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

    suspend fun stop() {
        withContext(NonCancellable) {
            if (!started && !engineJob.isActive) return@withContext
            requestStop()
            engineJob.join()
            signalProvider.stop()
            dispatchGuard.retain(emptySet())
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

    private fun installInitialDefinitions(snapshot: AutomationStoreSnapshot) {
        definitions.clear()
        evaluators.clear()
        snapshot.document.automations.forEach { definition ->
            definitions[definition.id] = definition
            if (definition.enabled && AutomationValidator.validate(definition).isEmpty()) {
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
            if (!definition.enabled || AutomationValidator.validate(definition).isNotEmpty()) {
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
        if (!evaluator.conditionsPass(context)) {
            TboxRepository.addLog(
                "DEBUG",
                LOG_TAG,
                "${definition.name}: conditions rejected trigger=${fire.triggerId}",
            )
            return
        }
        val state = executionStates.getOrPut(definition.id, ::ExecutionState)
        when (definition.runMode) {
            AutomationRunMode.SINGLE -> {
                if (state.active.isNotEmpty()) return
                launchRun(definition, context, state)
            }

            AutomationRunMode.RESTART -> {
                state.active.values.forEach(Job::cancel)
                state.active.clear()
                state.queued.clear()
                launchRun(definition, context, state)
            }

            AutomationRunMode.QUEUED -> {
                if (state.active.isEmpty()) {
                    launchRun(definition, context, state)
                } else if (state.active.size + state.queued.size < definition.maxRuns) {
                    state.queued.addLast(context)
                }
            }

            AutomationRunMode.PARALLEL -> {
                if (state.active.size < definition.maxRuns) {
                    launchRun(definition, context, state)
                }
            }
        }
    }

    private fun launchRun(
        definition: AutomationDefinition,
        context: AutomationTriggerContext,
        state: ExecutionState,
    ) {
        if (!acquireGlobalDispatchBudget()) {
            TboxRepository.addLog(
                "ERROR",
                LOG_TAG,
                "Global loop guard blocked ${definition.name}",
            )
            return
        }
        val nowElapsed = SystemClock.elapsedRealtime()
        if (!dispatchGuard.tryAcquire(definition.id, nowElapsed)) {
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
                "${definition.name}: started trigger=${context.triggerId}",
            )
            var result: AutomationActionResult? = null
            var cancelled = false
            try {
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
                val shouldDisable = !cancelled &&
                    dispatchGuard.recordOutcome(definition.id, success)
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
            definition.enabled &&
            definition.runMode == AutomationRunMode.QUEUED &&
            state.active.isEmpty() &&
            state.queued.isNotEmpty()
        ) {
            val context = state.queued.removeFirst()
            launchRun(definition, context, state)
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

    private fun acquireGlobalDispatchBudget(
        nowElapsedMillis: Long = SystemClock.elapsedRealtime(),
    ): Boolean {
        while (
            recentDispatchElapsedMillis.isNotEmpty() &&
            nowElapsedMillis - recentDispatchElapsedMillis.first() > LOOP_GUARD_WINDOW_MS
        ) {
            recentDispatchElapsedMillis.removeFirst()
        }
        if (recentDispatchElapsedMillis.size >= LOOP_GUARD_MAX_RUNS) return false
        recentDispatchElapsedMillis.addLast(nowElapsedMillis)
        return true
    }

    private fun collectSignalInterests(
        definitions: Collection<AutomationDefinition>,
    ): Set<AutomationSignalKey> =
        definitions.asSequence()
            .filter { it.enabled && AutomationValidator.validate(it).isEmpty() }
            .flatMap { it.signalInterests().asSequence() }
            .toSet()

    companion object {
        private const val LOG_TAG = "Automation"
        private const val TICK_MS = 250L
        private const val EVENT_BUFFER_CAPACITY = 256
        private const val LOOP_GUARD_WINDOW_MS = 10_000L
        private const val LOOP_GUARD_MAX_RUNS = 20
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
        -> Unit

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
