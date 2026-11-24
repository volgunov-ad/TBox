package vad.dashing.dclinstall;

/**
 * Shell script mb_daemon runs as root. Line endings are LF.
 * Pure Java: tools/dcl_install/test.sh compiles this file with the JDK only.
 */
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
    static final String LAUNCHER_HOME = "com.wt.launcher3/.MainActivity";
    static final String SYSTEMUI = "com.android.systemui";
    static final String IOTTUBE = "com.autopai.iottube";
    static final String VOICE = "com.iflytek.cutefly.speechclient.hmi";
    static final String SPEECH_CLIENT = "com.mengbo.speechclient";
    static final String MANUAL = "com.mengbo.electronicmanual";
    static final String USB_HANDLER = "android.car.usb.handler";
    static final String USB_MOUNT = "vad.dashing.usbmount";
    static final String BLANK_WALLPAPER = "vad.dashing.blankwallpaper";

    /** While this file exists the head unit shows the stock look and the installer skips its boot fixups. */
    static final String DEALER_FLAG = "/sdcard/dcl_dealer.flag";
    static final String DEALER_FLAG_TMP = "/sdcard/dcl_dealer.flag.tmp";

    static final String PAYLOAD_DIR = "/sdcard/dcl_install";
    static final String STAGED_APK = PAYLOAD_DIR + "/dcl.apk";
    static final String LOG_FILE = "/sdcard/dcl_install_log.txt";
    static final String RESULT_FILE = "/sdcard/dcl_install_result.txt";
    static final String BOOT_LOG_FILE = "/sdcard/dcl_boot_log.txt";
    static final String BOOT_RESULT_FILE = "/sdcard/dcl_boot_result.txt";
    static final String AUTORUN = "/sdcard/autorun.sh";
    static final String FLAG = "/sdcard/autorun.flag";
    static final String PROP = "vendor.autorun.sdcard.logfile";
    /** Taken before a root script starts and removed by that script on every exit. */
    static final String LOCK_FILE = "/sdcard/dcl_install.lock";
    /** Substring MbDaemon writes into autorun.sh. A foreign client does not have it. */
    static final String OWN_MARKER = "/sdcard/dcl_";

    static final String PERM_FINE_LOCATION = "android.permission.ACCESS_FINE_LOCATION";
    static final String PERM_COARSE_LOCATION = "android.permission.ACCESS_COARSE_LOCATION";
    static final String PERM_READ_STORAGE = "android.permission.READ_EXTERNAL_STORAGE";
    static final String PERM_WRITE_STORAGE = "android.permission.WRITE_EXTERNAL_STORAGE";
    static final String PERM_WRITE_SECURE = "android.permission.WRITE_SECURE_SETTINGS";
    static final String PERM_MANAGE_STACKS = "android.permission.MANAGE_ACTIVITY_STACKS";
    static final String PERM_EMBEDDING = "android.permission.ACTIVITY_EMBEDDING";
    static final String PERM_READ_LOGS = "android.permission.READ_LOGS";

    final String dclApkPath;
    final boolean uninstallExisting;
    final boolean hideSystemUi;
    final boolean hideIottube;
    final boolean hideVoice;
    final boolean hideSpeechClient;
    final boolean hideManual;
    /** Launcher 0.8.2+ releases the vold USB gate and mounts sticks itself. */
    final boolean launcherHasNativeUnlock;

    InstallPlan(
            String dclApkPath,
            boolean uninstallExisting,
            boolean hideSystemUi,
            boolean hideIottube,
            boolean hideVoice,
            boolean hideSpeechClient,
            boolean hideManual,
            boolean launcherHasNativeUnlock) {
        this.dclApkPath = dclApkPath;
        this.uninstallExisting = uninstallExisting;
        this.hideSystemUi = hideSystemUi;
        this.hideIottube = hideIottube;
        this.hideVoice = hideVoice;
        this.hideSpeechClient = hideSpeechClient;
        this.hideManual = hideManual;
        this.launcherHasNativeUnlock = launcherHasNativeUnlock;
    }

    /**
     * Install of the APK the user picked. Native unlock follows that file's versionName,
     * not whatever launcher happens to be installed (or hidden) right now.
     */
    static InstallPlan forApk(
            String dclApkPath,
            boolean uninstallExisting,
            boolean hideSystemUi,
            boolean hideIottube,
            boolean hideVoice,
            boolean hideSpeechClient,
            boolean hideManual,
            String fileVersionName) {
        return new InstallPlan(
                dclApkPath,
                uninstallExisting,
                hideSystemUi,
                hideIottube,
                hideVoice,
                hideSpeechClient,
                hideManual,
                hasNativeUnlock(fileVersionName));
    }

    /**
     * True when versionName is 0.8.2 or newer.
     * Digits and dots are read up to the first other character, then padded to three parts.
     * "0.9" is 0.9.0, "v0.8.2" and "0.8.2ru" are 0.8.2.
     */
    static boolean hasNativeUnlock(String versionName) {
        int[] parts = versionParts(versionName);
        if (parts == null) return false;
        int[] min = new int[] {0, 8, 2};
        for (int i = 0; i < 3; i++) {
            if (parts[i] != min[i]) return parts[i] > min[i];
        }
        return true;
    }

    static int[] versionParts(String versionName) {
        if (versionName == null) return null;
        int i = 0;
        int n = versionName.length();
        while (i < n) {
            char c = versionName.charAt(i);
            if (c >= '0' && c <= '9') break;
            if (c == 'v' || c == 'V' || c == ' ' || c == '\t') {
                i++;
                continue;
            }
            return null;
        }
        if (i >= n) return null;
        int start = i;
        while (i < n) {
            char c = versionName.charAt(i);
            if ((c >= '0' && c <= '9') || c == '.') i++;
            else break;
        }
        String[] raw = versionName.substring(start, i).split("\\.", -1);
        int[] parts = new int[] {0, 0, 0};
        int count = 0;
        boolean digit = false;
        for (int p = 0; p < raw.length && count < 3; p++) {
            String piece = raw[p];
            if (piece.length() == 0) {
                parts[count++] = 0;
                continue;
            }
            long value;
            try {
                value = Long.parseLong(piece);
            } catch (NumberFormatException unparsable) {
                return null;
            }
            if (value < 0 || value > Integer.MAX_VALUE) return null;
            parts[count++] = (int) value;
            digit = true;
        }
        if (!digit) return null;
        return parts;
    }

    String wallpaperComponent() {
        return BOOT_WALLPAPER;
    }

    String toScript() {
        StringBuilder sh = scriptHeader();
        sh.append("echo STEP stage\n");
        sh.append("mkdir -p ").append(q(PAYLOAD_DIR)).append(" || fail stage\n");
        sh.append("cp ").append(q(dclApkPath)).append(' ').append(q(STAGED_APK)).append(" || fail stage\n");
        if (uninstallExisting) {
            sh.append("rollback() {\n");
            sh.append("  pm unhide ").append(LAUNCHER).append("\n");
            sh.append("  pm enable ").append(LAUNCHER).append("\n");
            sh.append("  pm unhide ").append(SYSTEMUI).append("\n");
            sh.append("  pm enable ").append(SYSTEMUI).append("\n");
            sh.append("  cmd package set-home-activity ").append(q(LAUNCHER_HOME)).append("\n");
            sh.append("}\n");
            sh.append("echo STEP uninstall\n");
            sh.append("pm uninstall ").append(DCL_PACKAGE).append(" || fail uninstall\n");
            sh.append("echo STEP install_dcl\n");
            sh.append("pm install -r -d -g -t ").append(q(STAGED_APK)).append(" || { rollback; fail install_dcl; }\n");
        } else {
            sh.append("echo STEP install_dcl\n");
            sh.append("pm install -r -d -g -t ").append(q(STAGED_APK)).append(" || fail install_dcl\n");
        }
        appendSetupBody(sh);
        finishScript(sh, true);
        return sh.toString();
    }

    /** Everything from toScript except uninstall/install of the DCL APK, for an already installed launcher. */
    String toSetupScript() {
        StringBuilder sh = scriptHeader();
        sh.append("pm path ").append(DCL_PACKAGE).append(" >/dev/null || fail no_dcl\n");
        appendSetupBody(sh);
        finishScript(sh, true);
        return sh.toString();
    }

    private void appendSetupBody(StringBuilder sh) {
        appendSetupBody(sh, !launcherHasNativeUnlock, true);
    }

    /** Grants, HOME, wallpaper, hides. The USB mount install and the compile are optional. */
    private void appendSetupBody(StringBuilder sh, boolean installUsbmount, boolean compile) {
        appendGrants(sh);
        appendHome(sh);
        if (installUsbmount) {
            sh.append("echo STEP usbmount\n");
            sh.append("pm install -r -d -g -t ").append(q(PAYLOAD_DIR + "/usbmount.apk")).append(" || fail usbmount\n");
        }
        if (!launcherHasNativeUnlock) appendUnstopUsb(sh, PAYLOAD_DIR + "/setwallpaper.dex", true);
        appendWallpaper(sh);
        appendHides(sh);
        appendHomeCheck(sh);
        if (compile) appendCompile(sh);
    }

    /**
     * Dealer visit on: stock launcher, SystemUI, stock wallpaper and USB handler back.
     * Optional packages that were hidden are listed in the flag file, one name per line,
     * then shown, so the visit looks like a full stock head unit and dealer-off can hide them again.
     */
    String toDealerOnScript() {
        StringBuilder sh = scriptHeader();
        sh.append("pm path ").append(DCL_PACKAGE).append(" >/dev/null || fail no_dcl\n");
        sh.append("echo STEP dealer_defaults\n");
        sh.append("settings delete global enable_freeform_support || echo WARN freeform\n");
        sh.append("settings delete global policy_control || echo WARN policy_control\n");
        sh.append("remove_secure ").append(q("enabled_notification_listeners")).append(' ').append(q(DCL_NOTIFICATION)).append("\n");
        sh.append("remove_secure ").append(q("enabled_accessibility_services")).append(' ').append(q(DCL_ACCESSIBILITY)).append("\n");
        sh.append("cur=`settings get secure enabled_accessibility_services`\n");
        sh.append("if [ -z \"$cur\" ] || [ \"$cur\" = null ]; then\n");
        sh.append("  settings put secure accessibility_enabled 0 || echo WARN accessibility\n");
        sh.append("fi\n");
        sh.append("echo STEP dealer_record\n");
        sh.append(": > ").append(q(DEALER_FLAG_TMP)).append("\n");
        recordIfHidden(sh, SYSTEMUI);
        recordIfHidden(sh, IOTTUBE);
        recordIfHidden(sh, VOICE);
        recordIfHidden(sh, SPEECH_CLIENT);
        recordIfHidden(sh, MANUAL);
        sh.append("mv ").append(q(DEALER_FLAG_TMP)).append(' ').append(q(DEALER_FLAG)).append(" || fail dealer_flag\n");
        sh.append("echo STEP dealer_stock\n");
        unhide(sh, SYSTEMUI);
        unhide(sh, LAUNCHER);
        unhide(sh, IOTTUBE);
        unhide(sh, VOICE);
        unhide(sh, SPEECH_CLIENT);
        unhide(sh, MANUAL);
        sh.append("cmd package set-home-activity ").append(q(LAUNCHER_HOME)).append(" || fail home\n");
        sh.append("CLASSPATH=").append(q(PAYLOAD_DIR + "/setwallpaper.dex"));
        sh.append(" app_process /system/bin SetWallpaper default || echo WARN wallpaper\n");
        sh.append("echo STEP dealer_hide\n");
        hideChecked(sh, DCL_PACKAGE, true);
        if (!launcherHasNativeUnlock) hideChecked(sh, USB_MOUNT, false);
        hideChecked(sh, BLANK_WALLPAPER, false);
        unhide(sh, USB_HANDLER);
        finishScript(sh, true);
        return sh.toString();
    }

    /**
     * Dealer visit off. Hides come from the flag file written by dealer-on, not from the
     * checkboxes on screen, so a visit cannot change which optional packages were hidden.
     */
    String toDealerOffScript() {
        StringBuilder sh = scriptHeader();
        sh.append("echo STEP dealer_unhide\n");
        unhide(sh, DCL_PACKAGE);
        if (!launcherHasNativeUnlock) unhide(sh, USB_MOUNT);
        unhide(sh, BLANK_WALLPAPER);
        sh.append("pm path ").append(DCL_PACKAGE).append(" >/dev/null || fail no_dcl\n");
        appendGrants(sh);
        appendHome(sh);
        if (!launcherHasNativeUnlock) appendUnstopUsb(sh, PAYLOAD_DIR + "/setwallpaper.dex", true);
        appendWallpaper(sh);
        sh.append("echo STEP hide\n");
        sh.append("pm enable ").append(LAUNCHER).append(" || fail enable_launcher\n");
        hideChecked(sh, LAUNCHER, true);
        hideChecked(sh, USB_HANDLER, true);
        sh.append("if [ -f ").append(q(DEALER_FLAG)).append(" ]; then\n");
        sh.append("  while IFS= read -r pkg; do\n");
        sh.append("    [ -n \"$pkg\" ] || continue\n");
        sh.append("    pm hide \"$pkg\"\n");
        sh.append("    dumpsys package \"$pkg\" | grep -q 'hidden=true' || echo WARN \"hide_$pkg\"\n");
        sh.append("  done < ").append(q(DEALER_FLAG)).append("\n");
        sh.append("fi\n");
        appendHomeCheck(sh);
        sh.append("rm -f ").append(q(DEALER_FLAG)).append("\n");
        finishScript(sh, true);
        return sh.toString();
    }

    String toGrantScript() {
        StringBuilder sh = scriptHeader();
        sh.append("pm path ").append(DCL_PACKAGE).append(" >/dev/null || fail no_dcl\n");
        appendGrants(sh);
        finishScript(sh, false);
        return sh.toString();
    }

    String toHideScript() {
        StringBuilder sh = scriptHeader();
        sh.append("pm path ").append(DCL_PACKAGE).append(" >/dev/null || fail no_dcl\n");
        appendHome(sh);
        if (hideSystemUi) appendWallpaper(sh);
        appendHides(sh);
        finishScript(sh, true);
        return sh.toString();
    }

    String toWallpaperScript() {
        StringBuilder sh = scriptHeader();
        appendWallpaper(sh);
        finishScript(sh, false);
        return sh.toString();
    }

    String toCompileScript() {
        StringBuilder sh = scriptHeader();
        sh.append("pm path ").append(DCL_PACKAGE).append(" >/dev/null || fail no_dcl\n");
        appendCompile(sh);
        sh.append("am force-stop ").append(DCL_PACKAGE).append("\n");
        sh.append("am start -n ").append(q(DCL_HOME)).append(" || echo WARN start\n");
        finishScript(sh, false);
        return sh.toString();
    }

    String toUninstallUsbScript() {
        StringBuilder sh = scriptHeader();
        sh.append("echo STEP uninstall_usb\n");
        sh.append("if pm path ").append(USB_MOUNT).append(" >/dev/null; then\n");
        sh.append("  pm uninstall ").append(USB_MOUNT).append(" || fail uninstall_usb\n");
        sh.append("else\n");
        sh.append("  echo WARN no_usbmount\n");
        sh.append("fi\n");
        finishScript(sh, false);
        return sh.toString();
    }

    /**
     * After unlock: let vold scan USB sticks when this launcher needs it, put back the two
     * grants boot drops, and, if the system dropped the boot wallpaper, set it again.
     * Own log and result files, and no payload cleanup, so a running install is untouched.
     */
    String toBootScript(String helperDex, String unlockDex, boolean restoreWallpaper, boolean unlockUsb) {
        StringBuilder sh = scriptHeader(BOOT_LOG_FILE, BOOT_RESULT_FILE);
        sh.append("echo STEP usb\n");
        if (launcherHasNativeUnlock) {
            sh.append("if pm path ").append(USB_MOUNT).append(" >/dev/null; then\n");
            sh.append("  echo WARN usbmount_present\n");
            sh.append("fi\n");
        } else if (unlockUsb) {
            appendUnstopUsb(sh, helperDex, false);
            sh.append("CLASSPATH=").append(q(unlockDex));
            sh.append(" app_process /system/bin UnlockUsb || echo WARN unlock_usb\n");
        } else {
            sh.append("echo skip unlock\n");
        }
        if (restoreWallpaper) {
            sh.append("echo STEP wallpaper\n");
            sh.append("CLASSPATH=").append(q(helperDex));
            sh.append(" app_process /system/bin SetWallpaper ").append(q(BOOT_WALLPAPER)).append(" || echo WARN wallpaper\n");
        }
        sh.append("if pm path ").append(DCL_PACKAGE).append(" >/dev/null; then\n");
        sh.append("  echo STEP listeners\n");
        sh.append("  cmd notification allow_listener ").append(q(DCL_NOTIFICATION)).append(" || echo WARN notification\n");
        sh.append("  append_secure enabled_notification_listeners ").append(q(DCL_NOTIFICATION)).append("\n");
        sh.append("  append_secure enabled_accessibility_services ").append(q(DCL_ACCESSIBILITY)).append("\n");
        sh.append("  settings put secure accessibility_enabled 1 || echo WARN accessibility\n");
        sh.append("fi\n");
        sh.append("echo OK\n");
        sh.append("echo OK > \"$RES\"\n");
        appendRelease(sh, false);
        return sh.toString();
    }

    /** Undo installer changes and return the head unit to the stock launcher. */
    String toRestoreScript() {
        StringBuilder sh = scriptHeader();
        sh.append("echo STEP restore\n");
        sh.append("settings delete global enable_freeform_support || echo WARN freeform\n");
        sh.append("settings delete global policy_control || echo WARN policy_control\n");
        sh.append("remove_secure ").append(q("enabled_notification_listeners")).append(' ').append(q(DCL_NOTIFICATION)).append("\n");
        sh.append("remove_secure ").append(q("enabled_accessibility_services")).append(' ').append(q(DCL_ACCESSIBILITY)).append("\n");
        unhide(sh, SYSTEMUI);
        unhide(sh, LAUNCHER);
        unhide(sh, IOTTUBE);
        unhide(sh, VOICE);
        unhide(sh, SPEECH_CLIENT);
        unhide(sh, MANUAL);
        unhide(sh, USB_HANDLER);
        sh.append("cmd package set-home-activity ").append(q(LAUNCHER_HOME)).append(" || echo WARN home\n");
        sh.append("CLASSPATH=").append(q(PAYLOAD_DIR + "/setwallpaper.dex"));
        sh.append(" app_process /system/bin SetWallpaper default || echo WARN wallpaper\n");
        sh.append("pm uninstall ").append(DCL_PACKAGE).append(" || echo WARN uninstall_dcl\n");
        sh.append("pm uninstall ").append(USB_MOUNT).append(" || echo WARN uninstall_usb\n");
        sh.append("pm uninstall ").append(BLANK_WALLPAPER).append(" || echo WARN uninstall_blank\n");
        sh.append("rm -f ").append(q(DEALER_FLAG)).append(' ').append(q(DEALER_FLAG_TMP)).append("\n");
        finishScript(sh, true);
        return sh.toString();
    }

    /** One-shot root grant so this installer can read DCL's appops. Own body, guarded cleanup. */
    static String appOpsGrantScript(String packageName) {
        StringBuilder sh = new StringBuilder();
        sh.append("#!/system/bin/sh\n");
        sh.append("pm grant ").append(packageName).append(" android.permission.GET_APP_OPS_STATS");
        sh.append(" > /sdcard/dcl_appops_grant.txt 2>&1\n");
        sh.append("echo EXIT:$? >> /sdcard/dcl_appops_grant.txt\n");
        appendRelease(sh, false);
        return sh.toString();
    }

    private static void recordIfHidden(StringBuilder sh, String pkg) {
        sh.append("if dumpsys package ").append(pkg).append(" | grep -q 'hidden=true'; then echo ");
        sh.append(q(pkg)).append(" >> ").append(q(DEALER_FLAG_TMP)).append("; fi\n");
    }

    private static void unhide(StringBuilder sh, String pkg) {
        sh.append("pm unhide ").append(pkg).append("\n");
        sh.append("pm enable ").append(pkg).append("\n");
        sh.append("dumpsys package ").append(pkg);
        sh.append(" | grep -q 'hidden=true' && echo WARN still_hidden_").append(pkg).append("\n");
    }

    private StringBuilder scriptHeader() {
        return scriptHeader(LOG_FILE, RESULT_FILE);
    }

    private StringBuilder scriptHeader(String logFile, String resultFile) {
        StringBuilder sh = new StringBuilder();
        sh.append("#!/system/bin/sh\n");
        sh.append("LOG=").append(q(logFile)).append("\n");
        sh.append("RES=").append(q(resultFile)).append("\n");
        sh.append(": > \"$LOG\"\n");
        sh.append("rm -f \"$RES\"\n");
        sh.append("exec >>\"$LOG\" 2>&1\n");
        sh.append("echo START\n");
        sh.append("fail() {\n");
        sh.append("  echo \"FAIL $1\"\n");
        sh.append("  echo \"FAIL $1\" > \"$RES\"\n");
        appendRelease(sh, false);
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
        sh.append("remove_secure() {\n");
        sh.append("  cur=`settings get secure \"$1\"`\n");
        sh.append("  if [ -z \"$cur\" ] || [ \"$cur\" = null ]; then return; fi\n");
        sh.append("  out=\"\"\n");
        sh.append("  IFS=:\n");
        sh.append("  for part in $cur; do\n");
        sh.append("    if [ \"$part\" = \"$2\" ]; then continue; fi\n");
        sh.append("    if [ -z \"$out\" ]; then out=\"$part\"; else out=\"$out:$part\"; fi\n");
        sh.append("  done\n");
        sh.append("  unset IFS\n");
        sh.append("  settings put secure \"$1\" \"$out\" || fail \"put $1\"\n");
        sh.append("}\n");
        return sh;
    }

    private void appendGrants(StringBuilder sh) {
        sh.append("echo STEP grants\n");
        grant(sh, PERM_FINE_LOCATION, true);
        grant(sh, PERM_COARSE_LOCATION, true);
        grant(sh, PERM_READ_STORAGE, true);
        grant(sh, PERM_WRITE_STORAGE, true);
        grant(sh, PERM_WRITE_SECURE, true);
        grant(sh, PERM_MANAGE_STACKS, false);
        grant(sh, PERM_EMBEDDING, false);
        grant(sh, PERM_READ_LOGS, false);
        appops(sh, "SYSTEM_ALERT_WINDOW", true);
        appops(sh, "WRITE_SETTINGS", false);
        appops(sh, "REQUEST_INSTALL_PACKAGES", false);
        sh.append("settings put global enable_freeform_support 1 || fail freeform\n");
        sh.append("settings put global policy_control ").append(q("immersive.full=*")).append(" || fail policy_control\n");
        sh.append("echo STEP listeners\n");
        sh.append("cmd notification allow_listener ").append(q(DCL_NOTIFICATION)).append(" || fail notification\n");
        sh.append("append_secure enabled_notification_listeners ").append(q(DCL_NOTIFICATION)).append("\n");
        sh.append("append_secure enabled_accessibility_services ").append(q(DCL_ACCESSIBILITY)).append("\n");
        sh.append("settings put secure accessibility_enabled 1 || fail accessibility\n");
    }

    private void appendCompile(StringBuilder sh) {
        sh.append("echo STEP compile\n");
        sh.append("cmd package compile -m speed -f ").append(DCL_PACKAGE).append(" || fail compile\n");
    }

    private void appendHome(StringBuilder sh) {
        sh.append("echo STEP home\n");
        sh.append("cmd package set-home-activity ").append(q(DCL_HOME)).append(" || fail home\n");
    }

    private void appendHomeCheck(StringBuilder sh) {
        sh.append("cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | grep -F ");
        sh.append(q(DCL_PACKAGE)).append(" >/dev/null || fail home_check\n");
    }

    private void appendWallpaper(StringBuilder sh) {
        sh.append("echo STEP wallpaper\n");
        sh.append("CLASSPATH=").append(q(PAYLOAD_DIR + "/setwallpaper.dex"));
        sh.append(" app_process /system/bin SetWallpaper ").append(q(wallpaperComponent())).append(" || fail wallpaper\n");
        sh.append("sleep 1\n");
        sh.append("dumpsys wallpaper | grep -F ").append(q(wallpaperComponent())).append(" >/dev/null || fail wallpaper_check\n");
    }

    private void appendHides(StringBuilder sh) {
        sh.append("echo STEP hide\n");
        sh.append("pm enable ").append(LAUNCHER).append(" || fail enable_launcher\n");
        hideChecked(sh, LAUNCHER, true);
        hideChecked(sh, USB_HANDLER, true);
        hideIf(sh, hideSystemUi, SYSTEMUI);
        hideIf(sh, hideIottube, IOTTUBE);
        hideIf(sh, hideVoice, VOICE);
        hideIf(sh, hideSpeechClient, SPEECH_CLIENT);
        hideIf(sh, hideManual, MANUAL);
    }

    private void finishScript(StringBuilder sh, boolean reboot) {
        sh.append("echo OK\n");
        sh.append("echo OK > \"$RES\"\n");
        appendRelease(sh, true);
        sh.append("sync\n");
        if (reboot) sh.append("reboot\n");
    }

    /**
     * Always drop our body and the single-flight lock. Drop autorun.sh only when it still
     * points at one of our bodies. A client that armed its own script while we ran keeps it,
     * and the trigger property stays so mb_daemon still picks that script up.
     */
    private static void appendRelease(StringBuilder sh, boolean payload) {
        sh.append("rm -f \"$0\"\n");
        sh.append("rm -f ").append(q(LOCK_FILE)).append("\n");
        if (payload) sh.append("rm -rf ").append(q(PAYLOAD_DIR)).append("\n");
        sh.append("if [ -f ").append(q(AUTORUN)).append(" ] && grep -qF '").append(OWN_MARKER);
        sh.append("' ").append(q(AUTORUN)).append("; then\n");
        sh.append("  rm -f ").append(q(AUTORUN)).append(' ').append(q(FLAG)).append("\n");
        sh.append("  setprop ").append(PROP).append(" ''\n");
        sh.append("fi\n");
    }

    private static void grant(StringBuilder sh, String permission, boolean required) {
        String name = permission.substring(permission.lastIndexOf('.') + 1);
        sh.append("pm grant ").append(DCL_PACKAGE).append(' ').append(permission).append(" || ");
        sh.append(required ? "fail grant_" : "echo WARN grant_").append(name).append("\n");
    }

    private static void appops(StringBuilder sh, String op, boolean required) {
        sh.append("appops set --user 0 ").append(DCL_PACKAGE).append(' ').append(op).append(" allow || ");
        sh.append(required ? "fail appops_" : "echo WARN appops_").append(op).append("\n");
        if (required) {
            sh.append("appops get --user 0 ").append(DCL_PACKAGE).append(' ').append(op);
            sh.append(" | grep -F allow >/dev/null || fail appops_").append(op).append("\n");
        }
    }

    /** Unstop only when the sticks package is actually installed. A missing package used to fail the whole script. */
    private static void appendUnstopUsb(StringBuilder sh, String helperDex, boolean fail) {
        sh.append("if pm path ").append(USB_MOUNT).append(" >/dev/null; then\n");
        sh.append("  CLASSPATH=").append(q(helperDex));
        sh.append(" app_process /system/bin Unstop ").append(USB_MOUNT).append(" || ");
        sh.append(fail ? "fail unstop_usb\n" : "echo WARN unstop_usb\n");
        sh.append("fi\n");
    }

    /** pm hide on Android 9 prints a line and exits 0 even when the package stays visible. */
    private static void hideChecked(StringBuilder sh, String pkg, boolean required) {
        sh.append("pm hide ").append(pkg).append("\n");
        sh.append("dumpsys package ").append(pkg).append(" | grep -q 'hidden=true' || ");
        sh.append(required ? "fail hide_" : "echo WARN hide_").append(pkg).append("\n");
    }

    private static void hideIf(StringBuilder sh, boolean hide, String pkg) {
        if (!hide) return;
        hideChecked(sh, pkg, false);
    }

    static String q(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }
}
