package vad.dashing.tbox.um980fw

import android.util.Log
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
        workingBaud: Int,
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
        var linkBaud = preBaud
        transport.beginExclusive()
        return try {
            Um980FirmwareUiStore.setPhase("prep", 0)
            // The settings dialog just read VERSIONA on this open port. Probe that baud
            // before any setBaud/reopen — a live baud change on CP210x/CH340 often drops
            // the link and the next probe reports no-link@115200.
            val liveBaud = if (probeAppAlive(2_500L)) {
                notePhase("link@${transport.currentBaud()}")
                transport.currentBaud()
            } else {
                discoverAppBaud(preBaud)
            }
            if (liveBaud == null) {
                notePhase("no-link@$preBaud")
                return fail("no_bootloader")
            }
            linkBaud = liveBaud
            delay(100)
            repeat(2) {
                sendLine("unlog")
                delay(120)
            }
            drain(150)

            Um980FirmwareUiStore.setPhase("reset", 2)
            val bootloaderSeen = enterBootloaderSoft(liveBaud)

            Um980FirmwareUiStore.setPhase("bootloader", 5)
            if (!bootloaderSeen && !waitForBootloaderBanner(liveBaud)) {
                return fail("no_bootloader")
            }
            delay(200)
            drain(200)
            // um980-fw.txt: "2" is sent twice, then "unlock Flash" / Ready / C.
            // The CRC 'C' follows Ready in the same read; do not drop it.
            transport.write("2\r\n2\r\n".toByteArray(Charsets.US_ASCII))
            Um980FirmwareUiStore.setPhase("menu2", 8)
            val xmodemMode = awaitXmodemMode(20_000L) ?: return fail("xmodem_start")

            Um980FirmwareUiStore.setPhase("xmodem", 10)
            xmodemSend(image, xmodemMode)?.let { return fail(it) }

            Um980FirmwareUiStore.setPhase("boot_app", 92)
            // After backup succeed the menu returns. Item 6 (four times) soft-resets into the app.
            bootAppAfterXmodem()
            delay(800)

            Um980FirmwareUiStore.setPhase("baud_restore", 95)
            restoreBaud(linkBaud)?.let { return fail(it) }

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
                Um980FirmwareUiStore.beginRecover()
                runCatching { recoverLinkBestEffort(linkBaud) }
                Um980FirmwareUiStore.endRecover()
            }
            runCatching { transport.endExclusive() }
            if (!transport.reopenAtBaud(linkBaud)) {
                runCatching { transport.setBaud(linkBaud) }
            }
            runCatching { pkgFile.delete() }
        }
    }

    /**
     * Soft entry.
     *
     * Field log on Build14259 (direct USB): the failure RX tail is `#VERSIONA` / Build14259,
     * so the module never left the app. ASCII `reset` at the working baud is only a hot reboot.
     * UPrecise enters N4 BootLoader only after the host is already on 460800 and the app answers.
     *
     * Do not reset until [probeAppAlive] succeeds at [UPGRADE_BAUD]. The read loop must already
     * be running (no reopen between that probe and `reset`), otherwise the ~2s menu is missed.
     */
    internal suspend fun enterBootloaderSoft(preBaud: Int): Boolean {
        var hostBaud = preBaud
        if (!ensureApp(hostBaud)) {
            val found = discoverAppBaud(hostBaud)
            if (found == null) {
                notePhase("no-link@$hostBaud")
                runCatching { Log.w(TAG, "soft: app silent at $hostBaud and sweep") }
                return false
            }
            hostBaud = found
        }
        if (!switchToUpgradeBaud(hostBaud)) {
            notePhase("no-460800")
            runCatching { Log.w(TAG, "soft: no app link at 460800") }
            runCatching { recoverLinkBestEffort(hostBaud) }
            return false
        }
        if (enteredBootloaderDuringSwitch) return true
        notePhase("460800-ok")
        // um980-fw.txt: one "reset" pair, then T@ at once. No SAVECONFIG —
        // 460800 stays RAM-only, so the saved rate (usually 115200) survives the flash.
        rxTail.clear()
        sendDoubleReset(leadingCrLf = false)
        // UPrecise COM6 capture: stream T@ through "system is rebooting" until the
        // N4 menu. Silence here lets the ROM boot the app instead.
        if (listenAtUpgradeBaudAfterReset()) return true
        if (sweepBootloaderAfterGarbage(hostBaud)) return true
        if (waitForBootloaderBanner(hostBaud, overallTimeoutMs = 12_000L)) return true
        notePhase("no-banner")
        runCatching { Log.w(TAG, "soft: 460800 link ok but no BootLoader after reset") }
        return false
    }

    /** True when [switchToUpgradeBaud] already observed the BootLoader banner. */
    private var enteredBootloaderDuringSwitch = false

    private suspend fun ensureApp(baud: Int): Boolean {
        if (transport.currentBaud() != baud && !transport.setBaud(baud)) return false
        delay(100)
        val ok = probeAppAlive(2_500L)
        if (ok) notePhase("link@$baud")
        return ok
    }

    /**
     * Find a baud the app answers on without reopening first.
     * Reopen is last: it drops DTR and was reporting no-link right after a good VERSIONA.
     */
    internal suspend fun discoverAppBaud(preferred: Int): Int? {
        val order = linkedSetOf(
            transport.currentBaud(),
            preferred.coerceIn(9600, 921600),
            115_200,
            UPGRADE_BAUD,
            57_600,
            38_400,
            9_600,
            230_400,
        ).filter { it in 9600..921600 }
        for (b in order) {
            if (transport.currentBaud() != b && !transport.setBaud(b)) continue
            delay(120)
            if (probeAppAlive(1_800L)) {
                notePhase("link@$b")
                return b
            }
        }
        for (b in linkedSetOf(transport.currentBaud(), preferred, UPGRADE_BAUD, 115_200)) {
            val baud = b.coerceIn(9600, 921600)
            if (!transport.reopenAtBaud(baud)) continue
            delay(400)
            if (probeAppAlive(5_000L)) {
                notePhase("link-reopen@$baud")
                return baud
            }
        }
        return null
    }

    /**
     * Leave the host on 460800 with the app answering, or with BootLoader already visible.
     * [setBaud] first (port stays open). Reopen only if that stays silent. If reopen drops
     * DTR and wipes RAM baud, SAVECONFIG 460800 and reopen once more, then probe again.
     */
    private suspend fun switchToUpgradeBaud(preBaud: Int): Boolean {
        // um980-fw.txt: a single com1+com2+com3 block, then the host moves to 460800.
        sendUpgradeBaudConfigBlock()
        delay(120)
        if (transport.setBaud(UPGRADE_BAUD) && probeAppAlive(2_000L)) return true
        runCatching { Log.w(TAG, "soft: setBaud 460800 silent, reopening") }
        if (transport.reopenAtBaud(UPGRADE_BAUD)) {
            delay(350)
            drain(80)
            if (probeAppAlive(2_000L)) return true
            if (awaitBootloaderNudged(2_000L)) {
                enteredBootloaderDuringSwitch = true
                return true
            }
        }
        runCatching { Log.w(TAG, "soft: persist 460800, then reopen") }
        if (transport.currentBaud() != preBaud) {
            if (!transport.setBaud(preBaud)) transport.reopenAtBaud(preBaud)
        }
        if (!probeAppAlive(2_000L)) return false
        sendUpgradeBaudConfigBlock()
        delay(SOFT_CONFIG_REPEAT_GAP_MS)
        sendUpgradeBaudConfigBlock()
        sendLine("SAVECONFIG")
        delay(SOFT_SAVECONFIG_SETTLE_MS)
        if (!transport.reopenAtBaud(UPGRADE_BAUD)) return false
        delay(400)
        drain(80)
        if (probeAppAlive(2_500L)) return true
        if (awaitBootloaderNudged(2_500L)) {
            enteredBootloaderDuringSwitch = true
            return true
        }
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

    /**
     * Stream the UPrecise upgrade flag `T@` at 460800 until `N4 BootLoader` / `boot>`.
     * The ROM boots the app if this flag is absent. Non-ASCII between pulses is normal.
     */
    private suspend fun listenAtUpgradeBaudAfterReset(): Boolean {
        val deadline = System.currentTimeMillis() + SOFT_QUIET_AFTER_RESET_MS
        val ascii = StringBuilder()
        var nextPulse = 0L
        while (System.currentTimeMillis() < deadline) {
            val now = System.currentTimeMillis()
            if (now >= nextPulse) {
                transport.write(UPGRADE_FLAG_PULSE)
                nextPulse = now + 30L
            }
            val chunk = transport.read(256, 40)
            if (chunk.isNotEmpty()) {
                noteRx(chunk)
                ascii.append(chunk.toString(Charsets.US_ASCII))
                if (ascii.length > 8_000) ascii.delete(0, ascii.length - 4_000)
                val hay = ascii.toString()
                if (hay.contains("BootLoader", ignoreCase = true) || hay.contains("boot>", ignoreCase = true)) {
                    notePhase("flag")
                    return true
                }
            }
            delay(10)
        }
        notePhase("no-flag")
        return false
    }

    /**
     * ROM menu reprints about every 2s and only at the baud it actually uses.
     * Try the saved baud first: that is where Build14259 went after a 460800 reset.
     */
    private suspend fun sweepBootloaderAfterGarbage(preBaud: Int): Boolean {
        val saved = preBaud.coerceIn(9600, 921600)
        // 460800 first: SAVECONFIG just made that the ROM baud, and the menu
        // reprints there. Leaving for 115200 before the reprint drops it.
        val order = linkedSetOf(UPGRADE_BAUD, saved)
        repeat(3) {
            for (baud in order) {
                if (transport.currentBaud() != baud && !transport.setBaud(baud)) continue
                delay(40)
                // Spontaneous menu first. A key before the prompt can take the default item.
                if (waitForAny(listOf("BootLoader", "boot>"), SOFT_MENU_QUIET_MS) != null) {
                    notePhase("banner@$baud")
                    return true
                }
                transport.write("\r\n".toByteArray(Charsets.US_ASCII))
                if (waitForAny(listOf("BootLoader", "boot>"), SOFT_MENU_SLICE_MS) != null) {
                    notePhase("banner@$baud")
                    return true
                }
            }
        }
        return false
    }

    private fun sendDoubleReset(leadingCrLf: Boolean = true) {
        // UPrecise Soft: "reset\r\nreset\r\n" then shortly "\r\nreset\r\nreset\r\n"
        val payload = if (leadingCrLf) "\r\nreset\r\nreset\r\n" else "reset\r\nreset\r\n"
        transport.write(payload.toByteArray(Charsets.US_ASCII))
    }

    /**
     * True if UM980 app firmware still answers ASCII.
     * VERSIONA is what the settings dialog already uses; unlog covers a module that
     * answers commands but has VERSIONA logging disabled.
     */
    private suspend fun probeAppAlive(timeoutMs: Long): Boolean {
        drain(60)
        sendLine("VERSIONA")
        delay(40)
        sendLine("unlog")
        return waitForAny(
            listOf("#VERSION", "UM980", "OK", "response", "command"),
            timeoutMs,
        ) != null
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
                // Same exit as a finished UPrecise flash: menu 6, four times.
                repeat(4) {
                    transport.write("6\r\n".toByteArray(Charsets.US_ASCII))
                    delay(40)
                }
                waitForAny(listOf("resetting the cpu", "\$G", "VERSION", "command"), 8_000L)
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

    private suspend fun xmodemSend(image: ByteArray, mode: Xmodem1k.CheckMode): String? {
        var offset = 0
        var seq = 1
        while (offset < image.size) {
            val end = minOf(offset + Xmodem1k.BLOCK_SIZE, image.size)
            val chunk = image.copyOfRange(offset, end)
            val frame = Xmodem1k.buildBlock(seq and 0xFF, chunk, mode)
            var acked = false
            // repeat() is inline: return@repeat only ends this attempt and sends the
            // same block again. The bootloader already moved on, so the duplicate
            // NAKs pile up and the transfer dies near block 256 (~16% on the bar).
            for (attempt in 1..Xmodem1k.MAX_RETRIES) {
                if (!transport.write(frame)) return "no_usb"
                when (awaitAckOrNak(10_000L)) {
                    Xmodem1k.ACK -> {
                        acked = true
                        break
                    }
                    Xmodem1k.NAK -> Unit
                    Xmodem1k.CAN -> return "xmodem_cancel"
                    else -> Unit
                }
            }
            if (!acked) {
                notePhase("xmodem@$offset/seq=$seq")
                return "xmodem_timeout"
            }
            offset = end
            seq = (seq + 1) and 0xFF
            val pct = 10 + ((offset.toLong() * 80L) / image.size).toInt()
            Um980FirmwareUiStore.setProgress(pct)
        }
        repeat(Xmodem1k.MAX_RETRIES) {
            transport.write(byteArrayOf(Xmodem1k.EOT))
            if (awaitAckOrNak(10_000L) == Xmodem1k.ACK) return null
        }
        return "xmodem_eot"
    }

    private suspend fun awaitXmodemMode(timeoutMs: Long): Xmodem1k.CheckMode? {
        val deadline = System.currentTimeMillis() + timeoutMs
        val buf = ByteArrayOutputStream()
        while (System.currentTimeMillis() < deadline) {
            val chunk = transport.read(256, 100)
            if (chunk.isNotEmpty()) {
                noteRx(chunk)
                buf.write(chunk)
                val bytes = buf.toByteArray()
                if (bytes.any { it == Xmodem1k.CAN }) return null
                Xmodem1k.startModeAfterReady(bytes)?.let { return it }
            }
            delay(20)
        }
        Log.w(TAG, "XMODEM start: no C/NAK after Ready")
        return null
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

    /**
     * Put every COM back on [workingBaud] and SAVECONFIG.
     * The live port flips as soon as its own `config com` is applied, so a burst of
     * com1+com2+com3+SAVECONFIG at 460800 loses SAVECONFIG. That left the module at
     * 460800 after a successful flash (Build25621 on COM6 and on the head unit).
     */
    internal suspend fun restoreBaud(workingBaud: Int): String? {
        val target = workingBaud.coerceIn(9600, 921600)
        val talking = findTalkingBaud(target) ?: return "baud_restore"
        if (transport.currentBaud() != talking && !transport.setBaud(talking)) return "baud_restore"
        // um980-fw.txt: after menu 6 the app is already on the saved baud and NMEA is flowing.
        // config/SAVECONFIG only if it actually came back on 460800.
        if (talking == target) return null
        val ports = ArrayDeque(listOf("com1", "com2", "com3"))
        while (ports.isNotEmpty()) {
            val com = ports.removeFirst()
            sendLine("config $com $target")
            if (waitForAny(listOf("OK"), 900L) != null) continue
            if (!transport.setBaud(target)) return "baud_restore"
            delay(200)
            drain(80)
            sendLine("config $com $target")
            if (waitForAny(listOf("OK"), 900L) == null) return "baud_restore"
        }
        if (transport.currentBaud() != target && !transport.setBaud(target)) return "baud_restore"
        delay(150)
        drain(60)
        sendLine("SAVECONFIG")
        if (waitForAny(listOf("OK"), 1_500L) == null) return "baud_restore"
        return null
    }

    private suspend fun findTalkingBaud(preferred: Int): Int? {
        val order = linkedSetOf(transport.currentBaud(), UPGRADE_BAUD, preferred, 115_200)
            .filter { it in 9600..921600 }
        for (baud in order) {
            if (transport.currentBaud() != baud && !transport.setBaud(baud)) continue
            delay(150)
            drain(80)
            sendLine("VERSIONA")
            // "#VERSION" / "$command" only. The boot menu banner contains "UM980"
            // and must not count as the application.
            if (waitForAny(listOf("#VERSION", "\$command", "\$GN", "\$GP"), 1_200L) != null) {
                return transport.currentBaud()
            }
        }
        return null
    }

    /**
     * After XMODEM the loader prints image-load lines, `backup succeed`, then the menu again.
     * UPrecise sends menu item 6 (`Soft reset cpu`) four times. Item 0 is not used.
     * NMEA follows `resetting the cpu...`. Do not reopen the port here.
     */
    internal suspend fun bootAppAfterXmodem() {
        val deadline = System.currentTimeMillis() + 20_000L
        val ascii = StringBuilder()
        while (System.currentTimeMillis() < deadline) {
            val chunk = transport.read(256, 80)
            if (chunk.isNotEmpty()) {
                noteRx(chunk)
                ascii.append(chunk.toString(Charsets.US_ASCII))
                if (ascii.length > 12_000) ascii.delete(0, ascii.length - 6_000)
                val hay = ascii.toString()
                if (hay.contains("\$GNRMC") || hay.contains("\$GNGGA") || hay.contains("\$command,")) return
                if (hay.contains("boot>")) break
            }
            delay(20)
        }
        notePhase("menu6")
        repeat(4) {
            transport.write("6\r\n".toByteArray(Charsets.US_ASCII))
            delay(40)
        }
        waitForAny(listOf("resetting the cpu", "\$GNRMC", "\$GNGGA", "\$command,"), 15_000L)
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
    private val softTrace = StringBuilder()

    private fun notePhase(tag: String) {
        if (softTrace.isNotEmpty()) softTrace.append(' ')
        softTrace.append(tag)
    }

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
        val detail = buildString {
            if (softTrace.isNotEmpty()) append(softTrace)
            val rx = rxTail.toString().trim()
            if (rx.isNotEmpty()) {
                if (isNotEmpty()) append(" | ")
                append(rx.take(140))
            }
        }.ifBlank { null }
        if (detail != null) Log.w(TAG, "UM980 FW rx tail: $detail")
        Um980FirmwareUiStore.finish(code, detail)
        return Result.failure(IllegalStateException(code))
    }

    companion object {
        private const val TAG = "Um980Fw"
        const val UPGRADE_BAUD = 460_800
        private const val BOOTLOADER_SWEEP_TIMEOUT_MS = 35_000L
        private const val BOOTLOADER_BAUD_SLICE_MS = 5_000L
        /** Settle after hot RESET during recover (module drops RAM baud, reboots app). */
        private const val RECOVER_RESET_SETTLE_MS = 2_500L
        /** Fallback only: second CONFIG block if the first 460800 probe stays silent. */
        private const val SOFT_CONFIG_REPEAT_GAP_MS = 50L
        /**
         * Quiet time on 460800 after reset, with no further keypresses.
         * Banner in the UPrecise capture is ~3.5s after the reset write and the menu
         * reprints ~2s later. Stopping at 3s (`no-reboot-text`) missed it; the next
         * CR/LF only got "The Board is reset, do not response any command".
         */
        private const val SOFT_QUIET_AFTER_RESET_MS = 20_000L
        private val UPGRADE_FLAG_PULSE = "T@".repeat(32).toByteArray(Charsets.US_ASCII)
        /** Listen for a menu that prints on its own, before sending a key. */
        private const val SOFT_MENU_QUIET_MS = 700L
        /** One N4 menu reprint period, plus a little slack. */
        private const val SOFT_MENU_SLICE_MS = 2_200L
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
