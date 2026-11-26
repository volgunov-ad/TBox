package vad.dashing.camd

/**
 * Shared constants for the root-side files on the head unit. Primary root path is
 * the shared rootsvc; legacy mb_daemon scripts still use these paths via
 * [vad.dashing.rootsvc.client.MbDaemonChannel].
 */
object DaemonScripts {
    const val PACKAGE_FILES = "/data/data/vad.dashing.camd/files"
    const val STATUS_FILE = "/data/local/tmp/camd.status"
    const val QCAR_LOG = "/data/local/tmp/camd-qcar.log"
    const val AUTORUN = "/sdcard/autorun.sh"
    const val AUTORUN_FLAG = "/sdcard/autorun.flag"
    const val AUTORUN_PROP = "vendor.autorun.sdcard.logfile"
    /** 8765 is TBox Monitor, 8766 is TBox MQTT local status. */
    const val API_PORT = 8770

    /**
     * mb_daemon deletes autorun.sh after running it, so an existing file is a script of another
     * app (DCL, dcl_install, UsbMount) still waiting or running. The shell reads it while it runs:
     * overwriting it in place would drop or corrupt that script. Bounded: a script nobody
     * triggered is never deleted.
     */
    const val FOREIGN_AUTORUN_WAIT_MS = 10_000L

    /**
     * Our own leftover (mb_daemon did not answer last time) is safe to overwrite at once.
     * Matches both the old inline scripts (ARM path in the body) and MbDaemonChannel's
     * one-line `exec …/camd_grab_arm.sh`.
     */
    fun isForeignAutorun(text: String?): Boolean {
        if (text == null) return false
        if (text.contains(GrabberScripts.ARM)) return false
        if (text.contains(GrabberScripts.OWN_MARKER)) return false
        return true
    }
}
