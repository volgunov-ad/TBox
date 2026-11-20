package vad.dashing.tbox.phone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhoneCallHangupMessageTest {

    @Test
    fun idle_failsImmediatelyWithNoActiveMessage() {
        assertEquals(
            HANGUP_NO_ACTIVE_CALL_MESSAGE,
            hangupImmediateFailureMessage(PhoneCallState.IDLE),
        )
    }

    @Test
    fun unknown_doesNotFailImmediately_distinctFromIdle() {
        assertNull(hangupImmediateFailureMessage(PhoneCallState.UNKNOWN))
        assertNotEquals(HANGUP_NO_ACTIVE_CALL_MESSAGE, HANGUP_STATE_UNKNOWN_MESSAGE)
    }

    @Test
    fun activeStates_doNotFailImmediately() {
        for (state in listOf(
            PhoneCallState.DIALING,
            PhoneCallState.ACTIVE,
            PhoneCallState.INCOMING,
            PhoneCallState.HELD,
        )) {
            assertNull(hangupImmediateFailureMessage(state))
        }
    }
}
