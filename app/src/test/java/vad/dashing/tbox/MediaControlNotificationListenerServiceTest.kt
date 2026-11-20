package vad.dashing.tbox

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MediaControlNotificationListenerServiceTest {

    @Before
    fun resetCamdWakeLatch() {
        MediaControlNotificationListenerService.resetCamdWakeAttemptedForTests()
    }

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

    @Test
    fun onListenerConnected_withoutCamdPackage_doesNotBroadcastStart() {
        val controller = Robolectric.buildService(MediaControlNotificationListenerService::class.java)
        val service = controller.create().get()
        val app = RuntimeEnvironment.getApplication()
        val before = shadowOf(app).broadcastIntents.size

        service.onListenerConnected()

        val camdStarts = shadowOf(app).broadcastIntents.drop(before).filter { intent ->
            intent.action == CAMD_START_ACTION && intent.`package` == CAMD_PACKAGE_NAME
        }
        assertTrue(camdStarts.isEmpty())
    }

    @Test
    fun onListenerConnected_withCamdPackage_wakesCamdOnceAcrossReconnect() {
        installCamdPackage()
        val controller = Robolectric.buildService(MediaControlNotificationListenerService::class.java)
        val service = controller.create().get()
        val app = RuntimeEnvironment.getApplication()
        val before = shadowOf(app).broadcastIntents.size

        service.onListenerConnected()
        service.onListenerConnected()

        val camdStarts = shadowOf(app).broadcastIntents.drop(before).filter { intent ->
            intent.action == CAMD_START_ACTION && intent.`package` == CAMD_PACKAGE_NAME
        }
        assertEquals(1, camdStarts.size)

        val startedServices = mutableListOf<Intent>()
        while (true) {
            val next = shadowOf(service).nextStartedService ?: break
            startedServices.add(next)
        }
        assertTrue(
            startedServices.any { intent ->
                intent.component?.className == "$CAMD_PACKAGE_NAME.CamdService"
            },
        )
    }

    private fun installCamdPackage() {
        val pm = RuntimeEnvironment.getApplication().packageManager
        Shadows.shadowOf(pm).addPackage(
            PackageInfo().apply {
                packageName = CAMD_PACKAGE_NAME
                applicationInfo = ApplicationInfo().apply {
                    packageName = CAMD_PACKAGE_NAME
                    flags = 0
                }
            },
        )
    }
}
