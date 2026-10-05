package vad.dashing.tbox

import android.content.ComponentName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MediaControlNotificationListenerServiceTest {

    @Test
    fun onListenerConnected_startsBackgroundServiceWithoutBootFlag() {
        val controller = Robolectric.buildService(MediaControlNotificationListenerService::class.java)
        val service = controller.create().get()

        service.onListenerConnected()

        val started = shadowOf(service).nextStartedService
        assertEquals(ComponentName(service, BackgroundService::class.java), started.component)
        assertEquals(BackgroundService.ACTION_START, started.action)
        assertFalse(started.getBooleanExtra(BackgroundService.EXTRA_START_FROM_BOOT, false))
        assertEquals(
            MediaControlNotificationListenerService.START_SOURCE_LISTENER_CONNECTED,
            started.getStringExtra(BackgroundService.EXTRA_START_SOURCE_ACTION),
        )
    }

    @Test
    fun onListenerConnected_twice_stillOnlyActionStartWithoutBoot() {
        val controller = Robolectric.buildService(MediaControlNotificationListenerService::class.java)
        val service = controller.create().get()

        service.onListenerConnected()
        service.onListenerConnected()

        val first = shadowOf(service).nextStartedService
        val second = shadowOf(service).nextStartedService
        assertEquals(BackgroundService.ACTION_START, first.action)
        assertEquals(BackgroundService.ACTION_START, second.action)
        assertFalse(first.getBooleanExtra(BackgroundService.EXTRA_START_FROM_BOOT, false))
        assertFalse(second.getBooleanExtra(BackgroundService.EXTRA_START_FROM_BOOT, false))
        // No third start queued from this call path alone
        assertNull(shadowOf(service).nextStartedService)
    }
}
