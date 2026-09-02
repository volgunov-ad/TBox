package vad.dashing.tbox.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import vad.dashing.tbox.automation.AutomationSignalId
import vad.dashing.tbox.automation.AutomationSignalSource

class AutomationDefaultSourceTest {
    @Test
    fun defaultNumericCondition_usesHeadUnit() {
        val condition = defaultNumericCondition()
        assertEquals(AutomationSignalId.ENGINE_RPM, condition.signal)
        assertEquals(AutomationSignalSource.HEAD_UNIT, condition.source)
    }
}
