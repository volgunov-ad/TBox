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
    const val CAMD_PREFS = "$WORK/camd_prefs.xml"
    const val DONE = "$WORK/done"
    const val ARM = "$WORK/arm"
    const val RUN_LOG = "$WORK/run.log"

    const val CAMD_PREFS_SOURCE = "/data/data/vad.dashing.camd/shared_prefs/camd.xml"
    const val CAMD_GRAB_LOG = "/data/local/tmp/camd-grab.log"
    const val CAMD_GRAB_VERSION = "/data/local/tmp/camd_grab.ver"

    const val AUTORUN = "/sdcard/autorun.sh"
    const val AUTORUN_FLAG = "/sdcard/autorun.flag"
    const val AUTORUN_PROP = "vendor.autorun.sdcard.logfile"

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
        "rm -f $RAW $LOGCAT $DONE $CAMD_PREFS",
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
        "logcat -v time -T 1 -s CamdRecorder:I CamdQcar:I > $LOGCAT 2>&1 &",
        "LOGCAT_PID=\$!",
        "$SAMPLER $RAW $CTL \$NONCE \$2 \$3",
        "kill \$LOGCAT_PID",
        "chmod 644 $RAW $LOGCAT",
        "echo \$NONCE > $DONE",
        "chmod 644 $DONE",
    ).joinToString("\n") + "\n"

    /**
     * mb_daemon deletes autorun.sh after running it, so an existing file is another app's
     * script (camd, DCL, dcl_install, UsbMount) still waiting or running.
     */
    fun isForeignAutorun(text: String?): Boolean = text != null && !text.contains(ARM)
}
