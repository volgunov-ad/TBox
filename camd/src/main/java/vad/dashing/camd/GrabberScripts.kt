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
    const val VERSION_FILE = "/data/local/tmp/camd_grab.ver"
    /** rootsvc helper.ensure install path (plan W5). */
    const val HELPER_NAME = "camd_grab"
    const val HELPER_VERSION_FILE = "/data/local/vad-rootsvc/helpers/camd_grab.ver"
    const val GRAB_VERSION = "9"
    const val BEAT = "/data/local/tmp/camd-grab.beat"
    const val LOG = "/data/local/tmp/camd-grab.log"
    const val ARM = "/data/local/tmp/camd-grab.arm"
    /** MbDaemonChannel body + substring that appears in the one-line autorun exec. */
    const val ARM_BODY = "/sdcard/camd_grab_arm.sh"
    const val OWN_MARKER = "camd_grab_arm"
    const val FRAME_RAM = "/mnt/camd-ram"

    /** True when either the rootsvc helper or the legacy tmp version stamp matches. */
    @JvmStatic
    fun grabVersionOk(): Boolean {
        val helpers = runCatching {
            java.io.File(HELPER_VERSION_FILE).readText().trim() == GRAB_VERSION
        }.getOrDefault(false)
        if (helpers) return true
        return runCatching {
            java.io.File(VERSION_FILE).readText().trim() == GRAB_VERSION
        }.getOrDefault(false)
    }

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
        lines.add("echo $GRAB_VERSION > $VERSION_FILE")
        lines.add("chmod 666 $VERSION_FILE")
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

    fun grantAccessibility(nonce: String): String {
        val svc = "vad.dashing.camd/vad.dashing.camd.CamdAccessibilityService"
        val lines = ArrayList<String>()
        lines.add("#!/system/bin/sh")
        lines.add("cur=\$(settings get secure enabled_accessibility_services 2>/dev/null)")
        lines.add("case \"\$cur\" in")
        lines.add("  *\"$svc\"*) : ;;")
        lines.add("  null|\"\") cur=\"$svc\" ;;")
        lines.add("  *) cur=\"\$cur:$svc\" ;;")
        lines.add("esac")
        lines.add("settings put secure enabled_accessibility_services \"\$cur\"")
        lines.add("settings put secure accessibility_enabled 1")
        lines.add("echo $nonce > $ARM")
        lines.add("chmod 666 $ARM")
        lines.add("exit 0")
        return lines.joinToString("\n") + "\n"
    }

    const val USB_UNLOCK_OUT = "/data/local/tmp/camd-usb-unlock.txt"

    fun usbUnlock(nonce: String, apkPath: String): String = listOf(
        "#!/system/bin/sh",
        "CLASSPATH='$apkPath' /system/bin/app_process /system/bin vad.dashing.camd.UsbUnlockMain > $USB_UNLOCK_OUT 2>&1",
        "chmod 644 $USB_UNLOCK_OUT",
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
