package vad.dashing.camd

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DaemonScriptsTest {
    @Test
    fun noAutorunIsFree() {
        assertFalse(DaemonScripts.isForeignAutorun(null))
    }

    @Test
    fun ownLeftoverIsOverwritten() {
        assertFalse(DaemonScripts.isForeignAutorun(GrabberScripts.stopAvm("n1")))
        assertFalse(DaemonScripts.isForeignAutorun(GrabberScripts.startAvm("n2")))
        assertFalse(
            DaemonScripts.isForeignAutorun(
                "#!/system/bin/sh\nexec /system/bin/sh '${GrabberScripts.ARM_BODY}'\n",
            ),
        )
    }

    @Test
    fun otherAppsScriptIsWaitedFor() {
        assertTrue(DaemonScripts.isForeignAutorun("#!/system/bin/sh\nexec /system/bin/sh '/sdcard/dcl_boot.sh'\n"))
        assertTrue(DaemonScripts.isForeignAutorun(""))
    }

    @Test
    fun usbUnlockScriptRunsOwnApkAndAnswersNonce() {
        val script = GrabberScripts.usbUnlock("u7", "/data/app/vad.dashing.camd-1/base.apk")
        assertTrue(script.contains("CLASSPATH='/data/app/vad.dashing.camd-1/base.apk' /system/bin/app_process /system/bin vad.dashing.camd.UsbUnlockMain"))
        assertTrue(script.contains("echo u7 > ${GrabberScripts.ARM}"))
        assertFalse(DaemonScripts.isForeignAutorun(script))
    }
}
