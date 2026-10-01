package vad.dashing.tbox.ui

import androidx.lifecycle.Lifecycle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class FloatingDashboardLifecycleOwnerTest {
    @Test
    fun advancesCreatedStartedResumed_forOverlayCollectors() {
        val owner = MyLifecycleOwner()
        assertEquals(Lifecycle.State.INITIALIZED, owner.lifecycle.currentState)

        owner.setCurrentState(Lifecycle.State.CREATED)
        owner.setCurrentState(Lifecycle.State.STARTED)
        owner.setCurrentState(Lifecycle.State.RESUMED)

        assertTrue(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
}
