package vad.dashing.tbox.mbcan

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object MbCanJobManager {
    private const val NORMAL_POLL_MS = 30_000L
    private const val BURST_POLL_MS = 1_500L
    private const val BURST_DURATION_MS = 15_000L

    private val mutex = Mutex()
    private var scope: CoroutineScope? = null
    private val activeSignals = mutableSetOf<MbCanSignal>()
    private val activeTypeRefCounts = mutableMapOf<String, Int>()
    private val burstUntil = mutableMapOf<MbCanSignal, Long>()
    private var pollJob: Job? = null

    suspend fun attach(serviceScope: CoroutineScope) {
        mutex.withLock {
            scope = serviceScope
            MbCanDiagnostics.log("DEBUG", "jobManager attach activeSignals=${activeSignals.joinToString()}")
            ensurePollJobLocked()
        }
    }

    suspend fun detach() {
        mutex.withLock {
            MbCanDiagnostics.log("DEBUG", "jobManager detach signals=${activeSignals.joinToString()}")
            pollJob?.cancel()
            pollJob = null
            if (MbCanEngineFacade.isInitialized()) {
                activeTypeRefCounts.keys.forEach { typeName ->
                    MbCanEngineFacade.unSubscribe(setOf(typeName))
                }
            }
            activeTypeRefCounts.clear()
            scope = null
        }
    }

    suspend fun onEngineInitialized() {
        val hasActive = mutex.withLock {
            resubscribeActiveTypesLocked()
            activeSignals.isNotEmpty()
        }
        MbCanEngineFacade.syncVehicleCfgCmdListener(hasActive)
    }

    /**
     * Re-issues OEM [MbCanEngineFacade.subscribe] for every retained type.
     *
     * Needed when a listener bridge (settings telemetry / steering `mVehicletener`)
     * calls [MbCanEngineFacade.ensureInitialized] during [replaceSignals]: the old
     * "defer until init" path then never replayed, so e.g. `eMBCAN_VEHICLE_STEERING_ANGLE`
     * stayed unsubscribed → A9 push dead, only 30 s poll. Idempotent (OEM dedups).
     */
    suspend fun ensureOemSubscriptions() {
        mutex.withLock {
            resubscribeActiveTypesLocked()
        }
    }

    private fun resubscribeActiveTypesLocked() {
        activeTypeRefCounts.keys.forEach { typeName ->
            // subscribe() ensureInitializes; OEM dedups already-subscribed types.
            MbCanEngineFacade.subscribe(setOf(typeName))
            MbCanDiagnostics.log("DEBUG", "ensure-subscribed type=$typeName")
        }
    }

    suspend fun replaceSignals(signals: Set<MbCanSignal>) {
        mutex.withLock {
            val toAdd = signals - activeSignals
            val toRemove = activeSignals - signals
            MbCanDiagnostics.log(
                "DEBUG",
                "replaceSignals active=${activeSignals.joinToString()} incoming=${signals.joinToString()} add=${toAdd.joinToString()} remove=${toRemove.joinToString()}"
            )
            toAdd.forEach { signal ->
                activeSignals.add(signal)
                signal.subscribeDataTypes.forEach { typeName ->
                    val newCount = (activeTypeRefCounts[typeName] ?: 0) + 1
                    activeTypeRefCounts[typeName] = newCount
                    if (newCount == 1) {
                        // subscribe() itself ensureInitializes — do not defer. Deferring
                        // while a later listener path inits the engine left types orphaned.
                        MbCanEngineFacade.subscribe(setOf(typeName))
                        MbCanDiagnostics.log("DEBUG", "subscribed type=$typeName via signal=$signal")
                    } else {
                        MbCanDiagnostics.log("DEBUG", "type ref++ type=$typeName count=$newCount via signal=$signal")
                    }
                }
            }
            toRemove.forEach { signal ->
                activeSignals.remove(signal)
                signal.subscribeDataTypes.forEach { typeName ->
                    val currentCount = activeTypeRefCounts[typeName] ?: 0
                    if (currentCount <= 1) {
                        activeTypeRefCounts.remove(typeName)
                        MbCanEngineFacade.unSubscribe(setOf(typeName))
                        MbCanDiagnostics.log("DEBUG", "unsubscribed type=$typeName via signal=$signal")
                    } else {
                        val nextCount = currentCount - 1
                        activeTypeRefCounts[typeName] = nextCount
                        MbCanDiagnostics.log("DEBUG", "type ref-- type=$typeName count=$nextCount via signal=$signal")
                    }
                }
                burstUntil.remove(signal)
            }
            if (activeSignals.isEmpty()) {
                pollJob?.cancel()
                pollJob = null
            } else {
                ensurePollJobLocked()
            }
        }
    }

    suspend fun requestBurst(signal: MbCanSignal) {
        mutex.withLock {
            val until = System.currentTimeMillis() + BURST_DURATION_MS
            burstUntil[signal] = until
            MbCanDiagnostics.log("DEBUG", "requestBurst signal=$signal until=$until")
            // Wake the shared poll so a write is not stuck behind a 30 s delay.
            pollJob?.cancel()
            pollJob = null
            ensurePollJobLocked()
        }
    }

    private fun ensurePollJobLocked() {
        val currentScope = scope ?: return
        if (activeSignals.isEmpty()) return
        if (pollJob?.isActive == true) return
        pollJob = currentScope.launch {
            while (isActive) {
                val snapshot = mutex.withLock { activeSignals.toList() }
                snapshot.forEach { signal ->
                    if (!isActive) return@launch
                    MbCanRepository.refreshSignal(signal)
                }
                val delayMs = mutex.withLock {
                    val now = System.currentTimeMillis()
                    val expired = burstUntil.entries.filter { it.value <= now }.map { it.key }
                    expired.forEach { burstUntil.remove(it) }
                    if (burstUntil.isNotEmpty()) BURST_POLL_MS else NORMAL_POLL_MS
                }
                delay(delayMs)
            }
        }
    }
}
