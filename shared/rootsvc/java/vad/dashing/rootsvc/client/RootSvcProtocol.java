package vad.dashing.rootsvc.client;

/**
 * Wire constants for rootsvc v1. Pure Java so DCL and tools/ can copy this package as-is.
 */
public final class RootSvcProtocol {
    public static final int PROTO = 1;
    public static final String SERVER_VERSION = "1.0.3";
    /** Supervisor: refresh dex and restart immediately (not a crash). */
    public static final int EXIT_UPGRADE = 42;
    /** Supervisor: deliberate {@code admin.restart} — reset fail counter, no backoff. */
    public static final int EXIT_RESTART = 43;
    public static final String SOCKET = "vad.dashing.rootsvc";
    public static final String PACKAGE = "vad.dashing.rootsvc";
    public static final String ENSURE_ACTION = "vad.dashing.rootsvc.ENSURE";
    /** Extra on {@link #ENSURE_ACTION}: bypass crashloop skip (Monitor «Перезапустить»). */
    public static final String ENSURE_EXTRA_FORCE = "force";
    public static final String LOCK_SOCKET = "vad.dashing.mbdaemon.lock";

    public static final String WATCHDOG_A11Y =
            "vad.dashing.rootsvc/vad.dashing.rootsvc.RootsvcAccessibilityService";

    public static final String DIR = "/data/local/vad-rootsvc";
    public static final String HELPERS_DIR = DIR + "/helpers";
    public static final String DEX_PATH = DIR + "/rootsvc.dex";
    public static final String STATE_PATH = DIR + "/state";
    public static final String LOG_PATH = DIR + "/rootsvc.log";
    public static final String SUPERVISOR_PID = DIR + "/supervisor.pid";
    public static final String BOOT_BODY = "/sdcard/vad_rootsvc_boot.sh";
    public static final String OWN_MARKER = "/sdcard/vad_rootsvc_";
    public static final String ARM_MARKER = "/sdcard/vad_rootsvc_arm";

    public static final String AUTORUN = "/sdcard/autorun.sh";
    public static final String AUTORUN_FLAG = "/sdcard/autorun.flag";
    public static final String AUTORUN_PROP = "vendor.autorun.sdcard.logfile";

    public static final int LINE_LIMIT = 16 * 1024;
    /** Cap each of stdout/stderr in {@code script.run} so the JSON line fits under {@link #LINE_LIMIT}. */
    public static final int SCRIPT_OUTPUT_LIMIT = 4 * 1024;
    /** Worker pool queue; overflow → wire {@code busy}. */
    public static final int WORKER_QUEUE_CAPACITY = 8;
    /** uid → packages cache TTL for {@code pm list packages -U}. */
    public static final long PEER_CACHE_TTL_MS = 60_000L;
    public static final long CONNECT_TIMEOUT_MS = 300L;
    public static final long ACK_TIMEOUT_MS = 1_000L;
    public static final long DEFAULT_RESULT_TIMEOUT_MS = 5_000L;
    public static final long HELPER_TIMEOUT_MS = 15_000L;
    public static final long SCRIPT_MAX_TIMEOUT_MS = 120_000L;
    public static final long IS_UP_CACHE_MS = 5_000L;
    public static final long DOWN_CACHE_MS = 30_000L;
    public static final long BOOT_WAIT_MS = 5_000L;
    public static final long BOOT_WINDOW_MS = 60_000L;

    public static final String ERR_FORBIDDEN = "forbidden";
    public static final String ERR_BAD_ARGS = "bad_args";
    public static final String ERR_UNKNOWN_CMD = "unknown_cmd";
    public static final String ERR_UNSUPPORTED = "unsupported";
    public static final String ERR_BUSY = "busy";
    public static final String ERR_TIMEOUT = "timeout";
    public static final String ERR_FAILED = "failed";
    public static final String ERR_PROTO = "proto";

    public static final String CMD_PING = "ping";
    public static final String CMD_HELLO = "hello";
    public static final String CMD_STATUS = "status";
    public static final String CMD_ADMIN_UPGRADE = "admin.upgrade";
    public static final String CMD_ADMIN_RESTART = "admin.restart";
    public static final String CMD_PKG_HIDE = "pkg.hide";
    public static final String CMD_PKG_UNHIDE = "pkg.unhide";
    public static final String CMD_PKG_ENABLE = "pkg.enable";
    public static final String CMD_PKG_DISABLE = "pkg.disable";
    public static final String CMD_PKG_GRANT = "pkg.grant";
    public static final String CMD_PKG_REVOKE = "pkg.revoke";
    public static final String CMD_PKG_APPOPS = "pkg.appops";
    public static final String CMD_PKG_UNSTOP = "pkg.unstop";
    public static final String CMD_SETTINGS_GET = "settings.get";
    public static final String CMD_SETTINGS_PUT = "settings.put";
    public static final String CMD_SETTINGS_A11Y_ADD = "settings.a11y.add";
    public static final String CMD_SETTINGS_A11Y_REMOVE = "settings.a11y.remove";
    public static final String CMD_SVC_CTL = "svc.ctl";
    public static final String CMD_USB_UNLOCK = "usb.unlock";
    public static final String CMD_WALLPAPER_SET = "wallpaper.set";
    public static final String CMD_HELPER_ENSURE = "helper.ensure";
    public static final String CMD_WM_LIST = "wm.list";
    public static final String CMD_WM_RESIZE = "wm.resize";
    public static final String CMD_WM_MINIMIZE = "wm.minimize";
    public static final String CMD_WM_DISMISS = "wm.dismiss";
    public static final String CMD_POWER_REBOOT = "power.reboot";
    public static final String CMD_SCRIPT_RUN = "script.run";

    private RootSvcProtocol() {
    }
}
