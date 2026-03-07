package vad.dashing.tbox.ui

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetInteractionPolicyTest {

    @Test
    fun isActionAllowed_returnsTrueWithoutExclusions() {
        val policy = DashboardWidgetInteractionPolicy(
            mode = DashboardWidgetInteractionMode.EDIT
        )

        val result = policy.isActionAllowed(
            offset = Offset(90f, 90f),
            width = 100f,
            height = 100f
        )

        assertEquals(true, result)
    }

    @Test
    fun isActionAllowed_returnsFalseInsideResizeExclusion() {
        val policy = DashboardWidgetInteractionPolicy(
            mode = DashboardWidgetInteractionMode.EDIT,
            exclusions = listOf(ResizeHandleWidgetHitExclusion)
        )

        val result = policy.isActionAllowed(
            offset = Offset(95f, 95f),
            width = 100f,
            height = 100f
        )

        assertEquals(false, result)
    }
}
