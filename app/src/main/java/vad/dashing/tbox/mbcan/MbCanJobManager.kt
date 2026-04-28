package vad.dashing.tbox.mbcan

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import vad.dashing.tbox.TboxRepository

object MbCanJobManager {
    private const val NORMAL_POLL_MS = 60_000L
    private const val BURST_POLL_MS = 1_500L
    private const val BURST_DURATION_MS = 15_000L

    private val mutex = Mutex()
    private var scope: CoroutineScope? = null
    private val activeSignals = mutableSetOf<MbCanSignal>()
    private val activeTypeRefCounts = mutableMapOf<String, Int>()
    private val signalJobs = mutableMapOf<MbCanSignal, Job>()
    private val burstUntil = mutableMapOf<MbCanSignal, Long>()

    suspend fun attach(serviceScope: CoroutineScope) {
        mutex.withLock {
            scope = serviceScope
            TboxRepository.addLog("DEBUG", "MBCAN_TMP", "jobManager attach activeSignals=${activeSignals.joinToString()}")
            activeSignals.forEach { ensureSignalJobLocked(it) }
        }
    }

    suspend fun detach() {
        mutex.withLock {
            TboxRepository.addLog("DEBUG", "MBCAN_TMP", "jobManager detach jobs=${signalJobs.keys.joinToString()}")
            signalJobs.values.forEach { it.cancel() }
            signalJobs.clear()
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
            activeTypeRefCounts.keys.forEach { typeName ->
                MbCanEngineFacade.subscribe(setOf(typeName))
                TboxRepository.addLog("DEBUG", "MBCAN_TMP", "late-subscribed type=$typeName")
            }
            activeSignals.isNotEmpty()
        }
        MbCanEngineFacade.syncVehicleCfgCmdListener(hasActive)
    }

    suspend fun replaceSignals(signals: Set<MbCanSignal>) {
        mutex.withLock {
            val toAdd = signals - activeSignals
            val toRemove = activeSignals - signals
            TboxRepository.addLog(
                "DEBUG",
                "MBCAN_TMP",
                "replaceSignals active=${activeSignals.joinToString()} incoming=${signals.joinToString()} add=${toAdd.joinToString()} remove=${toRemove.joinToString()}"
            )
            toAdd.forEach { signal ->
                activeSignals.add(signal)
                signal.subscribeDataTypes.forEach { typeName ->
                    val newCount = (activeTypeRefCounts[typeName] ?: 0) + 1
                    activeTypeRefCounts[typeName] = newCount
                    if (newCount == 1) {
                        if (MbCanEngineFacade.isInitialized()) {
                            MbCanEngineFacade.subscribe(setOf(typeName))
                            TboxRepository.addLog("DEBUG", "MBCAN_TMP", "subscribed type=$typeName via signal=$signal")
                        } else {
                            TboxRepository.addLog("DEBUG", "MBCAN_TMP", "defer subscribe type=$typeName via signal=$signal until engine init")
                        }
                    } else {
                        TboxRepository.addLog("DEBUG", "MBCAN_TMP", "type ref++ type=$typeName count=$newCount via signal=$signal")
                    }
                }
                ensureSignalJobLocked(signal)
            }
            toRemove.forEach { signal ->
                activeSignals.remove(signal)
                signal.subscribeDataTypes.forEach { typeName ->
                    val currentCount = activeTypeRefCounts[typeName] ?: 0
                    if (currentCount <= 1) {
                        activeTypeRefCounts.remove(typeName)
                        if (MbCanEngineFacade.isInitialized()) {
                            MbCanEngineFacade.unSubscribe(setOf(typeName))
                            TboxRepository.addLog("DEBUG", "MBCAN_TMP", "unsubscribed type=$typeName via signal=$signal")
                        } else {
                            TboxRepository.addLog("DEBUG", "MBCAN_TMP", "drop deferred type=$typeName via signal=$signal")
                        }
                    } else {
                        val nextCount = currentCount - 1
                        activeTypeRefCounts[typeName] = nextCount
                        TboxRepository.addLog("DEBUG", "MBCAN_TMP", "type ref-- type=$typeName count=$nextCount via signal=$signal")
                    }
                }
                signalJobs.remove(signal)?.cancel()
                burstUntil.remove(signal)
            }
        }
    }

    suspend fun requestBurst(signal: MbCanSignal) {
        mutex.withLock {
            val until = System.currentTimeMillis() + BURST_DURATION_MS
            burstUntil[signal] = until
            TboxRepository.addLog("DEBUG", "MBCAN_TMP", "requestBurst signal=$signal until=$until")
        }
    }

    private fun ensureSignalJobLocked(signal: MbCanSignal) {
        val currentScope = scope ?: return
        if (signalJobs[signal]?.isActive == true) return
        signalJobs[signal] = currentScope.launch(Dispatchers.IO) {
            while (isActive) {
                MbCanRepository.refreshSignal(signal)
                val now = System.currentTimeMillis()
                val delayMs = mutex.withLock {
                    val inBurst = (burstUntil[signal] ?: 0L) > now
                    if (!inBurst) {
                        burstUntil.remove(signal)
                    }
                    if (inBurst) BURST_POLL_MS else NORMAL_POLL_MS
                }
                delay(delayMs)
            }
        }
    }
}

