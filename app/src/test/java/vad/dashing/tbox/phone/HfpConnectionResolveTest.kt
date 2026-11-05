package vad.dashing.tbox.phone

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.tbox.resolveHfpClientConnected

class HfpConnectionResolveTest {

    @Test
    fun adapterProfileAloneIsEnough() {
        assertTrue(
            resolveHfpClientConnected(
                adapterProfileConnected = true,
                headsetClientConnectedDevices = 0,
                connectionBroadcastConnected = false,
            ),
        )
    }

    @Test
    fun proxyConnectedDevicesAloneIsEnough() {
        // OEM: getProfileConnectionState(16) stale DISCONNECTED while HFP Client has a device.
        assertTrue(
            resolveHfpClientConnected(
                adapterProfileConnected = false,
                headsetClientConnectedDevices = 1,
                connectionBroadcastConnected = null,
            ),
        )
    }

    @Test
    fun connectionBroadcastAloneIsEnough() {
        assertTrue(
            resolveHfpClientConnected(
                adapterProfileConnected = false,
                headsetClientConnectedDevices = 0,
                connectionBroadcastConnected = true,
            ),
        )
    }

    @Test
    fun allNegativeMeansDisconnected() {
        assertFalse(
            resolveHfpClientConnected(
                adapterProfileConnected = false,
                headsetClientConnectedDevices = 0,
                connectionBroadcastConnected = false,
            ),
        )
        assertFalse(
            resolveHfpClientConnected(
                adapterProfileConnected = false,
                headsetClientConnectedDevices = 0,
                connectionBroadcastConnected = null,
            ),
        )
    }

    @Test
    fun broadcastFalseDoesNotOverrideProxyDevices() {
        assertTrue(
            resolveHfpClientConnected(
                adapterProfileConnected = false,
                headsetClientConnectedDevices = 2,
                connectionBroadcastConnected = false,
            ),
        )
    }
}
