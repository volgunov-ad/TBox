package vad.dashing.tbox.externalapi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ExternalApiPairingSessionTest {
    @Test
    fun approveThenStop_keepsApprovedRequestForStatusPoll() {
        val session = ExternalApiPairingSession()
        session.startPairing()
        val pending = session.submitPairRequest("client-1", "TBox API tools (PC)", "tools")
        assertEquals(ExternalApiPairRequestStatus.PENDING, pending.status)

        val token = "test-access-token"
        val approved = session.approveRequest(pending.requestId, token)
        assertNotNull(approved)
        assertEquals(ExternalApiPairRequestStatus.APPROVED, approved!!.status)
        assertEquals(token, approved.accessToken)

        // Mirrors ExternalApiController.approvePair → stopPairing after token issue.
        session.stopPairing()
        assertFalse(session.isPairingActive())

        val polled = session.getRequest(pending.requestId)
        assertNotNull("approved request must survive stopPairing for client poll", polled)
        assertEquals(ExternalApiPairRequestStatus.APPROVED, polled!!.status)
        assertEquals(token, polled.accessToken)
        assertEquals("client-1", polled.clientId)
    }

    @Test
    fun stopPairing_dropsUnresolvedPending() {
        val session = ExternalApiPairingSession()
        session.startPairing()
        val pending = session.submitPairRequest("client-2", "Other", null)
        session.stopPairing()
        assertNull(session.getRequest(pending.requestId))
        assertTrue(session.pendingRequestsSnapshot().isEmpty())
    }

    @Test
    fun pollForApproved_handsOutTokenOnce() {
        val session = ExternalApiPairingSession()
        session.startPairing()
        val pending = session.submitPairRequest("client-4", "Name", null)
        assertEquals(
            ExternalApiPairRequestStatus.PENDING,
            session.takeRequestForPoll(pending.requestId)?.status,
        )
        session.approveRequest(pending.requestId, "once")
        assertEquals("once", session.takeRequestForPoll(pending.requestId)?.accessToken)
        assertNull(session.takeRequestForPoll(pending.requestId))
    }

    @Test
    fun submitPairRequest_capsPendingAndReplacesSameClient() {
        val session = ExternalApiPairingSession()
        session.startPairing()
        val first = session.submitPairRequest("same", "Name", null)
        val second = session.submitPairRequest("same", "Name", null)
        assertNull(session.getRequest(first.requestId))
        assertNotNull(session.getRequest(second.requestId))
        repeat(ExternalApiConstants.MAX_PENDING_PAIR_REQUESTS - 1) { index ->
            session.submitPairRequest("client-$index", "Name", null)
        }
        val overflow = runCatching { session.submitPairRequest("overflow", "Name", null) }
        assertTrue(overflow.isFailure)
    }

    @Test
    fun startPairing_clearsPreviousApproved() {
        val session = ExternalApiPairingSession()
        session.startPairing()
        val pending = session.submitPairRequest("client-3", "Name", null)
        session.approveRequest(pending.requestId, "tok")
        session.stopPairing()
        assertNotNull(session.getRequest(pending.requestId))

        session.startPairing()
        assertNull(session.getRequest(pending.requestId))
        assertTrue(session.isPairingActive())
    }
}
