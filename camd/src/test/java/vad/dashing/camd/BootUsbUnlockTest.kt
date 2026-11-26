package vad.dashing.camd

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BootUsbUnlockTest {
    @Test
    fun unlocksOnlyEarlyWithHiddenSystemUiAndNoMountedStick() {
        assertTrue(BootUsbUnlock.due(40_000L, systemUiHidden = true, usbMounted = false))
        assertFalse(BootUsbUnlock.due(40_000L, systemUiHidden = false, usbMounted = false))
        assertFalse(BootUsbUnlock.due(40_000L, systemUiHidden = true, usbMounted = true))
        assertFalse(
            BootUsbUnlock.due(BootUsbUnlock.BOOT_WINDOW_MS, systemUiHidden = true, usbMounted = false),
        )
    }
}
