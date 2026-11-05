package vad.dashing.tbox.adb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import vad.dashing.tbox.R

class LocalhostAdbReasonsTest {

    @Test
    fun stringRes_coversAllReasons() {
        assertEquals(
            R.string.localhost_adb_reason_tcp_enable_failed,
            LocalhostAdbReasons.stringRes(LocalhostAdbSession.Reason.TcpEnableFailed),
        )
        assertEquals(
            R.string.localhost_adb_reason_tcp_not_ready,
            LocalhostAdbReasons.stringRes(LocalhostAdbSession.Reason.TcpNotReady),
        )
        assertEquals(
            R.string.localhost_adb_reason_connect_failed,
            LocalhostAdbReasons.stringRes(LocalhostAdbSession.Reason.AdbConnectFailed),
        )
    }

    @Test
    fun message_withoutContext_usesFallbacksAndHidesTransportNoise() {
        assertEquals(
            LocalhostAdbReasons.FALLBACK_TCP_NOT_READY,
            LocalhostAdbReasons.message(null, LocalhostAdbSession.Reason.TcpNotReady),
        )
        assertEquals(
            LocalhostAdbReasons.FALLBACK_TCP_ENABLE_FAILED,
            LocalhostAdbReasons.message(null, LocalhostAdbSession.Reason.TcpEnableFailed, ""),
        )
        assertEquals(
            LocalhostAdbReasons.FALLBACK_CONNECT_FAILED,
            LocalhostAdbReasons.message(
                null,
                LocalhostAdbSession.Reason.AdbConnectFailed,
                "ADB transport closed",
            ),
        )
        assertEquals(
            "Permission denial",
            LocalhostAdbReasons.message(
                null,
                LocalhostAdbSession.Reason.AdbConnectFailed,
                "Permission denial",
            ),
        )
    }

    @Test
    fun message_replacesBlankOrEnumNameDetailForTcpReasons() {
        assertEquals(
            LocalhostAdbReasons.FALLBACK_TCP_NOT_READY,
            LocalhostAdbReasons.message(
                null,
                LocalhostAdbSession.Reason.TcpNotReady,
                "TcpNotReady",
            ),
        )
    }

    @Test
    fun fromSessionReasonName_andVirtualDisplayMapping() {
        assertEquals(
            LocalhostAdbSession.Reason.TcpNotReady,
            LocalhostAdbReasons.fromSessionReasonName("TcpNotReady"),
        )
        assertNull(LocalhostAdbReasons.fromSessionReasonName("DisplayNotFound"))
        assertEquals(
            LocalhostAdbReasons.FALLBACK_TCP_ENABLE_FAILED,
            LocalhostAdbReasons.forVirtualDisplay(
                null,
                VirtualDisplayAdb.Reason.TcpEnableFailed,
            ),
        )
        assertEquals(
            "shell boom",
            LocalhostAdbReasons.forVirtualDisplay(
                null,
                VirtualDisplayAdb.Reason.ShellCommandFailed,
                "shell boom",
            ),
        )
        assertEquals(
            LocalhostAdbReasons.FALLBACK_CONNECT_FAILED,
            LocalhostAdbReasons.forPackageAdb(
                null,
                PackageAdbActions.Reason.AdbConnectFailed,
                "connection refused",
            ),
        )
    }

    @Test
    fun userFacingConnectDetail_delegatesToReasons() {
        assertEquals(
            LocalhostAdbReasons.FALLBACK_CONNECT_FAILED,
            AdbIoErrors.userFacingAdbConnectDetail("ADB transport closed"),
        )
        assertEquals(
            LocalhostAdbReasons.FALLBACK_CONNECT_FAILED,
            AdbIoErrors.USER_FACING_CONNECT_FAILED_RU,
        )
    }
}
