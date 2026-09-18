package vad.dashing.tbox.obd

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Owns ELM327 Bluetooth lifecycle, Mode 01 polling for interested PIDs,
 * and on-demand Mode 02 / 03 / 04 / 07 / 09 / 0A requests.
 */
class Elm327Manager(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    companion object {
        private const val TAG = "Elm327Manager"
        private val REOPEN_BACKOFF_MS = longArrayOf(3_000L, 10_000L, 30_000L)
        private const val POLL_IDLE_MS = 250L
        private const val BETWEEN_PIDS_MS = 40L
        private const val PAIRING_RETRY_MS = 60_000L

        /** Consecutive transacts without an ELM `>` prompt before forcing a reconnect. */
        private const val SILENT_FAILURE_LIMIT = 3

        /** Open-but-silent session is dropped after this window while polling is active. */
        private const val STALE_LINK_MS = 30_000L
    }

    /** Transact timed out / got no prompt repeatedly — the RFCOMM link is silently dead. */
    private class Elm327DeadLinkException(message: String) : Exception(message)

    private val sessionMutex = Mutex()
    private var session: Elm327BluetoothSession? = null
    /** In-flight connect/init session; closed immediately on [stop] to abort hung RFCOMM. */
    private val connectingSession = AtomicReference<Elm327BluetoothSession?>(null)
    private var loopJob: Job? = null
    private var deviceAddress: String = ""
    private var pairingPin: String = ""
    private var reopenFailureStreak = 0
    private var silentFailureStreak = 0
    private var lastGoodTransactMs = 0L
    private var lastPairingAttemptMs = 0L
    private val interestedPidIds = AtomicReference<Set<String>>(emptySet())
    private val dtcRequestPending = AtomicBoolean(false)
    private val pendingDtcRequestPending = AtomicBoolean(false)
    private val permanentDtcRequestPending = AtomicBoolean(false)
    private val clearDtcRequestPending = AtomicBoolean(false)
    private val discoveryRequestPending = AtomicBoolean(false)
    private val freezeFrameRequestPending = AtomicBoolean(false)
    private val monitorStatusRequestPending = AtomicBoolean(false)
    private val vinRequestPending = AtomicBoolean(false)
    private val diagPackRequestPending = AtomicBoolean(false)

    /**
     * Invoked on IO after a successful Mode 01 support discovery.
     * [pids] are raw Mode 01 PID bytes reported by the ECU.
     */
    var onPidDiscoverySuccess: (suspend (pids: Set<Int>, atMs: Long) -> Unit)? = null

    /**
     * Invoked when auto-pairing succeeds with a concrete PIN (including one found by
     * trying defaults). Used to persist the PIN for the next launch.
     */
    var onPairingPinResolved: (suspend (pin: String) -> Unit)? = null

    @Volatile
    private var running = false

    fun start(address: String) {
        val mac = address.trim().uppercase()
        if (mac.isEmpty()) {
            ObdRepository.setLastError("no_device")
            ObdRepository.setStatus("no_device")
            return
        }
        if (running && mac == deviceAddress && loopJob?.isActive == true) return
        stopInternal(clearInterest = false)
        deviceAddress = mac
        running = true
        ObdRepository.setLastError(null)
        ObdRepository.setStatus("starting")
        loopJob = scope.launch(Dispatchers.IO) {
            runLoop()
        }
    }

    fun setPairingPin(pin: String) {
        val normalized = pin.trim()
        if (normalized == pairingPin) return
        pairingPin = normalized
        lastPairingAttemptMs = 0L
    }

    fun stop() {
        stopInternal(clearInterest = true)
        ObdRepository.setStatus("stopped")
    }

    private fun stopInternal(clearInterest: Boolean) {
        running = false
        loopJob?.cancel()
        loopJob = null
        if (clearInterest) {
            interestedPidIds.set(emptySet())
        }
        connectingSession.getAndSet(null)?.close()
        scope.launch(Dispatchers.IO) {
            sessionMutex.withLock {
                session?.close()
                session = null
            }
        }
        ObdRepository.resetConnectionState()
    }

    fun setInterestedPids(pidIds: Set<String>) {
        interestedPidIds.set(pidIds.map { ObdPid.normalizeId(it) }.toSet())
    }

    fun requestStoredDtcs() {
        if (!ObdRepository.connected.value) {
            ObdRepository.setDtcError("not_connected")
            return
        }
        dtcRequestPending.set(true)
    }

    fun requestPendingDtcs() {
        if (!ObdRepository.connected.value) {
            ObdRepository.setDtcError("not_connected")
            return
        }
        pendingDtcRequestPending.set(true)
    }

    fun requestClearDtcs() {
        if (!ObdRepository.connected.value) {
            ObdRepository.setDtcError("not_connected")
            return
        }
        clearDtcRequestPending.set(true)
    }

    fun requestPidDiscovery() {
        if (!ObdRepository.connected.value) {
            ObdRepository.setDiscoveryError("not_connected")
            return
        }
        discoveryRequestPending.set(true)
    }

    fun requestFreezeFrame() {
        if (!ObdRepository.connected.value) {
            ObdRepository.setFreezeFrameError("not_connected")
            return
        }
        freezeFrameRequestPending.set(true)
    }

    fun requestMonitorStatus() {
        if (!ObdRepository.connected.value) {
            ObdRepository.setMonitorError("not_connected")
            return
        }
        monitorStatusRequestPending.set(true)
    }

    fun requestPermanentDtcs() {
        if (!ObdRepository.connected.value) {
            ObdRepository.setDtcError("not_connected")
            return
        }
        permanentDtcRequestPending.set(true)
    }

    fun requestVin() {
        if (!ObdRepository.connected.value) {
            ObdRepository.setVinError("not_connected")
            return
        }
        vinRequestPending.set(true)
    }

    /**
     * One-shot diagnostic pack: monitors → stored/pending/permanent DTC → freeze frame → VIN.
     * Does not export a file (UI exports after completion).
     */
    fun requestDiagPack() {
        if (!ObdRepository.connected.value) {
            ObdRepository.setDiagPackError("not_connected")
            return
        }
        diagPackRequestPending.set(true)
    }

    private fun hasHighPriorityRequest(): Boolean =
        dtcRequestPending.get() ||
            pendingDtcRequestPending.get() ||
            permanentDtcRequestPending.get() ||
            clearDtcRequestPending.get() ||
            discoveryRequestPending.get() ||
            freezeFrameRequestPending.get() ||
            monitorStatusRequestPending.get() ||
            vinRequestPending.get() ||
            diagPackRequestPending.get()

    private suspend fun runLoop() {
        while (scope.isActive && running) {
            try {
                ensureSession()
                val sess = sessionMutex.withLock { session } ?: continue
                if (diagPackRequestPending.getAndSet(false)) {
                    runDiagPack(sess)
                }
                if (discoveryRequestPending.getAndSet(false)) {
                    runPidDiscovery(sess)
                }
                if (clearDtcRequestPending.getAndSet(false)) {
                    clearDtcs(sess)
                }
                if (dtcRequestPending.getAndSet(false)) {
                    readDtcs(sess, kind = DtcKind.STORED)
                }
                if (pendingDtcRequestPending.getAndSet(false)) {
                    readDtcs(sess, kind = DtcKind.PENDING)
                }
                if (permanentDtcRequestPending.getAndSet(false)) {
                    readDtcs(sess, kind = DtcKind.PERMANENT)
                }
                if (freezeFrameRequestPending.getAndSet(false)) {
                    readFreezeFrame(sess)
                }
                if (monitorStatusRequestPending.getAndSet(false)) {
                    readMonitorStatus(sess)
                }
                if (vinRequestPending.getAndSet(false)) {
                    readVin(sess)
                }
                // On-demand handlers swallow exceptions into their own error flows;
                // re-check the dead-link streak here so the loop still reconnects.
                if (silentFailureStreak >= SILENT_FAILURE_LIMIT) {
                    throw Elm327DeadLinkException("no ELM response x$silentFailureStreak")
                }
                pollInterested(sess)
                delay(POLL_IDLE_MS)
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                Log.w(TAG, "loop error: ${e.message}")
                ObdRepository.setConnected(false)
                ObdRepository.setLastError(e.message ?: e.javaClass.simpleName)
                ObdRepository.setStatus("reconnecting")
                sessionMutex.withLock {
                    session?.close()
                    session = null
                }
                val wait = REOPEN_BACKOFF_MS[
                    reopenFailureStreak.coerceIn(0, REOPEN_BACKOFF_MS.lastIndex),
                ]
                reopenFailureStreak++
                delay(wait)
            }
        }
    }

    private suspend fun ensureSession() {
        sessionMutex.withLock {
            val existing = session
            if (existing != null && existing.isOpen) {
                // A socket can stay "connected" after a silent RFCOMM drop. While
                // polling is active, a long window without any adapter response
                // means the link is dead — drop it and reconnect below.
                val polling = interestedPidIds.get().isNotEmpty()
                val silentForMs = System.currentTimeMillis() - lastGoodTransactMs
                if (!polling || silentForMs <= STALE_LINK_MS) return
                existing.close()
                session = null
                Log.w(TAG, "stale ELM link: ${silentForMs}ms without response; reconnecting")
                ObdRepository.setConnected(false)
                ObdRepository.setStatus("reconnecting")
            }
        }
        // Do not hold [sessionMutex] across connect/init — otherwise stop() cannot close a hung socket.
        ObdRepository.setStatus("connecting")
        ObdRepository.setConnected(false)
        maybePairDevice()
        if (!running) return
        val s = Elm327BluetoothSession(deviceAddress)
        connectingSession.set(s)
        try {
            withContext(Dispatchers.IO) {
                s.open()
                if (!running) {
                    s.close()
                    return@withContext
                }
                val initRsp = s.runInit()
                Log.i(TAG, "init: $initRsp")
                ObdRepository.setAdapterVersion(Elm327Protocol.parseAdapterVersion(initRsp))
                refreshProtocolInfo(s)
            }
            if (!running) {
                s.close()
                return
            }
            sessionMutex.withLock {
                if (!running) {
                    s.close()
                    return
                }
                session = s
            }
            reopenFailureStreak = 0
            silentFailureStreak = 0
            lastGoodTransactMs = System.currentTimeMillis()
            ObdRepository.setConnected(true)
            ObdRepository.setStatus("connected")
            ObdRepository.setLastError(null)
        } catch (e: Exception) {
            s.close()
            throw e
        } finally {
            connectingSession.compareAndSet(s, null)
        }
    }

    private suspend fun maybePairDevice() {
        val now = System.currentTimeMillis()
        if (now - lastPairingAttemptMs < PAIRING_RETRY_MS) return
        lastPairingAttemptMs = now
        if (Elm327BtPairing.isBonded(deviceAddress)) return
        ObdRepository.setStatus("pairing")
        val result = Elm327BtPairing.ensureBonded(context, deviceAddress, pairingPin)
        Log.i(
            TAG,
            "pairing ensureBonded=${result.success} usedPin=${result.usedPin != null} for $deviceAddress",
        )
        val resolved = result.usedPin?.trim().orEmpty()
        if (result.success && resolved.isNotEmpty() && resolved != pairingPin) {
            pairingPin = resolved
            onPairingPinResolved?.invoke(resolved)
        }
        ObdRepository.setStatus("connecting")
        // Still attempt RFCOMM even if bonding failed — some stacks allow insecure connect.
        if (!result.success) {
            Log.w(TAG, "not bonded; trying insecure RFCOMM anyway")
        }
    }

    private suspend fun pollInterested(sess: Elm327BluetoothSession) {
        val ids = interestedPidIds.get()
        if (ids.isEmpty()) return
        for (id in ids) {
            if (!running || hasHighPriorityRequest()) {
                return
            }
            val pid = ObdPid.fromId(id) ?: continue
            withContext(Dispatchers.IO) {
                when (pid) {
                    ObdPid.ADAPTER_VOLTAGE -> {
                        val raw = timedTransact(sess, Elm327Protocol.ADAPTER_VOLTAGE_REQUEST)
                        val v = Elm327Protocol.parseAdapterVoltage(raw)
                        if (v != null) {
                            ObdRepository.setAdapterVoltage(v)
                            ObdRepository.putValue(pid.id, v)
                        }
                    }
                    else -> {
                        val modePid = pid.mode01Pid ?: return@withContext
                        val raw = timedTransact(sess, Elm327Protocol.mode01Request(modePid))
                        val data = Elm327Protocol.parseMode01DataBytes(raw, modePid)
                            ?: return@withContext
                        val value = pid.decodeMode01(data) ?: return@withContext
                        ObdRepository.putValue(pid.id, value)
                    }
                }
            }
            delay(BETWEEN_PIDS_MS)
        }
    }

    private fun timedTransact(
        sess: Elm327BluetoothSession,
        command: String,
        timeoutMs: Long = 4_000L,
    ): String {
        val t0 = System.currentTimeMillis()
        val raw = sess.transact(command, timeoutMs = timeoutMs)
        val elapsed = (System.currentTimeMillis() - t0).coerceAtLeast(0L)
        if (Elm327Protocol.isSilentTimeout(raw, elapsed, timeoutMs)) {
            // No `>` prompt: the adapter never answered. Count it — a silently dead
            // RFCOMM link throws no IOException, so without this the loop hangs forever.
            silentFailureStreak++
            ObdRepository.noteBusError(elapsed)
            if (silentFailureStreak >= SILENT_FAILURE_LIMIT) {
                throw Elm327DeadLinkException("no ELM response x$silentFailureStreak ($command)")
            }
        } else {
            silentFailureStreak = 0
            lastGoodTransactMs = System.currentTimeMillis()
            if (Elm327Protocol.isElmError(raw)) {
                ObdRepository.noteBusError(elapsed)
            } else {
                ObdRepository.noteBusOk(elapsed)
            }
        }
        return raw
    }

    private fun refreshProtocolInfo(sess: Elm327BluetoothSession) {
        runCatching {
            val desc = timedTransact(sess, Elm327Protocol.PROTOCOL_DESC_REQUEST, timeoutMs = 3_000L)
            ObdRepository.setProtocolDescription(Elm327Protocol.parseAtTextResponse(desc))
        }.onFailure { if (it is Elm327DeadLinkException) throw it }
        runCatching {
            val num = timedTransact(sess, Elm327Protocol.PROTOCOL_NUM_REQUEST, timeoutMs = 3_000L)
            ObdRepository.setProtocolNumber(Elm327Protocol.parseAtTextResponse(num))
        }.onFailure { if (it is Elm327DeadLinkException) throw it }
    }

    private enum class DtcKind { STORED, PENDING, PERMANENT }

    private suspend fun readDtcs(sess: Elm327BluetoothSession, kind: DtcKind) {
        ObdRepository.setDtcReading(true)
        try {
            val cmd = when (kind) {
                DtcKind.STORED -> Elm327Protocol.STORED_DTC_REQUEST
                DtcKind.PENDING -> Elm327Protocol.PENDING_DTC_REQUEST
                DtcKind.PERMANENT -> Elm327Protocol.PERMANENT_DTC_REQUEST
            }
            val raw = withContext(Dispatchers.IO) {
                timedTransact(sess, cmd, timeoutMs = 8_000L)
            }
            val parsed = when (kind) {
                DtcKind.STORED -> Elm327Protocol.parseStoredDtcs(raw)
                DtcKind.PENDING -> Elm327Protocol.parsePendingDtcs(raw)
                DtcKind.PERMANENT -> Elm327Protocol.parsePermanentDtcs(raw)
            }
            parsed.fold(
                onSuccess = { codes ->
                    when (kind) {
                        DtcKind.STORED -> ObdRepository.setDtcSuccess(codes)
                        DtcKind.PENDING -> ObdRepository.setPendingDtcSuccess(codes)
                        DtcKind.PERMANENT -> ObdRepository.setPermanentDtcSuccess(codes)
                    }
                },
                onFailure = { e -> ObdRepository.setDtcError(e.message ?: "dtc_failed") },
            )
        } catch (e: Exception) {
            ObdRepository.setDtcError(e.message ?: e.javaClass.simpleName)
        } finally {
            ObdRepository.setDtcReading(false)
        }
    }

    private suspend fun clearDtcs(sess: Elm327BluetoothSession) {
        ObdRepository.setDtcClearing(true)
        try {
            val raw = withContext(Dispatchers.IO) {
                timedTransact(sess, Elm327Protocol.CLEAR_DTC_REQUEST, timeoutMs = 8_000L)
            }
            Elm327Protocol.parseClearDtcsResponse(raw).fold(
                onSuccess = { ObdRepository.clearDtcListsAfterSuccessfulClear() },
                onFailure = { e -> ObdRepository.setDtcError(e.message ?: "clear_failed") },
            )
        } catch (e: Exception) {
            ObdRepository.setDtcError(e.message ?: e.javaClass.simpleName)
        } finally {
            ObdRepository.setDtcClearing(false)
        }
    }

    private suspend fun runPidDiscovery(sess: Elm327BluetoothSession) {
        ObdRepository.setDiscoveryRunning(true)
        ObdRepository.setDiscoveryError(null)
        try {
            val supported = linkedSetOf<Int>()
            var bitfieldPid = 0x00
            var pages = 0
            while (running && pages < 8) {
                pages++
                val raw = withContext(Dispatchers.IO) {
                    timedTransact(
                        sess,
                        Elm327Protocol.mode01Request(bitfieldPid),
                        timeoutMs = 8_000L,
                    )
                }
                val parsed = Elm327Protocol.parsePidSupportBitfield(raw, bitfieldPid)
                val page = parsed.getOrElse { e ->
                    ObdRepository.setDiscoveryError(e.message ?: "discovery_failed")
                    return
                }
                supported.addAll(page.supportedPids)
                val next = page.nextBitfieldPid ?: break
                bitfieldPid = next
                delay(BETWEEN_PIDS_MS)
            }
            val atMs = System.currentTimeMillis()
            Log.i(TAG, "PID discovery: ${supported.size} pids")
            onPidDiscoverySuccess?.invoke(supported, atMs)
            ObdRepository.setDiscoveryError(null)
        } catch (e: Exception) {
            ObdRepository.setDiscoveryError(e.message ?: e.javaClass.simpleName)
        } finally {
            ObdRepository.setDiscoveryRunning(false)
        }
    }

    /**
     * Mode 02 freeze frame: causative DTC (`0202`), support bitfields, then known [ObdPid] values.
     */
    private suspend fun readFreezeFrame(sess: Elm327BluetoothSession) {
        ObdRepository.setFreezeFrameReading(true)
        ObdRepository.setFreezeFrameError(null)
        try {
            val dtcRaw = withContext(Dispatchers.IO) {
                timedTransact(
                    sess,
                    Elm327Protocol.mode02Request(Elm327Protocol.FREEZE_FRAME_DTC_PID),
                    timeoutMs = 8_000L,
                )
            }
            val dtc = Elm327Protocol.parseFreezeFrameDtc(dtcRaw).getOrElse { e ->
                ObdRepository.setFreezeFrameError(e.message ?: "ff_dtc_failed")
                return
            }

            val supportedFf = discoverMode02SupportedPids(sess)
            val knownByModePid = ObdPid.entries
                .mapNotNull { pid -> pid.mode01Pid?.let { modePid -> modePid to pid } }
                .toMap()
            val toRead = when {
                supportedFf != null ->
                    supportedFf.filter {
                        it != Elm327Protocol.FREEZE_FRAME_DTC_PID && it in knownByModePid
                    }
                // Have a causative DTC but no Mode 02 bitfield — try every known decoder.
                dtc != null -> knownByModePid.keys.toList()
                else -> emptyList()
            }

            val values = linkedMapOf<String, Double>()
            for (modePid in toRead) {
                if (!running) break
                val obdPid = knownByModePid[modePid] ?: continue
                val raw = withContext(Dispatchers.IO) {
                    timedTransact(sess, Elm327Protocol.mode02Request(modePid), timeoutMs = 6_000L)
                }
                val data = Elm327Protocol.parseMode02DataBytes(raw, modePid) ?: continue
                val value = obdPid.decodeMode01(data) ?: continue
                values[obdPid.id] = value
                delay(BETWEEN_PIDS_MS)
            }

            Log.i(TAG, "freeze frame: dtc=${dtc?.code} values=${values.size}")
            ObdRepository.setFreezeFrameSuccess(dtc = dtc, values = values)
        } catch (e: Exception) {
            ObdRepository.setFreezeFrameError(e.message ?: e.javaClass.simpleName)
        } finally {
            ObdRepository.setFreezeFrameReading(false)
        }
    }

    /**
     * Walk Mode 02 support pages (`0200` / `0220`…).
     * Returns null when the first page fails (e.g. NO DATA / no freeze frame support).
     */
    private suspend fun discoverMode02SupportedPids(sess: Elm327BluetoothSession): Set<Int>? {
        val supported = linkedSetOf<Int>()
        var bitfieldPid = 0x00
        var pages = 0
        while (running && pages < 8) {
            pages++
            val raw = withContext(Dispatchers.IO) {
                timedTransact(
                    sess,
                    Elm327Protocol.mode02Request(bitfieldPid),
                    timeoutMs = 8_000L,
                )
            }
            val page = Elm327Protocol.parseMode02PidSupportBitfield(raw, bitfieldPid).getOrElse {
                return if (pages == 1) null else supported
            }
            supported.addAll(page.supportedPids)
            val next = page.nextBitfieldPid ?: break
            bitfieldPid = next
            delay(BETWEEN_PIDS_MS)
        }
        return supported
    }

    /** Mode 01 PID `01` + `41` readiness / MIL status. */
    private suspend fun readMonitorStatus(sess: Elm327BluetoothSession) {
        ObdRepository.setMonitorReading(true)
        ObdRepository.setMonitorError(null)
        try {
            val raw01 = withContext(Dispatchers.IO) {
                timedTransact(sess, Elm327Protocol.mode01Request(0x01), timeoutMs = 8_000L)
            }
            val since = Elm327Protocol.parseMonitorStatus(raw01, 0x01).getOrElse { e ->
                ObdRepository.setMonitorError(e.message ?: "monitor_01_failed")
                return
            }
            delay(BETWEEN_PIDS_MS)
            val raw41 = withContext(Dispatchers.IO) {
                timedTransact(sess, Elm327Protocol.mode01Request(0x41), timeoutMs = 8_000L)
            }
            val cycle = Elm327Protocol.parseMonitorStatus(raw41, 0x41).getOrNull()
            Log.i(
                TAG,
                "monitor: mil=${since.milOn} dtcCount=${since.confirmedDtcCount} spark=${since.sparkIgnition} cycle=${cycle != null}",
            )
            ObdRepository.setMonitorSuccess(sinceCleared = since, thisCycle = cycle)
        } catch (e: Exception) {
            ObdRepository.setMonitorError(e.message ?: e.javaClass.simpleName)
        } finally {
            ObdRepository.setMonitorReading(false)
        }
    }

    private suspend fun readVin(sess: Elm327BluetoothSession) {
        ObdRepository.setVinError(null)
        try {
            val raw = withContext(Dispatchers.IO) {
                timedTransact(sess, Elm327Protocol.VIN_REQUEST, timeoutMs = 10_000L)
            }
            Elm327Protocol.parseVin(raw).fold(
                onSuccess = { ObdRepository.setVinSuccess(it) },
                onFailure = { e -> ObdRepository.setVinError(e.message ?: "vin_failed") },
            )
        } catch (e: Exception) {
            ObdRepository.setVinError(e.message ?: e.javaClass.simpleName)
        }
    }

    /**
     * Full diagnostic snapshot for service / export: monitors, all DTC lists, freeze frame, VIN.
     */
    private suspend fun runDiagPack(sess: Elm327BluetoothSession) {
        ObdRepository.setDiagPackRunning(true)
        ObdRepository.setDiagPackError(null)
        try {
            readMonitorStatus(sess)
            if (!running) return
            delay(BETWEEN_PIDS_MS)
            readDtcs(sess, DtcKind.STORED)
            if (!running) return
            delay(BETWEEN_PIDS_MS)
            readDtcs(sess, DtcKind.PENDING)
            if (!running) return
            delay(BETWEEN_PIDS_MS)
            readDtcs(sess, DtcKind.PERMANENT)
            if (!running) return
            delay(BETWEEN_PIDS_MS)
            readFreezeFrame(sess)
            if (!running) return
            delay(BETWEEN_PIDS_MS)
            readVin(sess)
            ObdRepository.setDiagPackCompleted()
            Log.i(TAG, "diag pack completed")
        } catch (e: Exception) {
            ObdRepository.setDiagPackError(e.message ?: e.javaClass.simpleName)
            ObdRepository.setDiagPackRunning(false)
        }
    }
}

/**
 * Aggregates OBD PID interest from multiple dashboard surfaces (tab / main / floating).
 */
object ObdInterestAggregator {
    private val sources = mutableMapOf<String, Set<String>>()
    private val lock = Any()

    @Volatile
    private var manager: Elm327Manager? = null

    fun attach(manager: Elm327Manager?) {
        synchronized(lock) {
            this.manager = manager
            pushLocked()
        }
    }

    fun setSourcePids(sourceId: String, pidIds: Set<String>) {
        synchronized(lock) {
            if (pidIds.isEmpty()) {
                sources.remove(sourceId)
            } else {
                sources[sourceId] = pidIds.map { ObdPid.normalizeId(it) }.toSet()
            }
            pushLocked()
        }
    }

    fun clearSource(sourceId: String) {
        setSourcePids(sourceId, emptySet())
    }

    fun requestStoredDtcs() {
        synchronized(lock) {
            manager?.requestStoredDtcs()
        }
    }

    fun requestPendingDtcs() {
        synchronized(lock) {
            manager?.requestPendingDtcs()
        }
    }

    fun requestClearDtcs() {
        synchronized(lock) {
            manager?.requestClearDtcs()
        }
    }

    fun requestPidDiscovery() {
        synchronized(lock) {
            manager?.requestPidDiscovery()
        }
    }

    fun requestFreezeFrame() {
        synchronized(lock) {
            manager?.requestFreezeFrame()
        }
    }

    fun requestMonitorStatus() {
        synchronized(lock) {
            manager?.requestMonitorStatus()
        }
    }

    fun requestPermanentDtcs() {
        synchronized(lock) {
            manager?.requestPermanentDtcs()
        }
    }

    fun requestVin() {
        synchronized(lock) {
            manager?.requestVin()
        }
    }

    fun requestDiagPack() {
        synchronized(lock) {
            manager?.requestDiagPack()
        }
    }

    private fun pushLocked() {
        val union = sources.values.flatten().toSet()
        manager?.setInterestedPids(union)
    }
}
