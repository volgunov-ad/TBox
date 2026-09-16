package vad.dashing.tbox.obd

import android.content.Context
import android.util.Log
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
 * and on-demand Mode 02 / 03 / 04 / 07 requests.
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
    }

    private val sessionMutex = Mutex()
    private var session: Elm327BluetoothSession? = null
    /** In-flight connect/init session; closed immediately on [stop] to abort hung RFCOMM. */
    private val connectingSession = AtomicReference<Elm327BluetoothSession?>(null)
    private var loopJob: Job? = null
    private var deviceAddress: String = ""
    private var pairingPin: String = ""
    private var reopenFailureStreak = 0
    private var lastPairingAttemptMs = 0L
    private val interestedPidIds = AtomicReference<Set<String>>(emptySet())
    private val dtcRequestPending = AtomicBoolean(false)
    private val pendingDtcRequestPending = AtomicBoolean(false)
    private val clearDtcRequestPending = AtomicBoolean(false)
    private val discoveryRequestPending = AtomicBoolean(false)
    private val freezeFrameRequestPending = AtomicBoolean(false)

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

    private suspend fun runLoop() {
        while (scope.isActive && running) {
            try {
                ensureSession()
                val sess = sessionMutex.withLock { session } ?: continue
                if (discoveryRequestPending.getAndSet(false)) {
                    runPidDiscovery(sess)
                }
                if (clearDtcRequestPending.getAndSet(false)) {
                    clearDtcs(sess)
                }
                if (dtcRequestPending.getAndSet(false)) {
                    readDtcs(sess, pending = false)
                }
                if (pendingDtcRequestPending.getAndSet(false)) {
                    readDtcs(sess, pending = true)
                }
                if (freezeFrameRequestPending.getAndSet(false)) {
                    readFreezeFrame(sess)
                }
                pollInterested(sess)
                delay(POLL_IDLE_MS)
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
            if (session?.isOpen == true) return
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
            if (!running ||
                dtcRequestPending.get() ||
                pendingDtcRequestPending.get() ||
                clearDtcRequestPending.get() ||
                discoveryRequestPending.get() ||
                freezeFrameRequestPending.get()
            ) {
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
        if (Elm327Protocol.isElmError(raw)) {
            ObdRepository.noteBusError(elapsed)
        } else {
            ObdRepository.noteBusOk(elapsed)
        }
        return raw
    }

    private fun refreshProtocolInfo(sess: Elm327BluetoothSession) {
        runCatching {
            val desc = timedTransact(sess, Elm327Protocol.PROTOCOL_DESC_REQUEST, timeoutMs = 3_000L)
            ObdRepository.setProtocolDescription(Elm327Protocol.parseAtTextResponse(desc))
        }
        runCatching {
            val num = timedTransact(sess, Elm327Protocol.PROTOCOL_NUM_REQUEST, timeoutMs = 3_000L)
            ObdRepository.setProtocolNumber(Elm327Protocol.parseAtTextResponse(num))
        }
    }

    private suspend fun readDtcs(sess: Elm327BluetoothSession, pending: Boolean) {
        ObdRepository.setDtcReading(true)
        try {
            val cmd = if (pending) {
                Elm327Protocol.PENDING_DTC_REQUEST
            } else {
                Elm327Protocol.STORED_DTC_REQUEST
            }
            val raw = withContext(Dispatchers.IO) {
                timedTransact(sess, cmd, timeoutMs = 8_000L)
            }
            val parsed = if (pending) {
                Elm327Protocol.parsePendingDtcs(raw)
            } else {
                Elm327Protocol.parseStoredDtcs(raw)
            }
            parsed.fold(
                onSuccess = { codes ->
                    if (pending) ObdRepository.setPendingDtcSuccess(codes)
                    else ObdRepository.setDtcSuccess(codes)
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

    private fun pushLocked() {
        val union = sources.values.flatten().toSet()
        manager?.setInterestedPids(union)
    }
}
