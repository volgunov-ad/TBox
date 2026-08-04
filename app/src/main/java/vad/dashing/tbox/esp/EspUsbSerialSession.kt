package vad.dashing.tbox.esp

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import android.util.Log
import vad.dashing.tbox.TboxRepository
import vad.dashing.tbox.location.LocationIncomingBitRate
import java.io.Closeable
import java.nio.charset.Charset
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Minimal USB CDC (ACM) host session for ESP32-S3 TinyUSB CDC.
 * Opens only Espressif VID (0x303A) — never falls back to other CDC devices
 * (e.g. TBox RNDIS) to avoid wedging the shared USB host.
 *
 * All bulk OUT / control / close run on a single [usbIo] thread to avoid
 * concurrent [UsbDeviceConnection] use (HU host wedges otherwise).
 */
class EspUsbSerialSession(
    private val context: Context,
    private val onLine: (String) -> Unit,
    private val onConnectionChanged: (Boolean) -> Unit,
    private val onError: (String) -> Unit,
) : Closeable {
    companion object {
        private const val TAG = "EspUsbSerial"
        const val ACTION_USB_PERMISSION = "vad.dashing.tbox.USB_PERMISSION"
        const val ESPRESSIF_VID = 0x303A
        private const val READ_TIMEOUT_MS = 200
        private const val WRITE_TIMEOUT_MS = 500
        private const val WRITE_OTA_TIMEOUT_MS = 3_000
        private const val PERMISSION_RETRY_MIN_MS = 45_000L
        private const val MAX_LINE_BUFFER = 32 * 1024
    }

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private val running = AtomicBoolean(false)
    private val loggedFirstRx = AtomicBoolean(false)
    /** >0 while UM980/OTA critical transfer — skip soft reconnect/close. */
    private val criticalIoDepth = AtomicInteger(0)
    @Volatile private var lastNoDeviceLogMs = 0L
    @Volatile private var lastPermissionRequestMs = 0L
    private val ioLock = Any()
    private val usbIo = Executors.newSingleThreadExecutor { r ->
        Thread(r, "esp-usb-io").apply { isDaemon = true }
    }
    private var connection: UsbDeviceConnection? = null
    private var usbInterface: UsbInterface? = null
    private var inEndpoint: UsbEndpoint? = null
    private var outEndpoint: UsbEndpoint? = null
    private var openDeviceId: Int = -1
    private var readThread: Thread? = null
    private val lineBuffer = StringBuilder()

    private val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    val device = extraUsbDevice(intent) ?: return
                    if (device.vendorId == ESPRESSIF_VID) {
                        tryConnect(force = false)
                    }
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    val device = extraUsbDevice(intent)
                    // Only tear down if our Espressif companion left — never close on
                    // unrelated USB detach (can wedge HU host / TBox RNDIS).
                    if (device != null && isOpenCompanionDevice(device)) {
                        runCatching {
                            runOnUsbIo(timeoutMs = 3_000L) {
                                closeConnectionOnly(force = false, allowDuringCritical = true)
                            }
                        }.onFailure {
                            closeConnectionOnly(force = false, allowDuringCritical = true)
                        }
                    }
                }
                ACTION_USB_PERMISSION -> {
                    val device = extraUsbDevice(intent)
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    if (granted && device != null && device.vendorId == ESPRESSIF_VID) {
                        openDevice(device, force = false)
                    } else if (!granted) {
                        onError("USB permission denied")
                    }
                }
            }
        }
    }

    fun beginCriticalIo() {
        criticalIoDepth.incrementAndGet()
    }

    fun endCriticalIo() {
        criticalIoDepth.updateAndGet { d -> (d - 1).coerceAtLeast(0) }
    }

    fun isCriticalIo(): Boolean = criticalIoDepth.get() > 0

    fun start() {
        if (!running.compareAndSet(false, true)) return
        val filter = IntentFilter(ACTION_USB_PERMISSION).apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(permissionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(permissionReceiver, filter)
        }
        tryConnect(force = false)
    }

    fun tryConnect(force: Boolean = false) {
        if (isCriticalIo() && !force) {
            Log.w(TAG, "skip tryConnect during critical USB IO")
            return
        }
        // force=true still skips when critical — only service stop uses close(force=true).
        if (force && isCriticalIo()) {
            Log.w(TAG, "skip forced tryConnect during critical USB IO")
            return
        }
        val device = findCompanionDevice()
        if (device == null) {
            val now = System.currentTimeMillis()
            if (now - lastNoDeviceLogMs >= 15_000L) {
                lastNoDeviceLogMs = now
                Log.d(TAG, "tryConnect: no companion USB device")
            }
            return
        }
        if (usbManager.hasPermission(device)) {
            openDevice(device, force = force)
        } else {
            val now = System.currentTimeMillis()
            if (now - lastPermissionRequestMs < PERMISSION_RETRY_MIN_MS) {
                Log.d(TAG, "skip permission request (throttled)")
                return
            }
            lastPermissionRequestMs = now
            Log.i(TAG, "requesting USB permission for ${device.deviceName}")
            TboxRepository.addLog("INFO", "Companion", "requesting USB permission")
            val piFlags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val pi = PendingIntent.getBroadcast(
                context,
                0,
                Intent(ACTION_USB_PERMISSION),
                piFlags,
            )
            usbManager.requestPermission(device, pi)
        }
    }

    fun writeLine(payload: String) {
        val bytes = if (payload.endsWith("\n")) {
            payload.toByteArray(Charsets.UTF_8)
        } else {
            (payload + "\n").toByteArray(Charsets.UTF_8)
        }
        writeBytes(bytes, ota = false)
    }

    /** Raw CDC bulk write. Set [ota] for longer per-chunk timeout during firmware transfer. */
    fun writeBytes(bytes: ByteArray, ota: Boolean = true): Boolean {
        if (bytes.isEmpty()) return true
        val timeout = if (ota) WRITE_OTA_TIMEOUT_MS else WRITE_TIMEOUT_MS
        return try {
            runOnUsbIo(timeoutMs = timeout.toLong() + 1_000L) {
                writeBytesLocked(bytes, timeout)
            }
        } catch (e: Exception) {
            onError("USB write failed: ${e.message}")
            false
        }
    }

    private fun writeBytesLocked(bytes: ByteArray, timeout: Int): Boolean {
        synchronized(ioLock) {
            val conn = connection
            val out = outEndpoint
            if (conn == null || out == null) {
                onError("ESP USB not connected")
                return false
            }
            return try {
                var offset = 0
                while (offset < bytes.size) {
                    val n = minOf(bytes.size - offset, out.maxPacketSize.coerceAtLeast(64))
                    val chunk = if (offset == 0 && n == bytes.size) {
                        bytes
                    } else {
                        bytes.copyOfRange(offset, offset + n)
                    }
                    val written = conn.bulkTransfer(out, chunk, chunk.size, timeout)
                    if (written < 0) {
                        onError("USB write failed rc=$written")
                        return false
                    }
                    offset += written
                }
                true
            } catch (e: Exception) {
                onError("USB write failed: ${e.message}")
                false
            }
        }
    }

    private fun <T> runOnUsbIo(timeoutMs: Long, block: () -> T): T {
        if (Thread.currentThread().name == "esp-usb-io") {
            return block()
        }
        val future = usbIo.submit(block)
        return try {
            future.get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: Exception) {
            future.cancel(true)
            throw e
        }
    }

    private fun extraUsbDevice(intent: Intent): UsbDevice? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
        }
    }

    private fun isOpenCompanionDevice(device: UsbDevice): Boolean {
        if (device.vendorId != ESPRESSIF_VID) return false
        synchronized(ioLock) {
            return connection != null && openDeviceId == device.deviceId
        }
    }

    /**
     * Tear down the current Espressif CDC handle (if any) and open again.
     * Used after heartbeat loss so reconnect is not blocked by "already open".
     * Does not touch non-Espressif devices. Runs on [usbIo].
     */
    fun forceReopen() {
        if (isCriticalIo()) {
            Log.w(TAG, "skip forceReopen during critical USB IO")
            return
        }
        runCatching {
            runOnUsbIo(timeoutMs = 5_000L) {
                closeConnectionOnly(force = false, allowDuringCritical = false)
                tryConnectOnIoThread(force = true)
            }
        }.onFailure { e ->
            Log.w(TAG, "forceReopen failed: ${e.message}")
        }
    }

    private fun findCompanionDevice(): UsbDevice? {
        // Espressif only — CDC-class fallback claimed TBox RNDIS and wedged the HU USB host.
        return usbManager.deviceList.values.firstOrNull { it.vendorId == ESPRESSIF_VID }
    }

    private fun tryConnectOnIoThread(force: Boolean) {
        // Already on usbIo — openDevice still uses runOnUsbIo which short-circuits.
        tryConnect(force = force)
    }

    private fun openDevice(device: UsbDevice, force: Boolean) {
        runOnUsbIo(timeoutMs = 5_000L) {
            openDeviceOnIoThread(device, force)
        }
    }

    private fun openDeviceOnIoThread(device: UsbDevice, force: Boolean) {
        synchronized(ioLock) {
            if (connection != null && openDeviceId == device.deviceId && !force) {
                Log.d(TAG, "already open deviceId=${device.deviceId}")
                return
            }
        }
        if (isCriticalIo()) {
            Log.w(TAG, "skip openDevice during critical USB IO")
            return
        }
        closeConnectionOnly(force = false, allowDuringCritical = false)
        val dataIntf = findDataInterface(device) ?: run {
            onError("No CDC data interface on ${device.deviceName}")
            return
        }
        val commIntf = findCommInterface(device)
        val conn = usbManager.openDevice(device) ?: run {
            onError("openDevice failed")
            return
        }
        if (commIntf != null) {
            try {
                conn.claimInterface(commIntf, true)
            } catch (_: Exception) {
            }
            assertCdcControlLineState(conn, commIntf.id, dtr = true, rts = true)
        }
        if (!conn.claimInterface(dataIntf, true)) {
            conn.close()
            onError("claimInterface failed")
            return
        }
        var epIn: UsbEndpoint? = null
        var epOut: UsbEndpoint? = null
        for (e in 0 until dataIntf.endpointCount) {
            val ep = dataIntf.getEndpoint(e)
            if (ep.type != UsbConstants.USB_ENDPOINT_XFER_BULK) continue
            if (ep.direction == UsbConstants.USB_DIR_IN) epIn = ep
            if (ep.direction == UsbConstants.USB_DIR_OUT) epOut = ep
        }
        if (epIn == null || epOut == null) {
            conn.releaseInterface(dataIntf)
            conn.close()
            onError("Missing bulk endpoints")
            return
        }
        synchronized(ioLock) {
            connection = conn
            usbInterface = dataIntf
            inEndpoint = epIn
            outEndpoint = epOut
            openDeviceId = device.deviceId
        }
        onConnectionChanged(true)
        startReadLoop()
        if (commIntf != null) {
            assertCdcControlLineState(conn, commIntf.id, dtr = true, rts = true)
        }
        writeBytesLocked(
            EspCompanionProtocol.encodeHello().toByteArray(Charsets.UTF_8),
            WRITE_TIMEOUT_MS,
        )
        usbIo.execute {
            try {
                Thread.sleep(300)
                writeBytesLocked(
                    EspCompanionProtocol.encodeHello().toByteArray(Charsets.UTF_8),
                    WRITE_TIMEOUT_MS,
                )
            } catch (_: Exception) {
            }
        }
        Log.i(TAG, "ESP companion connected: ${device.deviceName}")
    }

    private fun assertCdcControlLineState(
        conn: UsbDeviceConnection,
        interfaceId: Int,
        dtr: Boolean,
        rts: Boolean,
    ) {
        val value = (if (dtr) 0x01 else 0) or (if (rts) 0x02 else 0)
        val rc = conn.controlTransfer(
            /* requestType */ 0x21,
            /* request */ 0x22,
            /* value */ value,
            /* index */ interfaceId,
            /* buffer */ null,
            /* length */ 0,
            /* timeout */ WRITE_TIMEOUT_MS,
        )
        if (rc < 0) {
            Log.w(TAG, "SET_CONTROL_LINE_STATE failed rc=$rc if=$interfaceId")
        } else {
            Log.d(TAG, "SET_CONTROL_LINE_STATE ok value=$value if=$interfaceId")
        }
    }

    private fun findCommInterface(device: UsbDevice): UsbInterface? {
        for (i in 0 until device.interfaceCount) {
            val intf = device.getInterface(i)
            if (intf.interfaceClass == UsbConstants.USB_CLASS_COMM) {
                return intf
            }
        }
        return null
    }

    private fun findDataInterface(device: UsbDevice): UsbInterface? {
        var cdcData: UsbInterface? = null
        for (i in 0 until device.interfaceCount) {
            val intf = device.getInterface(i)
            if (intf.interfaceClass == UsbConstants.USB_CLASS_CDC_DATA) {
                cdcData = intf
                break
            }
        }
        if (cdcData != null) return cdcData
        for (i in 0 until device.interfaceCount) {
            val intf = device.getInterface(i)
            var hasIn = false
            var hasOut = false
            for (e in 0 until intf.endpointCount) {
                val ep = intf.getEndpoint(e)
                if (ep.type != UsbConstants.USB_ENDPOINT_XFER_BULK) continue
                if (ep.direction == UsbConstants.USB_DIR_IN) hasIn = true
                if (ep.direction == UsbConstants.USB_DIR_OUT) hasOut = true
            }
            if (hasIn && hasOut) return intf
        }
        return null
    }

    private fun startReadLoop() {
        readThread?.interrupt()
        readThread = Thread({
            val buf = ByteArray(256)
            val charset: Charset = Charsets.UTF_8
            while (running.get() && !Thread.currentThread().isInterrupted) {
                val conn: UsbDeviceConnection?
                val ep: UsbEndpoint?
                synchronized(ioLock) {
                    conn = connection
                    ep = inEndpoint
                }
                if (conn == null || ep == null) break
                val n = try {
                    conn.bulkTransfer(ep, buf, buf.size, READ_TIMEOUT_MS)
                } catch (_: Exception) {
                    -1
                }
                if (n <= 0) continue
                if (loggedFirstRx.compareAndSet(false, true)) {
                    Log.i(TAG, "First RX $n bytes from companion")
                }
                LocationIncomingBitRate.noteBytes(LocationSource.ESP32, n)
                val chunk = String(buf, 0, n, charset)
                synchronized(lineBuffer) {
                    lineBuffer.append(chunk)
                    if (lineBuffer.length > MAX_LINE_BUFFER) {
                        lineBuffer.delete(0, lineBuffer.length - MAX_LINE_BUFFER)
                    }
                    while (true) {
                        val idx = lineBuffer.indexOf("\n")
                        if (idx < 0) break
                        val line = lineBuffer.substring(0, idx).trimEnd('\r')
                        lineBuffer.delete(0, idx + 1)
                        if (line.isNotEmpty()) {
                            try {
                                onLine(line)
                            } catch (e: Exception) {
                                Log.w(TAG, "onLine error", e)
                            }
                        }
                    }
                }
            }
        }, "esp-usb-read").apply {
            isDaemon = true
            start()
        }
    }

    private fun closeConnectionOnly(force: Boolean, allowDuringCritical: Boolean = force) {
        if (!allowDuringCritical && isCriticalIo()) {
            Log.w(TAG, "skip closeConnection during critical USB IO")
            return
        }
        val conn: UsbDeviceConnection?
        val intf: UsbInterface?
        synchronized(ioLock) {
            readThread?.interrupt()
            readThread = null
            conn = connection
            intf = usbInterface
            connection = null
            usbInterface = null
            inEndpoint = null
            outEndpoint = null
            openDeviceId = -1
            synchronized(lineBuffer) { lineBuffer.setLength(0) }
            loggedFirstRx.set(false)
        }
        try {
            intf?.let { conn?.releaseInterface(it) }
        } catch (_: Exception) {
        }
        try {
            conn?.close()
        } catch (_: Exception) {
        }
        onConnectionChanged(false)
    }

    override fun close() {
        close(force = true)
    }

    fun close(force: Boolean) {
        if (!force && isCriticalIo()) {
            Log.w(TAG, "defer session close during critical USB IO")
            return
        }
        runCatching {
            runOnUsbIo(timeoutMs = 3_000L) {
                if (!running.compareAndSet(true, false)) {
                    closeConnectionOnly(force = true, allowDuringCritical = true)
                    return@runOnUsbIo
                }
                try {
                    context.unregisterReceiver(permissionReceiver)
                } catch (_: Exception) {
                }
                closeConnectionOnly(force = true, allowDuringCritical = true)
            }
        }.onFailure {
            // Fallback if executor already shut down.
            if (running.compareAndSet(true, false)) {
                try {
                    context.unregisterReceiver(permissionReceiver)
                } catch (_: Exception) {
                }
            }
            closeConnectionOnly(force = true, allowDuringCritical = true)
        }
        usbIo.shutdown()
    }
}
