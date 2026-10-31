package vad.dashing.tbox

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HuDspModeTest {
    @Test
    fun containsExactlyTheSupportedModes() {
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 250), HuDspMode.MODES)
        HuDspMode.MODES.forEach { mode ->
            assertEquals("service call adayo.setting.v2.0 86 i32 $mode", HuDspMode.writeCommand(mode))
            assertEquals(mode, HuDspMode.normalize(mode))
        }
    }

    @Test
    fun invalidValuesAreNeverSent() {
        listOf(-1, 9, 10, 11, 249, 251, Int.MAX_VALUE).forEach { mode ->
            assertTrue(runCatching { HuDspMode.writeCommand(mode) }.isFailure)
            assertEquals(1, HuDspMode.normalize(mode))
        }
        assertEquals(1, HuDspMode.normalize(null))
    }

    @Test
    fun parsesAcceptedAndRejectedReplies() {
        assertEquals(0, HuDspMode.parseStatus("Result: Parcel(00000000 00000000 '........')"))
        assertEquals(-1, HuDspMode.parseStatus("Result: Parcel(00000000 ffffffff '........')"))
        assertEquals(-3, HuDspMode.parseStatus("Result: Parcel(00000000 fffffffd '........')"))
        assertEquals(
            0,
            HuDspMode.parseStatus("Result: Parcel(\n  0x00000000: 00000000 00000000 '........')"),
        )
    }

    @Test
    fun rejectsMissingTruncatedOrExceptionReplies() {
        assertNull(HuDspMode.parseStatus("Service adayo.setting.v2.0 does not exist"))
        assertNull(HuDspMode.parseStatus("Result: Parcel(00000000)"))
        assertNull(HuDspMode.parseStatus("Result: Parcel(fffffffc 00000000 '........')"))
        assertNull(HuDspMode.parseStatus(""))
    }

    @Test
    fun startupRequiresExplicitOptInAndA10() {
        assertNull(HuDspMode.startupMode(false, HeadUnitCanMode.Android10Vhal, 2))
        assertNull(HuDspMode.startupMode(true, HeadUnitCanMode.Android9MbCan, 2))
        assertNull(HuDspMode.startupMode(true, HeadUnitCanMode.Android10Vhal, 9))
        assertNull(HuDspMode.startupMode(true, HeadUnitCanMode.Android10Vhal, 10))
        assertNull(HuDspMode.startupMode(true, HeadUnitCanMode.Android10Vhal, 11))
        HuDspMode.MODES.forEach { mode ->
            assertEquals(mode, HuDspMode.startupMode(true, HeadUnitCanMode.Android10Vhal, mode))
        }
    }

    @Test
    fun startupReappliesOnABoundedScheduleEvenAfterAcceptance() = runBlocking {
        var elapsed = 0L
        val attempts = mutableListOf<Long>()
        HuDspStartup.run(
            pause = { elapsed += it },
            apply = { attempts += elapsed },
        )
        assertEquals(listOf(0L, 10_000L, 30_000L, 60_000L, 120_000L), attempts)
    }

    @Test
    fun cancellationStopsFurtherStartupAttempts() = runBlocking {
        var attempts = 0
        val result = runCatching {
            HuDspStartup.run(
                pause = { throw CancellationException("disabled") },
                apply = { attempts++ },
            )
        }
        assertTrue(result.exceptionOrNull() is CancellationException)
        assertEquals(1, attempts)
    }
}
