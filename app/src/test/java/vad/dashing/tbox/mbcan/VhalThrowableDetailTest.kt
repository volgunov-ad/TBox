package vad.dashing.tbox.mbcan

import org.junit.Assert.assertEquals
import org.junit.Test
import java.lang.reflect.InvocationTargetException

class VhalThrowableDetailTest {
    @Test
    fun unwrapsInvocationTargetWithNullMessage() {
        val root = IllegalStateException("not connected")
        val wrapped = InvocationTargetException(root)
        val outer = IllegalStateException("CarPropertyManager is null", wrapped)

        assertEquals(
            "IllegalStateException: CarPropertyManager is null <- " +
                "InvocationTargetException: - <- " +
                "IllegalStateException: not connected",
            VhalThrowableDetail.describe(outer),
        )
    }

    @Test
    fun keepsBlankCauseMessageVisible() {
        val wrapped = InvocationTargetException(RuntimeException())
        assertEquals(
            "InvocationTargetException: - <- RuntimeException: -",
            VhalThrowableDetail.describe(wrapped),
        )
    }
}
