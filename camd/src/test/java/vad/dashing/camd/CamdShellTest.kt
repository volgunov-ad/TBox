package vad.dashing.camd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit

class CamdShellTest {

    private class FakeProcess(
        private val output: String,
        private val exitCode: Int,
        private val hang: Boolean = false,
    ) : Process() {
        var destroyed = false
            private set

        override fun getOutputStream(): OutputStream = object : OutputStream() {
            override fun write(b: Int) = Unit
        }

        override fun getInputStream(): InputStream = ByteArrayInputStream(output.toByteArray())
        override fun getErrorStream(): InputStream = ByteArrayInputStream(ByteArray(0))
        override fun waitFor(): Int = exitCode
        override fun waitFor(timeout: Long, unit: TimeUnit): Boolean = !hang
        override fun exitValue(): Int = exitCode
        override fun destroy() {
            destroyed = true
        }

        override fun destroyForcibly(): Process = this
        override fun isAlive(): Boolean = !destroyed && hang
    }

    @Test
    fun returnsTrimmedOutputOnSuccess() {
        val process = FakeProcess("  ok line\n", 0)
        val text = CamdShell.exec(arrayOf("true")) { process }
        assertEquals("ok line", text)
    }

    @Test
    fun timesOutAndDestroysAHangingChild() {
        val process = FakeProcess("", 0, hang = true)
        try {
            CamdShell.exec(arrayOf("hang"), timeoutMs = 120) { process }
            fail("timeout expected")
        } catch (expected: CamdShell.CommandException) {
            assertTrue(expected.message!!.contains("timeout"))
            assertTrue(process.destroyed)
        }
    }

    @Test
    fun reportsNonZeroExitWithOutput() {
        val process = FakeProcess("boom details", 3)
        try {
            CamdShell.exec(arrayOf("failing")) { process }
            fail("failure expected")
        } catch (expected: CamdShell.CommandException) {
            assertTrue(expected.message!!.contains("failing завершился 3"))
            assertTrue(expected.message!!.contains("boom details"))
        }
    }

    @Test
    fun wrapsStartFailures() {
        try {
            CamdShell.exec(arrayOf("nope")) { throw IOException("device missing") }
            fail("failure expected")
        } catch (expected: CamdShell.CommandException) {
            assertTrue(expected.message!!.contains("start nope"))
            assertTrue(expected.message!!.contains("device missing"))
        }
    }
}
