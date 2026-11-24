package vad.dashing.tbox.phone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneLinkPolicyTest {

    @Test
    fun auto_directConfirmed_stopsCompanion() {
        val plan = transportPlan(
            path = PhonePath.AUTO,
            foreground = true,
            directConfirmed = true,
            radioConfirmed = false,
            keepDirectForTelemetry = false,
            radioOn = true,
            searchSince = 50L,
            now = 10_000L,
            passesWithoutLink = 3,
        )
        assertTrue(plan.direct)
        assertFalse(plan.radio)
        assertEquals(0L, plan.searchSince)
    }

    @Test
    fun auto_companionConfirmed_keepsDirectOnlyForTelemetry() {
        val idle = transportPlan(
            path = PhonePath.AUTO,
            foreground = true,
            directConfirmed = false,
            radioConfirmed = true,
            keepDirectForTelemetry = false,
            radioOn = false,
            searchSince = 1L,
            now = 9_000L,
            passesWithoutLink = 0,
        )
        assertFalse(idle.direct)
        assertTrue(idle.radio)
        assertEquals(0L, idle.searchSince)

        val withTelemetry = transportPlan(
            path = PhonePath.AUTO,
            foreground = false,
            directConfirmed = false,
            radioConfirmed = true,
            keepDirectForTelemetry = true,
            radioOn = true,
            searchSince = 1L,
            now = 9_000L,
            passesWithoutLink = 0,
        )
        assertTrue(withTelemetry.direct)
        assertFalse(withTelemetry.radio)
        assertEquals(0L, withTelemetry.searchSince)
    }

    @Test
    fun permissionBlock_needsRationaleLossOrSecondDenial() {
        assertFalse(
            markBluetoothPermissionBlocked(
                granted = false,
                showedRationaleBefore = false,
                showsRationaleAfter = false,
                denialsThisSession = 1,
            ),
        )
        assertFalse(
            markBluetoothPermissionBlocked(
                granted = false,
                showedRationaleBefore = false,
                showsRationaleAfter = true,
                denialsThisSession = 1,
            ),
        )
        assertTrue(
            markBluetoothPermissionBlocked(
                granted = false,
                showedRationaleBefore = true,
                showsRationaleAfter = false,
                denialsThisSession = 1,
            ),
        )
        assertTrue(
            markBluetoothPermissionBlocked(
                granted = false,
                showedRationaleBefore = true,
                showsRationaleAfter = true,
                denialsThisSession = 2,
            ),
        )
        assertFalse(
            markBluetoothPermissionBlocked(
                granted = true,
                showedRationaleBefore = true,
                showsRationaleAfter = false,
                denialsThisSession = 2,
            ),
        )
    }

    @Test
    fun auto_foregroundWaitsBeforeAddingCompanion() {
        val early = transportPlan(
            path = PhonePath.AUTO,
            foreground = true,
            directConfirmed = false,
            radioConfirmed = false,
            keepDirectForTelemetry = false,
            radioOn = false,
            searchSince = 0L,
            now = 1_000L,
            passesWithoutLink = 0,
        )
        assertTrue(early.direct)
        assertFalse(early.radio)
        assertEquals(1_000L, early.searchSince)

        val later = transportPlan(
            path = PhonePath.AUTO,
            foreground = true,
            directConfirmed = false,
            radioConfirmed = false,
            keepDirectForTelemetry = false,
            radioOn = false,
            searchSince = 1_000L,
            now = 1_000L + DIRECT_FIRST_MS,
            passesWithoutLink = 0,
        )
        assertTrue(later.radio)
    }

    @Test
    fun auto_backgroundDoesNotScanCompanion() {
        val plan = transportPlan(
            path = PhonePath.AUTO,
            foreground = false,
            directConfirmed = false,
            radioConfirmed = false,
            keepDirectForTelemetry = true,
            radioOn = false,
            searchSince = 0L,
            now = 60_000L,
            passesWithoutLink = 4,
        )
        assertTrue(plan.direct)
        assertFalse(plan.radio)
    }

    @Test
    fun auto_emptyDirectPassStartsCompanionInForeground() {
        val plan = transportPlan(
            path = PhonePath.AUTO,
            foreground = true,
            directConfirmed = false,
            radioConfirmed = false,
            keepDirectForTelemetry = false,
            radioOn = false,
            searchSince = 5_000L,
            now = 6_000L,
            passesWithoutLink = 1,
        )
        assertTrue(plan.radio)
    }

    @Test
    fun fixedPaths_ignoreAuto() {
        val direct = transportPlan(
            path = PhonePath.DIRECT,
            foreground = true,
            directConfirmed = false,
            radioConfirmed = true,
            keepDirectForTelemetry = false,
            radioOn = true,
            searchSince = 3L,
            now = 4L,
            passesWithoutLink = 2,
        )
        assertTrue(direct.direct)
        assertFalse(direct.radio)

        val companion = transportPlan(
            path = PhonePath.COMPANION,
            foreground = true,
            directConfirmed = true,
            radioConfirmed = false,
            keepDirectForTelemetry = true,
            radioOn = false,
            searchSince = 3L,
            now = 4L,
            passesWithoutLink = 0,
        )
        assertFalse(companion.direct)
        assertTrue(companion.radio)
    }
}
