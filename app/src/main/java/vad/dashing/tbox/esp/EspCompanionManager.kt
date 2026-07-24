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
import vad.dashing.tbox.TboxRepository
import vad.dashing.tbox.location.LocationMockManager
import java.io.File
import java.io.InputStream
import java.util.Date
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
    private val isLocValuesTrueEvaluator: (LocValues) -> Boolean,
) {
    companion object {
        private const val TAG = "EspCompanionManager"
        private const val WATCHDOG_MS = 1_000L
        private const val RECONNECT_INTERVAL_MS = 3_000L
        private const val OTA_BEGIN_TIMEOUT_MS = 15_000L
        private const val OTA_DONE_TIMEOUT_MS = 60_000L
        private const val OTA_CHUNK = 1024
        /** FW may block ~1.2s per UM980 cmd on older builds; keep USB open. */
        private const val UM980_CMD_GUARD_MS = 20_000L
        private const val UM980_RSP_TIMEOUT_MS = 2_500L
    }

    private var session: EspUsbSerialSession? = null
    private var watchdogJob: Job? = null
    private var sourceJob: Job? = null
    private var um980BatchJob: Job? = null
    private var androidLocationSource: AndroidLocationSource? = null
    private var loggedFirstLine = false
    private var lastReconnectAttemptMs = 0L
    private var lastOtaProgressLogPct = -1
    private val otaMutex = Mutex()
    private val otaInbox = AtomicReference<Channel<EspMessage>?>(null)
    private val um980RspWaiter = AtomicReference<CompletableDeferred<EspMessage.Um980Rsp>?>(null)
    /** While > now, heartbeat watchdog must not tear down USB. */
    private val um980UsbGuardUntilMs = AtomicLong(0L)

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
                val last = EspCompanionRepository.lastHeartbeatAtMs.value
                val connected = EspCompanionRepository.connected.value
                if (connected && last > 0L &&
                    now - last > EspCompanionProtocol.HEARTBEAT_TIMEOUT_MS
                ) {
                    // Soft recover only: never close UsbDeviceConnection on HU — that can
                    // wedge the shared USB host and take down TBox (RNDIS) until reboot.
                    Log.w(TAG, "link quiet >${EspCompanionProtocol.HEARTBEAT_TIMEOUT_MS}ms (soft recover)")
                    EspCompanionRepository.updateConnected(false)
                    EspCompanionRepository.updateLastError("ESP link timeout")
                    session?.writeLine(EspCompanionProtocol.encodeHello().trimEnd())
                }
                // Periodic restore when option is on (manager only runs if enabled).
                if (!EspCompanionRepository.connected.value &&
                    now - lastReconnectAttemptMs >= RECONNECT_INTERVAL_MS &&
                    !isUsbCritical()
                ) {
                    lastReconnectAttemptMs = now
                    Log.d(TAG, "auto-reconnect attempt")
                    session?.tryConnect(force = false)
                }
            }
        }

        sourceJob = scope.launch {
            locationSource.collect { source ->
                applyLocationSource(source)
            }
        }
    }

    fun stop() {
        Log.i(TAG, "stopping companion USB session")
        TboxRepository.addLog("INFO", "Companion", "USB session stopped")
        um980BatchJob?.cancel()
        um980BatchJob = null
        sourceJob?.cancel()
        sourceJob = null
        watchdogJob?.cancel()
        watchdogJob = null
        androidLocationSource?.stop()
        androidLocationSource = null
        // Service teardown must release USB even mid-transfer.
        session?.close(force = true)
        session = null
        loggedFirstLine = false
        lastReconnectAttemptMs = 0L
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
        val bit = 1 shl channel
        val next = EspCompanionRepository.relayMask.value xor bit
        setRelayMask(next)
    }

    fun requestHello() {
        session?.writeLine(EspCompanionProtocol.encodeHello().trimEnd())
    }

    fun sendUm980Cmd(cmd: String) {
        if (EspCompanionRepository.otaBusy.value) return
        val trimmed = cmd.trim()
        if (trimmed.isEmpty()) return
        Log.i(TAG, "UM980 cmd: ${trimmed.take(64)}")
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
        if (baud !in EspCompanionProtocol.UM980_BAUD_OPTIONS) return
        Log.i(TAG, "UM980 baud request: $baud")
        armUm980UsbGuard()
        val sess = session ?: return
        sess.beginCriticalIo()
        try {
            sess.writeLine(EspCompanionProtocol.encodeUm980Baud(baud).trimEnd())
        } finally {
            sess.endCriticalIo()
        }
    }

    fun sendUm980Commands(commands: List<String>) {
        um980BatchJob?.cancel()
        um980BatchJob = scope.launch {
            val sess = session ?: return@launch
            val list = commands.map { it.trim() }.filter { it.isNotEmpty() }
            if (list.isEmpty()) return@launch
            Log.i(TAG, "UM980 batch: ${list.size} cmd(s)")
            sess.beginCriticalIo()
            try {
                for (trimmed in list) {
                    if (EspCompanionRepository.otaBusy.value) break
                    Log.d(TAG, "UM980 cmd: ${trimmed.take(64)}")
                    armUm980UsbGuard()
                    val waiter = CompletableDeferred<EspMessage.Um980Rsp>()
                    um980RspWaiter.set(waiter)
                    sess.writeLine(EspCompanionProtocol.encodeUm980Cmd(trimmed).trimEnd())
                    val rsp = withTimeoutOrNull(UM980_RSP_TIMEOUT_MS) { waiter.await() }
                    um980RspWaiter.compareAndSet(waiter, null)
                    if (rsp == null) {
                        Log.w(TAG, "UM980 no response for: ${trimmed.take(40)}")
                    } else {
                        Log.d(TAG, "UM980 rsp ok=${rsp.ok} lines=${rsp.lines.size} cmd=${trimmed.take(40)}")
                    }
                    delay(80)
                }
                armUm980UsbGuard(extraMs = 3_000L)
            } finally {
                sess.endCriticalIo()
            }
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
            }
            is EspMessage.Heartbeat -> {
                EspCompanionRepository.updateHeartbeat(msg.uptimeMs)
                EspCompanionRepository.updateConnected(true)
            }
            is EspMessage.Gps -> {
                val loc = EspCompanionProtocol.gpsToLocValues(msg, Date())
                EspCompanionRepository.updateLocValues(loc)
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
                if (msg.cmd.equals("CONFIG", ignoreCase = true) ||
                    msg.lines.any { it.contains("CONFIG", ignoreCase = true) }
                ) {
                    EspCompanionRepository.replaceUm980ConfigSnapshot(
                        Um980Commands.parseConfigSnapshot(msg.lines)
                    )
                }
            }
            is EspMessage.Um980Baud -> {
                EspCompanionRepository.updateHeartbeat(0L)
                val info = EspCompanionRepository.deviceInfo.value
                EspCompanionRepository.updateDeviceInfo(info.copy(um980Baud = msg.baud))
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

    private fun applyLocationSource(source: LocationSource) {
        when (source) {
            LocationSource.ANDROID -> {
                if (androidLocationSource == null) {
                    androidLocationSource = AndroidLocationSource(context) { loc ->
                        if (locationSource.value == LocationSource.ANDROID) {
                            publishActiveLocation(loc)
                        }
                    }.also { it.start() }
                }
                locationMockManager.stopMockLocation()
            }
            LocationSource.ESP32 -> {
                androidLocationSource?.stop()
                androidLocationSource = null
                val loc = EspCompanionRepository.locValues.value
                if (loc.updateTime != null) {
                    publishActiveLocation(loc)
                }
            }
            LocationSource.TBOX -> {
                androidLocationSource?.stop()
                androidLocationSource = null
                // TBox path continues to update via BackgroundService.ansLOCValues
            }
        }
        if (source == LocationSource.ANDROID && mockLocation.value) {
            locationMockManager.stopMockLocation()
        }
    }

    private fun publishActiveLocation(loc: LocValues) {
        TboxRepository.updateLocationUpdateTime()
        if (loc.rawValue != TboxRepository.locValues.value.rawValue ||
            loc.latitude != TboxRepository.locValues.value.latitude ||
            loc.longitude != TboxRepository.locValues.value.longitude
        ) {
            TboxRepository.updateLocValues(loc)
        }
        val truth = when {
            !loc.locateStatus || (loc.latitude == 0.0 && loc.longitude == 0.0) -> false
            locationSource.value == LocationSource.TBOX -> isLocValuesTrueEvaluator(loc)
            else -> loc.locateStatus
        }
        TboxRepository.updateIsLocValuesTrue(truth)
        // Mock pushes are owned by MockLocationJob (configurable period).
    }

    /** Called from TBox LOC path when source is TBOX (location already in TboxRepository). */
    fun onTboxLocValues(loc: LocValues) {
        if (locationSource.value != LocationSource.TBOX) return
        // Mock pushes are owned by MockLocationJob.
    }
}
