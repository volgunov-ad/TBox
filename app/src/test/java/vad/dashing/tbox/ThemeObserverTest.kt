package vad.dashing.tbox

import android.content.ContentResolver
import android.os.Looper
import android.provider.Settings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import vad.dashing.tbox.utils.ThemeObserver

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ThemeObserverTest {

    private val delivered = mutableListOf<Int>()

    @Before
    fun setUp() {
        HeadUnitDayNightMapping.usesAdayoKeysOverride = null
        HeadUnitDayNightRepository.resetAppDayNightControlForTests()
        delivered.clear()
    }

    @After
    fun tearDown() {
        HeadUnitDayNightMapping.usesAdayoKeysOverride = null
        HeadUnitDayNightRepository.resetAppDayNightControlForTests()
    }

    @Test
    fun startObserving_a9Keys_deliversLightByDefault() {
        val context = RuntimeEnvironment.getApplication()
        val observer = ThemeObserver(context) { delivered += it }

        observer.startObserving()
        idleMain()

        assertEquals(listOf(HeadUnitDayNightMapping.THEME_LIGHT), delivered)
        observer.stopObserving()
    }

    @Test
    fun deliverThemeMode_dedupsSameTheme() {
        HeadUnitDayNightMapping.usesAdayoKeysOverride = true
        val context = RuntimeEnvironment.getApplication()
        val resolver: ContentResolver = context.contentResolver
        Settings.Global.putInt(resolver, HeadUnitDayNightMapping.A10_SKIN_KEY, HeadUnitDayNightMapping.A10_SKIN_DAY)

        val observer = ThemeObserver(context) { delivered += it }
        observer.startObserving()
        idleMain()
        assertEquals(listOf(HeadUnitDayNightMapping.THEME_LIGHT), delivered)

        // Same skin again via ContentObserver path would also dedup; force via resync later.
        Settings.Global.putInt(resolver, HeadUnitDayNightMapping.A10_SKIN_KEY, HeadUnitDayNightMapping.A10_SKIN_DAY)
        observer.onChange(false)
        idleMain()
        idleMain(ThemeObserver.THEME_CHANGE_DEBOUNCE_MS)
        assertEquals(listOf(HeadUnitDayNightMapping.THEME_LIGHT), delivered)

        observer.stopObserving()
    }

    @Test
    fun resyncFromHeadUnit_bypassesDedup_andReadsAdayoSkin() {
        // Simulate cold-start race: first read used A9 keys (light), then mode/keys switch to A10
        // with dark adayo_skin — resync must deliver dark even if we clear and re-read.
        HeadUnitDayNightMapping.usesAdayoKeysOverride = false
        val context = RuntimeEnvironment.getApplication()
        val resolver: ContentResolver = context.contentResolver
        Settings.System.putInt(
            resolver,
            HeadUnitDayNightRepository.DAY_NIGHT_STATUS_KEY,
            HeadUnitDayNightMapping.THEME_LIGHT,
        )
        Settings.Global.putInt(
            resolver,
            HeadUnitDayNightRepository.NIGHT_MODE_AUTO_KEY,
            HeadUnitDayNightRepository.NIGHT_MODE_AUTO,
        )
        Settings.Global.putInt(resolver, HeadUnitDayNightMapping.A10_SKIN_KEY, HeadUnitDayNightMapping.A10_SKIN_NIGHT)

        val observer = ThemeObserver(context) { delivered += it }
        observer.startObserving()
        idleMain()
        assertEquals(listOf(HeadUnitDayNightMapping.THEME_LIGHT), delivered)

        HeadUnitDayNightMapping.usesAdayoKeysOverride = true
        observer.resyncFromHeadUnit()
        idleMain()

        assertEquals(
            listOf(HeadUnitDayNightMapping.THEME_LIGHT, HeadUnitDayNightMapping.THEME_DARK),
            delivered,
        )
        observer.stopObserving()
    }

    @Test
    fun resyncFromHeadUnit_whenAlreadyCorrectDark_redeliversAfterClearingDedup() {
        HeadUnitDayNightMapping.usesAdayoKeysOverride = true
        val context = RuntimeEnvironment.getApplication()
        val resolver: ContentResolver = context.contentResolver
        Settings.Global.putInt(resolver, HeadUnitDayNightMapping.A10_SKIN_KEY, HeadUnitDayNightMapping.A10_SKIN_NIGHT)

        val observer = ThemeObserver(context) { delivered += it }
        observer.startObserving()
        idleMain()
        assertEquals(listOf(HeadUnitDayNightMapping.THEME_DARK), delivered)

        observer.resyncFromHeadUnit()
        idleMain()
        assertEquals(
            listOf(HeadUnitDayNightMapping.THEME_DARK, HeadUnitDayNightMapping.THEME_DARK),
            delivered,
        )
        observer.stopObserving()
    }

    @Test
    fun resyncFromHeadUnit_noopWhenNotFollowingSystem() {
        HeadUnitDayNightMapping.usesAdayoKeysOverride = true
        val context = RuntimeEnvironment.getApplication()
        val resolver: ContentResolver = context.contentResolver
        Settings.Global.putInt(resolver, HeadUnitDayNightMapping.A10_SKIN_KEY, HeadUnitDayNightMapping.A10_SKIN_NIGHT)

        val observer = ThemeObserver(context) { delivered += it }
        observer.applyFollowMode(follow = false, manualTheme = HeadUnitDayNightMapping.THEME_LIGHT)
        idleMain()
        assertEquals(listOf(HeadUnitDayNightMapping.THEME_LIGHT), delivered)

        observer.resyncFromHeadUnit()
        idleMain()
        assertEquals(listOf(HeadUnitDayNightMapping.THEME_LIGHT), delivered)
        observer.stopObserving()
    }

    @Test
    fun onChange_a10_deliversDarkAfterDebounce() {
        HeadUnitDayNightMapping.usesAdayoKeysOverride = true
        val context = RuntimeEnvironment.getApplication()
        val resolver: ContentResolver = context.contentResolver
        Settings.Global.putInt(resolver, HeadUnitDayNightMapping.A10_SKIN_KEY, HeadUnitDayNightMapping.A10_SKIN_DAY)

        val observer = ThemeObserver(context) { delivered += it }
        observer.startObserving()
        idleMain()
        assertEquals(listOf(HeadUnitDayNightMapping.THEME_LIGHT), delivered)

        Settings.Global.putInt(resolver, HeadUnitDayNightMapping.A10_SKIN_KEY, HeadUnitDayNightMapping.A10_SKIN_NIGHT)
        observer.onChange(false)
        idleMain()
        assertEquals(listOf(HeadUnitDayNightMapping.THEME_LIGHT), delivered)
        idleMain(ThemeObserver.THEME_CHANGE_DEBOUNCE_MS)
        assertEquals(
            listOf(HeadUnitDayNightMapping.THEME_LIGHT, HeadUnitDayNightMapping.THEME_DARK),
            delivered,
        )
        observer.stopObserving()
    }

    private fun idleMain(advanceMs: Long = 0L) {
        val shadow = Shadows.shadowOf(Looper.getMainLooper())
        if (advanceMs > 0L) {
            shadow.idleFor(advanceMs, java.util.concurrent.TimeUnit.MILLISECONDS)
        } else {
            shadow.idle()
        }
    }
}
