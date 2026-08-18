package vad.dashing.tbox

import android.content.ContentResolver
import android.provider.Settings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class HeadUnitDayNightRepositoryTest {

    @Before
    fun resetOverride() {
        HeadUnitDayNightMapping.usesAdayoKeysOverride = null
        HeadUnitDayNightRepository.resetAppDayNightControlForTests()
    }

    @After
    fun clearOverride() {
        HeadUnitDayNightMapping.usesAdayoKeysOverride = null
        HeadUnitDayNightRepository.resetAppDayNightControlForTests()
    }

    @Test
    fun modeFromA9_mapsFourStates() {
        assertEquals(
            HeadUnitDayNightRepository.Mode.LightManual,
            HeadUnitDayNightMapping.modeFromA9(0, 1),
        )
        assertEquals(
            HeadUnitDayNightRepository.Mode.DarkManual,
            HeadUnitDayNightMapping.modeFromA9(2, 1),
        )
        assertEquals(
            HeadUnitDayNightRepository.Mode.LightAuto,
            HeadUnitDayNightMapping.modeFromA9(1, 1),
        )
        assertEquals(
            HeadUnitDayNightRepository.Mode.DarkAuto,
            HeadUnitDayNightMapping.modeFromA9(1, 2),
        )
    }

    @Test
    fun modeFromA10_mapsAutoSkinAndSkin() {
        assertEquals(
            HeadUnitDayNightRepository.Mode.LightManual,
            HeadUnitDayNightMapping.modeFromA10(autoSkin = 0, skin = 1),
        )
        assertEquals(
            HeadUnitDayNightRepository.Mode.DarkManual,
            HeadUnitDayNightMapping.modeFromA10(autoSkin = 0, skin = 2),
        )
        assertEquals(
            HeadUnitDayNightRepository.Mode.LightAuto,
            HeadUnitDayNightMapping.modeFromA10(autoSkin = 1, skin = 1),
        )
        assertEquals(
            HeadUnitDayNightRepository.Mode.DarkAuto,
            HeadUnitDayNightMapping.modeFromA10(autoSkin = 1, skin = 2),
        )
        // Missing/0 skin → night default
        assertEquals(
            HeadUnitDayNightRepository.Mode.DarkManual,
            HeadUnitDayNightMapping.modeFromA10(autoSkin = 0, skin = 0),
        )
    }

    @Test
    fun effectiveTheme_a9AndA10() {
        assertEquals(1, HeadUnitDayNightMapping.effectiveThemeFromA9(0, 2))
        assertEquals(2, HeadUnitDayNightMapping.effectiveThemeFromA9(2, 1))
        assertEquals(1, HeadUnitDayNightMapping.effectiveThemeFromA9(1, 1))
        assertEquals(2, HeadUnitDayNightMapping.effectiveThemeFromA9(1, 2))

        assertEquals(1, HeadUnitDayNightMapping.effectiveThemeFromA10(1))
        assertEquals(2, HeadUnitDayNightMapping.effectiveThemeFromA10(2))
        assertEquals(2, HeadUnitDayNightMapping.effectiveThemeFromA10(0))
    }

    @Test
    fun readMode_a9_mapsFourStatesFromSettings() {
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
    fun toggleManualTheme_a9_switchesBetweenManualDayAndNight() {
        val context = RuntimeEnvironment.getApplication()
        val resolver: ContentResolver = context.contentResolver

        Settings.Global.putInt(resolver, HeadUnitDayNightRepository.NIGHT_MODE_AUTO_KEY, 0)
        assertTrue(HeadUnitDayNightRepository.toggleManualTheme(context))
        assertEquals(2, Settings.Global.getInt(resolver, HeadUnitDayNightRepository.NIGHT_MODE_AUTO_KEY))

        assertTrue(HeadUnitDayNightRepository.toggleManualTheme(context))
        assertEquals(0, Settings.Global.getInt(resolver, HeadUnitDayNightRepository.NIGHT_MODE_AUTO_KEY))

        Settings.Global.putInt(resolver, HeadUnitDayNightRepository.NIGHT_MODE_AUTO_KEY, 1)
        Settings.System.putInt(resolver, HeadUnitDayNightRepository.DAY_NIGHT_STATUS_KEY, 1)
        assertEquals(HeadUnitDayNightRepository.Mode.LightAuto, HeadUnitDayNightRepository.readMode(context))

        assertTrue(HeadUnitDayNightRepository.toggleManualTheme(context))
        assertEquals(2, Settings.Global.getInt(resolver, HeadUnitDayNightRepository.NIGHT_MODE_AUTO_KEY))

        Settings.Global.putInt(resolver, HeadUnitDayNightRepository.NIGHT_MODE_AUTO_KEY, 1)
        Settings.System.putInt(resolver, HeadUnitDayNightRepository.DAY_NIGHT_STATUS_KEY, 2)
        assertEquals(HeadUnitDayNightRepository.Mode.DarkAuto, HeadUnitDayNightRepository.readMode(context))

        assertTrue(HeadUnitDayNightRepository.toggleManualTheme(context))
        assertEquals(0, Settings.Global.getInt(resolver, HeadUnitDayNightRepository.NIGHT_MODE_AUTO_KEY))
    }

    @Test
    fun enableAutoMode_a9_setsStockAutoValue() {
        val context = RuntimeEnvironment.getApplication()
        val resolver: ContentResolver = context.contentResolver

        Settings.Global.putInt(resolver, HeadUnitDayNightRepository.NIGHT_MODE_AUTO_KEY, 0)
        assertTrue(HeadUnitDayNightRepository.enableAutoMode(context))
        assertEquals(1, Settings.Global.getInt(resolver, HeadUnitDayNightRepository.NIGHT_MODE_AUTO_KEY))

        Settings.Global.putInt(resolver, HeadUnitDayNightRepository.NIGHT_MODE_AUTO_KEY, 2)
        assertTrue(HeadUnitDayNightRepository.enableAutoMode(context))
        assertEquals(1, Settings.Global.getInt(resolver, HeadUnitDayNightRepository.NIGHT_MODE_AUTO_KEY))
    }

    @Test
    fun readMode_a10_mapsFourStatesFromSettings() {
        HeadUnitDayNightMapping.usesAdayoKeysOverride = true
        val context = RuntimeEnvironment.getApplication()
        val resolver: ContentResolver = context.contentResolver

        Settings.Global.putInt(resolver, HeadUnitDayNightMapping.A10_AUTO_SKIN_KEY, 0)
        Settings.Global.putInt(resolver, HeadUnitDayNightMapping.A10_SKIN_KEY, 1)
        assertEquals(HeadUnitDayNightRepository.Mode.LightManual, HeadUnitDayNightRepository.readMode(context))
        assertEquals(1, HeadUnitDayNightRepository.readEffectiveTheme(context))

        Settings.Global.putInt(resolver, HeadUnitDayNightMapping.A10_SKIN_KEY, 2)
        assertEquals(HeadUnitDayNightRepository.Mode.DarkManual, HeadUnitDayNightRepository.readMode(context))
        assertEquals(2, HeadUnitDayNightRepository.readEffectiveTheme(context))

        Settings.Global.putInt(resolver, HeadUnitDayNightMapping.A10_AUTO_SKIN_KEY, 1)
        Settings.Global.putInt(resolver, HeadUnitDayNightMapping.A10_SKIN_KEY, 1)
        assertEquals(HeadUnitDayNightRepository.Mode.LightAuto, HeadUnitDayNightRepository.readMode(context))

        Settings.Global.putInt(resolver, HeadUnitDayNightMapping.A10_SKIN_KEY, 2)
        assertEquals(HeadUnitDayNightRepository.Mode.DarkAuto, HeadUnitDayNightRepository.readMode(context))
    }

    @Test
    fun toggleManualTheme_a10_disablesAutoAndRequestsLauncherSkin() {
        HeadUnitDayNightMapping.usesAdayoKeysOverride = true
        val context = RuntimeEnvironment.getApplication()
        val resolver: ContentResolver = context.contentResolver
        val shadowApp = Shadows.shadowOf(context)

        Settings.Global.putInt(resolver, HeadUnitDayNightMapping.A10_AUTO_SKIN_KEY, 1)
        Settings.Global.putInt(resolver, HeadUnitDayNightMapping.A10_SKIN_KEY, 1)
        assertEquals(HeadUnitDayNightRepository.Mode.LightAuto, HeadUnitDayNightRepository.readMode(context))

        assertTrue(HeadUnitDayNightRepository.toggleManualTheme(context))
        assertEquals(0, Settings.Global.getInt(resolver, HeadUnitDayNightMapping.A10_AUTO_SKIN_KEY))

        val started = shadowApp.nextStartedService
        assertEquals(HeadUnitDayNightMapping.A10_SET_THEME_ACTION, started?.action)
        assertEquals(HeadUnitDayNightMapping.A10_LAUNCHER_PACKAGE, started?.`package`)
        assertEquals(2, started?.getIntExtra(HeadUnitDayNightMapping.A10_SET_THEME_EXTRA_SKIN, -1))
    }

    @Test
    fun enableAutoMode_a10_setsAutoSkinAndBroadcasts() {
        HeadUnitDayNightMapping.usesAdayoKeysOverride = true
        val context = RuntimeEnvironment.getApplication()
        val resolver: ContentResolver = context.contentResolver
        val shadowApp = Shadows.shadowOf(context)

        Settings.Global.putInt(resolver, HeadUnitDayNightMapping.A10_AUTO_SKIN_KEY, 0)
        Settings.Global.putInt(resolver, HeadUnitDayNightMapping.A10_SKIN_KEY, 2)

        assertTrue(HeadUnitDayNightRepository.enableAutoMode(context))
        assertEquals(1, Settings.Global.getInt(resolver, HeadUnitDayNightMapping.A10_AUTO_SKIN_KEY))

        val broadcast = shadowApp.broadcastIntents.lastOrNull {
            it.action == HeadUnitDayNightMapping.A10_AUTO_THEME_BROADCAST
        }
        assertEquals(HeadUnitDayNightMapping.A10_AUTO_THEME_BROADCAST, broadcast?.action)
        assertEquals(HeadUnitDayNightRepository.Mode.DarkAuto, HeadUnitDayNightRepository.readMode(context))
    }

    @Test
    fun writeAutoMode_a10_mapsLegacyA9ValuesToSetTheme() {
        HeadUnitDayNightMapping.usesAdayoKeysOverride = true
        val context = RuntimeEnvironment.getApplication()
        val resolver: ContentResolver = context.contentResolver

        assertTrue(HeadUnitDayNightRepository.writeAutoMode(context, HeadUnitDayNightRepository.NIGHT_MODE_LIGHT_MANUAL))
        assertEquals(0, Settings.Global.getInt(resolver, HeadUnitDayNightMapping.A10_AUTO_SKIN_KEY))

        val started = Shadows.shadowOf(context).nextStartedService
        assertEquals(HeadUnitDayNightMapping.A10_SET_THEME_ACTION, started?.action)
        assertEquals(1, started?.getIntExtra(HeadUnitDayNightMapping.A10_SET_THEME_EXTRA_SKIN, -1))
    }

    @Test
    fun detachedMode_toggleDoesNotWriteSettings_andEnableAutoReenablesFollow() {
        val context = RuntimeEnvironment.getApplication()
        val resolver: ContentResolver = context.contentResolver
        Settings.Global.putInt(resolver, HeadUnitDayNightRepository.NIGHT_MODE_AUTO_KEY, 0)

        var persistedFollow: Boolean? = null
        var persistedTheme: Int? = null
        var observerManual: Int? = null
        var observerFollow: Boolean? = null
        HeadUnitDayNightRepository.persistFollowSystem = { persistedFollow = it }
        HeadUnitDayNightRepository.persistAppLocalTheme = { persistedTheme = it }
        HeadUnitDayNightRepository.applyThemeObserverManualTheme = { observerManual = it }
        HeadUnitDayNightRepository.applyThemeObserverFollowMode = { follow, _ -> observerFollow = follow }

        HeadUnitDayNightRepository.syncFromPersisted(followSystem = false, appLocalTheme = 1)
        assertEquals(HeadUnitDayNightRepository.Mode.LightManual, HeadUnitDayNightRepository.readMode(context))
        assertEquals(1, HeadUnitDayNightRepository.readEffectiveTheme(context))

        assertTrue(HeadUnitDayNightRepository.toggleManualTheme(context))
        assertEquals(2, HeadUnitDayNightRepository.appLocalTheme())
        assertEquals(2, persistedTheme)
        assertEquals(2, observerManual)
        assertEquals(HeadUnitDayNightRepository.Mode.DarkManual, HeadUnitDayNightRepository.readMode(context))
        // HU Settings untouched
        assertEquals(0, Settings.Global.getInt(resolver, HeadUnitDayNightRepository.NIGHT_MODE_AUTO_KEY))

        assertTrue(HeadUnitDayNightRepository.enableAutoMode(context))
        assertTrue(HeadUnitDayNightRepository.isFollowSystem())
        assertEquals(true, persistedFollow)
        assertEquals(true, observerFollow)
    }
}
