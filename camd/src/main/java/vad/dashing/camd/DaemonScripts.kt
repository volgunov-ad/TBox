package vad.dashing.camd

/**
 * Shared constants for the root-side files on the head unit. mb_daemon runs the
 * arm scripts written by [CameraWindows]; the command daemon was retired.
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
}
