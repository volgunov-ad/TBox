package vad.dashing.winpatch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.winpatch.WinPatchCodec.FileState

class WinPatchCodecTest {

    @Test
    fun md5ConstantsAreLowercaseHexAndDistinct() {
        for (t in WinPatchCodec.TARGETS) {
            assertTrue(Regex("^[0-9a-f]{32}$").matches(t.stockMd5))
            assertTrue(Regex("^[0-9a-f]{32}$").matches(t.patchedMd5))
            assertTrue(t.stockMd5 != t.patchedMd5)
        }
    }

    @Test
    fun targetsPointAtExpectedSystemPathsAndProcesses() {
        assertEquals("/system/app/AndroidAuto/AndroidAuto.apk", WinPatchCodec.ANDROID_AUTO.systemPath)
        assertEquals("/system/app/CarPlayView/CarPlayView.apk", WinPatchCodec.CAR_PLAY.systemPath)
        assertEquals("com.adayo.androidauto", WinPatchCodec.ANDROID_AUTO.processName)
        assertEquals("com.adayo.carplay.view", WinPatchCodec.CAR_PLAY.processName)
        assertEquals("/data/local/tmp/AndroidAuto_orig_backup.apk", WinPatchCodec.ANDROID_AUTO.legacyBackupPath)
        assertEquals("/data/local/tmp/CarPlayView_orig_backup.apk", WinPatchCodec.CAR_PLAY.legacyBackupPath)
    }

    @Test
    fun fileStateMapsKnownAndUnknown() {
        val t = WinPatchCodec.ANDROID_AUTO
        assertEquals(FileState.Missing, WinPatchCodec.fileState(null, t))
        assertEquals(FileState.Stock, WinPatchCodec.fileState(t.stockMd5, t))
        assertEquals(FileState.Stock, WinPatchCodec.fileState(t.stockMd5.uppercase(), t))
        assertEquals(FileState.Patched, WinPatchCodec.fileState(t.patchedMd5, t))
        assertEquals(FileState.Unknown, WinPatchCodec.fileState("0123456789abcdef0123456789abcdef", t))
    }

    @Test
    fun parseMd5MapReadsMd5sumOutput() {
        val output = """
            abcdef0123456789abcdef0123456789  /system/app/AndroidAuto/AndroidAuto.apk
            821cf2db51435747bd974a81fd52d1bb  /data/local/tmp/winpatch/AndroidAuto.orig.apk
            md5sum: /x: No such file or directory
        """.trimIndent()
        val map = WinPatchCodec.parseMd5Map(output)
        assertEquals("abcdef0123456789abcdef0123456789", map["/system/app/AndroidAuto/AndroidAuto.apk"])
        assertEquals("821cf2db51435747bd974a81fd52d1bb", map["/data/local/tmp/winpatch/AndroidAuto.orig.apk"])
        assertEquals(2, map.size)
    }

    @Test
    fun applyScriptPreflightsBeforeWriteProtectDance() {
        val s = WinPatchCodec.applyScript(
            "/storage/emulated/0/Android/data/vad.dashing.winpatch/files/AndroidAuto_p5.apk",
            "/storage/emulated/0/Android/data/vad.dashing.winpatch/files/CarPlayView_p3.apk",
        )
        assertTrue(s.contains("fail nostage_aa"))
        assertTrue(s.contains("fail nostage_cp"))
        val preflight = s.indexOf("nostage_aa")
        val wpOff = s.indexOf("emmc_disable_all_wp")
        assertTrue(preflight >= 0 && preflight < wpOff)
        assertTrue(s.contains("ensure_backup ${WinPatchCodec.ANDROID_AUTO.systemPath}"))
        assertTrue(s.contains("ensure_backup ${WinPatchCodec.CAR_PLAY.systemPath}"))
        assertTrue(s.contains(WinPatchCodec.ANDROID_AUTO.legacyBackupPath))
        assertTrue(s.contains(WinPatchCodec.CAR_PLAY.legacyBackupPath))
        assertTrue(s.contains("badbackup_${'$'}NAME"))
        assertTrue(s.contains("nobackup_${'$'}NAME"))
        assertFalse(s.contains("TBOX_FW"))
    }

    @Test
    fun applyScriptWritesBothTargetsAndRestartsProcesses() {
        val s = WinPatchCodec.applyScript("/stage/aa.apk", "/stage/cp.apk")
        assertTrue(s.contains("wp_on() { echo emmc_enable_all_wp >"))
        assertTrue(s.contains("trap 'wp_on' EXIT"))
        assertTrue(s.contains("mount -o remount,rw / || fail remountsys"))
        assertTrue(
            s.contains(
                "write_one ${WinPatchCodec.ANDROID_AUTO.systemPath} \"${'$'}AA_STAGE\" ${WinPatchCodec.ANDROID_AUTO.patchedMd5} aa",
            ),
        )
        assertTrue(
            s.contains(
                "write_one ${WinPatchCodec.CAR_PLAY.systemPath} \"${'$'}CP_STAGE\" ${WinPatchCodec.CAR_PLAY.patchedMd5} cp",
            ),
        )
        assertTrue(s.contains("chcon u:object_r:system_file:s0"))
        assertTrue(s.contains("chmod 644"))
        assertTrue(s.contains("echo 3 > /proc/sys/vm/drop_caches"))
        assertTrue(s.contains("kill \$(pidof com.adayo.androidauto) 2>/dev/null"))
        assertTrue(s.contains("kill \$(pidof com.adayo.carplay.view) 2>/dev/null"))
        assertTrue(s.contains(WinPatchCodec.APPLIED_MARKER))
        assertFalse(s.contains(WinPatchCodec.RESTORED_MARKER))
    }

    @Test
    fun restoreScriptUsesBackupsOnly() {
        val s = WinPatchCodec.restoreScript()
        assertTrue(s.contains("fail nobackup_aa"))
        assertTrue(s.contains("fail nobackup_cp"))
        assertTrue(
            s.contains(
                "write_one ${WinPatchCodec.ANDROID_AUTO.systemPath} \"${'$'}AA_BAK\" ${WinPatchCodec.ANDROID_AUTO.stockMd5} aa",
            ),
        )
        assertTrue(s.contains(WinPatchCodec.CAR_PLAY.stockMd5))
        assertTrue(s.contains(WinPatchCodec.RESTORED_MARKER))
        assertFalse(s.contains(WinPatchCodec.APPLIED_MARKER))
        assertFalse(s.contains("nostage"))
        assertFalse(s.contains("AA_STAGE"))
        assertFalse(s.contains("CP_STAGE"))
    }

    @Test
    fun parsesOutcomeMarkers() {
        assertEquals(WinPatchCodec.Outcome.Applied, WinPatchCodec.parseOutcome("x\n${WinPatchCodec.APPLIED_MARKER}"))
        assertEquals(WinPatchCodec.Outcome.Restored, WinPatchCodec.parseOutcome(WinPatchCodec.RESTORED_MARKER))
        assertEquals(WinPatchCodec.Outcome.Failed("verify_aa"), WinPatchCodec.parseOutcome("${WinPatchCodec.ERR_MARKER} verify_aa"))
        assertEquals(WinPatchCodec.Outcome.Failed("failed"), WinPatchCodec.parseOutcome(""))
    }
}
