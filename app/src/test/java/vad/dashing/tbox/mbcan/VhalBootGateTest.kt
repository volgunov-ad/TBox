package vad.dashing.tbox.mbcan

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VhalBootGateTest {
    @Test
    fun defersOnlyWhileBootIsUnfinished() {
        assertTrue(VhalBootGate.shouldDefer(bootCompletedRaw = "0", gateAlreadyPassed = false))
        assertTrue(VhalBootGate.shouldDefer(bootCompletedRaw = "", gateAlreadyPassed = false))
        assertFalse(VhalBootGate.shouldDefer(bootCompletedRaw = "1", gateAlreadyPassed = false))
    }

    @Test
    fun doesNotDeferWhenPropertyIsUnreadableOrGateAlreadyPassed() {
        assertFalse(VhalBootGate.shouldDefer(bootCompletedRaw = null, gateAlreadyPassed = false))
        assertFalse(VhalBootGate.shouldDefer(bootCompletedRaw = "0", gateAlreadyPassed = true))
        assertFalse(VhalBootGate.shouldDefer(bootCompletedRaw = "1", gateAlreadyPassed = true))
    }
}
