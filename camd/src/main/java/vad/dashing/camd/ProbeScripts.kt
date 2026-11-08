package vad.dashing.camd

/**
 * One-shot root probe. The long script is a separate file. /sdcard/autorun.sh only
 * starts it in the background and returns, so mb_daemon is free for the next probe.
 * The Download log is created before AVMapp is stopped.
 */
object ProbeScripts {
    const val RESULT_FILE = "/data/local/tmp/camd-probe-result.txt"
    const val DOWNLOAD_DIR = "/sdcard/Download"
    const val SECONDS = 20

    fun bodyPath(nonce: String): String = "/data/data/vad.dashing.camd/files/camd-probe-$nonce.sh"

    fun launcher(bodyPath: String): String = lf(
        "#!/system/bin/sh",
        "sh $bodyPath >/data/local/tmp/camd-probe-runner.log 2>&1 &",
        "exit 0"
    )

    fun body(stopAvm: Boolean, input: CameraInput, nonce: String): String {
        val kind = if (stopAvm) "stopavm" else "nostop"
        val lines = ArrayList<String>()
        lines.add("#!/system/bin/sh")
        lines.add("STAMP=`date +%Y%m%d-%H%M%S`")
        lines.add("LOG=$DOWNLOAD_DIR/camd-probe-$kind-${input.id}-\$STAMP.txt")
        lines.add("mkdir -p $DOWNLOAD_DIR")
        lines.add("echo \"variant=$kind input=${input.id} ${input.width}x${input.height} nonce=$nonce\" > \"\$LOG\"")
        lines.add("date >> \"\$LOG\"")
        lines.add("chmod 666 \"\$LOG\"")
        lines.add("ps -A | grep qcarcam_test | grep -v grep | while read user pid rest; do kill -9 \"\$pid\"; done")
        lines.add("sleep 1")
        if (stopAvm) {
            lines.add("echo \"=== stopping AVMapp ===\" >> \"\$LOG\"")
            lines.add("setprop ctl.stop AVMapp")
            lines.add("sleep 2")
            lines.add("echo \"=== AVMapp after stop ===\" >> \"\$LOG\"")
            lines.add("getprop init.svc.AVMapp >> \"\$LOG\"")
            lines.add("ps -A | grep AVMapp >> \"\$LOG\"")
        }
        lines.add("echo \"=== props ===\" >> \"\$LOG\"")
        lines.add("getprop init.svc.AVMapp >> \"\$LOG\"")
        lines.add("getprop vendor.camera.mbhal.type >> \"\$LOG\"")
        lines.add("getprop vendor.cartype.version >> \"\$LOG\"")
        lines.add("echo \"=== ps before ===\" >> \"\$LOG\"")
        lines.add("ps -A | grep -E \"AVMapp|qcarcam|oms_service|dms_service\" >> \"\$LOG\"")
        lines.add("cat > /data/local/tmp/camd-probe.xml << 'EOF'")
        lines.add(WindowXml.build(input, 0.20f, 0.20f, 0.60f, 0.60f).trimEnd())
        lines.add("EOF")
        lines.add("logcat -s qcarcam_test:V -v time >> \"\$LOG\" 2>&1 &")
        lines.add("LC=\$!")
        lines.add("echo \"=== qcarcam ${SECONDS}s ===\" >> \"\$LOG\"")
        lines.add("/system/bin/qcarcam_test -config=/data/local/tmp/camd-probe.xml -seconds=$SECONDS -nonInteractive -printfps >> \"\$LOG\" 2>&1")
        lines.add("echo \"qcarcam_exit=\$?\" >> \"\$LOG\"")
        lines.add("kill \$LC >/dev/null 2>&1")
        lines.add("echo \"=== ps after ===\" >> \"\$LOG\"")
        lines.add("ps -A | grep -E \"AVMapp|qcarcam\" >> \"\$LOG\"")
        if (stopAvm) {
            lines.add("setprop ctl.start AVMapp")
            lines.add("sleep 1")
            lines.add("echo \"=== AVMapp after restart ===\" >> \"\$LOG\"")
            lines.add("getprop init.svc.AVMapp >> \"\$LOG\"")
            lines.add("ps -A | grep AVMapp >> \"\$LOG\"")
        }
        lines.add("chmod 666 \"\$LOG\"")
        lines.add("echo \"$nonce\" > $RESULT_FILE")
        lines.add("echo \"\$LOG\" >> $RESULT_FILE")
        lines.add("chmod 666 $RESULT_FILE")
        return lines.joinToString("\n") + "\n"
    }

    private fun lf(vararg lines: String): String = lines.joinToString("\n") + "\n"
}
