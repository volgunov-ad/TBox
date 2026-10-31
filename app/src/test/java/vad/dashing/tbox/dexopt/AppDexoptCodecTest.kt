package vad.dashing.tbox.dexopt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppDexoptCodecTest {

    private val verifyDump = """
        Dexopt state:
          [vad.dashing.tbox]
            path: /data/app/vad.dashing.tbox-wIJU3qfagkHI5Ap2t0gHbg==/base.apk
              arm64: [status=verify] [reason=boot]
    """.trimIndent()

    @Test
    fun compileCommand_isDetachedSpeedCompile() {
        assertEquals(
            "trap '' HUP; nohup cmd package compile -m speed -f vad.dashing.tbox </dev/null >/dev/null 2>&1 &",
            AppDexoptCodec.compileCommand(" vad.dashing.tbox "),
        )
    }

    @Test
    fun statusCommand_grepsDexoptStatus() {
        assertEquals(
            "dumpsys package vad.dashing.tbox | grep 'status='",
            AppDexoptCodec.statusCommand("vad.dashing.tbox"),
        )
    }

    @Test
    fun parseFilters_readsEveryAbi() {
        val dump = "arm64: [status=speed] [reason=unknown]\narm: [status=speed-profile] [reason=bg-dexopt]"
        assertEquals(listOf("speed", "speed-profile"), AppDexoptCodec.parseFilters(dump))
    }

    @Test
    fun isFullyCompiled_falseForVerifyAndPartialProfile() {
        assertFalse(AppDexoptCodec.isFullyCompiled(verifyDump))
        assertFalse(AppDexoptCodec.isFullyCompiled("arm64: [status=speed-profile] [reason=install]"))
        assertFalse(AppDexoptCodec.isFullyCompiled("arm64: [status=speed]\narm: [status=quicken]"))
    }

    @Test
    fun isFullyCompiled_trueForSpeedOrEverything() {
        assertTrue(AppDexoptCodec.isFullyCompiled("      arm64: [status=speed] [reason=unknown]"))
        assertTrue(AppDexoptCodec.isFullyCompiled("arm64: [status=everything] [reason=unknown]"))
    }

    @Test
    fun compileCommand_usesRequestedFilter() {
        assertEquals(
            "trap '' HUP; nohup cmd package compile -m speed-profile -f ru.yandex.yandexnavi </dev/null >/dev/null 2>&1 &",
            AppDexoptCodec.compileCommand("ru.yandex.yandexnavi", AppDexoptCodec.FILTER_SPEED_PROFILE),
        )
    }

    @Test
    fun parseDexoptDump_primaryStatusPerPackage() {
        val dump = """
            Dexopt state:
              [com.android.cts.priv.ctsshim]
              [com.qualcomm.qti.qms.service.telemetry]
                path: /vendor/app/SSGTelemetryService/SSGTelemetryService.apk
                  arm: [status=quicken] [reason=first-boot]
              [vad.dashing.tbox]
                path: /data/app/vad.dashing.tbox-jY7DrbRfU-CM_sizdIewAw==/base.apk
                  arm64: [status=run-from-apk] [reason=unknown]
              [com.dual.abi]
                path: /data/app/com.dual.abi-1/base.apk
                  arm: [status=quicken] [reason=install]
                  arm64: [status=speed] [reason=unknown]
              [com.with.secondary]
                path: /data/app/com.with.secondary-1/base.apk
                  arm64: [status=speed-profile] [reason=install]
                known secondary dex files:
                  /data/user/0/com.with.secondary/files/plugin.dex
                    arm64: [status=run-from-apk] [reason=unknown]
        """.trimIndent()
        assertEquals(
            mapOf(
                "com.qualcomm.qti.qms.service.telemetry" to "quicken",
                "vad.dashing.tbox" to "run-from-apk",
                "com.dual.abi" to "speed",
                "com.with.secondary" to "speed-profile",
            ),
            AppDexoptCodec.parseDexoptDump(dump),
        )
    }

    @Test
    fun hasReachedFilter_matchesPrimaryStatus() {
        assertTrue(AppDexoptCodec.hasReachedFilter(verifyDump, AppDexoptCodec.FILTER_VERIFY))
        assertFalse(AppDexoptCodec.hasReachedFilter(verifyDump, AppDexoptCodec.FILTER_SPEED))
        assertTrue(
            AppDexoptCodec.hasReachedFilter("arm64: [status=everything]", AppDexoptCodec.FILTER_SPEED),
        )
        assertFalse(
            AppDexoptCodec.hasReachedFilter("arm64: [status=speed]", AppDexoptCodec.FILTER_SPEED_PROFILE),
        )
        assertFalse(AppDexoptCodec.hasReachedFilter("", AppDexoptCodec.FILTER_VERIFY))
    }

    @Test
    fun isFullyCompiled_falseWithoutStatusLines() {
        assertFalse(AppDexoptCodec.isFullyCompiled(""))
        assertFalse(AppDexoptCodec.isFullyCompiled("/system/bin/sh: dumpsys: not found"))
    }
}
