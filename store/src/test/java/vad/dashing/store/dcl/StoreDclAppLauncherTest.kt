package vad.dashing.store.dcl

import android.os.Bundle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class StoreDclAppLauncherTest {

    @Test
    fun parseLaunchOutcome_okAndAcceptedAreSuccess() {
        assertEquals(
            StoreDclLaunchOutcome.Success,
            parseLaunchOutcome(Bundle().apply {
                putString(StoreDclContract.KEY_STATUS, StoreDclContract.STATUS_OK)
            }),
        )
        assertEquals(
            StoreDclLaunchOutcome.Success,
            parseLaunchOutcome(Bundle().apply {
                putString(StoreDclContract.KEY_STATUS, StoreDclContract.STATUS_ACCEPTED)
            }),
        )
    }

    @Test
    fun parseLaunchOutcome_errorCarriesCode() {
        val outcome = parseLaunchOutcome(Bundle().apply {
            putString(StoreDclContract.KEY_STATUS, StoreDclContract.STATUS_ERROR)
            putString(StoreDclContract.KEY_ERROR_CODE, StoreDclContract.ERROR_FORBIDDEN)
            putString(StoreDclContract.KEY_MESSAGE, "caller not allowed")
        })
        assertTrue(outcome is StoreDclLaunchOutcome.Failed)
        val failed = outcome as StoreDclLaunchOutcome.Failed
        assertEquals(StoreDclContract.ERROR_FORBIDDEN, failed.errorCode)
        assertEquals("caller not allowed", failed.message)
    }

    @Test
    fun rawFallback_onAnyNonSuccess() {
        assertFalse(storeDclShouldUseRawFallback(StoreDclLaunchOutcome.Success))
        assertTrue(storeDclShouldUseRawFallback(StoreDclLaunchOutcome.Unavailable))
        assertTrue(
            storeDclShouldUseRawFallback(
                StoreDclLaunchOutcome.Failed(
                    errorCode = StoreDclContract.ERROR_FORBIDDEN,
                    message = null,
                ),
            ),
        )
        assertTrue(
            storeDclShouldUseRawFallback(
                StoreDclLaunchOutcome.Failed(errorCode = "busy", message = "try later"),
            ),
        )
    }
}
