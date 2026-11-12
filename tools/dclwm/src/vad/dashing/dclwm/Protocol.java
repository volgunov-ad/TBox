package vad.dashing.dclwm;

/** Shared names for the window helper and the launcher that talks to it. */
public final class Protocol {
    public static final String SOCKET = "dclwm";
    public static final String LAUNCHER_PACKAGE = "ras.dashing.tbox.launcher";
    /** Off-screen stack rect. A 1x1 resize stays visible: this firmware clamps it to 88 px. */
    public static final int OFF_LEFT = 2000;
    public static final int OFF_TOP = 2000;
    public static final int OFF_RIGHT = 2200;
    public static final int OFF_BOTTOM = 2200;
    public static final String DEX_ASSET = "dclwm.dex";
    public static final String PID_FILE = "/data/local/tmp/dclwm.pid";
    public static final String LOG_FILE = "/data/local/tmp/dclwm.log";
    public static final String ARM_MARKER = "/data/local/tmp/dclwm.arm";
    public static final String AUTORUN = "/sdcard/autorun.sh";
    public static final String AUTORUN_FLAG = "/sdcard/autorun.flag";
    public static final String AUTORUN_PROP = "vendor.autorun.sdcard.logfile";

    private Protocol() {
    }
}
