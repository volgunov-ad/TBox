package vad.dashing.dclinstall;

import android.app.WallpaperManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.provider.Settings;
import android.util.Log;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/** What is already in place for DCL, read from the head unit without root. */
final class DclStatus {
    private static final String TAG = "DclStatus";

    final String title;
    final boolean done;
    /** True when this row could not be read. It is not shown as a failure. */
    final boolean unknown;
    /** Optional rows are choices. A missing one is not a failure. */
    final boolean optional;
    final String detail;

    private DclStatus(String title, boolean done, boolean unknown, boolean optional, String detail) {
        this.title = title;
        this.done = done;
        this.unknown = unknown;
        this.optional = optional;
        this.detail = detail;
    }

    static List<DclStatus> collect(Context context) {
        PackageManager packages = context.getPackageManager();
        List<DclStatus> rows = new ArrayList<DclStatus>();
        String version = versionName(packages, InstallPlan.DCL_PACKAGE);
        boolean installed = version != null;
        rows.add(row("Лаунчер DCL установлен", installed, false, installed ? version : "файл ещё не выбран или не поставлен"));
        rows.add(row("Кнопка HOME открывает DCL", isHome(packages), false, ""));
        rows.add(row("Геолокация", permission(packages, "android.permission.ACCESS_FINE_LOCATION")
                && permission(packages, "android.permission.ACCESS_COARSE_LOCATION"), false, ""));
        rows.add(row("Память", permission(packages, "android.permission.READ_EXTERNAL_STORAGE")
                && permission(packages, "android.permission.WRITE_EXTERNAL_STORAGE"), false, ""));
        rows.add(state("Поверх других окон", appOp("SYSTEM_ALERT_WINDOW")));
        rows.add(state("Изменение настроек", appOp("WRITE_SETTINGS")));
        rows.add(state("Установка пакетов", appOp("REQUEST_INSTALL_PACKAGES")));
        rows.add(row("Системные настройки", permission(packages, "android.permission.WRITE_SECURE_SETTINGS"), false, ""));
        rows.add(row("Управление окнами", permission(packages, "android.permission.MANAGE_ACTIVITY_STACKS"), false, ""));
        rows.add(row("Встраивание окон", permission(packages, "android.permission.ACTIVITY_EMBEDDING"), false, ""));
        rows.add(row("Чтение журнала", permission(packages, "android.permission.READ_LOGS"), false, ""));
        rows.add(row("Свободные окна", "1".equals(global(context, "enable_freeform_support")), false, ""));
        rows.add(row("Полный экран", "immersive.full=*".equals(global(context, "policy_control")), false, ""));
        rows.add(row("Доступ к уведомлениям", secureContains(context, "enabled_notification_listeners", InstallPlan.DCL_NOTIFICATION), false, ""));
        rows.add(row("Служба специальных возможностей", accessibility(context), false, ""));
        rows.add(row("Обои для быстрой загрузки", wallpaperReady(context), false, ""));
        rows.add(row("Программа флешек", versionName(packages, "vad.dashing.usbmount") != null, false, ""));
        rows.add(row("Штатный лаунчер скрыт", hidden(packages, InstallPlan.LAUNCHER), false, ""));
        rows.add(row("System UI скрыт", hidden(packages, InstallPlan.SYSTEMUI), true, ""));
        rows.add(row("IoT Tube скрыт", hidden(packages, InstallPlan.IOTTUBE), true, ""));
        rows.add(row("Голосовой помощник скрыт", hidden(packages, InstallPlan.VOICE), true, ""));
        rows.add(row("Клиент голоса скрыт", hidden(packages, InstallPlan.SPEECH_CLIENT), true, ""));
        rows.add(row("Электронное руководство скрыто", hidden(packages, InstallPlan.MANUAL), true, ""));
        return rows;
    }

    private static DclStatus row(String title, boolean done, boolean optional, String detail) {
        Log.i(TAG, (done ? "ok " : "no ") + title);
        return new DclStatus(title, done, false, optional, detail);
    }

    private static DclStatus state(String title, Boolean done) {
        if (done == null) {
            Log.i(TAG, "unknown " + title);
            return new DclStatus(title, false, true, false, "не удалось проверить");
        }
        return row(title, done.booleanValue(), false, "");
    }

    private static String versionName(PackageManager packages, String packageName) {
        try {
            PackageInfo info = packages.getPackageInfo(packageName, 0);
            return info.versionName == null ? "" : info.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    private static boolean isHome(PackageManager packages) {
        Intent home = new Intent(Intent.ACTION_MAIN);
        home.addCategory(Intent.CATEGORY_HOME);
        ResolveInfo resolved = packages.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
        return resolved != null
                && resolved.activityInfo != null
                && InstallPlan.DCL_PACKAGE.equals(resolved.activityInfo.packageName);
    }

    private static boolean permission(PackageManager packages, String name) {
        return packages.checkPermission(name, InstallPlan.DCL_PACKAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private static final String OP_READ = "android.permission.GET_APP_OPS_STATS";

    /** Lets this app read DCL's overlay and settings switches. One root grant, then it sticks. */
    static boolean grantSelfAppOps(Context context) {
        if (context.getPackageManager().checkPermission(OP_READ, context.getPackageName())
                == PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        String script = "#!/system/bin/sh\n"
                + "pm grant " + context.getPackageName() + " " + OP_READ
                + " > /sdcard/dcl_appops_grant.txt 2>&1\n"
                + "echo EXIT:$? >> /sdcard/dcl_appops_grant.txt\n"
                + "rm -f " + InstallPlan.AUTORUN + " " + InstallPlan.FLAG + "\n"
                + "setprop " + InstallPlan.PROP + " ''\n";
        try {
            new java.io.File("/sdcard/dcl_appops_grant.txt").delete();
            java.io.FileOutputStream out = new java.io.FileOutputStream(InstallPlan.AUTORUN);
            try {
                out.write(script.getBytes("UTF-8"));
            } finally {
                out.close();
            }
            command("chmod", "777", InstallPlan.AUTORUN);
            new java.io.File(InstallPlan.FLAG).createNewFile();
            command("setprop", InstallPlan.PROP, "");
            Thread.sleep(300);
            command("setprop", InstallPlan.PROP, InstallPlan.FLAG);
            long deadline = System.currentTimeMillis() + 15000L;
            java.io.File result = new java.io.File("/sdcard/dcl_appops_grant.txt");
            while (System.currentTimeMillis() < deadline && !result.isFile()) {
                Thread.sleep(300);
            }
            Log.i(TAG, "appops grant " + (result.isFile() ? "done" : "timeout"));
        } catch (Exception e) {
            Log.i(TAG, "appops grant " + e);
        }
        return true;
    }

    private static Boolean appOp(String name) {
        String text = command("appops", "get", "--user", "0", InstallPlan.DCL_PACKAGE, name);
        String flat = text.replace('\n', ' ');
        if (flat.length() > 180) flat = flat.substring(0, 180);
        Log.i(TAG, name + " [" + flat + "]");
        if (text.contains("Security") || text.length() == 0) return null;
        if (text.contains("allow")) return Boolean.TRUE;
        return Boolean.FALSE;
    }

    private static String command(String... args) {
        try {
            Process process = new ProcessBuilder(args).redirectErrorStream(true).start();
            InputStream in = process.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[256];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            process.waitFor();
            return new String(out.toByteArray(), "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    private static boolean accessibility(Context context) {
        int enabled = Settings.Secure.getInt(context.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 0);
        return enabled == 1 && secureContains(
                context, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, InstallPlan.DCL_ACCESSIBILITY);
    }

    private static boolean secureContains(Context context, String key, String component) {
        String value = Settings.Secure.getString(context.getContentResolver(), key);
        return value != null && value.contains(component);
    }

    private static String global(Context context, String key) {
        String value = Settings.Global.getString(context.getContentResolver(), key);
        return value == null ? "" : value.trim();
    }

    private static boolean wallpaperReady(Context context) {
        try {
            android.app.WallpaperInfo info = WallpaperManager.getInstance(context).getWallpaperInfo();
            if (info == null || info.getComponent() == null) return false;
            return InstallPlan.BOOT_WALLPAPER.equals(info.getComponent().flattenToString());
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * A hidden package is invisible to a normal app, so a missing system package means it is hidden.
     * These packages are part of the A9 firmware and are not uninstalled.
     */
    private static boolean hidden(PackageManager packages, String packageName) {
        return versionName(packages, packageName) == null;
    }
}
