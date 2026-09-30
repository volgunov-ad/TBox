package vad.dashing.tbox.um980fw

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedQueue

class Um980PkgValidatorTest {
    @Test
    fun acceptsKnownMagic() {
        val magic = byteArrayOf(0xA5.toByte(), 0xA4.toByte(), 0xA3.toByte(), 0xA2.toByte())
        assertNull(Um980PkgValidator.validate(3004096L, magic))
    }

    @Test
    fun rejectsBadMagic() {
        assertEquals("bad_magic", Um980PkgValidator.validate(100_000L, byteArrayOf(1, 2, 3, 4)))
    }

    @Test
    fun buildFromFileName() {
        assertEquals(25102, Um980PkgValidator.buildFromFileName("UM980_R4.10Build25102.pkg"))
    }

    @Test
    fun buildFromVersionA() {
        val line =
            "#VERSIONA,97,GPS,FINE,2430,313482500,25102,0,18,229;" +
                "\"UM980\",\"R4.10Build25102\",\"HRPT00-S10C-P\"*1ad470b2"
        assertEquals(25102, Um980PkgValidator.buildFromVersionA(line))
    }
}

class Xmodem1kTest {
    @Test
    fun checksumFrameLayout() {
        val payload = byteArrayOf(0xA5.toByte(), 0xA4.toByte(), 0xA3.toByte(), 0xA2.toByte())
        val frame = Xmodem1k.buildBlock(1, payload, Xmodem1k.CheckMode.CHECKSUM)
        assertEquals(Xmodem1k.STX, frame[0])
        assertEquals(1.toByte(), frame[1])
        assertEquals(0xFE.toByte(), frame[2])
        assertEquals(payload[0], frame[3])
        assertEquals(3 + 1024 + 1, frame.size)
        val data = frame.copyOfRange(3, 3 + 1024)
        assertEquals(Xmodem1k.checksum(data).toByte(), frame.last())
    }

    @Test
    fun crc16KnownVector() {
        // Empty 1024 zeros → CRC 0
        val zeros = ByteArray(1024)
        assertEquals(0, Xmodem1k.crc16(zeros))
        val frame = Xmodem1k.buildBlock(1, ByteArray(0), Xmodem1k.CheckMode.CRC16)
        assertEquals(3 + 1024 + 2, frame.size)
        assertTrue(frame[0] == Xmodem1k.STX)
    }
}

class Um980FwBootloaderBaudSweepTest {
    @Test
    fun candidatesPreferCurrentThenUpgradeThenPre() {
        val list = Um980FirmwareUpdater.bootloaderBaudCandidates(
            preBaud = 115_200,
            currentBaud = Um980FirmwareUpdater.UPGRADE_BAUD,
        )
        assertEquals(Um980FirmwareUpdater.UPGRADE_BAUD, list.first())
        assertTrue(list.contains(115_200))
        assertTrue(list.indexOf(115_200) > 0)
        assertEquals(list.size, list.toSet().size)
    }

    @Test
    fun candidatesDedupWhenPreEqualsUpgrade() {
        val list = Um980FirmwareUpdater.bootloaderBaudCandidates(
            preBaud = Um980FirmwareUpdater.UPGRADE_BAUD,
            currentBaud = Um980FirmwareUpdater.UPGRADE_BAUD,
        )
        assertEquals(1, list.count { it == Um980FirmwareUpdater.UPGRADE_BAUD })
    }

    @Test
    fun isBootloaderBannerRecognizesNeedles() {
        assertTrue(Um980FirmwareUpdater.isBootloaderBanner("BootLoader"))
        assertTrue(Um980FirmwareUpdater.isBootloaderBanner("boot>"))
        assertFalse(Um980FirmwareUpdater.isBootloaderBanner("rebooting"))
    }

    @Test
    fun waitForBootloaderSweepsToSavedBaud() = runBlocking {
        val transport = FakeUm980BinaryTransport(initialBaud = Um980FirmwareUpdater.UPGRADE_BAUD)
        // Banner after host matches saved baud and nudges with CR/LF (post-drain).
        transport.onWrite = { bytes ->
            if (transport.currentBaud() == 115_200 && bytes.contentEquals("\r\n".toByteArray())) {
                transport.enqueueAscii("N4 BootLoader\r\nboot>\r\n")
            }
        }
        val updater = Um980FirmwareUpdater(transport)
        assertTrue(updater.waitForBootloaderBanner(preBaud = 115_200, overallTimeoutMs = 8_000L))
        assertEquals(115_200, transport.currentBaud())
    }

    @Test
    fun softFallbackFindsBootloaderWhen460800Silent() = runBlocking {
        // Companion-style: setBaud/reopen both just change baud; module answers at 460800.
        val transport = FakeUm980BinaryTransport(initialBaud = 115_200)
        var configBatches = 0
        var resetBursts = 0
        transport.onWrite = { bytes ->
            val s = bytes.toString(Charsets.US_ASCII)
            if (s.contains("config com1") && s.contains("config com2") && s.contains("config com3")) {
                configBatches++
                assertTrue(
                    "CONFIG must be one batch write",
                    s.indexOf("config com1") >= 0 &&
                        s.indexOf("config com2") > s.indexOf("config com1") &&
                        s.indexOf("config com3") > s.indexOf("config com2"),
                )
            }
            if (s.contains("reset\r\nreset")) resetBursts++
            when {
                s.contains("unlog", ignoreCase = true) &&
                    (transport.currentBaud() == 115_200 || transport.currentBaud() == 460_800) ->
                    transport.enqueueAscii("\$command,unlog,response: OK*21\r\n")
                transport.currentBaud() == 460_800 && s.contains("reset", ignoreCase = true) ->
                    transport.enqueueAscii("system is rebooting\r\n\r\nN4 BootLoader 2020.04\r\nboot> ")
                transport.currentBaud() == 460_800 && bytes.contentEquals("\r\n".toByteArray()) ->
                    transport.enqueueAscii("boot> ")
            }
        }
        val updater = Um980FirmwareUpdater(transport)
        assertTrue(updater.enterBootloaderSoft(preBaud = 115_200))
        assertEquals(460_800, transport.currentBaud())
        assertEquals("UPrecise sends CONFIG block twice", 2, configBatches)
        assertEquals("UPrecise sends double-reset twice", 2, resetBursts)
    }

    @Test
    fun softPrefersBootloaderNeedleWhenSameChunkHasRebooting() = runBlocking {
        val transport = FakeUm980BinaryTransport(initialBaud = 115_200)
        transport.onWrite = { bytes ->
            val s = bytes.toString(Charsets.US_ASCII)
            when {
                s.contains("unlog", ignoreCase = true) &&
                    (transport.currentBaud() == 115_200 || transport.currentBaud() == 460_800) ->
                    transport.enqueueAscii("\$command,unlog,response: OK*21\r\n")
                // Single chunk: rebooting + BootLoader (old needle order discarded BootLoader).
                transport.currentBaud() == 460_800 && s.contains("reset", ignoreCase = true) ->
                    transport.enqueueAscii("system is rebooting\r\nN4 BootLoader 2020.04\r\nboot> ")
            }
        }
        val updater = Um980FirmwareUpdater(transport)
        assertTrue(updater.enterBootloaderSoft(preBaud = 115_200))
    }

    @Test
    fun softAbortsWithoutResetIf460800LinkDeadAndRestores() = runBlocking {
        val transport = FakeUm980BinaryTransport(initialBaud = 115_200)
        var sawDoubleReset = false
        var sawHotReset = false
        transport.onWrite = { bytes ->
            val s = bytes.toString(Charsets.US_ASCII)
            if (s.contains("reset\r\nreset", ignoreCase = true)) sawDoubleReset = true
            if (s.trim().equals("RESET", ignoreCase = true) ||
                s.equals("RESET\r\n", ignoreCase = true)
            ) {
                sawHotReset = true
            }
            // Answer only at 115200 so recoverLink can restore; silence at 460800.
            if (transport.currentBaud() == 115_200 && s.contains("unlog", ignoreCase = true)) {
                transport.enqueueAscii("\$command,unlog,response: OK*21\r\n")
            }
        }
        val updater = Um980FirmwareUpdater(transport)
        assertFalse(updater.enterBootloaderSoft(preBaud = 115_200))
        assertFalse("must not reset until the app answers at 460800", sawDoubleReset)
        assertTrue("recover must hot-RESET like GNSS reboot UI", sawHotReset)
        assertTrue(transport.reopenAtBaudCalls.contains(115_200))
        assertEquals(115_200, transport.currentBaud())
    }

    @Test
    fun discoverUsesBaudThatAnswersWhenPreferredIsSilent() = runBlocking {
        val transport = FakeUm980BinaryTransport(initialBaud = 115_200)
        transport.onWrite = { bytes ->
            val s = bytes.toString(Charsets.US_ASCII)
            if (transport.currentBaud() == 460_800 && s.contains("unlog", ignoreCase = true)) {
                transport.enqueueAscii("\$command,unlog,response: OK*21\r\n")
            }
        }
        val updater = Um980FirmwareUpdater(transport)
        assertEquals(460_800, updater.discoverAppBaud(preferred = 115_200))
    }

    @Test
    fun recoverLinkReopensAndSendsHotReset() = runBlocking {
        val transport = FakeUm980BinaryTransport(initialBaud = 460_800)
        var sawHotReset = false
        var sawSaveConfig = false
        transport.onWrite = { bytes ->
            val s = bytes.toString(Charsets.US_ASCII)
            if (s.equals("RESET\r\n", ignoreCase = true)) sawHotReset = true
            if (s.contains("SAVECONFIG", ignoreCase = true)) sawSaveConfig = true
            if (transport.currentBaud() == 460_800 && s.contains("unlog", ignoreCase = true)) {
                transport.enqueueAscii("\$command,unlog,response: OK*21\r\n")
            }
            if (transport.currentBaud() == 115_200 && s.contains("unlog", ignoreCase = true)) {
                transport.enqueueAscii("\$command,unlog,response: OK*21\r\n")
            }
        }
        val updater = Um980FirmwareUpdater(transport)
        updater.recoverLinkBestEffort(preBaud = 115_200)
        assertTrue(sawHotReset)
        assertTrue(sawSaveConfig)
        assertTrue(transport.reopenAtBaudCalls.isNotEmpty())
        assertEquals(115_200, transport.currentBaud())
    }
}

/**
 * In-memory UART pipe for FW updater unit tests.
 * [read] sleeps in small slices so coroutine [delay] in the updater can progress under runBlocking.
 */
private class FakeUm980BinaryTransport(
    initialBaud: Int,
) : Um980BinaryTransport {
    private var baud: Int = initialBaud
    private val rx = ConcurrentLinkedQueue<Byte>()
    var onWrite: ((ByteArray) -> Unit)? = null
    val reopenAtBaudCalls = mutableListOf<Int>()
    var pulseHardwareResetResult = false

    fun enqueueAscii(text: String) {
        for (b in text.toByteArray(Charsets.US_ASCII)) {
            rx.offer(b)
        }
    }

    override fun currentBaud(): Int = baud

    override fun setBaud(baud: Int): Boolean {
        this.baud = baud
        return true
    }

    override fun reopenAtBaud(baud: Int): Boolean {
        reopenAtBaudCalls.add(baud)
        this.baud = baud
        return true
    }

    override fun pulseHardwareReset(): Boolean = pulseHardwareResetResult

    override fun write(bytes: ByteArray): Boolean {
        onWrite?.invoke(bytes)
        return true
    }

    override fun read(maxBytes: Int, timeoutMs: Long): ByteArray {
        val deadline = System.currentTimeMillis() + timeoutMs.coerceAtLeast(0L)
        val out = ArrayList<Byte>(maxBytes.coerceAtMost(256))
        while (out.size < maxBytes) {
            val next = rx.poll()
            if (next != null) {
                out.add(next)
                continue
            }
            if (System.currentTimeMillis() >= deadline) break
            try {
                Thread.sleep(10)
            } catch (_: InterruptedException) {
                break
            }
        }
        return ByteArray(out.size) { out[it] }
    }

    override fun beginExclusive() = Unit

    override fun endExclusive() = Unit
}
