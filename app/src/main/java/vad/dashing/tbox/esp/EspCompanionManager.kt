package vad.dashing.tbox.esp

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import vad.dashing.tbox.LocValues
import vad.dashing.tbox.EspRelayWidgetMode
import vad.dashing.tbox.TboxRepository
import vad.dashing.tbox.location.LocationMockManager
import java.io.File
import java.io.InputStream
import java.util.Date
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Owns USB session lifecycle, protocol dispatch, and publishing active location
 * when [LocationSource.ESP32] is selected.
 */
class EspCompanionManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val locationSource: StateFlow<LocationSource>,
    private val mockLocation: StateFlow<Boolean>,
    private val locationMockManager: LocationMockManager,
    private val requestVtg: StateFlow<Boolean>,
    private val requestZda: StateFlow<Boolean>,
    private val requestGst: StateFlow<Boolean>,
) {
    companion object {
        private const val TAG = "EspCompanionManager"
        private const val WATCHDOG_MS = 1_000L
        private val REOPEN_BACKOFF_MS = longArrayOf(3_000L, 10_000L, 30_000L)
        private const val OTA_BEGIN_TIMEOUT_MS = 15_000L
        private const val OTA_DONE_TIMEOUT_MS = 60_000L
        private const val OTA_CHUNK = 1024
        /** FW may block ~1.2s per UM980 cmd on older builds; keep USB open. */
        private const val UM980_CMD_GUARD_MS = 20_000L
        private const val UM980_RSP_TIMEOUT_MS = 2_500L
        private const val UM980_POST_SAVE_REFRESH_DELAY_MS = 2_000L
        private const val UM980_ENSURE_SIGNALGROUP_NONE = 0
        private const val UM980_BAUD_SETTLE_MS = 400L
    }

    private var session: EspUsbSerialSession? = null
    private var watchdogJob: Job? = null
    private var um980BatchJob: Job? = null
    private var loggedFirstLine = false
    private var lastReconnectAttemptMs = 0L
    private var reopenFailureStreak = 0
    private var lastOtaProgressLogPct = -1
    /** One optional VTG/ZDA/GST batch per companion USB link session. */
    private var optionalNmeaEnableSentForLink = false
    private val um980BusyGeneration = AtomicInteger(0)
    private val otaMutex = Mutex()
    private val otaInbox = AtomicReference<Channel<EspMessage>?>(null)
    private val um980RspWaiter = AtomicReference<CompletableDeferred<EspMessage.Um980Rsp>?>(null)
    private val um980BaudWaiter = AtomicReference<CompletableDeferred<EspMessage.Um980Baud>?>(null)
    /** While > now, heartbeat watchdog must not tear down USB. */
    private val um980UsbGuardUntilMs = AtomicLong(0L)
    private val relayPulseJobs = arrayOfNulls<Job>(8)

    private fun currentReopenIntervalMs(): Long {
        val idx = reopenFailureStreak.coerceIn(0, REOPEN_BACKOFF_MS.lastIndex)
        return REOPEN_BACKOFF_MS[idx]
    }

    private fun noteSuccessfulRx() {
        reopenFailureStreak = 0
    }

    fun start() {
        if (session != null) return
        Log.i(TAG, "starting companion USB session")
        TboxRepository.addLog("INFO", "Companion", "USB session starting")
        session = EspUsbSerialSession(
            context = context,
            onLine = { line ->
                if (!loggedFirstLine) {
                    loggedFirstLine = true
                    Log.i(TAG, "first line: ${line.take(120)}")
                }
                handleLine(line)
            },
            onConnectionChanged = { connected ->
                val was = EspCompanionRepository.connected.value
                EspCompanionRepository.updateConnected(connected)
                if (was != connected) {
                    Log.i(TAG, if (connected) "USB connected" else "USB disconnected")
                    TboxRepository.addLog(
                        "INFO",
                        "Companion",
                        if (connected) "USB connected" else "USB disconnected",
                    )
                }
                if (!connected && locationSource.value == LocationSource.ESP32) {
                    // Keep last fix briefly; watchdog clears on timeout.
                }
            },
            onError = { msg ->
                Log.w(TAG, msg)
                EspCompanionRepository.updateLastError(msg)
                TboxRepository.addLog("WARN", "Companion", msg)
            },
        ).also { it.start() }

        watchdogJob = scope.launch {
            while (isActive) {
                delay(WATCHDOG_MS)
                if (EspCompanionRepository.otaBusy.value) continue
                if (System.currentTimeMillis() < um980UsbGuardUntilMs.get()) continue
                val now = System.currentTimeMillis()
                val lastMsg = EspCompanionRepository.lastMessageAtMs.value
                val connectedAt = EspCompanionRepository.connectedAtMs.value
                val connected = EspCompanionRepository.connected.value
                val quietTooLong = EspCompanionProtocol.shouldForceReopenLink(
                    connected = connected,
                    lastMessageAtMs = lastMsg,
                    connectedAtMs = connectedAt,
                    nowMs = now,
                )
                if (quietTooLong) {
                    if (isUsbCritical()) {
                        session?.writeLine(EspCompanionProtocol.encodeHello().trimEnd())
                        continue
                    }
                    // Reopen only our Espressif CDC handle — do not leave a half-dead
                    // "already open" session that blocks tryConnect.
                    Log.w(TAG, "link quiet >${EspCompanionProtocol.HEARTBEAT_TIMEOUT_MS}ms (force reopen)")
                    EspCompanionRepository.updateConnected(false)
                    optionalNmeaEnableSentForLink = false
                    EspCompanionRepository.updateLastError("ESP link timeout")
                    TboxRepository.addLog("WARN", "Companion", "USB link timeout — reopening")
                    session?.forceReopen()
                    lastReconnectAttemptMs = now
                    reopenFailureStreak = (reopenFailureStreak + 1).coerceAtMost(REOPEN_BACKOFF_MS.lastIndex)
                }
                // Periodic restore when option is on (manager only runs if enabled).
                val reconnectGap = currentReopenIntervalMs()
                if (!EspCompanionRepository.connected.value &&
                    now - lastReconnectAttemptMs >= reconnectGap &&
                    !isUsbCritical()
                ) {
                    lastReconnectAttemptMs = now
                    Log.d(TAG, "auto-reconnect attempt")
                    session?.tryConnect(force = true)
                    reopenFailureStreak = (reopenFailureStreak + 1).coerceAtMost(REOPEN_BACKOFF_MS.lastIndex)
                }
            }
        }

        applyLocationSource(locationSource.value)
    }

    fun stop() {
        Log.i(TAG, "stopping companion USB session")
        TboxRepository.addLog("INFO", "Companion", "USB session stopped")
        um980BatchJob?.cancel()
        um980BatchJob = null
        watchdogJob?.cancel()
        watchdogJob = null
        // Service teardown must release USB even mid-transfer.
        session?.close(force = true)
        session = null
        loggedFirstLine = false
        lastReconnectAttemptMs = 0L
        reopenFailureStreak = 0
        optionalNmeaEnableSentForLink = false
        EspCompanionRepository.finishUm980ConfigBusy()
        EspCompanionRepository.updateConnected(false)
    }

    fun setRelayMask(mask: Int) {
        if (EspCompanionRepository.otaBusy.value) return
        val normalized = mask and 0xFF
        Log.d(TAG, "relaySet mask=0x${Integer.toHexString(normalized)}")
        session?.writeLine(EspCompanionProtocol.encodeRelaySet(normalized).trimEnd())
        EspCompanionRepository.updateRelayMask(normalized)
    }

    fun toggleRelay(channel: Int) {
        if (channel !in 0..7) return
        cancelRelayPulse(channel)
        val bit = 1 shl channel
        val next = EspCompanionRepository.relayMask.value xor bit
        setRelayMask(next)
    }

    fun setRelayChannel(channel: Int, on: Boolean) {
        if (channel !in 0..7) return
        cancelRelayPulse(channel)
        val bit = 1 shl channel
        val cur = EspCompanionRepository.relayMask.value
        val next = if (on) cur or bit else cur and bit.inv()
        setRelayMask(next)
    }

    /** Turn channel on, then off after [durationMs] (cancels any prior pulse on that channel). */
    fun pulseRelay(channel: Int, durationMs: Long = EspRelayWidgetMode.BUTTON_PULSE_MS) {
        if (channel !in 0..7) return
        if (durationMs <= 0L) {
            setRelayChannel(channel, true)
            return
        }
        cancelRelayPulse(channel)
        val bit = 1 shl channel
        val onMask = EspCompanionRepository.relayMask.value or bit
        setRelayMask(onMask)
        val job = scope.launch {
            delay(durationMs)
            val offMask = EspCompanionRepository.relayMask.value and bit.inv()
            setRelayMask(offMask)
        }
        relayPulseJobs[channel] = job
        job.invokeOnCompletion {
            if (relayPulseJobs[channel] === job) {
                relayPulseJobs[channel] = null
            }
        }
    }

    private fun cancelRelayPulse(channel: Int) {
        if (channel !in relayPulseJobs.indices) return
        relayPulseJobs[channel]?.cancel()
        relayPulseJobs[channel] = null
    }

    fun requestHello() {
        session?.writeLine(EspCompanionProtocol.encodeHello().trimEnd())
    }

    fun sendUm980Cmd(cmd: String) {
        if (EspCompanionRepository.otaBusy.value) return
        if (EspCompanionRepository.um980ConfigBusy.value) return
        val trimmed = cmd.trim()
        if (trimmed.isEmpty()) return
        Log.i(TAG, "UM980 cmd: ${trimmed.take(64)}")
        EspCompanionRepository.appendUm980TrafficLog(Um980LogDirection.TX, trimmed)
        armUm980UsbGuard()
        val sess = session ?: return
        sess.beginCriticalIo()
        try {
            sess.writeLine(EspCompanionProtocol.encodeUm980Cmd(trimmed).trimEnd())
        } finally {
            // Keep critical until guard window / rsp; end immediately for single fire-and-forget
            // after short delay via guard — release depth now, guard still blocks reconnect.
            sess.endCriticalIo()
        }
    }

    fun setUm980Baud(baud: Int) {
        if (EspCompanionRepository.otaBusy.value) return
        if (EspCompanionRepository.um980ConfigBusy.value) return
        if (baud !in EspCompanionProtocol.UM980_BAUD_OPTIONS) return
        Log.i(TAG, "UM980 baud request: $baud")
        val busyGen = um980BusyGeneration.incrementAndGet()
        EspCompanionRepository.beginUm980ConfigBusy()
        um980BatchJob?.cancel()
        um980BatchJob = scope.launch {
            val sess = session
            if (sess == null || !EspCompanionRepository.connected.value) {
                if (um980BusyGeneration.get() == busyGen) {
                    EspCompanionRepository.finishUm980ConfigBusy()
                }
                return@launch
            }
            sess.beginCriticalIo()
            try {
                if (EspCompanionRepository.isUm980Online()) {
                    val configCmd = Um980Commands.comBaudCommand(baud)
                    val configOk = awaitUm980CmdOk(sess, configCmd)
                    if (!configOk) {
                        val err = "UM980 $configCmd failed; ESP baud unchanged"
                        Log.w(TAG, err)
                        EspCompanionRepository.updateLastError(err)
                        TboxRepository.addLog("WARN", "Companion", err)
                        return@launch
                    }
                    val espOk = awaitEspBaudOk(sess, baud)
                    if (!espOk) {
                        val err = "ESP UART baud $baud rejected after UM980 CONFIG"
                        Log.w(TAG, err)
                        EspCompanionRepository.updateLastError(err)
                        TboxRepository.addLog("WARN", "Companion", err)
                        return@launch
                    }
                    delay(UM980_BAUD_SETTLE_MS)
                    val saveOk = awaitUm980CmdOk(sess, "SAVECONFIG")
                    if (!saveOk) {
                        val err = "SAVECONFIG after baud $baud failed"
                        Log.w(TAG, err)
                        EspCompanionRepository.updateLastError(err)
                        TboxRepository.addLog("WARN", "Companion", err)
                    } else {
                        Log.i(TAG, "UM980+ESP baud $baud applied and saved")
                        EspCompanionRepository.updateLastError(null)
                    }
                } else {
                    val espOk = awaitEspBaudOk(sess, baud)
                    if (!espOk) {
                        val err = "ESP UART baud $baud rejected (UM980 offline)"
                        Log.w(TAG, err)
                        EspCompanionRepository.updateLastError(err)
                    } else {
                        Log.i(TAG, "ESP UART baud $baud applied (UM980 offline, no CONFIG/SAVE)")
                        EspCompanionRepository.updateLastError(null)
                    }
                }
                armUm980UsbGuard(extraMs = 3_000L)
            } finally {
                sess.endCriticalIo()
                if (um980BusyGeneration.get() == busyGen) {
                    EspCompanionRepository.finishUm980ConfigBusy()
                }
            }
        }
    }

    /**
     * @param refreshConfigAfter after the batch, wait then read CONFIG/MODE so the snapshot matches.
     * @param ensureSignalGroup if > 0 and current snapshot group differs, send
     *   `CONFIG SIGNALGROUP N` after the batch and wait [Um980Commands.PRESET_SIGNALGROUP_REBOOT_MS]
     *   before refresh (module reboot).
     */
    fun sendUm980Commands(
        commands: List<String>,
        refreshConfigAfter: Boolean = false,
        ensureSignalGroup: Int = UM980_ENSURE_SIGNALGROUP_NONE,
    ) {
        val busyGen = um980BusyGeneration.incrementAndGet()
        EspCompanionRepository.beginUm980ConfigBusy()
        um980BatchJob?.cancel()
        um980BatchJob = scope.launch {
            val sess = session
            if (sess == null) {
                if (um980BusyGeneration.get() == busyGen) {
                    EspCompanionRepository.finishUm980ConfigBusy()
                }
                return@launch
            }
            val list = commands.map { it.trim() }.filter { it.isNotEmpty() }
            if (list.isEmpty()) {
                if (um980BusyGeneration.get() == busyGen) {
                    EspCompanionRepository.finishUm980ConfigBusy()
                }
                return@launch
            }
            Log.i(
                TAG,
                "UM980 batch: ${list.size} cmd(s) refreshAfter=$refreshConfigAfter " +
                    "ensureSg=$ensureSignalGroup",
            )
            sess.beginCriticalIo()
            try {
                runUm980CommandList(sess, list)
                var usedSignalGroupReboot = false
                if (ensureSignalGroup > 0) {
                    val current = EspCompanionRepository.um980ConfigSnapshot.value.signalGroup
                    if (current != ensureSignalGroup) {
                        Log.i(
                            TAG,
                            "UM980 ensure SIGNALGROUP $ensureSignalGroup (was $current)",
                        )
                        runUm980CommandList(
                            sess,
                            listOf("CONFIG SIGNALGROUP $ensureSignalGroup"),
                        )
                        usedSignalGroupReboot = true
                        Log.i(
                            TAG,
                            "UM980 SIGNALGROUP reboot pause ${Um980Commands.PRESET_SIGNALGROUP_REBOOT_MS}ms",
                        )
                        delay(Um980Commands.PRESET_SIGNALGROUP_REBOOT_MS)
                    }
                }
                if (refreshConfigAfter) {
                    if (!usedSignalGroupReboot) {
                        Log.i(TAG, "UM980 post-batch pause ${UM980_POST_SAVE_REFRESH_DELAY_MS}ms")
                        delay(UM980_POST_SAVE_REFRESH_DELAY_MS)
                    }
                    if (!EspCompanionRepository.otaBusy.value) {
                        Log.i(TAG, "UM980 auto refresh CONFIG/MODE/MASK/VERSION")
                        runUm980CommandList(sess, Um980Commands.refreshSnapshotCommands())
                    }
                }
                armUm980UsbGuard(
                    extraMs = if (usedSignalGroupReboot) 5_000L else 3_000L,
                )
            } finally {
                sess.endCriticalIo()
                if (um980BusyGeneration.get() == busyGen) {
                    EspCompanionRepository.finishUm980ConfigBusy()
                }
            }
        }
    }

    private suspend fun awaitUm980CmdOk(
        sess: EspUsbSerialSession,
        cmd: String,
    ): Boolean {
        val trimmed = cmd.trim()
        if (trimmed.isEmpty()) return false
        Log.d(TAG, "UM980 cmd: ${trimmed.take(64)}")
        EspCompanionRepository.appendUm980TrafficLog(Um980LogDirection.TX, trimmed)
        armUm980UsbGuard()
        val waiter = CompletableDeferred<EspMessage.Um980Rsp>()
        um980RspWaiter.set(waiter)
        sess.writeLine(EspCompanionProtocol.encodeUm980Cmd(trimmed).trimEnd())
        val rsp = withTimeoutOrNull(UM980_RSP_TIMEOUT_MS) { waiter.await() }
        um980RspWaiter.compareAndSet(waiter, null)
        if (rsp == null) {
            Log.w(TAG, "UM980 no response for: ${trimmed.take(40)}")
            EspCompanionRepository.appendUm980TrafficLog(
                Um980LogDirection.RX,
                "(timeout) $trimmed",
            )
            return false
        }
        val blob = rsp.lines.joinToString("\n")
        val parseFail = blob.contains("PARSING FAILD", ignoreCase = true) ||
            blob.contains("GRAMMAR ERROR", ignoreCase = true)
        if (parseFail) return false
        val hasOk = blob.contains("OK", ignoreCase = true)
        return rsp.ok || hasOk
    }

    private suspend fun awaitEspBaudOk(sess: EspUsbSerialSession, baud: Int): Boolean {
        armUm980UsbGuard()
        val waiter = CompletableDeferred<EspMessage.Um980Baud>()
        um980BaudWaiter.set(waiter)
        sess.writeLine(EspCompanionProtocol.encodeUm980Baud(baud).trimEnd())
        val ack = withTimeoutOrNull(UM980_RSP_TIMEOUT_MS) { waiter.await() }
        um980BaudWaiter.compareAndSet(waiter, null)
        if (ack == null) {
            Log.w(TAG, "ESP baud ack timeout for $baud")
            return false
        }
        return ack.ok && ack.baud == baud
    }

    private suspend fun runUm980CommandList(
        sess: EspUsbSerialSession,
        list: List<String>,
    ) {
        for (trimmed in list) {
            if (EspCompanionRepository.otaBusy.value) break
            Log.d(TAG, "UM980 cmd: ${trimmed.take(64)}")
            EspCompanionRepository.appendUm980TrafficLog(Um980LogDirection.TX, trimmed)
            armUm980UsbGuard()
            val waiter = CompletableDeferred<EspMessage.Um980Rsp>()
            um980RspWaiter.set(waiter)
            sess.writeLine(EspCompanionProtocol.encodeUm980Cmd(trimmed).trimEnd())
            val rsp = withTimeoutOrNull(UM980_RSP_TIMEOUT_MS) { waiter.await() }
            um980RspWaiter.compareAndSet(waiter, null)
            if (rsp == null) {
                Log.w(TAG, "UM980 no response for: ${trimmed.take(40)}")
                EspCompanionRepository.appendUm980TrafficLog(
                    Um980LogDirection.RX,
                    "(timeout) $trimmed",
                )
            } else {
                Log.d(TAG, "UM980 rsp ok=${rsp.ok} lines=${rsp.lines.size} cmd=${trimmed.take(40)}")
            }
            delay(80)
        }
    }

    fun rebootCompanion() {
        if (EspCompanionRepository.otaBusy.value) return
        Log.i(TAG, "reboot requested")
        TboxRepository.addLog("INFO", "Companion", "Reboot requested")
        session?.writeLine(EspCompanionProtocol.encodeReboot().trimEnd())
    }

    private fun armUm980UsbGuard(extraMs: Long = UM980_CMD_GUARD_MS) {
        um980UsbGuardUntilMs.set(System.currentTimeMillis() + extraMs)
    }

    private fun isUsbCritical(): Boolean {
        return EspCompanionRepository.otaBusy.value ||
            System.currentTimeMillis() < um980UsbGuardUntilMs.get() ||
            session?.isCriticalIo() == true
    }

    /**
     * Stream an ESP app image (magic 0xE9) over CDC OTA from a local cache file.
     */
    fun startFirmwareUpdate(file: File) {
        scope.launch {
            updateFirmware(file)
        }
    }

    suspend fun updateFirmware(file: File): Result<Unit> = otaMutex.withLock {
        val sess = session
        if (sess == null || !EspCompanionRepository.connected.value) {
            val err = "no_usb"
            Log.w(TAG, "OTA aborted: no USB")
            EspCompanionRepository.finishOta(err)
            return Result.failure(IllegalStateException(err))
        }
        val channel = Channel<EspMessage>(Channel.BUFFERED)
        otaInbox.set(channel)
        EspCompanionRepository.beginOta()
        lastOtaProgressLogPct = -1
        sess.beginCriticalIo()
        return try {
            val image = file.readBytes()
            val imageSize = image.size.toLong()
            if (image.isEmpty()) throw IllegalArgumentException("empty")
            EspCompanionProtocol.validateFirmwareImage(imageSize, image[0].toInt() and 0xFF)?.let {
                throw IllegalArgumentException(it)
            }
            val crc = EspCompanionProtocol.crc32Ieee(image)
            Log.i(TAG, "OTA begin size=$imageSize crc=0x${crc.toString(16)}")
            TboxRepository.addLog("INFO", "Companion", "OTA begin ($imageSize bytes)")
            if (!sess.writeBytes(
                    EspCompanionProtocol.encodeOtaBegin(imageSize, crc).toByteArray(Charsets.UTF_8),
                    ota = true,
                )
            ) {
                throw IllegalStateException("no_usb")
            }
            val beginAck = awaitOta(channel, OTA_BEGIN_TIMEOUT_MS) {
                it is EspMessage.OtaAck && it.phase == "begin"
            } as EspMessage.OtaAck
            if (!beginAck.ok) {
                throw IllegalStateException(beginAck.err ?: "begin_failed")
            }
            Log.i(TAG, "OTA begin ack ok")
            var offset = 0
            while (offset < image.size) {
                val end = minOf(offset + OTA_CHUNK, image.size)
                val chunk = image.copyOfRange(offset, end)
                val frame = EspCompanionProtocol.encodeOtaChunkFrame(chunk)
                if (!sess.writeBytes(frame, ota = true)) {
                    throw IllegalStateException("no_usb")
                }
                offset = end
                val pct = ((offset.toLong() * 100L) / imageSize).toInt().coerceIn(0, 99)
                EspCompanionRepository.updateOtaProgress(pct)
                logOtaProgress(pct)
                while (true) {
                    val msg = channel.tryReceive().getOrNull() ?: break
                    when (msg) {
                        is EspMessage.OtaAck -> {
                            if (!msg.ok) {
                                throw IllegalStateException(msg.err ?: "chunk_failed")
                            }
                            if (msg.offset > 0 && imageSize > 0) {
                                val ackPct = ((msg.offset * 100L) / imageSize).toInt().coerceIn(0, 99)
                                EspCompanionRepository.updateOtaProgress(ackPct)
                                logOtaProgress(ackPct)
                            }
                        }
                        is EspMessage.OtaDone -> {
                            if (!msg.ok) throw IllegalStateException(msg.err ?: "ota_failed")
                        }
                        else -> Unit
                    }
                }
            }
            if (!sess.writeBytes(
                    EspCompanionProtocol.encodeOtaEnd().toByteArray(Charsets.UTF_8),
                    ota = true,
                )
            ) {
                throw IllegalStateException("no_usb")
            }
            val done = withTimeout(OTA_DONE_TIMEOUT_MS) {
                while (true) {
                    when (val msg = channel.receive()) {
                        is EspMessage.OtaAck -> {
                            if (msg.phase == "end" && !msg.ok) {
                                throw IllegalStateException(msg.err ?: "end_failed")
                            }
                        }
                        is EspMessage.OtaDone -> return@withTimeout msg
                        else -> Unit
                    }
                }
                @Suppress("UNREACHABLE_CODE")
                error("unreachable")
            }
            if (!done.ok) {
                throw IllegalStateException(done.err ?: "ota_failed")
            }
            EspCompanionRepository.finishOta(null)
            Log.i(TAG, "OTA complete, companion rebooting")
            TboxRepository.addLog("INFO", "Companion", "OTA complete, companion rebooting")
            runCatching { file.delete() }
            Result.success(Unit)
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "OTA timeout")
            EspCompanionRepository.finishOta("timeout")
            TboxRepository.addLog("WARN", "Companion", "OTA timeout")
            runCatching { file.delete() }
            Result.failure(e)
        } catch (e: Exception) {
            Log.w(TAG, "OTA failed: ${e.message}")
            EspCompanionRepository.finishOta(e.message ?: "ota_failed")
            TboxRepository.addLog("WARN", "Companion", "OTA failed: ${e.message}")
            runCatching { file.delete() }
            Result.failure(e)
        } finally {
            sess.endCriticalIo()
            otaInbox.set(null)
            channel.close()
        }
    }

    private fun logOtaProgress(pct: Int) {
        val milestone = when {
            pct >= 75 -> 75
            pct >= 50 -> 50
            pct >= 25 -> 25
            else -> 0
        }
        if (milestone > lastOtaProgressLogPct) {
            lastOtaProgressLogPct = milestone
            Log.i(TAG, "OTA progress ${milestone}%")
        }
    }

    /** Same as [updateFirmware] but from an already-buffered stream (copied to bytes). */
    suspend fun updateFirmware(input: InputStream, size: Long): Result<Unit> {
        val tmp = File(context.cacheDir, "esp_ota_stream.bin")
        return try {
            input.use { src ->
                tmp.outputStream().use { dst -> src.copyTo(dst) }
            }
            if (size > 0 && tmp.length() != size) {
                // Prefer on-disk length after copy.
            }
            updateFirmware(tmp)
        } finally {
            tmp.delete()
        }
    }

    private suspend fun awaitOta(
        channel: Channel<EspMessage>,
        timeoutMs: Long,
        predicate: (EspMessage) -> Boolean,
    ): EspMessage = withTimeout(timeoutMs) {
        while (true) {
            val msg = channel.receive()
            if (predicate(msg)) return@withTimeout msg
            if (msg is EspMessage.OtaAck && !msg.ok) {
                throw IllegalStateException(msg.err ?: "ota_nack")
            }
            if (msg is EspMessage.OtaDone && !msg.ok) {
                throw IllegalStateException(msg.err ?: "ota_failed")
            }
        }
        @Suppress("UNREACHABLE_CODE")
        error("unreachable")
    }

    private fun handleLine(line: String) {
        val msg = EspCompanionProtocol.parseLine(line) ?: return
        EspCompanionRepository.noteRxMessage()
        noteSuccessfulRx()
        when (msg) {
            is EspMessage.Hello -> {
                Log.i(TAG, "hello fw=${msg.fw} baud=${msg.baud} um980=${msg.um980}")
                EspCompanionRepository.updateDeviceInfo(
                    EspDeviceInfo(
                        firmwareVersion = msg.fw,
                        gpioInCount = msg.gpioInCount,
                        relayCount = msg.relayCount,
                        um980 = msg.um980,
                        um980Baud = msg.baud,
                    )
                )
                EspCompanionRepository.updateHeartbeat(0L)
                EspCompanionRepository.updateConnected(true)
                EspCompanionRepository.updateLastError(null)
                if (msg.um980) {
                    maybeSendOptionalNmeaEnableCommands()
                }
            }
            is EspMessage.Heartbeat -> {
                EspCompanionRepository.updateHeartbeat(msg.uptimeMs)
                EspCompanionRepository.updateConnected(true)
            }
            is EspMessage.Gps -> {
                val loc = EspCompanionProtocol.gpsToLocValues(msg, Date())
                EspCompanionRepository.updateLocValues(loc)
                // GPS traffic counts as link liveness (same as usbgps lastRead).
                EspCompanionRepository.updateHeartbeat(0L)
                EspCompanionRepository.appendUm980TrafficLog(
                    direction = Um980LogDirection.RX,
                    text = formatGpsLog(msg),
                    isGeo = true,
                )
                maybeSendOptionalNmeaEnableCommands()
                if (locationSource.value == LocationSource.ESP32) {
                    publishActiveLocation(loc)
                }
            }
            is EspMessage.Gpio -> EspCompanionRepository.updateGpioMask(msg.mask)
            is EspMessage.GpioEvent -> {
                Log.d(TAG, "gpioEvent ch=${msg.channel} level=${msg.level}")
                EspCompanionRepository.applyGpioEvent(msg.channel, msg.level)
            }
            is EspMessage.Relay -> EspCompanionRepository.updateRelayMask(msg.mask)
            is EspMessage.Um980Rsp -> {
                // Count as link liveness: older FW suppresses hb while collecting UART replies.
                EspCompanionRepository.updateHeartbeat(0L)
                EspCompanionRepository.updateConnected(true)
                um980RspWaiter.get()?.complete(msg)
                EspCompanionRepository.updateUm980Response(msg.cmd, msg.lines, msg.ok)
                EspCompanionRepository.appendUm980TrafficLog(
                    direction = Um980LogDirection.RX,
                    text = formatUm980RspLog(msg),
                )
            }
            is EspMessage.Um980Baud -> {
                EspCompanionRepository.updateHeartbeat(0L)
                val info = EspCompanionRepository.deviceInfo.value
                EspCompanionRepository.updateDeviceInfo(info.copy(um980Baud = msg.baud))
                um980BaudWaiter.get()?.complete(msg)
                if (msg.ok) {
                    Log.i(TAG, "UM980 baud ok: ${msg.baud}")
                } else {
                    Log.w(TAG, "UM980 baud rejected: ${msg.baud}")
                    EspCompanionRepository.updateLastError("UM980 baud rejected: ${msg.baud}")
                }
            }
            is EspMessage.RebootAck -> {
                Log.i(TAG, "reboot ack")
                EspCompanionRepository.updateLastError(null)
                TboxRepository.addLog("INFO", "Companion", "Companion reboot acknowledged")
            }
            is EspMessage.OtaAck, is EspMessage.OtaDone -> {
                otaInbox.get()?.trySend(msg)
            }
        }
    }

    private fun formatGpsLog(msg: EspMessage.Gps): String {
        return "gps fix=${msg.fix} lat=${msg.lat} lon=${msg.lon} " +
            "sats=${msg.satsUsed}/${msg.satsVis} spd=${msg.speedKmh}"
    }

    private fun formatUm980RspLog(msg: EspMessage.Um980Rsp): String {
        val head = when {
            !msg.ok -> "FAIL"
            msg.lines.any { it.contains("PARSING FAILD", ignoreCase = true) ||
                it.contains("GRAMMAR ERROR", ignoreCase = true) } -> "ERR"
            else -> "OK"
        }
        val body = msg.lines.joinToString(" | ").ifBlank { "(no lines)" }
        val cmd = msg.cmd.ifBlank { "?" }
        return "$cmd $head: $body"
    }

    /**
     * Once per companion USB link: enable optional Unicore NMEA sentences from companion prefs.
     * Does not SAVECONFIG (same as USB [vad.dashing.tbox.usbgnss.UsbGnssNmeaEnableCommands]).
     */
    private fun maybeSendOptionalNmeaEnableCommands() {
        if (optionalNmeaEnableSentForLink) return
        if (EspCompanionRepository.otaBusy.value) return
        val lines = vad.dashing.tbox.usbgnss.UsbGnssNmeaEnableCommands.buildUnicoreLines(
            requestVtg = requestVtg.value,
            requestZda = requestZda.value,
            requestGst = requestGst.value,
        )
        optionalNmeaEnableSentForLink = true
        if (lines.isEmpty()) return
        Log.i(TAG, "UM980 optional NMEA enable: ${lines.joinToString()}")
        sendUm980Commands(lines, refreshConfigAfter = false)
    }

    /** Called from [vad.dashing.tbox.BackgroundService] after clearing the active location slot. */
    fun applyLocationSource(source: LocationSource) {
        when (source) {
            LocationSource.ANDROID -> {
                // Android GNSS is owned by BackgroundService (works without companion USB).
                locationMockManager.stopMockLocation()
            }
            LocationSource.ESP32 -> {
                val loc = EspCompanionRepository.locValues.value
                if (loc.updateTime != null) {
                    publishActiveLocation(loc)
                }
            }
            LocationSource.TBOX -> {
                // TBox path continues to update via BackgroundService.ansLOCValues
            }
            LocationSource.USB -> {
                // Direct USB NMEA is owned by BackgroundService / UsbNmeaLocationSource.
            }
        }
        if (source == LocationSource.ANDROID && mockLocation.value) {
            locationMockManager.stopMockLocation()
        }
    }

    private fun publishActiveLocation(loc: LocValues) {
        TboxRepository.updateLocationUpdateTime()
        val prev = TboxRepository.locValues.value
        if (loc.rawValue != prev.rawValue ||
            loc.latitude != prev.latitude ||
            loc.longitude != prev.longitude ||
            loc.speed != prev.speed ||
            loc.locateStatus != prev.locateStatus
        ) {
            TboxRepository.updateLocValues(loc)
        }
        // Location truth is owned by BackgroundService (liveUsable / GeoDisplayState).
    }

    /** Called from TBox LOC path when source is TBOX (location already in TboxRepository). */
    fun onTboxLocValues(loc: LocValues) {
        if (locationSource.value != LocationSource.TBOX) return
        // Mock pushes are owned by MockLocationJob.
    }
}
