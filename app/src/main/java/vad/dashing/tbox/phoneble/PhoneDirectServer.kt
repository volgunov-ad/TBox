package vad.dashing.tbox.phoneble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import vad.dashing.tbox.TboxRepository
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * RFCOMM server for the phone app on an already bonded phone (see [PhoneDirectWire]).
 * Runs next to HFP/A2DP on the same ACL link; checked on both A9 and A10 head units.
 * Keys of the phones paired here stay in this app's private preferences.
 */
@SuppressLint("MissingPermission")
class PhoneDirectServer(
    context: Context,
    private val scope: CoroutineScope,
    private val readSnapshot: suspend () -> PhoneBleCodec.Snapshot,
    private val executeCommand: suspend (op: Int, seat: Int, arg: Int) -> Unit,
) {
    private class Link(val socket: BluetoothSocket, val address: String) {
        @Volatile var phoneIdHex: String? = null
        @Volatile var lastValidAt: Long = SystemClock.elapsedRealtime()
    }

    private val manager = context.getSystemService(BluetoothManager::class.java)
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val host = PhoneDirectHost(PhoneDirectHost.fromJson(prefs.getString(KEY_PHONES, null)))
    private val links = ConcurrentHashMap.newKeySet<Link>()
    private val snapMutex = Mutex()
    private var jobs: List<Job> = emptyList()
    @Volatile private var serverSocket: BluetoothServerSocket? = null
    @Volatile private var learnUntil = 0L
    private var snapGen = 0
    private var cachedSnap: PhoneBleCodec.Snapshot? = null
    private var cachedAt = 0L

    fun start() {
        if (jobs.isNotEmpty()) return
        PhoneDirectRepository.server = this
        publishPhones()
        jobs = listOf(
            scope.launch(Dispatchers.IO) { acceptLoop() },
            scope.launch(Dispatchers.IO) { housekeeping() },
        )
    }

    fun stop() {
        jobs.forEach { it.cancel() }
        jobs = emptyList()
        closeQuietly(serverSocket)
        serverSocket = null
        links.forEach { closeQuietly(it.socket) }
        links.clear()
        saveRecords()
        if (PhoneDirectRepository.server === this) PhoneDirectRepository.server = null
        learnUntil = 0L
        PhoneDirectRepository.setLearnUntil(0L)
        PhoneDirectRepository.setPending(null)
        PhoneDirectRepository.setState(PhoneDirectState.OFF)
    }

    fun beginLearn() {
        host.beginLearn()
        learnUntil = SystemClock.elapsedRealtime() + LEARN_WINDOW_MS
        PhoneDirectRepository.setPending(null)
        PhoneDirectRepository.setLearnUntil(learnUntil)
    }

    fun endLearn() {
        host.endLearn()
        learnUntil = 0L
        PhoneDirectRepository.setPending(null)
        PhoneDirectRepository.setLearnUntil(0L)
    }

    fun allow(idHex: String) {
        val name = PhoneDirectRepository.pending.value?.name.orEmpty()
        if (host.allow(idHex)) {
            saveRecords()
            TboxRepository.addLog("INFO", TAG, "Phone paired: $name ($idHex)")
        }
        endLearn()
        publishPhones()
    }

    fun deny(idHex: String) {
        host.deny(idHex)
        PhoneDirectRepository.setPending(null)
    }

    fun forget(idHex: String) {
        if (host.forget(idHex)) saveRecords()
        links.filter { it.phoneIdHex == idHex }.forEach { closeQuietly(it.socket) }
        publishPhones()
    }

    private suspend fun acceptLoop() {
        while (currentCoroutineContext().isActive) {
            val adapter = runCatching { manager?.adapter }.getOrNull()
            if (adapter == null || !adapter.isEnabled) {
                PhoneDirectRepository.setState(PhoneDirectState.BLUETOOTH_OFF)
                delay(RETRY_MS)
                continue
            }
            try {
                val socket = adapter.listenUsingRfcommWithServiceRecord(
                    PhoneDirectWire.SERVICE_NAME,
                    PhoneDirectWire.SERVICE_UUID,
                )
                serverSocket = socket
                PhoneDirectRepository.setState(PhoneDirectState.LISTENING)
                while (currentCoroutineContext().isActive) {
                    val client = socket.accept()
                    if (links.size >= MAX_LINKS) {
                        closeQuietly(client)
                        continue
                    }
                    val link = Link(client, client.remoteDevice?.address.orEmpty())
                    links += link
                    scope.launch(Dispatchers.IO) { serve(link) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                // Bluetooth turned off or the stack restarted: listen again after a pause.
                Log.w(TAG, "listen: ${e.message}")
            } catch (e: SecurityException) {
                Log.e(TAG, "listen: no permission", e)
                PhoneDirectRepository.setState(PhoneDirectState.ERROR)
            } finally {
                closeQuietly(serverSocket)
                serverSocket = null
            }
            if (currentCoroutineContext().isActive) delay(RETRY_MS)
        }
    }

    private suspend fun serve(link: Link) {
        val reader = PhoneDirectWire.FrameReader()
        val buffer = ByteArray(256)
        try {
            val input = link.socket.inputStream
            while (currentCoroutineContext().isActive) {
                val count = input.read(buffer)
                if (count < 0) break
                for (payload in reader.feed(buffer, count)) handle(link, payload)
                if (reader.broken) break
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Log.d(TAG, "link ${link.address} closed: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "link ${link.address} failed", e)
        } finally {
            closeQuietly(link.socket)
            links -= link
            if (link.phoneIdHex != null) publishPhones()
        }
    }

    private suspend fun handle(link: Link, payload: ByteArray) {
        when (val result = host.onPayload(payload)) {
            is PhoneDirectHost.Result.PairRequest -> {
                link.lastValidAt = SystemClock.elapsedRealtime()
                PhoneDirectRepository.setPending(PhoneDirectPhone(result.idHex, result.name))
            }
            PhoneDirectHost.Result.Accepted -> link.lastValidAt = SystemClock.elapsedRealtime()
            is PhoneDirectHost.Result.Command -> {
                bind(link, result.idHex)
                // The cached state predates this command.
                snapMutex.withLock { cachedSnap = null }
                try {
                    executeCommand(result.op, result.seat, result.arg)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    TboxRepository.addLog("ERROR", TAG, "Phone command failed: ${e.message}")
                }
            }
            is PhoneDirectHost.Result.Refresh -> {
                bind(link, result.idHex)
                val (gen, snap) = snapshot()
                val pages = host.sealSnapshot(result.idHex, result.counter, gen, snap, result.textHash, result.groups)
                if (pages.isNotEmpty()) send(link, pages)
            }
            PhoneDirectHost.Result.Ignored -> Unit
        }
    }

    private fun bind(link: Link, idHex: String) {
        link.lastValidAt = SystemClock.elapsedRealtime()
        if (link.phoneIdHex != idHex) {
            link.phoneIdHex = idHex
            TboxRepository.addLog("INFO", TAG, "Phone linked: $idHex (${link.address})")
            publishPhones()
        }
    }

    /** One read serves every phone for [SNAP_FRESH_MS], like the companion's cache. */
    private suspend fun snapshot(): Pair<Int, PhoneBleCodec.Snapshot> = snapMutex.withLock {
        val now = SystemClock.elapsedRealtime()
        val cached = cachedSnap
        if (cached != null && now - cachedAt < SNAP_FRESH_MS) {
            val age = now - cachedAt
            val position = cached.positionMs
            val adjusted = if (cached.playing == 1 && position != null) {
                val moved = position + age
                cached.copy(positionMs = cached.durationMs?.let { minOf(moved, it) } ?: moved)
            } else {
                cached
            }
            return@withLock snapGen to adjusted
        }
        val fresh = readSnapshot()
        snapGen = if (snapGen >= 255) 1 else snapGen + 1
        cachedSnap = fresh
        cachedAt = now
        snapGen to fresh
    }

    private fun send(link: Link, payloads: List<ByteArray>) {
        val frames = payloads.map(PhoneDirectWire::frame)
        val out = ByteArray(frames.sumOf { it.size })
        var offset = 0
        for (frame in frames) {
            frame.copyInto(out, offset)
            offset += frame.size
        }
        try {
            val stream = link.socket.outputStream
            synchronized(link) {
                stream.write(out)
                stream.flush()
            }
        } catch (e: IOException) {
            closeQuietly(link.socket)
        }
    }

    private suspend fun housekeeping() {
        var lastSaveAt = SystemClock.elapsedRealtime()
        while (currentCoroutineContext().isActive) {
            delay(1_000L)
            val now = SystemClock.elapsedRealtime()
            if (learnUntil != 0L && now >= learnUntil) endLearn()
            for (link in links) {
                val limit = if (link.phoneIdHex == null) STRANGER_IDLE_MS else LINK_IDLE_MS
                if (now - link.lastValidAt >= limit) closeQuietly(link.socket)
            }
            // A counter write per refresh would wear the flash; replay of the last few
            // seconds after a power cut is the accepted cost, as on the companion.
            if (now - lastSaveAt >= COUNTER_SAVE_MS && host.takeCountersDirty()) {
                saveRecords()
                lastSaveAt = now
            }
        }
    }

    private fun saveRecords() {
        prefs.edit().putString(KEY_PHONES, PhoneDirectHost.toJson(host.records())).apply()
    }

    private fun publishPhones() {
        val online = links.mapNotNull { it.phoneIdHex }.toSet()
        PhoneDirectRepository.setPhones(
            host.records().map { PhoneDirectPhone(it.idHex, it.name, it.idHex in online) },
        )
    }

    private fun closeQuietly(socket: BluetoothSocket?) {
        runCatching { socket?.close() }
    }

    private fun closeQuietly(socket: BluetoothServerSocket?) {
        runCatching { socket?.close() }
    }

    private companion object {
        const val TAG = "Phone direct"
        const val PREFS = "phone_direct"
        const val KEY_PHONES = "phones"
        const val MAX_LINKS = 3
        const val RETRY_MS = 5_000L
        const val LEARN_WINDOW_MS = 90_000L
        const val SNAP_FRESH_MS = 2_000L
        /** A link that never sent a valid packet is dropped, as the companion drops strangers. */
        const val STRANGER_IDLE_MS = 20_000L
        /** The phone refreshes about every 2 s while its screen is open. */
        const val LINK_IDLE_MS = 30_000L
        const val COUNTER_SAVE_MS = 10_000L
    }
}
