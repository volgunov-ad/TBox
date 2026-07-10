package vad.dashing.tbox

import android.content.ContentResolver
import android.provider.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class HeadUnitDayNightRepositoryTest {
    @Test
    fun readMode_mapsFourStatesFromSettings() {
        val context = RuntimeEnvironment.getApplication()
        val resolver: ContentResolver = context.contentResolver

        Settings.Global.putInt(resolver, HeadUnitDayNightRepository.NIGHT_MODE_AUTO_KEY, 0)
        assertEquals(HeadUnitDayNightRepository.Mode.LightManual, HeadUnitDayNightRepository.readMode(context))

        Settings.Global.putInt(resolver, HeadUnitDayNightRepository.NIGHT_MODE_AUTO_KEY, 2)
        assertEquals(HeadUnitDayNightRepository.Mode.DarkManual, HeadUnitDayNightRepository.readMode(context))

        Settings.Global.putInt(resolver, HeadUnitDayNightRepository.NIGHT_MODE_AUTO_KEY, 1)
        Settings.System.putInt(resolver, HeadUnitDayNightRepository.DAY_NIGHT_STATUS_KEY, 1)
        assertEquals(HeadUnitDayNightRepository.Mode.LightAuto, HeadUnitDayNightRepository.readMode(context))

        Settings.System.putInt(resolver, HeadUnitDayNightRepository.DAY_NIGHT_STATUS_KEY, 2)
        assertEquals(HeadUnitDayNightRepository.Mode.DarkAuto, HeadUnitDayNightRepository.readMode(context))
    }

    @Test
    fun cycleMode_rotatesThroughStockAutoValues() {
        val context = RuntimeEnvironment.getApplication()
        val resolver: ContentResolver = context.contentResolver

        Settings.Global.putInt(resolver, HeadUnitDayNightRepository.NIGHT_MODE_AUTO_KEY, 0)
        assertTrue(HeadUnitDayNightRepository.cycleMode(context))
        assertEquals(1, Settings.Global.getInt(resolver, HeadUnitDayNightRepository.NIGHT_MODE_AUTO_KEY))

        assertTrue(HeadUnitDayNightRepository.cycleMode(context))
        assertEquals(2, Settings.Global.getInt(resolver, HeadUnitDayNightRepository.NIGHT_MODE_AUTO_KEY))

        assertTrue(HeadUnitDayNightRepository.cycleMode(context))
        assertEquals(1, Settings.Global.getInt(resolver, HeadUnitDayNightRepository.NIGHT_MODE_AUTO_KEY))

        Settings.System.putInt(resolver, HeadUnitDayNightRepository.DAY_NIGHT_STATUS_KEY, 2)
        assertEquals(HeadUnitDayNightRepository.Mode.DarkAuto, HeadUnitDayNightRepository.readMode(context))

        assertTrue(HeadUnitDayNightRepository.cycleMode(context))
        assertEquals(0, Settings.Global.getInt(resolver, HeadUnitDayNightRepository.NIGHT_MODE_AUTO_KEY))
    }
}
