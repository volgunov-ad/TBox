package vad.dashing.tbox.phone

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.os.SystemClock
import androidx.annotation.RequiresApi
import android.util.Log
import vad.dashing.tbox.phoneble.PhoneBleCodec
import vad.dashing.tbox.phoneble.PhoneBleLinkPolicy
import java.util.ArrayDeque
import java.util.UUID

/**
 * Phone is the BLE central. It connects to the companion, writes commands to the inbox
 * characteristic and receives snapshot pages as notifications.
 * Calls from the UI thread. GATT callbacks hop back to the main thread before touching state.
 */
@SuppressLint("MissingPermission")
class PhoneRadio(
    context: Context,
    private val assembler: PhoneBleCodec.SnapshotAssembler,
    private val onSnap: (PhoneBleCodec.Snapshot) -> Unit,
) : PhoneLink {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val manager = appContext.getSystemService(BluetoothManager::class.java)
    private val serviceUuid = ParcelUuid(PhoneBleCodec.SERVICE_UUID)
    private val pending = ArrayDeque<ByteArray>()
    private var gatt: BluetoothGatt? = null
    private var inbox: BluetoothGattCharacteristic? = null
    private var running = false
    private var scanning = false
    private var writing = false
    private var snapOnLink = false
    private var strangerDrop = false
    /** elapsedRealtime when the current scan or connect attempt began. */
    private var attemptAt = 0L
    /** elapsedRealtime of the last accepted sealed snapshot (0 = none on this link). */
    private var lastSnapAt = 0L
    /** elapsedRealtime when [writing] became true. */
    private var writeAt = 0L

    @Volatile override var linkUp: Boolean = false
        private set

    override val confirmed: Boolean get() = linkUp && snapOnLink

    override var storeKey: ByteArray = ByteArray(PhoneBleCodec.KEY_LEN)
    override var waitingCounter: Long = -1L

    val bluetoothOn: Boolean
        get() = runCatching { manager?.adapter?.isEnabled == true }.getOrDefault(false)

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device ?: return
            main.post { connect(device) }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.w(TAG, "scan failed: $errorCode")
            main.post {
                scanning = false
                rescanLater()
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            main.post {
                if (gatt != this@PhoneRadio.gatt) return@post
                if (newState == BluetoothGatt.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                    if (!gatt.requestMtu(PhoneBleCodec.ATT_MTU)) gatt.discoverServices()
                } else {
                    dropLink()
                    rescanLater()
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            main.post {
                if (gatt != this@PhoneRadio.gatt) return@post
                if (mtu < PhoneBleCodec.SEALED_LEN + 3) {
                    Log.w(TAG, "mtu $mtu is too small")
                }
                gatt.discoverServices()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            main.post {
                if (gatt != this@PhoneRadio.gatt) return@post
                val service = gatt.getService(PhoneBleCodec.SERVICE_UUID)
                val inboxChr = service?.getCharacteristic(PhoneBleCodec.INBOX_UUID)
                val snapChr = service?.getCharacteristic(PhoneBleCodec.SNAP_UUID)
                if (status != BluetoothGatt.GATT_SUCCESS || inboxChr == null || snapChr == null) {
                    Log.w(TAG, "phone service missing")
                    dropLink()
                    rescanLater()
                    return@post
                }
                inbox = inboxChr
                gatt.setCharacteristicNotification(snapChr, true)
                val cccd = snapChr.getDescriptor(CCCD)
                if (cccd == null || !writeCccd(gatt, cccd)) {
                    dropLink()
                    rescanLater()
                }
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            main.post {
                if (gatt != this@PhoneRadio.gatt) return@post
                if (descriptor.uuid != CCCD) return@post
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    linkUp = true
                    lastSnapAt = 0L
                    attemptAt = SystemClock.elapsedRealtime()
                    scheduleWatchdog(PhoneBleLinkPolicy.LINK_STALE_MS)
                    pump()
                } else {
                    dropLink()
                    rescanLater()
                }
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            main.post {
                if (gatt != this@PhoneRadio.gatt) return@post
                writing = false
                writeAt = 0L
                if (linkUp) scheduleWatchdog(PhoneBleLinkPolicy.LINK_STALE_MS)
                pump()
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            main.post { acceptSnap(value) }
        }

        @Deprecated("Used before API 33")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            if (Build.VERSION.SDK_INT >= 33) return
            @Suppress("DEPRECATION")
            val value = characteristic.value ?: return
            main.post { acceptSnap(value) }
        }
    }

    override fun start() {
        if (running) {
            recoverIfStale()
            if (gatt == null) startScan()
            return
        }
        running = true
        startScan()
    }

    /**
     * After a long background/doze pause the OS may leave a dead GATT handle, a scan
     * that never fires [ScanCallback.onScanFailed], or a zombie link that stays up
     * without snapshots. Force a clean search without touching pairing material in
     * [PhoneStore]. Skips a fresh in-flight scan/connect; reopens a stale linked GATT.
     */
    override fun kickSearch() {
        if (!running || !bluetoothOn) return
        val now = SystemClock.elapsedRealtime()
        val snapAge = if (lastSnapAt != 0L) now - lastSnapAt else now - attemptAt
        if (!PhoneBleLinkPolicy.resumeShouldKick(
                linkUp = linkUp,
                scanningOrConnecting = scanning || gatt != null,
                attemptAgeMs = now - attemptAt,
                snapAgeMs = snapAge,
            )
        ) {
            return
        }
        Log.i(TAG, "kickSearch: drop stale attempt/link and rescan linkUp=$linkUp")
        main.removeCallbacks(watchdog)
        stopScan()
        dropLink(peerRejected = false)
        startScan()
    }

    override fun stop() {
        running = false
        main.removeCallbacksAndMessages(null)
        stopScan()
        gatt?.close()
        gatt = null
        inbox = null
        linkUp = false
        writing = false
        writeAt = 0L
        lastSnapAt = 0L
        pending.clear()
    }

    override fun write(payload: ByteArray) {
        if (!running) return
        if (pending.size >= PENDING_MAX) pending.removeFirst()
        pending.addLast(payload)
        pump()
    }

    private fun startScan() {
        if (!running || scanning || gatt != null || !bluetoothOn) return
        val scanner = runCatching { manager?.adapter?.bluetoothLeScanner }.getOrNull() ?: return
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        val filters = listOf(ScanFilter.Builder().setServiceUuid(serviceUuid).build())
        attemptAt = SystemClock.elapsedRealtime()
        scanning = guarded("startScan") { scanner.startScan(filters, settings, scanCallback) }
        if (scanning) scheduleWatchdog(PhoneBleLinkPolicy.SCAN_STALE_MS)
    }

    private fun stopScan() {
        if (!scanning) return
        scanning = false
        val scanner = runCatching { manager?.adapter?.bluetoothLeScanner }.getOrNull() ?: return
        guarded("stopScan") { scanner.stopScan(scanCallback) }
    }

    private fun connect(device: BluetoothDevice) {
        if (!running || gatt != null) return
        stopScan()
        snapOnLink = false
        strangerDrop = false
        attemptAt = SystemClock.elapsedRealtime()
        gatt = guardedOrNull("connect") {
            device.connectGatt(appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        }
        if (gatt == null) {
            rescanLater()
        } else {
            scheduleWatchdog(PhoneBleLinkPolicy.CONNECT_STALE_MS)
        }
    }

    private fun rescanLater() {
        if (!running) return
        main.postDelayed({ startScan() }, if (strangerDrop) STRANGER_RESCAN_MS else RESCAN_MS)
    }

    private fun scheduleWatchdog(delayMs: Long) {
        main.removeCallbacks(watchdog)
        main.postDelayed(watchdog, delayMs)
    }

    private val watchdog = Runnable { recoverIfStale(force = true) }

    /**
     * Clears a hung scan (scanning=true, no results), a hung connectGatt that never
     * reaches [linkUp], a zombie link that stays up without snapshots (doze/USB),
     * or a write that never gets a callback. Without this, [startScan] bails because
     * gatt/scanning stay set and the UI sits on "Looking for the companion" (or a
     * stale "Connected") until the process is killed.
     */
    private fun recoverIfStale(force: Boolean = false) {
        if (!running) return
        val now = SystemClock.elapsedRealtime()
        val attemptAge = now - attemptAt
        val snapAge = if (lastSnapAt != 0L) now - lastSnapAt else attemptAge
        val writeAge = if (writing) now - writeAt else 0L
        val hungScan = PhoneBleLinkPolicy.hungScan(scanning, gatt != null, attemptAge, force)
        val hungConnect = PhoneBleLinkPolicy.hungConnect(linkUp, gatt != null, attemptAge, force)
        val staleLink = PhoneBleLinkPolicy.staleLinked(linkUp, snapAge, force = false)
        val hungWrite = PhoneBleLinkPolicy.hungWrite(writing, writeAge, force = false)
        if (!hungScan && !hungConnect && !staleLink && !hungWrite) return
        Log.w(
            TAG,
            "stale BLE attempt scan=$scanning gatt=${gatt != null} linkUp=$linkUp " +
                "writing=$writing attemptAge=${attemptAge}ms snapAge=${snapAge}ms",
        )
        stopScan()
        // Local stale recovery (USB down, doze zombie) is not a companion stranger reject.
        dropLink(peerRejected = false)
        rescanLater()
    }

    /**
     * @param peerRejected when true, a link that never got a sealed snap is treated as a
     *   stranger (companion idle-drop) and rescans after [STRANGER_RESCAN_MS]. Local
     *   watchdogs pass false so a missing HU/USB snapshot does not add that backoff.
     */
    private fun dropLink(peerRejected: Boolean = true) {
        strangerDrop = peerRejected && linkUp && !snapOnLink
        gatt?.close()
        gatt = null
        inbox = null
        linkUp = false
        writing = false
        writeAt = 0L
        lastSnapAt = 0L
        pending.clear()
    }

    private fun pump() {
        val radio = gatt ?: return
        val chr = inbox ?: return
        if (!linkUp || writing || pending.isEmpty()) return
        val payload = pending.removeFirst()
        writing = true
        writeAt = SystemClock.elapsedRealtime()
        scheduleWatchdog(PhoneBleLinkPolicy.WRITE_STALE_MS)
        val ok = writeInbox(radio, chr, payload)
        if (!ok) {
            writing = false
            writeAt = 0L
            pending.addFirst(payload)
            main.postDelayed({ pump() }, 200)
        }
    }

    private fun acceptSnap(value: ByteArray) {
        val open = PhoneBleCodec.open(storeKey, value) ?: return
        if (!PhoneBleCodec.isSnapType(open.type)) return
        if (open.counter != waitingCounter) return
        snapOnLink = true
        lastSnapAt = SystemClock.elapsedRealtime()
        scheduleWatchdog(PhoneBleLinkPolicy.LINK_STALE_MS)
        onSnap(assembler.accept(open.type, open.body))
    }

    private fun writeInbox(
        radio: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
    ): Boolean =
        if (Build.VERSION.SDK_INT >= 33) writeInboxNew(radio, characteristic, value)
        else writeInboxOld(radio, characteristic, value)

    @RequiresApi(33)
    private fun writeInboxNew(
        radio: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
    ): Boolean = guarded("write") {
        val code = radio.writeCharacteristic(
            characteristic,
            value,
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
        )
        if (code != BluetoothStatusCodes.SUCCESS) error("write $code")
    }

    @Suppress("DEPRECATION")
    private fun writeInboxOld(
        radio: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
    ): Boolean = guarded("write") {
        characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        characteristic.value = value
        if (!radio.writeCharacteristic(characteristic)) error("write rejected")
    }

    private fun writeCccd(radio: BluetoothGatt, descriptor: BluetoothGattDescriptor): Boolean =
        if (Build.VERSION.SDK_INT >= 33) writeCccdNew(radio, descriptor) else writeCccdOld(radio, descriptor)

    @RequiresApi(33)
    private fun writeCccdNew(radio: BluetoothGatt, descriptor: BluetoothGattDescriptor): Boolean =
        guarded("cccd") {
            val code = radio.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            if (code != BluetoothStatusCodes.SUCCESS) error("cccd $code")
        }

    @Suppress("DEPRECATION")
    private fun writeCccdOld(radio: BluetoothGatt, descriptor: BluetoothGattDescriptor): Boolean =
        guarded("cccd") {
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            if (!radio.writeDescriptor(descriptor)) error("cccd rejected")
        }

    private inline fun guarded(what: String, block: () -> Unit): Boolean =
        try {
            block()
            true
        } catch (e: SecurityException) {
            Log.w(TAG, "$what: no permission", e)
            false
        } catch (e: IllegalStateException) {
            Log.w(TAG, "$what: adapter off", e)
            false
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, what, e)
            false
        }

    private inline fun <T> guardedOrNull(what: String, block: () -> T): T? =
        try {
            block()
        } catch (e: SecurityException) {
            Log.w(TAG, "$what: no permission", e)
            null
        } catch (e: IllegalStateException) {
            Log.w(TAG, "$what: adapter off", e)
            null
        }

    private companion object {
        const val TAG = "PhoneRadio"
        const val PENDING_MAX = 16
        const val RESCAN_MS = 800L
        /** The companion drops a phone it does not know and refuses it for a minute. */
        const val STRANGER_RESCAN_MS = 10_000L
        val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}
