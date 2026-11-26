package vad.dashing.dclwm;

/**
 * The one-shot script mb_daemon runs. It starts the helper and returns.
 * Does not delete autorun.sh, the shared flag, or clear the property — mb_daemon
 * cleans those itself (channel-lock protocol §3.4).
 */
public final class ArmScript {
    private ArmScript() {
    }

    public static String text(String dexPath) {
        return text(dexPath, "started");
    }

    /** @param nonce written into the arm marker so the caller can wait for this run */
    public static String text(String dexPath, String nonce) {
        String qdex = "'" + dexPath.replace("'", "'\\''") + "'";
        String qnonce = nonce.replace("'", "").replace("\n", "");
        return "#!/system/bin/sh\n"
                + "DEX=" + qdex + "\n"
                + "PID=" + Protocol.PID_FILE + "\n"
                + "LOG=" + Protocol.LOG_FILE + "\n"
                + "MARK=" + Protocol.ARM_MARKER + "\n"
                + "if [ -f \"$PID\" ]; then\n"
                + "  old=`cat \"$PID\"`\n"
                + "  if kill -0 \"$old\" 2>/dev/null; then\n"
                + "    echo already " + qnonce + " > \"$MARK\"\n"
                + "    chmod 666 \"$MARK\"\n"
                + "    exit 0\n"
                + "  fi\n"
                + "fi\n"
                + "if [ ! -f \"$DEX\" ]; then\n"
                + "  echo missing " + qnonce + " > \"$MARK\"\n"
                + "  chmod 666 \"$MARK\"\n"
                + "  exit 1\n"
                + "fi\n"
                + ": >> \"$LOG\"\n"
                + "chmod 666 \"$LOG\"\n"
                + "export CLASSPATH=\"$DEX\"\n"
                + "setsid /system/bin/app_process /system/bin vad.dashing.dclwm.Helper >> \"$LOG\" 2>&1 < /dev/null &\n"
                + "i=0\n"
                + "while [ \"$i\" -lt 25 ]; do\n"
                + "  if [ -f \"$PID\" ]; then\n"
                + "    echo started " + qnonce + " > \"$MARK\"\n"
                + "    chmod 666 \"$MARK\" \"$LOG\"\n"
                + "    exit 0\n"
                + "  fi\n"
                + "  i=$((i + 1))\n"
                + "  sleep 0.2\n"
                + "done\n"
                + "echo failed " + qnonce + " > \"$MARK\"\n"
                + "chmod 666 \"$MARK\"\n"
                + "exit 1\n";
    }

    public static void main(String[] args) {
        String path = args.length == 0 ? "/data/local/tmp/dclwm.dex" : args[0];
        System.out.print(text(path));
    }
}
