package vad.dashing.dclinstall;

/**
 * Shared markers and foreign-autorun helpers for the mb_daemon channel.
 * Arming goes through {@link RootExec} ({@code MbDaemonChannel} §3.4).
 * Pure Java so {@code tools/dcl_install/test.sh} can compile this file with the JDK only.
 */
final class MbDaemon {
    static final String INSTALL_BODY = "/sdcard/dcl_install.sh";
    static final String BOOT_BODY = "/sdcard/dcl_boot.sh";
    static final String APPOPS_BODY = "/sdcard/dcl_appops.sh";
    /** Both bodies live under this prefix: an autorun.sh naming it is our own leftover. */
    private static final String OWN_MARKER = InstallPlan.OWN_MARKER;

    private MbDaemon() {}

    /**
     * True when {@code autorun.sh} text exists and does not name one of our bodies.
     * Used by unit tests; the live path uses {@code MbDaemonChannel}.
     */
    static boolean isForeign(String autorunText) {
        return autorunText != null && !autorunText.contains(OWN_MARKER);
    }

    /** Prepend own-marker comment and nonce so {@code MbDaemonChannel} can return promptly. */
    static String wrapWithNonce(String script, String nonce) {
        String body = script == null ? "" : script;
        if (body.startsWith("#!")) {
            int nl = body.indexOf('\n');
            if (nl >= 0) {
                body = body.substring(nl + 1);
            }
        }
        StringBuilder sh = new StringBuilder();
        sh.append("#!/system/bin/sh\n");
        sh.append("# ").append(InstallPlan.OWN_MARKER).append('\n');
        sh.append("echo started ").append(nonce).append(" > ")
                .append(InstallPlan.q(InstallPlan.ARM_MARKER)).append('\n');
        sh.append("chmod 666 ").append(InstallPlan.q(InstallPlan.ARM_MARKER)).append(" 2>/dev/null\n");
        sh.append(body);
        if (!body.endsWith("\n")) {
            sh.append('\n');
        }
        return sh.toString();
    }
}
