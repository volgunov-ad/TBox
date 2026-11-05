package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UsageStatsStickyHideTest {

    private val maps = "ru.yandex.yandexnavi"
    private val settings = "com.android.settings"
    private val hideWatch = setOf(maps, OemOverlayAppMonitor.AVM_PACKAGE)

    private fun next(
        previous: UsageStatsStickyHide.State = UsageStatsStickyHide.State.EMPTY,
        focused: String? = null,
        activity: UsageStatsStickyHide.PackageActivitySignal =
            UsageStatsStickyHide.PackageActivitySignal.ABSENT,
        now: Long = 1_000L,
        ttl: Long = UsageStatsStickyHide.SAFETY_TTL_MS,
        overlay: String? = null,
        watch: Set<String> = hideWatch,
    ) = UsageStatsStickyHide.next(
        previous = previous,
        focusedPackage = focused,
        hideWatch = watch,
        stickyPackageActivity = activity,
        nowElapsedMs = now,
        safetyTtlMs = ttl,
        oemOverlayPackage = overlay,
    )

    @Test
    fun enter_whenFocusedIsHideWatched() {
        val s = next(focused = maps, now = 5_000L)
        assertEquals(maps, s.stickyHidePackage)
        assertEquals(5_000L, s.lastSeenElapsedMs)
    }

    @Test
    fun keep_whenOtherNonWatchedAppBecomesFocused_dualVd() {
        val entered = next(focused = maps, now = 1_000L)
        // Settings resumes on VD2; Maps still painted on VD1 — no leave signal yet.
        val kept = next(
            previous = entered,
            focused = settings,
            activity = UsageStatsStickyHide.PackageActivitySignal.ABSENT,
            now = 3_000L,
            ttl = 60_000L,
        )
        assertEquals(maps, kept.stickyHidePackage)
        assertEquals(1_000L, kept.lastSeenElapsedMs)
    }

    @Test
    fun refresh_whenStickyPackageResightedWhileOtherFocused() {
        val entered = next(focused = maps, now = 1_000L)
        val refreshed = next(
            previous = entered,
            focused = settings,
            activity = UsageStatsStickyHide.PackageActivitySignal.RESUMED,
            now = 8_000L,
        )
        assertEquals(maps, refreshed.stickyHidePackage)
        assertEquals(8_000L, refreshed.lastSeenElapsedMs)
    }

    @Test
    fun clear_onExplicitBackgroundEvent() {
        val entered = next(focused = maps, now = 1_000L)
        val cleared = next(
            previous = entered,
            focused = settings,
            activity = UsageStatsStickyHide.PackageActivitySignal.BACKGROUNDED,
            now = 4_000L,
        )
        assertNull(cleared.stickyHidePackage)
    }

    @Test
    fun clear_onSafetyTtlWithoutResight() {
        val entered = next(focused = maps, now = 1_000L)
        val stillHeld = next(
            previous = entered,
            focused = settings,
            activity = UsageStatsStickyHide.PackageActivitySignal.ABSENT,
            now = 1_000L + 59_000L,
            ttl = 60_000L,
        )
        assertEquals(maps, stillHeld.stickyHidePackage)

        val cleared = next(
            previous = entered,
            focused = settings,
            activity = UsageStatsStickyHide.PackageActivitySignal.ABSENT,
            now = 1_000L + 60_000L,
            ttl = 60_000L,
        )
        assertNull(cleared.stickyHidePackage)
    }

    @Test
    fun transfer_whenAnotherHideWatchedPackageFocused() {
        val entered = next(focused = maps, now = 1_000L)
        val transferred = next(
            previous = entered,
            focused = OemOverlayAppMonitor.AVM_PACKAGE,
            now = 2_000L,
            overlay = OemOverlayAppMonitor.AVM_PACKAGE,
        )
        assertEquals(OemOverlayAppMonitor.AVM_PACKAGE, transferred.stickyHidePackage)
        assertEquals(2_000L, transferred.lastSeenElapsedMs)
    }

    @Test
    fun clear_immediatelyWhenOemAvmOverlayGone() {
        val entered = next(
            focused = OemOverlayAppMonitor.AVM_PACKAGE,
            now = 1_000L,
            overlay = OemOverlayAppMonitor.AVM_PACKAGE,
        )
        assertEquals(OemOverlayAppMonitor.AVM_PACKAGE, entered.stickyHidePackage)
        val cleared = next(
            previous = entered,
            focused = settings,
            activity = UsageStatsStickyHide.PackageActivitySignal.ABSENT,
            now = 2_000L,
            overlay = null,
        )
        assertNull(cleared.stickyHidePackage)
    }

    @Test
    fun avmGone_thenTransferToFocusedHideWatch() {
        val entered = next(
            focused = OemOverlayAppMonitor.AVM_PACKAGE,
            now = 1_000L,
            overlay = OemOverlayAppMonitor.AVM_PACKAGE,
        )
        val transferred = next(
            previous = entered,
            focused = maps,
            now = 2_000L,
            overlay = null,
        )
        assertEquals(maps, transferred.stickyHidePackage)
    }

    @Test
    fun clear_whenStickyRemovedFromHideWatch() {
        val entered = next(focused = maps, now = 1_000L)
        val cleared = next(
            previous = entered,
            focused = settings,
            watch = setOf(OemOverlayAppMonitor.AVM_PACKAGE),
            now = 2_000L,
        )
        assertNull(cleared.stickyHidePackage)
    }

    @Test
    fun clear_whenHideWatchEmpty() {
        val entered = next(focused = maps, now = 1_000L)
        val cleared = next(
            previous = entered,
            focused = maps,
            watch = emptySet(),
            now = 2_000L,
        )
        assertNull(cleared.stickyHidePackage)
    }

    @Test
    fun ignore_focusedOutsideHideWatch_withoutPriorSticky() {
        val s = next(focused = settings, now = 1_000L)
        assertNull(s.stickyHidePackage)
    }
}
