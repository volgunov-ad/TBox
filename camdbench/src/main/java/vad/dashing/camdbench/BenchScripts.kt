package vad.dashing.camdbench

/** Root-side paths and the scripts mb_daemon runs for a benchmark. */
object BenchScripts {
    const val PACKAGE_FILES = "/data/data/vad.dashing.camdbench/files"
    const val SAMPLER_ASSET = "camdbench_sampler"
    const val SAMPLER_STAGED = "$PACKAGE_FILES/camdbench_sampler"
    const val RUN_STAGED = "$PACKAGE_FILES/run.sh"
    const val CTL = "$PACKAGE_FILES/ctl"

    const val WORK = "/data/local/tmp/camdbench"
    const val SAMPLER = "$WORK/camdbench_sampler"
    const val RUN = "$WORK/run.sh"
    const val RAW = "$WORK/raw.txt"
    const val LOGCAT = "$WORK/logcat.txt"
    const val LOGCAT_PID = "$WORK/logcat.pid"
    const val LOGCAT_STOP = "$WORK/logcat.stop"
    const val CAMD_PREFS = "$WORK/camd_prefs.xml"
    const val DONE = "$WORK/done"
    const val ARM = "$WORK/arm"
    const val RUN_LOG = "$WORK/run.log"

    const val CAMD_PREFS_SOURCE = "/data/data/vad.dashing.camd/shared_prefs/camd.xml"
    const val CAMD_GRAB_LOG = "/data/local/tmp/camd-grab.log"
    /** rootsvc helper.ensure redirects camd_grab stdout/stderr here. */
    const val CAMD_GRAB_LOG_HELPER = "/data/local/vad-rootsvc/helpers/camd_grab.log"
    const val CAMD_GRAB_VERSION = "/data/local/tmp/camd_grab.ver"
    const val CAMD_GRAB_VERSION_HELPER = "/data/local/vad-rootsvc/helpers/camd_grab.ver"

    const val AUTORUN = "/sdcard/autorun.sh"
    const val AUTORUN_FLAG = "/sdcard/autorun.flag"
    const val AUTORUN_PROP = "vendor.autorun.sdcard.logfile"
    /** MbDaemonChannel body + substring that appears in the one-line autorun exec. */
    const val ARM_BODY = "/sdcard/camdbench_arm.sh"
    const val OWN_MARKER = "camdbench_arm"

    private const val LOGCAT_FILTER = "-v time -s CamdRecorder:I CamdQcar:I"

    const val INTERVAL_MS = 2_000L

    /** The sampler stops by itself this long after the planned end if the app is gone. */
    const val SAMPLER_GRACE_SEC = 60

    fun ctlRun(nonce: String): String = "run $nonce\n"

    /** mb_daemon script: stage the sampler and start run.sh in the background. */
    fun start(nonce: String, maxSec: Int): String = listOf(
        "#!/system/bin/sh",
        "mkdir -p $WORK",
        "chmod 777 $WORK",
        "ps -A | grep camdbench_sampler | grep -v grep | while read user pid rest; do kill \$pid; done",
        "rm -f $RAW $LOGCAT $LOGCAT_PID $LOGCAT_STOP $DONE $CAMD_PREFS",
        "cp $SAMPLER_STAGED $SAMPLER",
        "chmod 755 $SAMPLER",
        "cp $RUN_STAGED $RUN",
        "chmod 755 $RUN",
        "sh $RUN $nonce $maxSec $INTERVAL_MS > $RUN_LOG 2>&1 < /dev/null &",
        "echo $nonce > $ARM",
        "chmod 644 $ARM",
        "exit 0",
    ).joinToString("\n") + "\n"

    /** Runs as root for the whole measurement; leaves [DONE] with the nonce at the end. */
    fun run(): String = listOf(
        "#!/system/bin/sh",
        "trap '' HUP",
        "NONCE=\$1",
        "cp $CAMD_PREFS_SOURCE $CAMD_PREFS 2>/dev/null",
        "chmod 644 $CAMD_PREFS 2>/dev/null",
        // logcat readers get dropped by logd ("read: unexpected EOF!"), so restart them;
        // the final dump backfills the gap, duplicates are removed when parsing.
        "(",
        "  while [ ! -f $LOGCAT_STOP ]; do",
        "    logcat $LOGCAT_FILTER -T 1 >> $LOGCAT 2>&1 &",
        "    echo \$! > $LOGCAT_PID",
        "    wait \$!",
        "    sleep 1",
        "  done",
        ") &",
        "$SAMPLER $RAW $CTL \$NONCE \$2 \$3",
        "touch $LOGCAT_STOP",
        "kill \$(cat $LOGCAT_PID) 2>/dev/null",
        "sleep 2",
        "kill \$(cat $LOGCAT_PID) 2>/dev/null",
        "logcat $LOGCAT_FILTER -d >> $LOGCAT 2>&1",
        "chmod 644 $RAW $LOGCAT",
        "echo \$NONCE > $DONE",
        "chmod 644 $DONE",
    ).joinToString("\n") + "\n"

    /**
     * mb_daemon deletes autorun.sh after running it, so an existing file is another app's
     * script (camd, DCL, dcl_install, UsbMount) still waiting or running.
     * Own leftovers: ARM path in the old inline body, or MbDaemonChannel exec marker.
     */
    fun isForeignAutorun(text: String?): Boolean {
        if (text == null) return false
        if (text.contains(ARM)) return false
        if (text.contains(OWN_MARKER)) return false
        return true
    }
}
