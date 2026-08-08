package vad.dashing.tbox.um980fw

import android.util.Log
import vad.dashing.tbox.esp.EspCompanionManager
import vad.dashing.tbox.esp.EspCompanionProtocol
import vad.dashing.tbox.esp.EspCompanionRepository
import java.io.ByteArrayOutputStream

/**
 * UM980 binary pipe via ESP companion [um980Bridge] framed tunnel.
 */
class EspUm980BinaryTransport(
    private val manager: EspCompanionManager,
) : Um980BinaryTransport {
    private val rxLock = Any()
    private val rx = ByteArrayOutputStream(64 * 1024)
    private val frameBuf = ByteArrayOutputStream()
    private var workingBaud: Int = EspCompanionRepository.deviceInfo.value.um980Baud.takeIf { it > 0 } ?: 115_200

    override fun currentBaud(): Int = workingBaud

    override fun setBaud(baud: Int): Boolean {
        workingBaud = baud
        return manager.setUm980BaudBlocking(baud)
    }

    override fun write(bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return true
        var off = 0
        while (off < bytes.size) {
            val end = minOf(off + EspCompanionProtocol.OTA_CHUNK_MAX, bytes.size)
            val chunk = bytes.copyOfRange(off, end)
            val frame = EspCompanionProtocol.encodeBridgeChunkFrame(chunk)
            if (!manager.writeBridgeFrame(frame)) return false
            off = end
        }
        return true
    }

    override fun read(maxBytes: Int, timeoutMs: Long): ByteArray {
        val deadline = System.currentTimeMillis() + timeoutMs.coerceAtLeast(0L)
        while (true) {
            synchronized(rxLock) {
                if (rx.size() > 0) {
                    val all = rx.toByteArray()
                    val n = minOf(maxBytes, all.size)
                    val out = all.copyOf(n)
                    rx.reset()
                    if (n < all.size) rx.write(all, n, all.size - n)
                    return out
                }
            }
            if (System.currentTimeMillis() >= deadline) return ByteArray(0)
            try {
                Thread.sleep(15)
            } catch (_: InterruptedException) {
                return ByteArray(0)
            }
        }
    }

    override fun beginExclusive() {
        manager.beginUm980Bridge { raw -> onBridgeRaw(raw) }
        workingBaud = EspCompanionRepository.deviceInfo.value.um980Baud.takeIf { it > 0 } ?: workingBaud
    }

    override fun endExclusive() {
        manager.endUm980Bridge()
        synchronized(rxLock) { rx.reset() }
        frameBuf.reset()
    }

    private fun onBridgeRaw(raw: ByteArray) {
        // Parse 0xA5 0x5A frames → UART payload into rx
        frameBuf.write(raw)
        val data = frameBuf.toByteArray()
        var i = 0
        while (i < data.size) {
            if (data.size - i < 4) break
            // resync
            if ((data[i].toInt() and 0xFF) != 0xA5) {
                i++
                continue
            }
            if (i + 1 >= data.size) break
            if ((data[i + 1].toInt() and 0xFF) != 0x5A) {
                i++
                continue
            }
            val plen = ((data[i + 2].toInt() and 0xFF) shl 8) or (data[i + 3].toInt() and 0xFF)
            if (plen <= 0 || plen > EspCompanionProtocol.OTA_CHUNK_MAX) {
                i++
                continue
            }
            val need = 4 + plen + 4
            if (data.size - i < need) break
            val payload = data.copyOfRange(i + 4, i + 4 + plen)
            val crcOff = i + 4 + plen
            val gotCrc =
                ((data[crcOff].toInt() and 0xFF).toLong() shl 24) or
                    ((data[crcOff + 1].toInt() and 0xFF).toLong() shl 16) or
                    ((data[crcOff + 2].toInt() and 0xFF).toLong() shl 8) or
                    (data[crcOff + 3].toInt() and 0xFF).toLong()
            val expect = EspCompanionProtocol.crc32Ieee(payload)
            if (gotCrc == expect) {
                synchronized(rxLock) {
                    rx.write(payload)
                    if (rx.size() > 512 * 1024) {
                        val keep = rx.toByteArray().let { it.copyOfRange(it.size - 256 * 1024, it.size) }
                        rx.reset()
                        rx.write(keep)
                    }
                }
            } else {
                Log.w(TAG, "bridge frame CRC mismatch")
            }
            i += need
        }
        // keep remainder
        frameBuf.reset()
        if (i < data.size) {
            frameBuf.write(data, i, data.size - i)
        }
    }

    companion object {
        private const val TAG = "EspUm980Bridge"
    }
}
