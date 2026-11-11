package vad.dashing.camd

import kotlin.jvm.JvmStatic

/**
 * Root side of a camera window. camd_grab is a normal executable, so it can open
 * libais_client.so. The script returns immediately and mb_daemon stays free.
 */
object GrabberScripts {
    const val DESIRED = "${DaemonScripts.PACKAGE_FILES}/grab.desired"
    const val BINARY = "${DaemonScripts.PACKAGE_FILES}/camd_grab.new"
    const val RUN = "/data/local/tmp/camd_grab"
    const val BEAT = "/data/local/tmp/camd-grab.beat"
    const val LOG = "/data/local/tmp/camd-grab.log"
    const val ARM = "/data/local/tmp/camd-grab.arm"
    const val FRAME_RAM = "/mnt/camd-ram"

    @JvmStatic
    fun frame(id: Int): String = "$FRAME_RAM/camd-uyvy-$id"

    fun launcher(nonce: String, stopAvm: Boolean): String {
        val lines = ArrayList<String>()
        lines.add("#!/system/bin/sh")
        lines.add("appops set vad.dashing.camd SYSTEM_ALERT_WINDOW allow")
        if (stopAvm) lines.add("setprop ctl.stop AVMapp")
        lines.add("chmod 755 $BINARY")
        lines.add("ps -A | grep camd_grab | grep -v grep | while read user pid rest; do kill -9 \$pid; done")
        lines.add("sleep 0.2")
        lines.add("cp $BINARY $RUN")
        lines.add("chmod 755 $RUN")
        lines.add("$RUN > $LOG 2>&1 < /dev/null &")
        lines.add("echo $nonce > $ARM")
        lines.add("chmod 666 $ARM")
        lines.add("chmod 666 $LOG")
        lines.add("exit 0")
        return lines.joinToString("\n") + "\n"
    }

    fun stopAvm(nonce: String): String = listOf(
        "#!/system/bin/sh",
        "setprop ctl.stop AVMapp",
        "echo $nonce > $ARM",
        "chmod 666 $ARM",
        "exit 0",
    ).joinToString("\n") + "\n"

    fun grantOverlay(nonce: String): String = listOf(
        "#!/system/bin/sh",
        "appops set vad.dashing.camd SYSTEM_ALERT_WINDOW allow",
        "echo $nonce > $ARM",
        "chmod 666 $ARM",
        "exit 0",
    ).joinToString("\n") + "\n"

    fun startAvm(nonce: String): String = listOf(
        "#!/system/bin/sh",
        "setprop ctl.start AVMapp",
        "echo $nonce > $ARM",
        "chmod 666 $ARM",
        "exit 0"
    ).joinToString("\n") + "\n"
}
