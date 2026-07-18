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
import java.io.Closeable
import java.nio.charset.Charset
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Minimal USB CDC (ACM) host session for ESP32-S3 TinyUSB CDC.
 * Prefers Espressif VID; also accepts CDC class interfaces.
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
        private const val WRITE_TIMEOUT_MS = 1000
    }

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private val running = AtomicBoolean(false)
    private var connection: UsbDeviceConnection? = null
    private var usbInterface: UsbInterface? = null
    private var inEndpoint: UsbEndpoint? = null
    private var outEndpoint: UsbEndpoint? = null
    private var readThread: Thread? = null
    private val lineBuffer = StringBuilder()

    private val permissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> tryConnect()
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    closeConnectionOnly()
                }
                ACTION_USB_PERMISSION -> {
                    val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    if (granted && device != null) {
                        openDevice(device)
                    } else {
                        onError("USB permission denied")
                    }
                }
            }
        }
    }

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
        tryConnect()
    }

    fun tryConnect() {
        val device = findCompanionDevice() ?: return
        if (usbManager.hasPermission(device)) {
            openDevice(device)
        } else {
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
        val conn = connection
        val out = outEndpoint
        if (conn == null || out == null) {
            onError("ESP USB not connected")
            return
        }
        try {
            conn.bulkTransfer(out, bytes, bytes.size, WRITE_TIMEOUT_MS)
        } catch (e: Exception) {
            onError("USB write failed: ${e.message}")
        }
    }

    private fun findCompanionDevice(): UsbDevice? {
        return usbManager.deviceList.values.firstOrNull { device ->
            device.vendorId == ESPRESSIF_VID || hasCdcInterface(device)
        }
    }

    private fun hasCdcInterface(device: UsbDevice): Boolean {
        for (i in 0 until device.interfaceCount) {
            val intf = device.getInterface(i)
            if (intf.interfaceClass == UsbConstants.USB_CLASS_CDC_DATA ||
                intf.interfaceClass == UsbConstants.USB_CLASS_COMM
            ) {
                return true
            }
        }
        return false
    }

    private fun openDevice(device: UsbDevice) {
        closeConnectionOnly()
        val dataIntf = findDataInterface(device) ?: run {
            onError("No CDC data interface on ${device.deviceName}")
            return
        }
        val conn = usbManager.openDevice(device) ?: run {
            onError("openDevice failed")
            return
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
        connection = conn
        usbInterface = dataIntf
        inEndpoint = epIn
        outEndpoint = epOut
        onConnectionChanged(true)
        startReadLoop()
        writeLine(EspCompanionProtocol.encodeHello().trimEnd())
        Log.i(TAG, "ESP companion connected: ${device.deviceName}")
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
                val conn = connection
                val ep = inEndpoint
                if (conn == null || ep == null) break
                val n = try {
                    conn.bulkTransfer(ep, buf, buf.size, READ_TIMEOUT_MS)
                } catch (_: Exception) {
                    -1
                }
                if (n <= 0) continue
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
        }, "esp-usb-read").apply {
            isDaemon = true
            start()
        }
    }

    private fun closeConnectionOnly() {
        readThread?.interrupt()
        readThread = null
        try {
            usbInterface?.let { connection?.releaseInterface(it) }
        } catch (_: Exception) {
        }
        try {
            connection?.close()
        } catch (_: Exception) {
        }
        connection = null
        usbInterface = null
        inEndpoint = null
        outEndpoint = null
        synchronized(lineBuffer) { lineBuffer.setLength(0) }
        onConnectionChanged(false)
    }

    override fun close() {
        if (!running.compareAndSet(true, false)) {
            closeConnectionOnly()
            return
        }
        try {
            context.unregisterReceiver(permissionReceiver)
        } catch (_: Exception) {
        }
        closeConnectionOnly()
    }
}
