package vad.dashing.tbox.um980fw

import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
        var upgradeSucceeded = false
        transport.beginExclusive()
        return try {
            Um980FirmwareUiStore.setPhase("prep", 0)
            // USB: reopen so line-coding matches working baud before Soft CONFIG.
            if (!applyHostBaud(preBaud)) {
                return fail("baud")
            }
            delay(150)
            // Stop NMEA at the known-good working baud first (UPrecise also unlogs before CONFIG).
            repeat(3) {
                sendLine("unlog")
                delay(120)
                sendLine("unlog")
                delay(150)
            }
            drain(200)

            var bootloaderSeen = false
            when (resetMode) {
                Um980FwResetMode.SOFT -> {
                    Um980FirmwareUiStore.setPhase("reset", 2)
                    bootloaderSeen = enterBootloaderSoft(preBaud)
                }
                Um980FwResetMode.HARD -> {
                    Um980FirmwareUiStore.setPhase("hard_reset", 2)
                    // Hard power-cycle drops RAM CONFIG — keep host on saved/working baud and
                    // listen *while* the user cycles power (banner is easy to miss after Continue).
                    bootloaderSeen = enterBootloaderHard(preBaud, onHardResetWait)
                }
            }

            Um980FirmwareUiStore.setPhase("bootloader", 5)
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
                runCatching {
                    Log.w(TAG, "VERSIONA build mismatch expect=$expectedBuild got=$gotBuild line=$versionA")
                }
            }
            Um980FirmwareUiStore.finish(null)
            upgradeSucceeded = true
            Result.success(versionA.orEmpty())
        } catch (e: Exception) {
            fail(e.message ?: "failed", e)
        } finally {
            if (!upgradeSucceeded) {
                runCatching { recoverLinkBestEffort(preBaud) }
            }
            runCatching { transport.endExclusive() }
            // Prefer reopen so CP210x/CH340 actually leave UPGRADE_BAUD after Soft.
            if (!transport.reopenAtBaud(preBaud)) {
                runCatching { transport.setBaud(preBaud) }
            }
            runCatching { pkgFile.delete() }
        }
    }

    /**
     * Soft entry.
     *
     * Build14259 on direct USB never showed BootLoader after CONFIG+reopen+SAVECONFIG:
     * USB reopen is slower than the N4 menu (~2s) and drops DTR (often RESET_N), so the
     * banner is gone before RX starts. UPrecise changes baud on an already-open port.
     *
     * Order:
     * 1. DTR pulse while the port stays open, listen at the working baud.
     * 2. ASCII double-reset at the working baud (known-good link) and listen there.
     * 3. UPrecise CONFIG 460800, [setBaud] (no reopen) so RAM baud survives, then reset.
     *    Reopen only if 460800 stays silent.
     * Do not SAVECONFIG 460800 — a failed Soft must not persist the upgrade baud.
     */
    internal suspend fun enterBootloaderSoft(preBaud: Int): Boolean {
        if (transport.pulseHardwareReset()) {
            runCatching { Log.i(TAG, "soft: DTR pulse, listen baud=${transport.currentBaud()}") }
            if (awaitBootloaderNudged(SOFT_DTR_LISTEN_MS)) return true
        }

        if (probeAppAlive(1_500L)) {
            sendDoubleReset(leadingCrLf = false)
            delay(SOFT_RESET_REPEAT_GAP_MS)
            sendDoubleReset(leadingCrLf = true)
            if (awaitBootloaderNudged(SOFT_PRE_RESET_LISTEN_MS)) {
                runCatching { Log.i(TAG, "BootLoader via reset at baud=${transport.currentBaud()}") }
                return true
            }
            // App may be rebooting; wait until it answers before CONFIG.
            if (!probeAppAlive(3_000L)) {
                transport.setBaud(preBaud)
                probeAppAlive(2_000L)
            }
        }

        sendUpgradeBaudConfigBlock()
        delay(SOFT_CONFIG_REPEAT_GAP_MS)
        sendUpgradeBaudConfigBlock()
        delay(200)
        // Keep the port open. reopenAtBaud drops DTR and wipes RAM 460800 / misses the menu.
        if (!transport.setBaud(UPGRADE_BAUD)) {
            runCatching { Log.w(TAG, "soft: setBaud($UPGRADE_BAUD) failed, reopening") }
            if (!transport.reopenAtBaud(UPGRADE_BAUD)) {
                runCatching { recoverLinkBestEffort(preBaud) }
                return false
            }
        }
        delay(300)
        drain(150)
        if (!probeAppAlive(2_000L)) {
            runCatching { Log.w(TAG, "soft: no OK at 460800 after setBaud — one reopen") }
            if (transport.reopenAtBaud(UPGRADE_BAUD)) {
                delay(400)
                drain(150)
            }
            if (!probeAppAlive(2_000L)) {
                if (awaitBootloaderNudged(2_500L)) return true
                runCatching { Log.w(TAG, "soft: no link at 460800 — restoring working baud") }
                runCatching { recoverLinkBestEffort(preBaud) }
                return false
            }
        }

        sendDoubleReset(leadingCrLf = false)
        delay(SOFT_RESET_REPEAT_GAP_MS)
        sendDoubleReset(leadingCrLf = true)
        if (awaitBootloaderNudged(SOFT_POST_RESET_LISTEN_MS)) {
            runCatching { Log.i(TAG, "BootLoader via Soft at baud=${transport.currentBaud()}") }
            return true
        }
        if (waitForBootloaderBanner(preBaud, overallTimeoutMs = 16_000L)) return true

        runCatching { Log.w(TAG, "soft: BootLoader not seen (recover deferred to update finally)") }
        return false
    }

    /** All three COM baud lines in one write — baud flips after the whole block is received. */
    private fun sendUpgradeBaudConfigBlock() {
        sendWorkingBaudConfigBlock(UPGRADE_BAUD)
    }

    private fun sendWorkingBaudConfigBlock(baud: Int) {
        val b = baud.coerceIn(9600, 921600)
        val block =
            "config com1 $b\r\n" +
                "config com2 $b\r\n" +
                "config com3 $b\r\n"
        transport.write(block.toByteArray(Charsets.US_ASCII))
    }

    /**
     * Hard entry: listen for BootLoader *while* the user power-cycles (banner often appears
     * before «Продолжить»), then one more sweep after Continue.
     */
    private suspend fun enterBootloaderHard(
        preBaud: Int,
        onHardResetWait: suspend () -> Unit,
    ): Boolean = coroutineScope {
        if (!transport.setBaud(preBaud)) {
            runCatching { Log.w(TAG, "hard: setBaud($preBaud) failed") }
        }
        delay(100)
        drain(100)

        val userWait = async {
            onHardResetWait()
        }
        while (isActive && !userWait.isCompleted) {
            if (waitForBootloaderBanner(preBaud, overallTimeoutMs = HARD_LISTEN_SLICE_MS)) {
                runCatching { Um980FirmwareUiStore.userContinuedHardReset() }
                userWait.cancel()
                runCatching { Log.i(TAG, "BootLoader during hard-reset wait at baud=${transport.currentBaud()}") }
                return@coroutineScope true
            }
        }
        runCatching { userWait.await() }
        waitForBootloaderBanner(preBaud, overallTimeoutMs = BOOTLOADER_SWEEP_TIMEOUT_MS)
    }

    /**
     * N4 menu reprints about every 2s and only while the host baud matches.
     * Nudge with CR/LF; ignore a bare "rebooting" hit so a later banner in the same window counts.
     */
    private suspend fun awaitBootloaderNudged(timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        var nextNudge = 0L
        while (System.currentTimeMillis() < deadline) {
            val now = System.currentTimeMillis()
            if (now >= nextNudge) {
                transport.write("\r\n".toByteArray(Charsets.US_ASCII))
                nextNudge = now + 1_200L
            }
            val slice = minOf(500L, (deadline - System.currentTimeMillis()).coerceAtLeast(50L))
            val hit = waitForAny(listOf("BootLoader", "boot>", "rebooting"), slice)
            if (hit != null && isBootloaderBanner(hit)) {
                runCatching { Log.i(TAG, "BootLoader at baud=${transport.currentBaud()}") }
                return true
            }
        }
        return false
    }

    private fun sendDoubleReset(leadingCrLf: Boolean = true) {
        // UPrecise Soft: "reset\r\nreset\r\n" then shortly "\r\nreset\r\nreset\r\n"
        val payload = if (leadingCrLf) "\r\nreset\r\nreset\r\n" else "reset\r\nreset\r\n"
        transport.write(payload.toByteArray(Charsets.US_ASCII))
    }

    /** True if UM980 app firmware still answers ASCII (unlog OK / command response). */
    private suspend fun probeAppAlive(timeoutMs: Long): Boolean {
        drain(80)
        sendLine("unlog")
        return waitForAny(listOf("OK", "response", "command"), timeoutMs) != null
    }

    /**
     * After a failed Soft path the module may be left at saved/RAM 460800 (Soft now SAVECONFIGs
     * upgrade baud before reopen) or in BootLoader — connection looks dead until power-cycle or UI
     * «Перезагрузка GNSS».
     *
     * USB: [Um980BinaryTransport.reopenAtBaud] per candidate, exit BootLoader if needed, CONFIG +
     * SAVECONFIG working baud, then hot `RESET` (same as GNSS reboot) so the live UART matches.
     */
    internal suspend fun recoverLinkBestEffort(preBaud: Int) {
        val target = preBaud.coerceIn(9600, 921600)
        var touchedApp = false
        for (baud in bootloaderBaudCandidates(target, transport.currentBaud())) {
            if (!applyHostBaud(baud)) continue
            delay(150)
            drain(100)
            transport.write("\r\n".toByteArray(Charsets.US_ASCII))
            // N4 BootLoader reprints the menu every ~2s — wait longer than one period.
            if (waitForAny(listOf("BootLoader", "boot>"), 2_500L) != null) {
                // Menu 0 = Load OS & GSP from flash
                transport.write("0\r\n".toByteArray(Charsets.US_ASCII))
                waitForAny(listOf("FreeRTOS", "VERSION", "\$G", "OK", "command"), 8_000L)
                delay(500)
            }
            if (!probeAppAlive(1_200L)) {
                sendLine("VERSIONA")
                if (waitForAny(listOf("VERSIONA", "UM980"), 1_500L) == null) continue
            }
            sendWorkingBaudConfigBlock(target)
            sendLine("SAVECONFIG")
            delay(SOFT_SAVECONFIG_SETTLE_MS)
            touchedApp = true
            runCatching { Log.i(TAG, "recoverLink: app answered at host baud=$baud → SAVECONFIG $target") }
            break
        }
        // Match UI GNSS reboot: reopen at working baud, hot RESET, reopen again.
        if (!applyHostBaud(target)) {
            runCatching { transport.setBaud(target) }
        }
        delay(200)
        sendLine("RESET")
        delay(RECOVER_RESET_SETTLE_MS)
        if (!applyHostBaud(target)) {
            runCatching { transport.setBaud(target) }
        }
        delay(300)
        drain(200)
        sendLine("unlog")
        delay(100)
        if (touchedApp || probeAppAlive(1_500L)) {
            runCatching { Log.i(TAG, "recoverLink: restored via reopen+RESET at baud=$target") }
        } else {
            runCatching { Log.w(TAG, "recoverLink: reopen+RESET sent; app OK not confirmed at $target") }
        }
    }

    /** Prefer [Um980BinaryTransport.reopenAtBaud]; fall back to [Um980BinaryTransport.setBaud]. */
    private fun applyHostBaud(baud: Int): Boolean {
        if (transport.currentBaud() == baud) {
            // Still reopen on USB when already at target — live coding may disagree with HW.
            if (transport.reopenAtBaud(baud)) return true
            return transport.setBaud(baud)
        }
        if (transport.reopenAtBaud(baud)) return true
        return transport.setBaud(baud)
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
                // setBaud keeps the port open. Reopen only if the live change is rejected —
                // a reopen on every candidate drops DTR and restarts the 2s menu.
                val switched = transport.setBaud(baud) || transport.reopenAtBaud(baud)
                if (!switched) {
                    runCatching { Log.w(TAG, "bootloader sweep: setBaud($baud) failed") }
                    continue
                }
                delay(80)
                drain(40)
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
                noteRx(chunk)
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

    private val rxTail = StringBuilder()

    private fun noteRx(chunk: ByteArray) {
        val s = chunk.toString(Charset.forName("US-ASCII"))
            .replace('\r', ' ')
            .replace('\n', '|')
            .replace(Regex("[^\\x20-\\x7E|]"), ".")
        rxTail.append(s)
        if (rxTail.length > 240) rxTail.delete(0, rxTail.length - 160)
    }

    private fun fail(code: String, e: Exception? = null): Result<String> {
        if (e != null) Log.w(TAG, "UM980 FW failed: $code", e) else Log.w(TAG, "UM980 FW failed: $code")
        val detail = rxTail.toString().trim().take(160).ifBlank { null }
        if (detail != null) Log.w(TAG, "UM980 FW rx tail: $detail")
        Um980FirmwareUiStore.finish(code, detail)
        return Result.failure(IllegalStateException(code))
    }

    companion object {
        private const val TAG = "Um980Fw"
        const val UPGRADE_BAUD = 460_800
        private const val BOOTLOADER_SWEEP_TIMEOUT_MS = 35_000L
        private const val BOOTLOADER_BAUD_SLICE_MS = 5_000L
        private const val HARD_LISTEN_SLICE_MS = 6_000L
        /** Settle after hot RESET during recover (module drops RAM baud, reboots app). */
        private const val RECOVER_RESET_SETTLE_MS = 2_500L
        /** UPrecise Soft: second CONFIG block ~50 ms after the first. */
        private const val SOFT_CONFIG_REPEAT_GAP_MS = 50L
        /** UPrecise Soft: second double-reset burst ~50 ms after the first. */
        private const val SOFT_RESET_REPEAT_GAP_MS = 50L
        /** Listen after a DTR pulse (port already open, menu ~2s). */
        private const val SOFT_DTR_LISTEN_MS = 3_000L
        /** Listen after ASCII reset at the working baud (banner ~3–4s after reset). */
        private const val SOFT_PRE_RESET_LISTEN_MS = 4_500L
        /** Listen after reset at 460800. */
        private const val SOFT_POST_RESET_LISTEN_MS = 8_000L
        /** Allow SAVECONFIG to commit during recover. */
        private const val SOFT_SAVECONFIG_SETTLE_MS = 800L

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
