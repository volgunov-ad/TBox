package vad.dashing.tbox.um980fw

import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.Charset

/**
 * UM980 `.pkg` flasher over [Um980BinaryTransport] (USB or ESP bridge).
 * Protocol: docs/UM980_FIRMWARE_UPDATE_RU.md
 */
class Um980FirmwareUpdater(
    private val transport: Um980BinaryTransport,
) {
    suspend fun update(
        pkgFile: File,
        resetMode: Um980FwResetMode,
        workingBaud: Int,
        onHardResetWait: suspend () -> Unit = {
            val gate = CompletableDeferred<Unit>()
            Um980FirmwareUiStore.awaitHardReset { gate.complete(Unit) }
            gate.await()
        },
    ): Result<String> {
        val image = try {
            pkgFile.readBytes()
        } catch (e: Exception) {
            return fail("bad_file", e)
        }
        Um980PkgValidator.validate(image.size.toLong(), image.copyOf(4.coerceAtMost(image.size)))
            ?.let { return fail(it) }

        val preBaud = workingBaud.coerceIn(9600, 921600)
        transport.beginExclusive()
        return try {
            Um980FirmwareUiStore.setPhase("prep", 0)
            // UPrecise (um980.dmslog8): several unlog pairs, then CONFIG COM* 460800 (no SAVECONFIG).
            repeat(3) {
                sendLine("unlog")
                delay(120)
                sendLine("unlog")
                delay(150)
            }
            for (com in listOf("com1", "com2", "com3")) {
                sendLine("config $com $UPGRADE_BAUD")
                delay(80)
            }
            if (!transport.setBaud(UPGRADE_BAUD)) {
                return fail("baud")
            }
            // Capture: ~2.5s between CONFIG and reset while host settles on 460800.
            delay(400)
            drain(300)
            sendLine("unlog")
            delay(120)
            sendLine("unlog")
            delay(200)
            drain(200)

            var bootloaderSeen = false
            when (resetMode) {
                Um980FwResetMode.SOFT -> {
                    Um980FirmwareUiStore.setPhase("reset", 2)
                    // UPrecise sends reset twice in one write: "\r\nreset\r\nreset\r\n".
                    sendLine("reset")
                    delay(40)
                    sendLine("reset")
                    val softHit = waitForAny(
                        listOf("rebooting", "BootLoader", "boot>"),
                        15_000L,
                    )
                    if (softHit == null) {
                        runCatching { Log.w(TAG, "soft reset: no reboot banner (continuing)") }
                    } else if (isBootloaderBanner(softHit)) {
                        // Bytes already consumed by waitForAny — do not require a second banner.
                        bootloaderSeen = true
                        runCatching {
                            Log.i(TAG, "BootLoader seen during soft reset at baud=${transport.currentBaud()}")
                        }
                    }
                }
                Um980FwResetMode.HARD -> {
                    Um980FirmwareUiStore.setPhase("hard_reset", 2)
                    onHardResetWait()
                }
            }

            Um980FirmwareUiStore.setPhase("bootloader", 5)
            // After power-cycle (and often soft reset) the module may speak BootLoader at the
            // *saved* baud (e.g. 115200) while the host is still on UPGRADE_BAUD (460800).
            if (!bootloaderSeen && !waitForBootloaderBanner(preBaud)) {
                return fail("no_bootloader")
            }
            delay(200)
            drain(200)
            // Menu item 2 = "Download from uart to flash" (N4 BootLoader; timeout 2s reprints menu).
            transport.write("2\r\n".toByteArray(Charsets.US_ASCII))
            Um980FirmwareUiStore.setPhase("menu2", 8)
            waitForAny(listOf("unlock", "xmodem", "download", "binary", "Ready"), 15_000L)
                ?: runCatching { Log.w(TAG, "no unlock banner (continuing to XMODEM)") }.getOrNull()

            Um980FirmwareUiStore.setPhase("xmodem", 10)
            xmodemSend(image)?.let { return fail(it) }

            Um980FirmwareUiStore.setPhase("boot_app", 92)
            waitForAny(listOf("FreeRTOS", "\$G", "\$GN", "#VERSION", "NMEA"), 60_000L)
            delay(1_500)

            Um980FirmwareUiStore.setPhase("baud_restore", 95)
            restoreBaud(preBaud)?.let { return fail(it) }

            Um980FirmwareUiStore.setPhase("verify", 98)
            val versionA = queryVersionA()
            val expectedBuild = Um980PkgValidator.buildFromFileName(pkgFile.name)
            val gotBuild = versionA?.let { Um980PkgValidator.buildFromVersionA(it) }
            if (expectedBuild != null && gotBuild != null && expectedBuild != gotBuild) {
                Log.w(TAG, "VERSIONA build mismatch expect=$expectedBuild got=$gotBuild line=$versionA")
            }
            Um980FirmwareUiStore.finish(null)
            Result.success(versionA.orEmpty())
        } catch (e: Exception) {
            fail(e.message ?: "failed", e)
        } finally {
            runCatching { transport.endExclusive() }
            runCatching { transport.setBaud(preBaud) }
            runCatching { pkgFile.delete() }
        }
    }

    private suspend fun xmodemSend(image: ByteArray): String? {
        val mode = awaitXmodemStart(20_000L) ?: return "xmodem_start"
        var offset = 0
        var seq = 1
        while (offset < image.size) {
            val end = minOf(offset + Xmodem1k.BLOCK_SIZE, image.size)
            val chunk = image.copyOfRange(offset, end)
            val frame = Xmodem1k.buildBlock(seq and 0xFF, chunk, mode)
            var acked = false
            repeat(Xmodem1k.MAX_RETRIES) {
                if (!transport.write(frame)) return "no_usb"
                when (awaitAckOrNak(10_000L)) {
                    Xmodem1k.ACK -> {
                        acked = true
                        return@repeat
                    }
                    Xmodem1k.NAK -> Unit
                    Xmodem1k.CAN -> return "xmodem_cancel"
                    else -> Unit
                }
            }
            if (!acked) return "xmodem_timeout"
            offset = end
            seq = if (seq >= 255) 1 else seq + 1
            val pct = 10 + ((offset.toLong() * 80L) / image.size).toInt()
            Um980FirmwareUiStore.setProgress(pct)
        }
        repeat(Xmodem1k.MAX_RETRIES) {
            transport.write(byteArrayOf(Xmodem1k.EOT))
            if (awaitAckOrNak(10_000L) == Xmodem1k.ACK) return null
        }
        return "xmodem_eot"
    }

    private suspend fun awaitXmodemStart(timeoutMs: Long): Xmodem1k.CheckMode? {
        val deadline = System.currentTimeMillis() + timeoutMs
        val buf = ByteArrayOutputStream()
        while (System.currentTimeMillis() < deadline) {
            val chunk = transport.read(64, 200)
            if (chunk.isNotEmpty()) buf.write(chunk)
            val bytes = buf.toByteArray()
            for (b in bytes) {
                when (b) {
                    Xmodem1k.CRC_LETTER -> return Xmodem1k.CheckMode.CRC16
                    Xmodem1k.NAK -> return Xmodem1k.CheckMode.CHECKSUM
                    Xmodem1k.CAN -> return null
                }
            }
            delay(50)
        }
        // Bootloader often ready after unlock without explicit NAK in noisy logs — try checksum.
        Log.w(TAG, "XMODEM start: no NAK/C, defaulting to checksum")
        return Xmodem1k.CheckMode.CHECKSUM
    }

    private suspend fun awaitAckOrNak(timeoutMs: Long): Byte? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val chunk = transport.read(32, 150)
            for (b in chunk) {
                when (b) {
                    Xmodem1k.ACK, Xmodem1k.NAK, Xmodem1k.CAN -> return b
                }
            }
            delay(20)
        }
        return null
    }

    private suspend fun restoreBaud(workingBaud: Int): String? {
        val candidates = linkedSetOf(workingBaud, UPGRADE_BAUD, 115200, 57600, 230400)
            .filter { it in 9600..921600 }
        for (baud in candidates) {
            if (!transport.setBaud(baud)) continue
            delay(300)
            drain(200)
            sendLine("VERSIONA")
            val lines = collectAscii(2_500L)
            if (lines.any { it.contains("VERSIONA", ignoreCase = true) }) {
                for (com in listOf("com1", "com2", "com3")) {
                    sendLine("config $com $workingBaud")
                    delay(60)
                }
                if (baud != workingBaud) {
                    transport.setBaud(workingBaud)
                    delay(200)
                }
                sendLine("SAVECONFIG")
                delay(500)
                return null
            }
        }
        // Last resort: force host baud + CONFIG even without VERSIONA
        transport.setBaud(workingBaud)
        for (com in listOf("com1", "com2", "com3")) {
            sendLine("config $com $workingBaud")
            delay(60)
        }
        sendLine("SAVECONFIG")
        return null
    }

    private suspend fun queryVersionA(): String? {
        sendLine("VERSIONA")
        val lines = collectAscii(3_000L)
        return lines.firstOrNull { it.contains("VERSIONA", ignoreCase = true) }
    }

    private fun sendLine(cmd: String) {
        transport.write((cmd.trimEnd('\r', '\n') + "\r\n").toByteArray(Charsets.US_ASCII))
    }

    private suspend fun drain(ms: Long) {
        val deadline = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < deadline) {
            transport.read(512, 50)
            delay(20)
        }
    }

    /**
     * Wait for N4 BootLoader, sweeping host baud when the banner is silent on [UPGRADE_BAUD].
     * Leaves the transport on the baud where the banner was seen (XMODEM uses that rate).
     */
    internal suspend fun waitForBootloaderBanner(
        preBaud: Int,
        overallTimeoutMs: Long = BOOTLOADER_SWEEP_TIMEOUT_MS,
    ): Boolean {
        val needles = listOf("BootLoader", "boot>")
        val ordered = bootloaderBaudCandidates(preBaud, transport.currentBaud())
        val overallDeadline = System.currentTimeMillis() + overallTimeoutMs.coerceAtLeast(1_000L)
        for (baud in ordered) {
            val remaining = overallDeadline - System.currentTimeMillis()
            if (remaining <= 0L) break
            if (baud != transport.currentBaud()) {
                if (!transport.setBaud(baud)) {
                    runCatching { Log.w(TAG, "bootloader sweep: setBaud($baud) failed") }
                    continue
                }
                delay(120)
                drain(80)
            }
            // Nudge prompt reprint after baud change / power-up.
            transport.write("\r\n".toByteArray(Charsets.US_ASCII))
            val slice = minOf(BOOTLOADER_BAUD_SLICE_MS, remaining)
            val hit = waitForAny(needles, slice)
            if (hit != null) {
                runCatching { Log.i(TAG, "BootLoader seen at baud=$baud (matched '$hit')") }
                return true
            }
        }
        return false
    }

    private suspend fun waitForAny(needles: List<String>, timeoutMs: Long): String? {
        val deadline = System.currentTimeMillis() + timeoutMs
        val ascii = StringBuilder()
        while (System.currentTimeMillis() < deadline) {
            val chunk = transport.read(256, 100)
            if (chunk.isNotEmpty()) {
                ascii.append(chunk.toString(Charset.forName("US-ASCII")))
                if (ascii.length > 8_000) ascii.delete(0, ascii.length - 4_000)
                val hay = ascii.toString()
                for (n in needles) {
                    if (hay.contains(n, ignoreCase = true)) return n
                }
            }
            delay(30)
        }
        return null
    }

    private suspend fun collectAscii(timeoutMs: Long): List<String> {
        val deadline = System.currentTimeMillis() + timeoutMs
        val buf = StringBuilder()
        while (System.currentTimeMillis() < deadline) {
            val chunk = transport.read(256, 80)
            if (chunk.isNotEmpty()) buf.append(chunk.toString(Charset.forName("US-ASCII")))
            delay(20)
        }
        return buf.toString().lines().map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun fail(code: String, e: Exception? = null): Result<String> {
        if (e != null) Log.w(TAG, "UM980 FW failed: $code", e) else Log.w(TAG, "UM980 FW failed: $code")
        Um980FirmwareUiStore.finish(code)
        return Result.failure(IllegalStateException(code))
    }

    companion object {
        private const val TAG = "Um980Fw"
        const val UPGRADE_BAUD = 460_800
        private const val BOOTLOADER_SWEEP_TIMEOUT_MS = 35_000L
        private const val BOOTLOADER_BAUD_SLICE_MS = 5_000L

        internal fun isBootloaderBanner(hit: String): Boolean =
            hit.contains("BootLoader", ignoreCase = true) ||
                hit.contains("boot>", ignoreCase = true)

        /**
         * Host baud order when hunting for BootLoader after reset.
         * Prefer current (usually [UPGRADE_BAUD]), then pre-upgrade, then common defaults.
         */
        internal fun bootloaderBaudCandidates(preBaud: Int, currentBaud: Int): List<Int> {
            val pre = preBaud.coerceIn(9600, 921600)
            val current = currentBaud.coerceIn(9600, 921600)
            return linkedSetOf(current, UPGRADE_BAUD, pre, 115_200, 57_600, 230_400, 38_400)
                .filter { it in 9600..921600 }
        }
    }
}
