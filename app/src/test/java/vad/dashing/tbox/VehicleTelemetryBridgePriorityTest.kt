package vad.dashing.tbox

import android.os.SystemClock
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class VehicleTelemetryBridgePriorityTest {
    @Before
    fun setUp() {
        VehicleTelemetryBridge.stop()
        VehicleTelemetryBridge.resetFreshnessForTest()
    }

    @Test
    fun tboxAcceptedWhenHuAbsent() {
        val now = SystemClock.elapsedRealtime()
        assertTrue(
            VehicleTelemetryBridge.acceptTboxHuPriority(VehicleTelemetryBridge.Signal.Rpm, now)
        )
    }

    @Test
    fun tboxBlockedWhileHuFresh() {
        val now = SystemClock.elapsedRealtime()
        VehicleTelemetryBridge.noteHuForTest(VehicleTelemetryBridge.Signal.Fuel, now)
        assertFalse(
            VehicleTelemetryBridge.acceptTboxHuPriority(
                VehicleTelemetryBridge.Signal.Fuel,
                now + 1_000L,
            )
        )
    }

    @Test
    fun tboxAcceptedAfterHuStale() {
        val now = SystemClock.elapsedRealtime()
        VehicleTelemetryBridge.noteHuForTest(VehicleTelemetryBridge.Signal.Odometer, now)
        assertTrue(
            VehicleTelemetryBridge.acceptTboxHuPriority(
                VehicleTelemetryBridge.Signal.Odometer,
                now + VehicleTelemetryBridge.FRESHNESS_MS + 1L,
            )
        )
    }
}
