package vad.dashing.tbox.phone

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import vad.dashing.tbox.phoneble.GenerationSlot
import vad.dashing.tbox.phoneble.PhoneBleCodec
import vad.dashing.tbox.phoneble.PhoneBleLinkPolicy
import vad.dashing.tbox.phoneble.PhoneDirectWire
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

/**
 * RFCOMM to TBox Monitor on a head unit the phone is already bonded with
 * ([PhoneDirectWire]). Candidates: the head unit that answered last time, devices
 * connected over HFP or A2DP, and bonded devices whose cached SDP lists the service.
 * A worker thread connects and reads; snapshots are opened on the main thread.
 */
@SuppressLint("MissingPermission")
class PhoneDirectLink(
    context: Context,
    private val store: PhoneStore,
    private val assembler: PhoneBleCodec.SnapshotAssembler,
    private val onSnap: (PhoneBleCodec.Snapshot) -> Unit,
    private val onTelemetryAck: (PhoneBleCodec.TelemetryAck) -> Unit = {},
) : PhoneLink {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val manager = appContext.getSystemService(BluetoothManager::class.java)
    /** Replaced when a write hangs: the stuck thread is abandoned with its dead socket. */
    @Volatile private var writer: ExecutorService = Executors.newSingleThreadExecutor()
    private val proxies = ConcurrentHashMap<Int, BluetoothProfile>()
    /** Bumped by [start]/[stop]; a worker of an older generation leaves the state alone. */
    @Volatile private var generation = 0
    /** Connected socket or the one blocked in connect(); closing it unblocks the worker. */
    private val sockets = GenerationSlot<BluetoothSocket>()
    /** Addresses whose SDP lookup said the TBox service is absent. Cleared when the UUID appears. */
    private val noService = ConcurrentHashMap.newKeySet<String>()
    /** fetchUuidsWithSdp is once per address per process, not once per connect attempt. */
    private val sdpAsked = ConcurrentHashMap.newKeySet<String>()
    /** Fast rejects of [PhoneStore.directAddress]. Out-of-range timeouts do not count. */
    private var rememberedFailures = 0
    private var running = false
    /** elapsedRealtime of link-up and of the last accepted snapshot (0 = none on this link). */
    private var linkAt = 0L
    private var lastSnapAt = 0L
    /** elapsedRealtime when the blocking connect() or the current write began (0 = none). */
    @Volatile private var connectAt = 0L
    @Volatile private var writeAt = 0L
    @Volatile private var writeLink: BluetoothSocket? = null
    private val watchdog = object : Runnable {
        override fun run() {
            if (!running) return
            recoverIfStale()
            main.postDelayed(this, WATCHDOG_MS)
        }
    }

    @Volatile override var linkUp: Boolean = false
        private set

    override val confirmed: Boolean get() = linkUp && lastSnapAt != 0L

    override var storeKey: ByteArray = ByteArray(PhoneBleCodec.KEY_LEN)
    @Volatile override var acceptFromCounter: Long = -1L
    @Volatile override var waitingCounter: Long = -1L

    /** Full passes over the candidates that found no head unit since [start]. */
    @Volatile var passesWithoutLink: Int = 0
        private set

    /** UI visible: retry every [RETRY_MS]; otherwise back off (see [PhoneBleLinkPolicy.directSearchRetryMs]). */
    @Volatile var foreground: Boolean = false

    override fun start() {
        if (running) {
            recoverIfStale()
            return
        }
        running = true
        passesWithoutLink = 0
        openProxies()
        val gen = ++generation
        Thread({ loop(gen) }, "phone-direct").apply {
            isDaemon = true
            start()
        }
        main.removeCallbacks(watchdog)
        main.postDelayed(watchdog, WATCHDOG_MS)
    }

    override fun stop() {
        if (!running) return
        running = false
        generation++
        main.removeCallbacks(watchdog)
        closeSocket()
        closeProxies()
        linkUp = false
        lastSnapAt = 0L
    }

    override fun kickSearch() {
        if (running) recoverIfStale()
    }

    override fun write(payload: ByteArray) {
        val link = liveSocket() ?: return
        if (!linkUp) return
        val frame = PhoneDirectWire.frame(payload)
        enqueueFrames(link, frame)
    }

    /**
     * Allocates [PhoneStore.nextCounter] and seals [body] inside the single-threaded
     * [writer] executor so high-rate telemetry (`YAW`, `LOC`, `MAG`) and UI packets
     * (`CMD`, `REFRESH`) are always written to the RFCOMM stream in strictly ascending
     * counter order.
     */
    fun writeSealed(
        type: Int,
        body: ByteArray,
        resetAcceptRange: Boolean = false,
        updateWaitingCounter: Boolean = false,
    ) {
        val link = liveSocket() ?: return
        if (!linkUp) return
        executeWrite {
            val counter = store.nextCounter()
            val window = PhoneBleLinkPolicy.acceptWindowAfterSend(
                previous = PhoneBleLinkPolicy.AcceptWindow(acceptFromCounter, waitingCounter),
                counter = counter,
                resetAcceptRange = resetAcceptRange,
                updateWaiting = updateWaitingCounter,
            )
            acceptFromCounter = window.acceptFrom
            waitingCounter = window.waiting
            val sealed = PhoneBleCodec.seal(store.key, type, store.id, counter, body)
            val frame = PhoneDirectWire.frame(sealed)
            writeFrameBlocking(link, frame)
        }
    }

    /**
     * Seals and writes a multi-packet batch (e.g. `LOC_POS` + `LOC_MOTION`) atomically
     * on the single-threaded [writer] executor.
     */
    fun writeSealedBatch(packets: List<Pair<Int, ByteArray>>) {
        if (packets.isEmpty()) return
        val link = liveSocket() ?: return
        if (!linkUp) return
        executeWrite {
            val frames = packets.map { (type, body) ->
                val counter = store.nextCounter()
                val sealed = PhoneBleCodec.seal(store.key, type, store.id, counter, body)
                PhoneDirectWire.frame(sealed)
            }
            val total = frames.sumOf { it.size }
            val combined = ByteArray(total)
            var offset = 0
            for (f in frames) {
                f.copyInto(combined, offset)
                offset += f.size
            }
            writeFrameBlocking(link, combined)
        }
    }

    private fun enqueueFrames(link: BluetoothSocket, frame: ByteArray) {
        executeWrite {
            writeFrameBlocking(link, frame)
        }
    }

    private fun executeWrite(block: () -> Unit) {
        val exec = writer
        try {
            exec.execute {
                if (exec.isShutdown) return@execute
                try {
                    block()
                } catch (e: Exception) {
                    Log.w(TAG, "write task", e)
                }
            }
        } catch (e: RejectedExecutionException) {
            Log.w(TAG, "writer rejected", e)
        }
    }

    private fun liveSocket(): BluetoothSocket? = sockets.current(generation)

    private fun writeFrameBlocking(link: BluetoothSocket, frame: ByteArray) {
        val started = SystemClock.elapsedRealtime()
        writeLink = link
        writeAt = started
        try {
            link.outputStream.write(frame)
            link.outputStream.flush()
        } catch (e: IOException) {
            Log.w(TAG, "write: ${e.message}")
            runCatching { link.close() }
        } finally {
            if (writeAt == started) writeAt = 0L
        }
    }

    /**
     * Same recovery as the BLE watchdog in [PhoneRadio]: a connect() that never returns,
     * a write stuck in a dead socket, or a link without snapshots. Closing the socket
     * unblocks the worker, which then searches again.
     */
    private fun recoverIfStale() {
        val now = SystemClock.elapsedRealtime()
        val connectStarted = connectAt
        val connectAge = if (connectStarted != 0L) now - connectStarted else 0L
        if (PhoneBleLinkPolicy.hungConnect(linkUp, connectStarted != 0L, connectAge, force = false)) {
            Log.w(TAG, "connect hung for ${connectAge}ms, closing")
            closeSocket()
            return
        }
        val writeStarted = writeAt
        val writeAge = if (writeStarted != 0L) now - writeStarted else 0L
        if (PhoneBleLinkPolicy.hungWrite(writeStarted != 0L, writeAge, force = false)) {
            Log.w(TAG, "write hung for ${writeAge}ms, closing and replacing the writer")
            runCatching { writeLink?.close() }
            closeSocket()
            writer.shutdownNow()
            writer = Executors.newSingleThreadExecutor()
            writeAt = 0L
            return
        }
        if (!linkUp) return
        val since = if (lastSnapAt != 0L) lastSnapAt else linkAt
        val age = now - since
        if (PhoneBleLinkPolicy.staleLinked(linkUp = true, snapAgeMs = age, force = false)) {
            Log.w(TAG, "stale direct link, reconnecting: age=${age}ms snap=${lastSnapAt != 0L}")
            closeSocket()
        }
    }

    private fun loop(gen: Int) {
        while (gen == generation) {
            try {
                if (!searchOnce(gen)) return
            } catch (e: Exception) {
                Log.w(TAG, "direct loop", e)
                if (!pause(gen, RETRY_MS)) return
            }
        }
    }

    /** False when this worker became obsolete during the wait. */
    private fun searchOnce(gen: Int): Boolean {
        val adapter = runCatching { manager?.adapter }.getOrNull()
        if (adapter == null || !adapter.isEnabled) return pause(gen, RETRY_MS)
        var answered = false
        var rememberedFailed = false
        val remembered = store.directAddress
        for (candidate in candidates(adapter)) {
            if (gen != generation) return false
            val attempt = connect(gen, adapter, candidate)
            if (attempt.discoveryFailed) noService.add(candidate.address)
            val link = attempt.socket
            if (link == null) {
                if (candidate.address == remembered && attempt.fastFail) rememberedFailed = true
                continue
            }
            answered = serve(gen, link, candidate)
            if (candidate.address == remembered && !answered) rememberedFailed = true
            break
        }
        if (gen != generation) return false
        if (answered) {
            rememberedFailures = 0
        } else if (rememberedFailed) {
            rememberedFailures++
            if (rememberedFailures >= PhoneBleLinkPolicy.FORGET_REMEMBERED_AFTER &&
                store.directAddress == remembered
            ) {
                store.directAddress = null
                remembered?.let { noService.remove(it) }
                rememberedFailures = 0
            }
        }
        val decision = PhoneBleLinkPolicy.directWaitAfterClose(
            answered = answered,
            foreground = foreground,
            passesWithoutLink = passesWithoutLink,
        )
        passesWithoutLink = decision.passesWithoutLink
        return pause(gen, decision.waitMs)
    }

    private fun candidates(adapter: BluetoothAdapter): List<BluetoothDevice> {
        val bonded = runCatching { adapter.bondedDevices.orEmpty() }.getOrDefault(emptySet())
        val remembered = store.directAddress
        val byAddress = LinkedHashMap<String, BluetoothDevice>()
        remembered?.let { last ->
            bonded.firstOrNull { it.address == last }?.let { byAddress[it.address] = it }
        }
        for (proxy in proxies.values) {
            runCatching { proxy.connectedDevices }.getOrNull()?.forEach { byAddress.putIfAbsent(it.address, it) }
        }
        for (device in bonded) byAddress.putIfAbsent(device.address, device)
        val ranked = ArrayList<Pair<Int, BluetoothDevice>>()
        for (device in byAddress.values) {
            val listed = listsService(device)
            if (listed) noService.remove(device.address)
            if (!listed && sdpAsked.add(device.address)) {
                runCatching { device.fetchUuidsWithSdp() }
            }
            if (device.address in noService) continue
            val btClass = runCatching { device.bluetoothClass }.getOrNull()
            val rank = PhoneBleLinkPolicy.directCandidateRank(
                majorClass = btClass?.majorDeviceClass ?: 0,
                deviceClass = btClass?.deviceClass ?: 0,
                hasServiceUuid = listed,
                remembered = device.address == remembered,
            ) ?: continue
            ranked += rank to device
        }
        return ranked.sortedBy { it.first }.map { it.second }
    }

    private fun listsService(device: BluetoothDevice): Boolean =
        runCatching { device.uuids }.getOrNull()
            ?.any { it.uuid == PhoneDirectWire.SERVICE_UUID } == true

    private fun connect(gen: Int, adapter: BluetoothAdapter, candidate: BluetoothDevice): ConnectAttempt {
        val started = SystemClock.elapsedRealtime()
        val attempt = try {
            candidate.createRfcommSocketToServiceRecord(PhoneDirectWire.SERVICE_UUID)
        } catch (e: Exception) {
            Log.w(TAG, "socket: ${e.message}")
            return ConnectAttempt(socket = null, discoveryFailed = discoveryFailed(e), fastFail = true)
        }
        val published = sockets.publish(gen, { generation }, attempt) { runCatching { it.close() } }
        if (!published) {
            return ConnectAttempt(socket = null, discoveryFailed = false, fastFail = false)
        }
        var failed: Exception? = null
        val ok = try {
            runCatching { adapter.cancelDiscovery() }
            connectAt = SystemClock.elapsedRealtime()
            attempt.connect()
            gen == generation
        } catch (e: Exception) {
            failed = e
            false
        } finally {
            connectAt = 0L
        }
        if (ok) return ConnectAttempt(socket = attempt, discoveryFailed = false, fastFail = false)
        val elapsed = SystemClock.elapsedRealtime() - started
        runCatching { attempt.close() }
        return ConnectAttempt(
            socket = null,
            discoveryFailed = failed?.let(::discoveryFailed) == true,
            fastFail = elapsed < PhoneBleLinkPolicy.DIRECT_FAST_FAIL_MS,
        )
    }

    private fun discoveryFailed(error: Exception): Boolean {
        val message = error.message?.lowercase().orEmpty()
        return "service discovery failed" in message
    }

    /** True when a sealed snapshot or telemetry ack arrived before the socket closed. */
    private fun serve(gen: Int, link: BluetoothSocket, peer: BluetoothDevice): Boolean {
        main.post {
            if (gen != generation) return@post
            linkAt = SystemClock.elapsedRealtime()
            lastSnapAt = 0L
        }
        linkUp = true
        val reader = PhoneDirectWire.FrameReader()
        val buffer = ByteArray(512)
        var reason = "obsolete"
        var answered = false
        try {
            val input = link.inputStream
            while (gen == generation) {
                val count = input.read(buffer)
                if (count < 0) {
                    reason = "eof"
                    break
                }
                for (payload in reader.feed(buffer, count)) {
                    if (peerAnswered(payload)) answered = true
                    main.post { if (gen == generation) acceptSnap(payload, peer) }
                }
                if (reader.broken) {
                    reason = "broken frame"
                    break
                }
            }
        } catch (e: Exception) {
            reason = "io: ${e.message}"
        } finally {
            Log.d(TAG, "link closed: $reason answered=$answered")
            runCatching { link.close() }
            if (gen == generation) {
                linkUp = false
                main.post { if (gen == generation) lastSnapAt = 0L }
            }
        }
        return answered
    }

    private fun peerAnswered(value: ByteArray): Boolean {
        val open = PhoneBleCodec.open(storeKey, value) ?: return false
        if (open.type == PhoneBleCodec.TYPE_TELEMETRY_ACK) {
            return PhoneBleCodec.readTelemetryAck(open.body) != null
        }
        return PhoneBleCodec.isSnapType(open.type)
    }

    private fun acceptSnap(value: ByteArray, peer: BluetoothDevice) {
        val open = PhoneBleCodec.open(storeKey, value) ?: return
        if (open.type == PhoneBleCodec.TYPE_TELEMETRY_ACK) {
            val ack = PhoneBleCodec.readTelemetryAck(open.body) ?: return
            lastSnapAt = SystemClock.elapsedRealtime()
            if (store.directAddress != peer.address) store.directAddress = peer.address
            onTelemetryAck(ack)
            return
        }
        if (!PhoneBleCodec.isSnapType(open.type)) return
        if (!PhoneBleLinkPolicy.counterInAcceptWindow(
                open.counter,
                PhoneBleLinkPolicy.AcceptWindow(acceptFromCounter, waitingCounter),
            )
        ) {
            return
        }
        lastSnapAt = SystemClock.elapsedRealtime()
        if (store.directAddress != peer.address) store.directAddress = peer.address
        onSnap(assembler.accept(open.type, open.body))
    }

    private fun closeSocket() {
        sockets.closeUpTo(generation) { runCatching { it.close() } }
    }

    private fun openProxies() {
        val adapter = runCatching { manager?.adapter }.getOrNull() ?: return
        for (profile in listOf(BluetoothProfile.HEADSET, BluetoothProfile.A2DP)) {
            runCatching {
                adapter.getProfileProxy(
                    appContext,
                    object : BluetoothProfile.ServiceListener {
                        override fun onServiceConnected(id: Int, proxy: BluetoothProfile) {
                            if (!running) {
                                closeProxy(id, proxy)
                                return
                            }
                            val previous = proxies.put(id, proxy)
                            if (previous != null && previous !== proxy) closeProxy(id, previous)
                        }

                        override fun onServiceDisconnected(id: Int) {
                            proxies.remove(id)
                        }
                    },
                    profile,
                )
            }
        }
    }

    private fun closeProxies() {
        for ((id, proxy) in proxies) closeProxy(id, proxy)
        proxies.clear()
    }

    private fun closeProxy(id: Int, proxy: BluetoothProfile) {
        runCatching { manager?.adapter?.closeProfileProxy(id, proxy) }
    }

    /** False when the worker became obsolete during the wait. */
    private fun pause(gen: Int, ms: Long): Boolean {
        val startedAt = SystemClock.elapsedRealtime()
        val until = startedAt + ms
        while (gen == generation) {
            val now = SystemClock.elapsedRealtime()
            val left = until - now
            if (left <= 0) return true
            // Opening the UI cuts a long background back-off short.
            if (foreground && now - startedAt >= RETRY_MS) return true
            try {
                Thread.sleep(minOf(left, 250L))
            } catch (e: InterruptedException) {
                return false
            }
        }
        return false
    }

    private class ConnectAttempt(
        val socket: BluetoothSocket?,
        val discoveryFailed: Boolean,
        val fastFail: Boolean,
    )

    private companion object {
        const val TAG = "PhoneDirectLink"
        const val RETRY_MS = PhoneBleLinkPolicy.DIRECT_RETRY_MS
        const val WATCHDOG_MS = 1_000L
    }
}
