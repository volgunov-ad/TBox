package vad.dashing.tbox

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BootCompleteReceiverTest {

    @Test
    fun debugBootCompletedAction_startsBootPipeline() {
        val context = ApplicationProvider.getApplicationContext<Application>()

        BootCompleteReceiver().onReceive(
            context,
            Intent(BootCompleteReceiver.ACTION_DEBUG_BOOT_COMPLETED),
        )

        val started = shadowOf(context).nextStartedService
        assertEquals(ComponentName(context, BackgroundService::class.java), started.component)
        assertEquals(BackgroundService.ACTION_START, started.action)
        assertTrue(started.getBooleanExtra(BackgroundService.EXTRA_START_FROM_BOOT, false))
        assertEquals(
            BootCompleteReceiver.ACTION_DEBUG_BOOT_COMPLETED,
            started.getStringExtra(BackgroundService.EXTRA_START_SOURCE_ACTION),
        )
    }
}
