package vad.dashing.mqtt.boot

import android.content.Intent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BootCompleteReceiverTest {
    @Test
    fun bootQuickBootPackageReplaceAndStorageRetryStartTheBridge() {
        assertTrue(isMqttBootAction(Intent.ACTION_BOOT_COMPLETED))
        assertTrue(isMqttBootAction(Intent.ACTION_MY_PACKAGE_REPLACED))
        assertTrue(isMqttBootAction("android.intent.action.QUICKBOOT_POWERON"))
        assertTrue(isMqttBootAction(ACTION_RETRY_STORAGE))
        assertFalse(isMqttBootAction(Intent.ACTION_BOOT_COMPLETED + ".other"))
        assertFalse(isMqttBootAction(null))
    }
}
