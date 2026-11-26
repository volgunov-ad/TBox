package vad.dashing.rootsvc

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.rootsvc.client.Allowlist
import vad.dashing.rootsvc.client.RootSvcProtocol

class AllowlistTest {
    @Test
    fun ourPackagesGetScriptRun() {
        val caps = Allowlist.capsForPackages(listOf(Allowlist.PKG_CAMD))
        assertTrue(caps.contains(RootSvcProtocol.CMD_SCRIPT_RUN))
        assertTrue(caps.contains(RootSvcProtocol.CMD_SVC_CTL))
        assertTrue(Allowlist.allows(listOf(Allowlist.PKG_TBOX), RootSvcProtocol.CMD_SCRIPT_RUN))
    }

    @Test
    fun monitorGetsStatusAndAdminRestart() {
        assertTrue(Allowlist.allows(listOf(Allowlist.PKG_TBOX), RootSvcProtocol.CMD_STATUS))
        assertTrue(Allowlist.allows(listOf(Allowlist.PKG_TBOX), RootSvcProtocol.CMD_ADMIN_RESTART))
        assertFalse(Allowlist.allows(listOf(Allowlist.PKG_DCL), RootSvcProtocol.CMD_STATUS))
        assertFalse(Allowlist.allows(listOf(Allowlist.PKG_DCL), RootSvcProtocol.CMD_ADMIN_RESTART))
    }

    @Test
    fun dclGetsScriptRunAndWindowCaps() {
        assertTrue(Allowlist.allows(listOf(Allowlist.PKG_DCL), RootSvcProtocol.CMD_SCRIPT_RUN))
        assertTrue(Allowlist.allows(listOf(Allowlist.PKG_DCL), RootSvcProtocol.CMD_USB_UNLOCK))
        assertTrue(Allowlist.allows(listOf(Allowlist.PKG_DCL), RootSvcProtocol.CMD_WM_LIST))
        assertTrue(Allowlist.allows(listOf(Allowlist.PKG_DCL), RootSvcProtocol.CMD_HELPER_ENSURE))
        assertFalse(Allowlist.allows(listOf(Allowlist.PKG_DCL), RootSvcProtocol.CMD_PKG_HIDE))
    }

    @Test
    fun pingAlwaysAllowed() {
        assertTrue(Allowlist.allows(emptyList(), RootSvcProtocol.CMD_PING))
        assertTrue(Allowlist.allows(listOf("com.unknown.app"), RootSvcProtocol.CMD_PING))
    }

    @Test
    fun sharedUserIdIntersectionDeniesUnknownPeer() {
        val caps = Allowlist.capsForPackages(listOf(Allowlist.PKG_CAMD, "com.evil.app"))
        assertTrue(caps.isEmpty())
    }

    @Test
    fun sharedUserIdIntersectionKeepsCommonCaps() {
        val caps = Allowlist.capsForPackages(listOf(Allowlist.PKG_CAMD, Allowlist.PKG_TBOX))
        assertTrue(caps.contains(RootSvcProtocol.CMD_USB_UNLOCK))
    }
}
