package vad.dashing.camd

/**
 * One-shot root probe. Writes a qcarcam log into /sdcard/Download so it can be
 * copied off the car. The stop variant parks AVMapp only for the 20 second run
 * and starts it again before the script exits.
 */
object ProbeScripts {
    const val RESULT_FILE = "/data/local/tmp/camd-probe-result.txt"
    const val DOWNLOAD_DIR = "/sdcard/Download"
    const val SECONDS = 20

    fun script(stopAvm: Boolean, input: CameraInput, nonce: String): String {
        val kind = if (stopAvm) "stopavm" else "nostop"
        val lines = ArrayList<String>()
        lines.add("#!/system/bin/sh")
        lines.add("STAMP=`date +%Y%m%d-%H%M%S`")
        lines.add("LOG=$DOWNLOAD_DIR/camd-probe-$kind-${input.id}-\$STAMP.txt")
        lines.add("mkdir -p $DOWNLOAD_DIR")
        lines.add("killall qcarcam_test >/dev/null 2>&1")
        lines.add("sleep 1")
        if (stopAvm) {
            lines.add("finish() {")
            lines.add("  setprop ctl.start AVMapp")
            lines.add("  echo \"=== AVMapp after restart ===\" >> \"\$LOG\"")
            lines.add("  getprop init.svc.AVMapp >> \"\$LOG\"")
            lines.add("  ps -A | grep AVMapp >> \"\$LOG\"")
            lines.add("  chmod 666 \"\$LOG\"")
            lines.add("  echo \"$nonce\" > $RESULT_FILE")
            lines.add("  echo \"\$LOG\" >> $RESULT_FILE")
            lines.add("  chmod 666 $RESULT_FILE")
            lines.add("}")
            lines.add("trap finish EXIT")
            lines.add("setprop ctl.stop AVMapp")
            lines.add("sleep 2")
        }
        lines.add("echo \"variant=$kind input=${input.id} ${input.width}x${input.height} nonce=$nonce\" > \"\$LOG\"")
        lines.add("date >> \"\$LOG\"")
        lines.add("echo \"=== props ===\" >> \"\$LOG\"")
        lines.add("getprop init.svc.AVMapp >> \"\$LOG\"")
        lines.add("getprop vendor.camera.mbhal.type >> \"\$LOG\"")
        lines.add("getprop vendor.cartype.version >> \"\$LOG\"")
        lines.add("echo \"=== ps before ===\" >> \"\$LOG\"")
        lines.add("ps -A | grep -E \"AVMapp|qcarcam|oms_service|dms_service\" >> \"\$LOG\"")
        lines.add("cat > /data/local/tmp/camd-probe.xml << 'EOF'")
        lines.add(WindowXml.build(input, 0.20f, 0.20f, 0.60f, 0.60f).trimEnd())
        lines.add("EOF")
        lines.add("echo \"=== qcarcam ${SECONDS}s ===\" >> \"\$LOG\"")
        lines.add("/system/bin/qcarcam_test -config=/data/local/tmp/camd-probe.xml -seconds=$SECONDS -nonInteractive -printfps >> \"\$LOG\" 2>&1")
        lines.add("echo \"=== ps after ===\" >> \"\$LOG\"")
        lines.add("ps -A | grep -E \"AVMapp|qcarcam\" >> \"\$LOG\"")
        if (!stopAvm) {
            lines.add("chmod 666 \"\$LOG\"")
            lines.add("echo \"$nonce\" > $RESULT_FILE")
            lines.add("echo \"\$LOG\" >> $RESULT_FILE")
            lines.add("chmod 666 $RESULT_FILE")
        }
        return lines.joinToString("\n") + "\n"
    }
}
