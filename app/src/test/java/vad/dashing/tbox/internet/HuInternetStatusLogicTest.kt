package vad.dashing.tbox.internet

import org.junit.Assert.assertEquals
import org.junit.Test

class HuInternetStatusLogicTest {
    @Test
    fun success_setsOnlineAndClearsFailures() {
        val afterFail = HuInternetStatusLogic.onFailure(HuInternetStatusLogic.Snapshot())
        val result = HuInternetStatusLogic.onSuccess(afterFail)
        assertEquals(HuInternetStatus.ONLINE, result.status)
        assertEquals(0, result.consecutiveFailures)
    }

    @Test
    fun firstFailure_keepsPreviousStatus() {
        val online = HuInternetStatusLogic.Snapshot(HuInternetStatus.ONLINE, 0)
        val afterOne = HuInternetStatusLogic.onFailure(online)
        assertEquals(HuInternetStatus.ONLINE, afterOne.status)
        assertEquals(1, afterOne.consecutiveFailures)

        val unknown = HuInternetStatusLogic.Snapshot()
        val afterUnknownFail = HuInternetStatusLogic.onFailure(unknown)
        assertEquals(HuInternetStatus.UNKNOWN, afterUnknownFail.status)
        assertEquals(1, afterUnknownFail.consecutiveFailures)
    }

    @Test
    fun secondFailure_setsOffline() {
        val online = HuInternetStatusLogic.Snapshot(HuInternetStatus.ONLINE, 0)
        val afterTwo = HuInternetStatusLogic.onFailure(HuInternetStatusLogic.onFailure(online))
        assertEquals(HuInternetStatus.OFFLINE, afterTwo.status)
        assertEquals(2, afterTwo.consecutiveFailures)
    }

    @Test
    fun successAfterFailure_restoresOnlineImmediately() {
        val online = HuInternetStatusLogic.Snapshot(HuInternetStatus.ONLINE, 0)
        val afterOneFail = HuInternetStatusLogic.onFailure(online)
        val restored = HuInternetStatusLogic.onSuccess(afterOneFail)
        assertEquals(HuInternetStatus.ONLINE, restored.status)
        assertEquals(0, restored.consecutiveFailures)
    }

    @Test
    fun automationStateKey_matchesCatalog() {
        assertEquals("unknown", HuInternetStatusLogic.automationStateKey(HuInternetStatus.UNKNOWN))
        assertEquals("checking", HuInternetStatusLogic.automationStateKey(HuInternetStatus.CHECKING))
        assertEquals("online", HuInternetStatusLogic.automationStateKey(HuInternetStatus.ONLINE))
        assertEquals("offline", HuInternetStatusLogic.automationStateKey(HuInternetStatus.OFFLINE))
    }
}
