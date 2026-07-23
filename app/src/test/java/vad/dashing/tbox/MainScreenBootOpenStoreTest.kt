package vad.dashing.tbox

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MainScreenBootOpenStoreTest {

    @Test
    fun markPending_thenClear_roundTrip() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        MainScreenBootOpenStore.clearPending(context)

        assertFalse(MainScreenBootOpenStore.isPending(context))

        MainScreenBootOpenStore.markPending(context, "android.intent.action.BOOT_COMPLETED")
        assertTrue(MainScreenBootOpenStore.isPending(context))
        assertEquals(
            "android.intent.action.BOOT_COMPLETED",
            MainScreenBootOpenStore.sourceAction(context),
        )
        assertTrue(MainScreenBootOpenStore.deadlineElapsedRealtimeMs(context) > 0L)

        MainScreenBootOpenStore.clearPending(context)
        assertFalse(MainScreenBootOpenStore.isPending(context))
        assertEquals("", MainScreenBootOpenStore.sourceAction(context))
    }
}
