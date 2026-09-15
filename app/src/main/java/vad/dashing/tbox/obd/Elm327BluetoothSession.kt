package vad.dashing.tbox.obd

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Classic Bluetooth RFCOMM session to an ELM327 adapter.
 */
class Elm327BluetoothSession(
    private val deviceAddress: String,
) {
    companion object {
        private const val TAG = "Elm327BtSession"
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private const val DEFAULT_TIMEOUT_MS = 4_000L
        private const val INIT_TIMEOUT_MS = 8_000L
    }

    private var socket: BluetoothSocket? = null
    private var reader: BufferedReader? = null
    private var writer: OutputStreamWriter? = null
    private val closed = AtomicBoolean(true)

    val isOpen: Boolean
        get() = !closed.get() && socket?.isConnected == true

    @SuppressLint("MissingPermission")
    fun open() {
        closeQuietly()
        closed.set(false)
        val adapter = BluetoothAdapter.getDefaultAdapter()
            ?: error("Bluetooth adapter unavailable")
        if (!adapter.isEnabled) error("Bluetooth disabled")
        val device: BluetoothDevice = try {
            adapter.getRemoteDevice(deviceAddress)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid MAC: $deviceAddress", e)
        }
        val sock = device.createInsecureRfcommSocketToServiceRecord(SPP_UUID)
        runCatching { adapter.cancelDiscovery() }
        sock.connect()
        socket = sock
        reader = BufferedReader(InputStreamReader(sock.inputStream, Charsets.US_ASCII))
        writer = OutputStreamWriter(sock.outputStream, Charsets.US_ASCII)
        Log.i(TAG, "connected to $deviceAddress")
    }

    fun close() {
        closeQuietly()
    }

    private fun closeQuietly() {
        closed.set(true)
        runCatching { writer?.close() }
        runCatching { reader?.close() }
        runCatching { socket?.close() }
        writer = null
        reader = null
        socket = null
    }

    /** Send [command] (without CR) and read until `>` prompt or timeout. */
    @Synchronized
    fun transact(command: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS): String {
        check(isOpen) { "session closed" }
        val w = writer ?: error("no writer")
        val r = reader ?: error("no reader")
        while (r.ready()) {
            r.read()
        }
        w.write(command.trim())
        w.write("\r")
        w.flush()
        val deadline = System.currentTimeMillis() + timeoutMs
        val sb = StringBuilder()
        while (System.currentTimeMillis() < deadline) {
            if (r.ready()) {
                val ch = r.read()
                if (ch < 0) break
                val c = ch.toChar()
                if (c == '>') break
                sb.append(c)
            } else {
                Thread.sleep(15)
            }
        }
        return sb.toString()
    }

    fun runInit(): String {
        val parts = mutableListOf<String>()
        for (cmd in Elm327Protocol.INIT_COMMANDS) {
            val timeout = if (cmd == "ATZ") INIT_TIMEOUT_MS else DEFAULT_TIMEOUT_MS
            parts += transact(cmd, timeoutMs = timeout)
            if (cmd == "ATZ") {
                Thread.sleep(800)
            }
        }
        return parts.joinToString(" | ")
    }
}
