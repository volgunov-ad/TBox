package vad.dashing.tbox.phone

import org.junit.Assert.assertEquals
import org.junit.Test

class PhoneCallNumberTest {

    @Test
    fun observedNumberWins() {
        assertEquals(
            "+79001112233",
            nextObservedCallNumber(PhoneCallState.ACTIVE, "+79200287693", PhoneCallState.ACTIVE, "+79001112233"),
        )
    }

    @Test
    fun lastNumberKeptAfterCallEnds() {
        assertEquals(
            "+79200287693",
            nextObservedCallNumber(PhoneCallState.ACTIVE, "+79200287693", PhoneCallState.IDLE, ""),
        )
    }

    @Test
    fun withheldNewCallDoesNotInheritPreviousCaller() {
        assertEquals(
            "",
            nextObservedCallNumber(PhoneCallState.IDLE, "+79200287693", PhoneCallState.INCOMING, ""),
        )
        assertEquals(
            "",
            nextObservedCallNumber(PhoneCallState.UNKNOWN, "+79200287693", PhoneCallState.DIALING, ""),
        )
    }

    @Test
    fun ongoingCallKeepsNumberWhenUpdateOmitsIt() {
        assertEquals(
            "+79200287693",
            nextObservedCallNumber(PhoneCallState.INCOMING, "+79200287693", PhoneCallState.ACTIVE, ""),
        )
    }
}
