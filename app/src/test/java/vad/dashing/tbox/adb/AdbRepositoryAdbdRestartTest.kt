package vad.dashing.tbox.adb

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdbRepositoryAdbdRestartTest {

    @Test
    fun dropConnectionAfterAdbdRestart_withoutLiveConnection_isNoOp() = runBlocking {
        val stateBefore = AdbRepository.state.value
        val consoleBefore = AdbRepository.consoleLog.value

        AdbRepository.dropConnectionAfterAdbdRestart("adbd restarted")

        assertEquals(stateBefore, AdbRepository.state.value)
        assertEquals(consoleBefore, AdbRepository.consoleLog.value)
    }

    @Test
    fun intentionalCloseGate_nestedRestoreAndToggleWindows_staysArmed() = runBlocking {
        AdbShutdownGate.resetForTests()
        assertFalse(AdbShutdownGate.isIntentionalTransportClose())

        AdbShutdownGate.withIntentionalTransportCloseSuspending {
            assertTrue(AdbShutdownGate.isIntentionalTransportClose())
            AdbShutdownGate.withIntentionalTransportCloseSuspending {
                assertTrue(AdbShutdownGate.shouldSuppressBenignDisconnect())
            }
            assertTrue(AdbShutdownGate.shouldSuppressBenignDisconnect())
        }

        assertFalse(AdbShutdownGate.isIntentionalTransportClose())
        AdbShutdownGate.resetForTests()
    }
}
