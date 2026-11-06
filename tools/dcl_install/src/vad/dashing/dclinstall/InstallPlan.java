package vad.dashing.dclinstall;

/** Shell script mb_daemon runs as root. Line endings are LF. */
final class InstallPlan {
    static final String DCL_PACKAGE = "ras.dashing.tbox.launcher";
    static final String DCL_HOME = DCL_PACKAGE + "/vad.dashing.tbox.LauncherHomeActivity";
    static final String DCL_NOTIFICATION =
            DCL_PACKAGE + "/vad.dashing.tbox.MediaControlNotificationListenerService";
    static final String DCL_ACCESSIBILITY =
            DCL_PACKAGE + "/vad.dashing.tbox.ui.launcher.LauncherNavAccessibilityService";
    static final String BOOT_WALLPAPER =
            "vad.dashing.dclinstall/vad.dashing.dclinstall.BootWallpaperService";

    static final String LAUNCHER = "com.wt.launcher3";
    static final String SYSTEMUI = "com.android.systemui";
    static final String IOTTUBE = "com.autopai.iottube";
    static final String VOICE = "com.iflytek.cutefly.speechclient.hmi";
    static final String SPEECH_CLIENT = "com.mengbo.speechclient";
    static final String MANUAL = "com.mengbo.electronicmanual";

    static final String PAYLOAD_DIR = "/sdcard/dcl_install";
    static final String LOG_FILE = "/sdcard/dcl_install_log.txt";
    static final String RESULT_FILE = "/sdcard/dcl_install_result.txt";
    static final String AUTORUN = "/sdcard/autorun.sh";
    static final String FLAG = "/sdcard/autorun.flag";
    static final String PROP = "vendor.autorun.sdcard.logfile";

    final String dclApkPath;
    final boolean uninstallExisting;
    final boolean hideSystemUi;
    final boolean hideIottube;
    final boolean hideVoice;
    final boolean hideSpeechClient;
    final boolean hideManual;

    InstallPlan(
            String dclApkPath,
            boolean uninstallExisting,
            boolean hideSystemUi,
            boolean hideIottube,
            boolean hideVoice,
            boolean hideSpeechClient,
            boolean hideManual) {
        this.dclApkPath = dclApkPath;
        this.uninstallExisting = uninstallExisting;
        this.hideSystemUi = hideSystemUi;
        this.hideIottube = hideIottube;
        this.hideVoice = hideVoice;
        this.hideSpeechClient = hideSpeechClient;
        this.hideManual = hideManual;
    }

    String wallpaperComponent() {
        return BOOT_WALLPAPER;
    }

    String toScript() {
        String wallpaper = wallpaperComponent();
        StringBuilder sh = new StringBuilder();
        sh.append("#!/system/bin/sh\n");
        sh.append("LOG=").append(q(LOG_FILE)).append("\n");
        sh.append("RES=").append(q(RESULT_FILE)).append("\n");
        sh.append(": > \"$LOG\"\n");
        sh.append("rm -f \"$RES\"\n");
        sh.append("exec >>\"$LOG\" 2>&1\n");
        sh.append("echo START\n");
        sh.append("fail() {\n");
        sh.append("  echo \"FAIL $1\"\n");
        sh.append("  echo \"FAIL $1\" > \"$RES\"\n");
        sh.append("  rm -f ").append(q(AUTORUN)).append(' ').append(q(FLAG)).append("\n");
        sh.append("  setprop ").append(PROP).append(" ''\n");
        sh.append("  exit 1\n");
        sh.append("}\n");
        sh.append("append_secure() {\n");
        sh.append("  cur=`settings get secure \"$1\"`\n");
        sh.append("  if [ -z \"$cur\" ] || [ \"$cur\" = null ]; then\n");
        sh.append("    settings put secure \"$1\" \"$2\" || fail \"put $1\"\n");
        sh.append("  elif echo \"$cur\" | grep -F \"$2\" >/dev/null; then\n");
        sh.append("    echo \"keep $1\"\n");
        sh.append("  else\n");
        sh.append("    settings put secure \"$1\" \"$cur:$2\" || fail \"put $1\"\n");
        sh.append("  fi\n");
        sh.append("}\n");
        if (uninstallExisting) {
            sh.append("echo STEP uninstall\n");
            sh.append("pm uninstall ").append(DCL_PACKAGE).append(" || fail uninstall\n");
        }
        sh.append("echo STEP install_dcl\n");
        sh.append("pm install -r -d -g -t ").append(q(dclApkPath)).append(" || fail install_dcl\n");
        sh.append("echo STEP grants\n");
        grant(sh, "android.permission.ACCESS_FINE_LOCATION");
        grant(sh, "android.permission.ACCESS_COARSE_LOCATION");
        grant(sh, "android.permission.READ_EXTERNAL_STORAGE");
        grant(sh, "android.permission.WRITE_EXTERNAL_STORAGE");
        grant(sh, "android.permission.WRITE_SECURE_SETTINGS");
        grant(sh, "android.permission.MANAGE_ACTIVITY_STACKS");
        grant(sh, "android.permission.ACTIVITY_EMBEDDING");
        grant(sh, "android.permission.READ_LOGS");
        appops(sh, "SYSTEM_ALERT_WINDOW");
        appops(sh, "WRITE_SETTINGS");
        appops(sh, "REQUEST_INSTALL_PACKAGES");
        sh.append("settings put global enable_freeform_support 1 || fail freeform\n");
        sh.append("settings put global policy_control ").append(q("immersive.full=*")).append(" || fail policy_control\n");
        sh.append("echo STEP listeners\n");
        sh.append("cmd notification allow_listener ").append(q(DCL_NOTIFICATION)).append(" || fail notification\n");
        sh.append("append_secure enabled_notification_listeners ").append(q(DCL_NOTIFICATION)).append("\n");
        sh.append("append_secure enabled_accessibility_services ").append(q(DCL_ACCESSIBILITY)).append("\n");
        sh.append("settings put secure accessibility_enabled 1 || fail accessibility\n");
        sh.append("echo STEP home\n");
        sh.append("cmd package set-home-activity ").append(q(DCL_HOME)).append(" || fail home\n");
        sh.append("echo STEP usbmount\n");
        sh.append("pm install -r -d -g -t ").append(q(PAYLOAD_DIR + "/usbmount.apk")).append(" || fail usbmount\n");
        sh.append("echo STEP wallpaper\n");
        sh.append("CLASSPATH=").append(q(PAYLOAD_DIR + "/setwallpaper.dex"));
        sh.append(" app_process /system/bin SetWallpaper ").append(q(wallpaper)).append(" || fail wallpaper\n");
        sh.append("sleep 1\n");
        sh.append("dumpsys wallpaper | grep -F ").append(q(wallpaper)).append(" >/dev/null || fail wallpaper_check\n");
        sh.append("echo STEP hide\n");
        sh.append("pm enable ").append(LAUNCHER).append(" || fail enable_launcher\n");
        sh.append("pm hide ").append(LAUNCHER).append(" || fail hide_launcher\n");
        hideIf(sh, hideSystemUi, SYSTEMUI);
        hideIf(sh, hideIottube, IOTTUBE);
        hideIf(sh, hideVoice, VOICE);
        hideIf(sh, hideSpeechClient, SPEECH_CLIENT);
        hideIf(sh, hideManual, MANUAL);
        sh.append("cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | grep -F ");
        sh.append(q(DCL_PACKAGE)).append(" >/dev/null || fail home_check\n");
        sh.append("echo OK\n");
        sh.append("echo OK > \"$RES\"\n");
        sh.append("rm -rf ").append(q(PAYLOAD_DIR)).append(' ').append(q(AUTORUN)).append(' ').append(q(FLAG)).append("\n");
        sh.append("setprop ").append(PROP).append(" ''\n");
        sh.append("sync\n");
        sh.append("reboot\n");
        return sh.toString();
    }

    private static void grant(StringBuilder sh, String permission) {
        sh.append("pm grant ").append(DCL_PACKAGE).append(' ').append(permission);
        sh.append(" || fail grant_").append(permission.substring(permission.lastIndexOf('.') + 1)).append("\n");
    }

    private static void appops(StringBuilder sh, String op) {
        sh.append("appops set ").append(DCL_PACKAGE).append(' ').append(op).append(" allow || fail appops_");
        sh.append(op).append("\n");
    }

    private static void hideIf(StringBuilder sh, boolean hide, String pkg) {
        if (!hide) return;
        sh.append("pm hide ").append(pkg).append(" || fail hide_").append(pkg).append("\n");
    }

    static String q(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }
}
