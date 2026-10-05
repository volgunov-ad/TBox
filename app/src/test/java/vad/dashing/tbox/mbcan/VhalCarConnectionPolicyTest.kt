package vad.dashing.tbox.mbcan

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VhalCarConnectionPolicyTest {
    @Test
    fun reusesOnlyWhenBridgeConnectedAndAvailable() {
        assertTrue(
            VhalCarConnectionPolicy.shouldReuseExistingBridge(
                bridgePresent = true,
                serviceConnected = true,
                availabilityAvailable = true,
            ),
        )
    }

    @Test
    fun doesNotReuseAfterCarServiceDisconnect() {
        assertFalse(
            VhalCarConnectionPolicy.shouldReuseExistingBridge(
                bridgePresent = true,
                serviceConnected = false,
                availabilityAvailable = true,
            ),
        )
    }

    @Test
    fun reconnectsOnlyTheActiveBridge() {
        assertTrue(VhalCarConnectionPolicy.shouldReconnectAfterDisconnect(activeBridgeIsSource = true))
        assertFalse(VhalCarConnectionPolicy.shouldReconnectAfterDisconnect(activeBridgeIsSource = false))
    }

    @Test
    fun doesNotReuseWhenUnavailableOrMissingBridge() {
        assertFalse(
            VhalCarConnectionPolicy.shouldReuseExistingBridge(
                bridgePresent = false,
                serviceConnected = true,
                availabilityAvailable = true,
            ),
        )
        assertFalse(
            VhalCarConnectionPolicy.shouldReuseExistingBridge(
                bridgePresent = true,
                serviceConnected = true,
                availabilityAvailable = false,
            ),
        )
    }
}
