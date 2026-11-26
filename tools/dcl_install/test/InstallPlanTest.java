package vad.dashing.dclinstall;

import java.io.File;
import java.io.FileOutputStream;

/** JDK-only checks for the root scripts. No Android SDK. Run tools/dcl_install/test.sh. */
public final class InstallPlanTest {
    public static void main(String[] args) {
        int failed = 0;
        failed += check("d01-setup", d01Setup());
        failed += check("d01-install", d01Install());
        failed += check("d01-old-no-usb", oldNoUsb());
        failed += check("d02-dealer-native", d02Native());
        failed += check("d02-dealer-old", d02Old());
        failed += check("d03-file-version", d03());
        failed += check("d04-restore-flag", d04());
        failed += check("d05-rollback", d05());
        failed += check("d05-stage-without-uninstall", d05Stage());
        failed += check("d06-cleanup", d06());
        failed += check("d06-appops", d06Appops());
        failed += check("d06-boot", d06Boot());
        failed += check("d08-hide", d08());
        failed += check("d09-dealer", d09());
        failed += check("d11-versions", d11());
        failed += check("d13-boot-usb", d13());
        failed += check("d14-grants", d14());
        failed += check("d20-hide-wallpaper", d20());
        failed += check("d21-rootsvc", d21());
        failed += check("d22-rootsvc-only", d22());
        failed += check("foreign", foreign());
        failed += check("lock", lock());
        failed += check("boot-mark", bootMark());
        failed += check("clock-jump", clockJump());
        failed += check("nonce-wrap", nonceWrap());
        if (failed != 0) {
            System.out.println(failed + " failed");
            System.exit(1);
        }
        System.out.println("OK");
    }

    private static int check(String name, String error) {
        if (error != null) {
            System.out.println("FAIL " + name + ": " + error);
            return 1;
        }
        return 0;
    }

    private static InstallPlan plan(boolean nativeUnlock) {
        return new InstallPlan("/sdcard/Download/dcl.apk", false, true, true, false, true, false, nativeUnlock);
    }

    private static InstallPlan replacing() {
        return new InstallPlan("/sdcard/Download/dcl.apk", true, true, true, false, false, false, false);
    }

    private static String d01Setup() {
        String script = plan(true).toSetupScript();
        if (script.contains("usbmount.apk")) return "setup still installs usbmount";
        if (script.contains("Unstop")) return "setup still runs Unstop";
        if (!script.contains("rootsvc.apk")) return "setup does not install rootsvc";
        return null;
    }

    private static String d01Install() {
        String script = plan(true).toScript();
        if (script.contains("usbmount.apk")) return "install still installs usbmount";
        if (script.contains("Unstop")) return "install still runs Unstop";
        if (!script.contains("rootsvc.apk")) return "install does not install rootsvc";
        return null;
    }

    private static String oldNoUsb() {
        String script = plan(false).toSetupScript();
        if (script.contains("usbmount.apk")) return "old launcher still installs usbmount";
        if (script.contains("Unstop")) return "old launcher still runs Unstop";
        if (!script.contains("rootsvc.apk")) return "old launcher setup skipped rootsvc";
        if (!script.contains("pm uninstall vad.dashing.usbmount")) {
            return "old launcher setup does not uninstall leftover usbmount";
        }
        return null;
    }

    private static String d02Native() {
        String script = plan(true).toDealerOffScript();
        if (script.contains("Unstop")) return "dealer-off with native unlock still runs Unstop";
        if (!script.contains("rm -f '/sdcard/dcl_dealer.flag'")) return "dealer-off does not clear the flag";
        if (script.contains("pm unhide vad.dashing.usbmount")) return "dealer-off still touches usbmount";
        return null;
    }

    private static String d02Old() {
        String script = plan(false).toDealerOffScript();
        if (script.contains("Unstop")) return "dealer-off old still runs Unstop";
        if (script.contains("pm unhide vad.dashing.usbmount")) return "dealer-off old still touches usbmount";
        return null;
    }

    private static String d03() {
        InstallPlan fresh = InstallPlan.forApk("/sdcard/Download/dcl.apk", false, true, true, false, false, false, "0.8.2");
        if (fresh.toScript().contains("usbmount.apk")) return "0.8.2 file still installs usbmount";
        if (fresh.toScript().contains("Unstop")) return "0.8.2 file still runs Unstop";
        if (!fresh.toScript().contains("rootsvc.apk")) return "0.8.2 file skipped rootsvc";
        InstallPlan old = InstallPlan.forApk("/sdcard/Download/dcl.apk", false, true, true, false, false, false, "0.7.7");
        if (old.toScript().contains("usbmount.apk")) return "0.7.7 file still installs usbmount";
        if (!old.toScript().contains("rootsvc.apk")) return "0.7.7 file skipped rootsvc";
        InstallPlan missing = InstallPlan.forApk("/sdcard/Download/dcl.apk", false, true, true, false, false, false, null);
        if (missing.toScript().contains("usbmount.apk")) return "unknown file version still installs usbmount";
        if (!InstallPlan.forApk("/x", false, false, false, false, false, false, "0.8.2-ru").launcherHasNativeUnlock) {
            return "forApk did not read the file version";
        }
        return null;
    }

    private static String d04() {
        String script = plan(true).toRestoreScript();
        if (!script.contains("rm -f '/sdcard/dcl_dealer.flag'")) return "restore leaves the dealer flag";
        if (!script.contains("pm uninstall ras.dashing.tbox.launcher")) return "restore does not uninstall DCL";
        if (script.contains("pm uninstall vad.dashing.rootsvc")) return "restore uninstalls rootsvc";
        return null;
    }

    private static String d05() {
        String script = replacing().toScript();
        if (!script.contains("rollback() {")) return "no rollback function";
        if (!script.contains("pm unhide com.wt.launcher3")) return "rollback does not unhide the stock launcher";
        if (!script.contains("pm unhide com.android.systemui")) return "rollback does not unhide SystemUI";
        if (!script.contains("cmd package set-home-activity 'com.wt.launcher3/.MainActivity'")) {
            return "rollback does not restore HOME";
        }
        if (!script.contains("{ rollback; fail install_dcl; }")) return "install failure does not roll back";
        int failFn = script.indexOf("fail() {");
        int failEnd = script.indexOf("exit 1", failFn);
        if (failFn < 0 || failEnd < 0 || !script.substring(failFn, failEnd).contains("rm -f '/sdcard/dcl_install/dcl.apk'")) {
            return "failed install keeps the staged APK";
        }
        if (!script.contains("cp '/sdcard/Download/dcl.apk' '/sdcard/dcl_install/dcl.apk'")) {
            return "APK is not staged before uninstall";
        }
        int install = script.indexOf("echo STEP install_dcl");
        if (install < 0) return "no install step";
        int line = script.indexOf("pm install -r -d -g -t", install);
        int end = script.indexOf('\n', line);
        String installLine = script.substring(line, end);
        if (!installLine.contains("'/sdcard/dcl_install/dcl.apk'")) return "install does not use the staged APK";
        if (installLine.contains("Download")) return "install still reads the original path";
        int uninstall = script.indexOf("pm uninstall");
        int copy = script.indexOf("cp ");
        if (copy < 0 || uninstall < copy) return "uninstall happens before the copy";
        return null;
    }

    private static String d05Stage() {
        String script = plan(false).toScript();
        if (script.contains("rollback()")) return "replace-in-place defined a rollback";
        if (!script.contains("cp '/sdcard/Download/dcl.apk' '/sdcard/dcl_install/dcl.apk'")) {
            return "replace-in-place does not stage the APK";
        }
        return null;
    }

    private static String d06() {
        String script = plan(false).toScript();
        String error = cleanupGuarded(script);
        if (error != null) return error;
        if (!script.contains("rm -rf '/sdcard/dcl_install'")) return "success path no longer removes the payload";
        if (script.contains("rm -f \"$0\" '/sdcard/autorun.sh'")) return "cleanup still deletes autorun unconditionally";
        if (script.contains("setprop vendor.autorun.sdcard.logfile")) {
            return "script still clears the autorun property";
        }
        return null;
    }

    private static String d06Appops() {
        String script = InstallPlan.appOpsGrantScript("vad.dashing.dclinstall");
        if (!script.contains("pm grant vad.dashing.dclinstall android.permission.GET_APP_OPS_STATS")) {
            return "appops grant target changed";
        }
        if (script.contains("rm -rf")) return "appops script removes the install payload";
        return cleanupGuarded(script);
    }

    private static String d06Boot() {
        String script = plan(true).toBootScript("/data/setwallpaper.dex", "/data/unlockusb.dex", false, false);
        if (script.contains("rm -rf")) return "boot script removes the install payload";
        return cleanupGuarded(script);
    }

    private static String cleanupGuarded(String script) {
        if (!script.contains("rm -f \"$0\"")) return "body is not removed";
        if (!script.contains("rm -f '/sdcard/dcl_install.lock'")) return "lock is not removed";
        if (script.contains("grep -qF '/sdcard/dcl_' '/sdcard/autorun.sh'")) {
            return "script still owns autorun cleanup (mb_daemon should)";
        }
        return null;
    }

    private static String d08() {
        String script = plan(true).toSetupScript();
        if (!script.contains("dumpsys package com.wt.launcher3 | grep -q 'hidden=true' || fail hide_com.wt.launcher3")) {
            return "launcher hide is not verified";
        }
        if (!script.contains("dumpsys package android.car.usb.handler | grep -q 'hidden=true' || fail hide_android.car.usb.handler")) {
            return "usb handler hide is not verified";
        }
        if (!script.contains("dumpsys package com.android.systemui | grep -q 'hidden=true' || echo WARN hide_com.android.systemui")) {
            return "optional SystemUI hide does not warn";
        }
        if (script.contains("fail hide_com.android.systemui")) return "optional SystemUI hide fails the script";
        if (script.contains("com.iflytek.cutefly.speechclient.hmi")) return "unchecked voice package is hidden";
        if (!script.contains("echo WARN hide_com.mengbo.speechclient")) return "checked speech client is missing";
        return null;
    }

    private static String d09() {
        String on = plan(true).toDealerOnScript();
        if (!on.contains("echo 'com.autopai.iottube' >> '/sdcard/dcl_dealer.flag.tmp'")) {
            return "dealer-on does not record iottube";
        }
        if (!on.contains("echo 'com.android.systemui' >> '/sdcard/dcl_dealer.flag.tmp'")) {
            return "dealer-on does not record SystemUI";
        }
        if (!on.contains("pm unhide com.autopai.iottube")) return "dealer-on leaves iottube hidden";
        if (!on.contains("pm unhide com.iflytek.cutefly.speechclient.hmi")) return "dealer-on leaves voice hidden";
        if (!on.contains("pm unhide com.mengbo.electronicmanual")) return "dealer-on leaves the manual hidden";
        int put = on.indexOf("settings put secure accessibility_enabled 0");
        int guard = on.lastIndexOf("if [ -z \"$cur\" ] || [ \"$cur\" = null ]; then", put);
        if (put < 0 || guard < 0) return "accessibility master switch is not conditional";
        if (on.contains("pm hide vad.dashing.usbmount")) return "dealer-on still hides usbmount";
        if (on.contains("pm hide vad.dashing.rootsvc")) return "dealer-on hides rootsvc";
        String off = plan(true).toDealerOffScript();
        if (!off.contains("while IFS= read -r pkg")) return "dealer-off does not replay the flag";
        if (!off.contains("done < '/sdcard/dcl_dealer.flag'")) return "dealer-off does not read the flag";
        int empty = off.indexOf("if [ ! -s '/sdcard/dcl_dealer.flag' ]");
        int hideUi = off.indexOf("pm hide com.android.systemui\n");
        if (empty < 0 || hideUi < empty) return "empty dealer flag does not hide SystemUI";
        InstallPlan noUi = new InstallPlan("/x", false, false, false, false, false, false, true);
        if (noUi.toDealerOffScript().contains("pm hide com.android.systemui\n")) {
            return "dealer-off hides SystemUI when the checkbox is off";
        }
        return null;
    }

    private static String d11() {
        String error = expect(false, "0.8.1");
        if (error != null) return error;
        error = expect(true, "0.8.2");
        if (error != null) return error;
        error = expect(true, "0.8.2-ru");
        if (error != null) return error;
        error = expect(true, "0.9");
        if (error != null) return error;
        error = expect(true, "1.0");
        if (error != null) return error;
        error = expect(true, "0.10.0");
        if (error != null) return error;
        error = expect(true, "v0.8.2");
        if (error != null) return error;
        error = expect(true, "0.8.2ru");
        if (error != null) return error;
        error = expect(false, null);
        if (error != null) return error;
        error = expect(false, "");
        if (error != null) return error;
        error = expect(false, "0.8");
        if (error != null) return error;
        error = expect(false, "0.7.9");
        if (error != null) return error;
        error = expect(true, "0.8.10");
        if (error != null) return error;
        error = expect(true, "V0.8.2");
        if (error != null) return error;
        if (InstallPlan.hasNativeUnlock("nope")) return "garbage version was accepted";
        return null;
    }

    private static String expect(boolean nativeUnlock, String version) {
        boolean got = InstallPlan.hasNativeUnlock(version);
        if (got == nativeUnlock) return null;
        return (version == null ? "null" : version) + " -> " + got;
    }

    private static String d13() {
        String nativeScript = plan(true).toBootScript("/data/a.dex", "/data/b.dex", false, false);
        if (nativeScript.contains("UnlockUsb")) return "native launcher boot still unlocks";
        if (nativeScript.contains("Unstop")) return "native launcher boot still unstops";
        if (!nativeScript.contains("pm uninstall vad.dashing.usbmount")) {
            return "boot does not uninstall leftover usbmount";
        }
        String skip = plan(false).toBootScript("/data/a.dex", "/data/b.dex", false, false);
        if (skip.contains("UnlockUsb")) return "visible SystemUI still unlocks";
        if (!skip.contains("echo skip unlock")) return "skipped unlock is silent";
        String run = plan(false).toBootScript("/data/a.dex", "/data/b.dex", true, true);
        if (!run.contains("UnlockUsb")) return "hidden SystemUI on an old launcher does not unlock";
        if (run.contains("Unstop")) return "boot still runs Unstop";
        if (!run.contains("SetWallpaper")) return "wallpaper restore was dropped";
        if (!run.contains(InstallPlan.ROOTSVC_A11Y)) return "boot lost rootsvc a11y grant";
        return null;
    }

    private static String d14() {
        String script = plan(false).toGrantScript();
        if (!script.contains("fail grant_WRITE_SECURE_SETTINGS")) return "secure settings is not required";
        if (!script.contains("fail grant_WRITE_EXTERNAL_STORAGE")) return "storage is not required";
        if (!script.contains("fail grant_ACCESS_FINE_LOCATION")) return "location is not required";
        if (!script.contains("fail appops_SYSTEM_ALERT_WINDOW")) return "overlay is not required";
        if (!script.contains("echo WARN grant_READ_LOGS")) return "READ_LOGS still fails the install";
        if (!script.contains("echo WARN grant_MANAGE_ACTIVITY_STACKS")) return "window grant still fails the install";
        if (!script.contains("echo WARN grant_ACTIVITY_EMBEDDING")) return "embedding grant still fails the install";
        if (!script.contains("echo WARN appops_WRITE_SETTINGS")) return "WRITE_SETTINGS still fails the install";
        if (!script.contains("echo WARN appops_REQUEST_INSTALL_PACKAGES")) return "install op still fails the install";
        if (script.contains("fail grant_READ_LOGS")) return "READ_LOGS has both fail and warn";
        return null;
    }

    private static String d20() {
        if (!plan(true).toHideScript().contains("SetWallpaper")) return "hiding SystemUI does not set wallpaper";
        InstallPlan keep = new InstallPlan("/x", false, false, false, false, false, false, true);
        if (keep.toHideScript().contains("SetWallpaper")) return "hide without SystemUI still sets wallpaper";
        return null;
    }

    private static String d21() {
        String setup = plan(true).toSetupScript();
        if (!setup.contains("pm grant vad.dashing.rootsvc android.permission.WRITE_EXTERNAL_STORAGE")) {
            return "setup does not grant rootsvc storage";
        }
        if (!setup.contains(InstallPlan.ROOTSVC_A11Y)) return "setup does not enable rootsvc a11y";
        if (setup.contains("pm install -r -d -g -t '/sdcard/dcl_install/rootsvc.apk'")) {
            return "rootsvc install still allows downgrade (-d)";
        }
        if (!setup.contains("if pm path vad.dashing.rootsvc >/dev/null; then")) {
            return "rootsvc install is not guarded by pm path";
        }
        if (!setup.contains("echo WARN rootsvc_keep_installed")) {
            return "existing rootsvc install failure is still fatal";
        }
        int keep = setup.indexOf("echo WARN rootsvc_keep_installed");
        int fail = setup.indexOf("fail rootsvc");
        if (keep < 0 || fail < 0 || fail < keep) {
            return "missing-package install must still fail rootsvc";
        }
        String listeners = InstallPlan.bootListenersOnlyScript();
        if (!listeners.contains(InstallPlan.DCL_ACCESSIBILITY)) return "listeners script lost DCL a11y";
        if (!listeners.contains(InstallPlan.ROOTSVC_A11Y)) return "listeners script lost rootsvc a11y";
        return null;
    }

    private static String d22() {
        String script = plan(true).toRootsvcScript();
        if (!script.contains("rootsvc.apk")) return "rootsvc-only script skips install";
        if (!script.contains("pm install -r -g -t '/sdcard/dcl_install/rootsvc.apk'")) {
            return "rootsvc-only script lost pm install -r -g -t";
        }
        if (!script.contains("pm grant vad.dashing.rootsvc android.permission.WRITE_EXTERNAL_STORAGE")) {
            return "rootsvc-only script does not grant storage";
        }
        if (!script.contains(InstallPlan.ROOTSVC_A11Y)) return "rootsvc-only script skips a11y";
        if (script.contains("pm path " + InstallPlan.DCL_PACKAGE)) {
            return "rootsvc-only script requires DCL";
        }
        if (script.contains("SetWallpaper") || script.contains("install_dcl") || script.contains("STEP hide")) {
            return "rootsvc-only script still does DCL setup";
        }
        if (script.contains("reboot")) return "rootsvc-only script reboots";
        return null;
    }

    private static String foreign() {
        if (MbDaemon.isForeign(null)) return "missing autorun was treated as foreign";
        if (!MbDaemon.isForeign("echo other")) return "a foreign script was treated as ours";
        if (MbDaemon.isForeign("#!/system/bin/sh\nexec /system/bin/sh '/sdcard/dcl_install.sh'\n")) {
            return "our body was treated as foreign";
        }
        if (MbDaemon.isForeign("exec /system/bin/sh '/sdcard/dcl_boot.sh'\n")) {
            return "boot body was treated as foreign";
        }
        return null;
    }

    private static String nonceWrap() {
        String wrapped = MbDaemon.wrapWithNonce("#!/system/bin/sh\necho hi\n", "n42");
        if (!wrapped.contains("# /sdcard/dcl_")) return "nonce wrap lost own marker";
        if (!wrapped.contains("echo started n42 > '/sdcard/dcl_arm'")) return "nonce wrap lost arm marker";
        if (!wrapped.contains("echo hi")) return "nonce wrap lost body";
        if (wrapped.indexOf("echo started") > wrapped.indexOf("echo hi")) return "nonce is not first";
        return null;
    }

    private static String lock() {
        File dir = new File(System.getProperty("java.io.tmpdir"), "dcl-lock-" + System.nanoTime());
        if (!dir.mkdirs()) return "temp dir";
        File lock = new File(dir, "lock");
        long boot = 40L;
        long otherBoot = 41L;
        long elapsed = 25_000L;
        if (RootRun.inProgress(lock, elapsed, boot)) return "missing lock is in progress";
        if (!RootRun.tryAcquire(lock, RootRun.OWNER_INSTALL, elapsed, boot)) return "could not acquire a free lock";
        if (!RootRun.OWNER_INSTALL.equals(RootRun.owner(lock))) return "owner was not stored";
        if (RootRun.readBootMark(lock) != boot) return "boot id was not stored";
        if (RootRun.readElapsed(lock) != elapsed) return "elapsed time was not stored";
        if (!RootRun.inProgress(lock, elapsed + 1_000L, boot)) return "fresh lock is not in progress";
        if (RootRun.inProgress(lock, elapsed + 1_000L, otherBoot)) return "old boot still in progress";
        if (RootRun.tryAcquire(lock, RootRun.OWNER_BOOT, elapsed + 1_000L, boot)) {
            return "second acquire succeeded";
        }
        if (!RootRun.tryAcquire(lock, RootRun.OWNER_BOOT, elapsed + 1_000L, otherBoot)) {
            return "other boot could not replace the lock";
        }
        if (!RootRun.tryAcquire(lock, RootRun.OWNER_INSTALL, elapsed + 2_000L, boot)) {
            return "same boot could not replace an old-boot lock";
        }
        if (RootRun.inProgress(lock, elapsed + 2_000L - 1_000L, boot)) return "elapsed reset still in progress";
        if (RootRun.inProgress(lock, elapsed + 2_000L + RootRun.STALE_MS, boot)) {
            return "stale elapsed is still in progress";
        }
        if (!RootRun.tryAcquire(lock, RootRun.OWNER_BOOT, elapsed + 2_000L + RootRun.STALE_MS, boot)) {
            return "stale lock could not be replaced";
        }
        if (!RootRun.OWNER_BOOT.equals(RootRun.owner(lock))) return "replaced owner was not stored";
        try {
            FileOutputStream old = new FileOutputStream(lock);
            try {
                old.write("install\n1\n".getBytes("UTF-8"));
            } finally {
                old.close();
            }
        } catch (Exception e) {
            return "could not write an old lock: " + e;
        }
        if (RootRun.inProgress(lock, elapsed, boot)) return "lock without a boot mark is in progress";
        if (!lock.delete()) return "cleanup";
        dir.delete();
        return null;
    }

    private static String bootMark() {
        long mark = RootRun.bootMark(5_000_000L, 20_000L);
        if (!RootRun.sameBoot(mark, RootRun.bootMark(5_000_500L, 20_400L))) return "same boot was split";
        if (RootRun.sameBoot(mark, RootRun.bootMark(5_000_000L + 60_000L, 20_000L))) return "a later boot matched";
        if (RootRun.sameBoot(0L, mark)) return "empty stamp matched";
        if (!RootRun.sameBoot(40L, 40L)) return "same boot count was split";
        if (RootRun.sameBoot(40L, 41L)) return "next boot count matched";
        return null;
    }

    /** A +4911 s wall jump, as on A9 boot5, must not drop a live lock or allow a second command. */
    private static String clockJump() {
        File dir = new File(System.getProperty("java.io.tmpdir"), "dcl-jump-" + System.nanoTime());
        if (!dir.mkdirs()) return "temp dir";
        File lock = new File(dir, "lock");
        long boot = 40L;
        long elapsed = 25_000L;
        long wall = 1_700_000_000_000L;
        long wallAfter = wall + 4_911_000L;
        if (wallAfter - wall < RootRun.STALE_MS) return "jump is inside the stale window";
        if (!RootRun.tryAcquire(lock, RootRun.OWNER_INSTALL, elapsed, boot)) return "could not acquire";
        if (!lock.setLastModified(wallAfter)) return "could not move the file mtime";
        if (!RootRun.inProgress(lock, elapsed + 1_000L, boot)) return "clock jump dropped a live lock";
        if (RootRun.tryAcquire(lock, RootRun.OWNER_BOOT, elapsed + 1_000L, boot)) {
            return "clock jump allowed a second command";
        }
        if (!lock.delete()) return "cleanup";
        dir.delete();
        return null;
    }
}
