package vad.dashing.camd

/**
 * Shell texts installed on the head unit. Built from lines so a Windows checkout
 * cannot turn them into CRLF scripts. mb_daemon runs the arm script; the arm script
 * starts [daemon] and returns, so the daemon stays up after the one-shot root script.
 */
object DaemonScripts {
    const val PACKAGE_FILES = "/data/data/vad.dashing.camd/files"
    const val DAEMON_FILE = "$PACKAGE_FILES/camd-daemon.sh"
    const val XML_FILE = "$PACKAGE_FILES/camd-window.xml"
    const val CMD_FILE = "$PACKAGE_FILES/camd.cmd"
    const val PID_FILE = "/data/local/tmp/camd.pid"
    const val STATUS_FILE = "/data/local/tmp/camd.status"
    const val ARM_FILE = "/data/local/tmp/camd-arm.txt"
    const val QCAR_PID_FILE = "/data/local/tmp/camd-qcar.pid"
    const val QCAR_LOG = "/data/local/tmp/camd-qcar.log"
    const val DAEMON_LOG = "/data/local/tmp/camd-daemon.log"
    const val AUTORUN = "/sdcard/autorun.sh"
    const val AUTORUN_FLAG = "/sdcard/autorun.flag"
    const val AUTORUN_PROP = "vendor.autorun.sdcard.logfile"
    const val API_PORT = 8766

    fun daemon(): String = lf(
        "#!/system/bin/sh",
        "PIDFILE=$PID_FILE",
        "if [ -f \"\$PIDFILE\" ]; then",
        "  old=`cat \"\$PIDFILE\"`",
        "  if kill -0 \"\$old\" 2>/dev/null; then",
        "    exit 0",
        "  fi",
        "fi",
        "echo \$\$ > \"\$PIDFILE\"",
        "chmod 666 \"\$PIDFILE\"",
        "CMD=$CMD_FILE",
        "STATUS=$STATUS_FILE",
        "XML=$XML_FILE",
        "QPID=$QCAR_PID_FILE",
        "LOG=$QCAR_LOG",
        "write_status() {",
        "  echo \"\$1\" > \"\$STATUS\"",
        "  chmod 666 \"\$STATUS\"",
        "}",
        "stop_qcar() {",
        "  if [ -f \"\$QPID\" ]; then",
        "    q=`cat \"\$QPID\"`",
        "    if kill -0 \"\$q\" 2>/dev/null; then",
        "      kill \"\$q\" 2>/dev/null",
        "      sleep 1",
        "      kill -9 \"\$q\" 2>/dev/null",
        "    fi",
        "    rm -f \"\$QPID\"",
        "  fi",
        "}",
        "write_status \"state=idle seq=0\"",
        "while true; do",
        "  if [ -f \"\$CMD\" ]; then",
        "    line=`cat \"\$CMD\"`",
        "    rm -f \"\$CMD\"",
        "    set -- \$line",
        "    op=\$1",
        "    if [ \"\$op\" = \"open\" ]; then",
        "      id=\$2",
        "      x=\$3",
        "      y=\$4",
        "      w=\$5",
        "      h=\$6",
        "      seq=\$7",
        "      stop_qcar",
        "      : > \"\$LOG\"",
        "      chmod 666 \"\$LOG\"",
        "      /system/bin/qcarcam_test -config=\"\$XML\" -seconds=86400 -nonInteractive >> \"\$LOG\" 2>&1 &",
        "      echo \$! > \"\$QPID\"",
        "      chmod 666 \"\$QPID\"",
        "      sleep 1",
        "      if kill -0 \$! 2>/dev/null; then",
        "        write_status \"state=open input=\$id x=\$x y=\$y w=\$w h=\$h qpid=\$! seq=\$seq\"",
        "      else",
        "        write_status \"state=error error=qcarcam_exit input=\$id seq=\$seq\"",
        "      fi",
        "    elif [ \"\$op\" = \"close\" ]; then",
        "      seq=\$2",
        "      stop_qcar",
        "      write_status \"state=idle seq=\$seq\"",
        "    else",
        "      write_status \"state=error error=bad_cmd seq=0\"",
        "    fi",
        "  fi",
        "  sleep 1",
        "  echo \$\$ > \"\$PIDFILE\"",
        "done"
    )

    fun arm(nonce: String): String = lf(
        "#!/system/bin/sh",
        "if [ ! -f $DAEMON_FILE ]; then",
        "  echo \"missing $nonce\" > $ARM_FILE",
        "  chmod 666 $ARM_FILE",
        "  exit 1",
        "fi",
        "if [ -f $PID_FILE ]; then",
        "  old=`cat $PID_FILE`",
        "  if kill -0 \"\$old\" 2>/dev/null; then",
        "    echo \"already $nonce\" > $ARM_FILE",
        "    chmod 666 $ARM_FILE",
        "    exit 0",
        "  fi",
        "fi",
        "sh $DAEMON_FILE >> $DAEMON_LOG 2>&1 &",
        "echo \"started $nonce\" > $ARM_FILE",
        "chmod 666 $ARM_FILE",
        "chmod 666 $DAEMON_LOG"
    )

    private fun lf(vararg lines: String): String = lines.joinToString("\n") + "\n"
}
