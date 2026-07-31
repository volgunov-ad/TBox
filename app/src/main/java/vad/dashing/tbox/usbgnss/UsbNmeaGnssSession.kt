package vad.dashing.tbox.usbgnss

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
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * USB CDC / bulk-serial session for a **user-selected** GNSS device.
 *
 * Does not auto-pick the first CDC device (that wedged TBox RNDIS on this HU).
 * DETACH only closes when the open device leaves.
 */
class UsbNmeaGnssSession(
    private val context: Context,
    private val onLine: (String) -> Unit,
    private val onConnectionChanged: (Boolean) -> Unit,
    private val onError: (String) -> Unit,
    private val onStableIdResolved: (String) -> Unit = {},
) : Closeable {
    companion object {
        private const val TAG = "UsbNmeaGnss"
        const val ACTION_USB_PERMISSION = "vad.dashing.tbox.USB_GNSS_PERMISSION"
        private const val READ_TIMEOUT_MS = 200
        private const val WRITE_TIMEOUT_MS = 500
        private const val PERMISSION_RETRY_MIN_MS = 45_000L
    }

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private val running = AtomicBoolean(false)
    private val loggedFirstRx = AtomicBoolean(false)
    private val ioLock = Any()
    private val usbIo = Executors.newSingleThreadExecutor { r ->
        Thread(r, "usb-gnss-io").apply { isDaemon = true }
    }

    @Volatile private var targetStableId: String = ""
    @Volatile private var targetBaud: Int = UsbGnssDeviceIds.DEFAULT_BAUD
    @Volatile private var requestVtg: Boolean = false
    @Volatile private var requestZda: Boolean = false
    @Volatile private var lastPermissionRequestMs: Long = 0L

    private var connection: UsbDeviceConnection? = null
    private var usbInterface: UsbInterface? = null
    private var commInterface: UsbInterface? = null
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
                    if (targetStableId.isNotBlank() &&
                        UsbGnssDeviceIds.matchesStableId(device, targetStableId) &&
                        UsbGnssDeviceScanner.isEligible(device)
                    ) {
                        tryConnect()
                    }
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    val device = extraUsbDevice(intent) ?: return
                    if (isOpenDevice(device)) {
                        closeConnectionOnly()
                    }
                }
                ACTION_USB_PERMISSION -> {
                    val device = extraUsbDevice(intent)
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    if (granted && device != null &&
                        targetStableId.isNotBlank() &&
                        UsbGnssDeviceIds.matchesStableId(device, targetStableId)
                    ) {
                        openDevice(device)
                    } else if (!granted) {
                        onError("USB permission denied")
                    }
                }
            }
        }
    }

    fun start(
        stableId: String,
        baud: Int,
        requestVtg: Boolean = false,
        requestZda: Boolean = false,
    ) {
        targetStableId = stableId.trim()
        targetBaud = baud.coerceIn(1_200, 2_000_000)
        this.requestVtg = requestVtg
        this.requestZda = requestZda
        if (!running.compareAndSet(false, true)) {
            // Already running ù update target and reconnect.
            closeConnectionOnly()
            tryConnect()
            return
        }
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
        tryConnect()
    }

    fun updateTarget(
        stableId: String,
        baud: Int,
        requestVtg: Boolean = this.requestVtg,
        requestZda: Boolean = this.requestZda,
    ) {
        val id = stableId.trim()
        val b = baud.coerceIn(1_200, 2_000_000)
        val changed =
            id != targetStableId ||
                b != targetBaud ||
                requestVtg != this.requestVtg ||
                requestZda != this.requestZda
        targetStableId = id
        targetBaud = b
        this.requestVtg = requestVtg
        this.requestZda = requestZda
        if (!running.get()) return
        if (changed) {
            closeConnectionOnly()
            tryConnect()
        } else if (connection == null) {
            tryConnect()
        }
    }

    /** Close link and attempt open again (e.g. NMEA silence after CP210x open). */
    fun forceReopen() {
        if (!running.get()) return
        Log.i(TAG, "forceReopen id=$targetStableId")
        runOnUsbIo(timeoutMs = 5_000L) {
            closeConnectionOnly()
            tryConnect()
        }
    }

    fun writeAsciiLine(line: String): Boolean {
        val payload = (line.trimEnd('\r', '\n') + "\r\n").toByteArray(Charsets.US_ASCII)
        return synchronized(ioLock) {
            val conn = connection ?: return false
            val ep = outEndpoint ?: return false
            val n = try {
                conn.bulkTransfer(ep, payload, payload.size, WRITE_TIMEOUT_MS)
            } catch (_: Exception) {
                -1
            }
            n == payload.size
        }
    }

    fun tryConnect() {
        if (!running.get()) return
        if (targetStableId.isBlank()) {
            Log.d(TAG, "tryConnect: no device selected")
            return
        }
        when (val found = UsbGnssDeviceScanner.findByStableIdResult(usbManager, targetStableId)) {
            is UsbGnssDeviceScanner.FindResult.NotFound -> {
                Log.d(TAG, "tryConnect: device not present id=$targetStableId")
                return
            }
            is UsbGnssDeviceScanner.FindResult.Ambiguous -> {
                onError("Multiple USB devices match $targetStableId (${found.count})")
                return
            }
            is UsbGnssDeviceScanner.FindResult.Unique -> {
                val device = found.device
                if (usbManager.hasPermission(device)) {
                    openDevice(device)
                } else {
                    val now = System.currentTimeMillis()
                    if (now - lastPermissionRequestMs < PERMISSION_RETRY_MIN_MS) {
                        Log.d(TAG, "skip permission request (throttled)")
                        return
                    }
                    lastPermissionRequestMs = now
                    Log.i(TAG, "requesting USB permission for ${device.deviceName}")
                    TboxRepository.addLog("INFO", "USB GNSS", "requesting USB permission")
                    val piFlags = PendingIntent.FLAG_UPDATE_CURRENT or
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
                    val pi = PendingIntent.getBroadcast(
                        context,
                        1,
                        Intent(ACTION_USB_PERMISSION),
                        piFlags,
                    )
                    usbManager.requestPermission(device, pi)
                }
            }
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

    private fun isOpenDevice(device: UsbDevice): Boolean {
        synchronized(ioLock) {
            return connection != null && openDeviceId == device.deviceId
        }
    }

    private fun openDevice(device: UsbDevice) {
        runOnUsbIo(timeoutMs = 5_000L) {
            openDeviceOnIoThread(device)
        }
    }

    private fun openDeviceOnIoThread(device: UsbDevice) {
        val actualSerial = runCatching { device.serialNumber }.getOrNull()?.trim().orEmpty()
        val parsed = UsbGnssDeviceIds.parseStableId(targetStableId)
        val wantSerial = parsed?.serial?.trim().orEmpty()
        if (wantSerial.isNotEmpty() && actualSerial.isNotEmpty() && actualSerial != wantSerial) {
            onError("USB device serial mismatch (want=$wantSerial got=$actualSerial)")
            return
        }
        synchronized(ioLock) {
            if (connection != null && openDeviceId == device.deviceId) {
                Log.d(TAG, "already open deviceId=${device.deviceId}")
                return
            }
        }
        closeConnectionOnly()
        val dataIntf = findDataInterface(device) ?: run {
            onError("No serial data interface on ${device.deviceName}")
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
            setLineCoding(conn, commIntf.id, targetBaud)
            assertCdcControlLineState(conn, commIntf.id, dtr = true, rts = true)
        }
        if (!conn.claimInterface(dataIntf, true)) {
            conn.close()
            onError("claimInterface failed")
            return
        }
        // CP210x / CH340 need vendor baud+DTR; CDC alone is not enough.
        val vendorInit = UsbUartBridgeInit.applyIfNeeded(
            device = device,
            connection = conn,
            interfaceId = dataIntf.id,
            baud = targetBaud,
        )
        if (!vendorInit && commIntf == null) {
            Log.w(
                TAG,
                "No CDC COMM and no known UART vendor init " +
                    "vid=${"%04x".format(device.vendorId)} ù RX may stay empty",
            )
        }
        var epIn: UsbEndpoint? = null
        var epOut: UsbEndpoint? = null
        for (e in 0 until dataIntf.endpointCount) {
            val ep = dataIntf.getEndpoint(e)
            if (ep.type != UsbConstants.USB_ENDPOINT_XFER_BULK) continue
            if (ep.direction == UsbConstants.USB_DIR_IN) epIn = ep
            if (ep.direction == UsbConstants.USB_DIR_OUT) epOut = ep
        }
        if (epIn == null) {
            conn.releaseInterface(dataIntf)
            conn.close()
            onError("Missing bulk IN endpoint")
            return
        }
        synchronized(ioLock) {
            connection = conn
            usbInterface = dataIntf
            commInterface = commIntf
            inEndpoint = epIn
            outEndpoint = epOut
            openDeviceId = device.deviceId
        }
        onConnectionChanged(true)
        startReadLoop()
        if (commIntf != null) {
            assertCdcControlLineState(conn, commIntf.id, dtr = true, rts = true)
        }
        sendOptionalNmeaEnableCommands()
        // Persist serial once readable after permission / open.
        val resolvedSerial = actualSerial.ifEmpty {
            runCatching { device.serialNumber }.getOrNull()?.trim().orEmpty()
        }
        if (resolvedSerial.isNotEmpty() && parsed != null) {
            val resolvedId = UsbGnssDeviceIds.formatStableId(
                parsed.vendorId,
                parsed.productId,
                resolvedSerial,
            )
            if (resolvedId != targetStableId) {
                targetStableId = resolvedId
            }
            onStableIdResolved(resolvedId)
        }
        Log.i(
            TAG,
            "USB GNSS connected: ${device.deviceName} baud=$targetBaud id=$targetStableId " +
                "vendorInit=$vendorInit",
        )
    }

    private fun sendOptionalNmeaEnableCommands() {
        val lines = UsbGnssNmeaEnableCommands.buildUnicoreLines(requestVtg, requestZda)
        if (lines.isEmpty()) return
        for (line in lines) {
            val ok = writeAsciiLine(line)
            Log.i(TAG, "NMEA enable '$line' ok=$ok")
            if (ok) {
                TboxRepository.addLog("INFO", "USB GNSS", "sent $line")
            }
        }
    }

    private fun setLineCoding(conn: UsbDeviceConnection, interfaceId: Int, baud: Int) {
        val buf = ByteBuffer.allocate(7).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(baud)
        buf.put(0) // 1 stop bit
        buf.put(0) // no parity
        buf.put(8) // 8 data bits
        val bytes = buf.array()
        val rc = conn.controlTransfer(
            /* requestType */ 0x21,
            /* request */ 0x20, // SET_LINE_CODING
            /* value */ 0,
            /* index */ interfaceId,
            /* buffer */ bytes,
            /* length */ bytes.size,
            /* timeout */ WRITE_TIMEOUT_MS,
        )
        if (rc < 0) {
            Log.w(TAG, "SET_LINE_CODING failed rc=$rc baud=$baud if=$interfaceId")
        } else {
            Log.d(TAG, "SET_LINE_CODING ok baud=$baud if=$interfaceId")
        }
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
            // Prefer IN+OUT; allow IN-only if that is all we have.
            if (hasIn && hasOut) return intf
            if (hasIn && cdcData == null) cdcData = intf
        }
        return cdcData
    }

    private fun startReadLoop() {
        readThread?.interrupt()
        readThread = Thread({
            val buf = ByteArray(256)
            val charset: Charset = Charsets.US_ASCII
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
                    Log.i(TAG, "First RX $n bytes from USB GNSS")
                }
                val chunk = String(buf, 0, n, charset)
                synchronized(lineBuffer) {
                    lineBuffer.append(chunk)
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
        }, "usb-gnss-read").apply {
            isDaemon = true
            start()
        }
    }

    private fun closeConnectionOnly() {
        val conn: UsbDeviceConnection?
        val intf: UsbInterface?
        val comm: UsbInterface?
        synchronized(ioLock) {
            readThread?.interrupt()
            readThread = null
            conn = connection
            intf = usbInterface
            comm = commInterface
            connection = null
            usbInterface = null
            commInterface = null
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
            comm?.let { conn?.releaseInterface(it) }
        } catch (_: Exception) {
        }
        try {
            conn?.close()
        } catch (_: Exception) {
        }
        onConnectionChanged(false)
    }

    private fun <T> runOnUsbIo(timeoutMs: Long, block: () -> T): T {
        if (Thread.currentThread().name == "usb-gnss-io") {
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

    override fun close() {
        runCatching {
            runOnUsbIo(timeoutMs = 3_000L) {
                if (running.compareAndSet(true, false)) {
                    try {
                        context.unregisterReceiver(permissionReceiver)
                    } catch (_: Exception) {
                    }
                }
                closeConnectionOnly()
            }
        }.onFailure {
            if (running.compareAndSet(true, false)) {
                try {
                    context.unregisterReceiver(permissionReceiver)
                } catch (_: Exception) {
                }
            }
            closeConnectionOnly()
        }
        usbIo.shutdown()
    }
}
