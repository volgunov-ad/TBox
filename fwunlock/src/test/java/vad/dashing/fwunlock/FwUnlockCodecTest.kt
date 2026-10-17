package vad.dashing.fwunlock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FwUnlockCodecTest {
    private val STOCK = FwUnlockCodec.STOCK_CONFIG

    private val APPLIED = FwUnlockCodec.buildApplyConfig(
        STOCK,
        listOf("vad.dashing.tbox", "com.example.browser"),
    )!!

    @Test
    fun stockConfigMatchesDeviceBytes() {
        assertEquals(206, STOCK.toByteArray(Charsets.UTF_8).size)
        assertTrue(STOCK.startsWith("{\r\n\t\"APPs\":\r\n\t[\r\n\t],"))
        assertTrue(STOCK.contains("\t/**\r\n\thost: host name\r\n"))
        assertTrue(STOCK.endsWith("\"Hosts\":\r\n    [\r\n\t]\r\n}\r\n"))
        assertTrue(FwUnlockCodec.isStock(STOCK))
    }

    @Test
    fun parsePackagesReadsEntriesAndIgnoresComment() {
        val content = """
            {
              "APPs": [
                {"packageName": "com.oem.first"},
                {"packageName" : "vad.dashing.tbox"}
              ],
              /** decoy inside a comment: {"packageName": "all"} must not be parsed */
              "Hosts": []
            }
        """.trimIndent()
        assertEquals(listOf("com.oem.first", "vad.dashing.tbox"), FwUnlockCodec.parsePackages(content))
        assertTrue(FwUnlockCodec.isStock(STOCK))
        assertFalse(FwUnlockCodec.isStock(content))
    }

    @Test
    fun buildApplyConfigWhitelistsAndStaysIdempotent() {
        assertTrue(APPLIED.contains("\t\t{\"packageName\": \"vad.dashing.tbox\"},"))
        assertTrue(APPLIED.contains("\t\t{\"packageName\": \"com.example.browser\"}"))
        assertEquals(
            listOf("vad.dashing.tbox", "com.example.browser"),
            FwUnlockCodec.parsePackages(APPLIED),
        )
        assertEquals(APPLIED, FwUnlockCodec.buildApplyConfig(APPLIED, listOf("com.example.browser", "vad.dashing.tbox")))
    }

    @Test
    fun buildApplyConfigPreservesOemEntriesAndMergesNew() {
        val withOem = """
            {"APPs": [{"packageName": "com.oem.app"}], "Hosts": []}
        """.trimIndent()
        val merged = FwUnlockCodec.buildApplyConfig(withOem, listOf("vad.dashing.tbox"))!!
        assertEquals(listOf("com.oem.app", "vad.dashing.tbox"), FwUnlockCodec.parsePackages(merged))
    }

    @Test
    fun buildApplyConfigRejectsInvalidNamesAndEmptyMerge() {
        assertNull(FwUnlockCodec.buildApplyConfig(STOCK, listOf("all")))
        assertNull(FwUnlockCodec.buildApplyConfig(STOCK, listOf("vad.dashing.tbox", "not a name!")))
        assertNull(FwUnlockCodec.buildApplyConfig(STOCK, listOf("singleword")))
        assertNull(FwUnlockCodec.buildApplyConfig(STOCK, emptyList()))
    }

    @Test
    fun applyScriptPerformsWriteProtectDanceAndRestartsCarService() {
        val script = FwUnlockCodec.applyScript()
        assertTrue(script.contains("echo emmc_disable_all_wp >"))
        assertTrue(script.contains("wp_on() { echo emmc_enable_all_wp >"))
        assertTrue(script.contains("trap 'wp_on' EXIT"))
        assertTrue(script.contains("mount -o remount,rw / || fail remountsys"))
        assertTrue(script.contains("S=${FwUnlockCodec.CONFIG_PATH}"))
        assertTrue(script.contains("chcon u:object_r:system_file:s0"))
        assertTrue(script.contains("echo 3 > /proc/sys/vm/drop_caches"))
        assertTrue(script.contains("[ \"${'$'}H1\" = \"${'$'}H2\" ] || fail verify"))
        assertTrue(script.contains("kill \$(pidof com.android.car) || fail carpid"))
        assertTrue(script.contains("[ -n \"\$(pidof com.android.car)\" ] || fail carback"))
        assertTrue(script.contains(FwUnlockCodec.APPLIED_MARKER))
        assertFalse(script.contains(FwUnlockCodec.RESTORED_MARKER))
    }

    @Test
    fun restoreScriptWritesStockConfigOnly() {
        val script = FwUnlockCodec.restoreScript()
        assertTrue(script.contains("trap 'wp_on' EXIT"))
        assertTrue(script.contains("[ \"${'$'}H1\" = \"${'$'}H2\" ] || fail verify"))
        assertTrue(script.contains("kill \$(pidof com.android.car) || fail carpid"))
        assertTrue(script.contains(FwUnlockCodec.RESTORED_MARKER))
        assertFalse(script.contains(FwUnlockCodec.APPLIED_MARKER))
        assertFalse(script.contains("rm -f"))
    }

    @Test
    fun parsesOutcomeMarkers() {
        assertEquals(FwUnlockCodec.Outcome.Applied, FwUnlockCodec.parseOutcome("x\n${FwUnlockCodec.APPLIED_MARKER}"))
        assertEquals(FwUnlockCodec.Outcome.Restored, FwUnlockCodec.parseOutcome(FwUnlockCodec.RESTORED_MARKER))
        assertEquals(
            FwUnlockCodec.Outcome.Failed("wpoff"),
            FwUnlockCodec.parseOutcome("${FwUnlockCodec.ERR_MARKER} wpoff"),
        )
        assertEquals(FwUnlockCodec.Outcome.Failed("failed"), FwUnlockCodec.parseOutcome(""))
    }
}
