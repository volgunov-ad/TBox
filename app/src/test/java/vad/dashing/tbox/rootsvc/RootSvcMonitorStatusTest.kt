package vad.dashing.tbox.rootsvc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vad.dashing.rootsvc.client.RootSvcResult

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RootSvcMonitorStatusTest {
    @Test
    fun parseStatusResult_readsVersionUptimeCrashloop() {
        val raw =
            """{"id":"a1","ok":true,"exit":0,"ms":0,"server":"1.0.0","proto":1,"uptimeMs":65000,"state":"crashloop","crashloop":true}"""
        val parsed = RootSvcMonitor.parseStatusResult(
            RootSvcResult.ok("a1", 0, null, null, 0L, raw),
            installed = true,
            pkgVer = "1.0.0",
        )
        assertTrue(parsed.up)
        assertEquals("1.0.0", parsed.serverVersion)
        assertEquals(1, parsed.proto)
        assertEquals(65_000L, parsed.uptimeMs)
        assertEquals("crashloop", parsed.state)
        assertTrue(parsed.crashloop)
        assertEquals("1.0.0", parsed.packageVersion)
    }

    @Test
    fun parseStatusResult_emptyStateIsNotCrashloop() {
        val raw =
            """{"id":"b2","ok":true,"exit":0,"ms":0,"server":"1.0.0","proto":1,"uptimeMs":1200,"state":"","crashloop":false}"""
        val parsed = RootSvcMonitor.parseStatusResult(
            RootSvcResult.ok("b2", 0, null, null, 0L, raw),
            installed = true,
            pkgVer = "0.1.0",
        )
        assertTrue(parsed.up)
        assertFalse(parsed.crashloop)
        assertEquals(1_200L, parsed.uptimeMs)
        assertNull(parsed.state)
    }
}
