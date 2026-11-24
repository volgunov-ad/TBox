package vad.dashing.dclinstall;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.WallpaperManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * After unlock, let vold scan USB sticks when this launcher still needs it, put DCL's
 * notification listener and accessibility service back, and restore the boot wallpaper
 * if the system dropped it. BOOT_COMPLETED is ordered: holding it would delay every later
 * receiver, UsbMount included. So boot only sets an alarm, and the alarm arms the script
 * once UsbMount has used /sdcard/autorun.sh.
 */
public class BootReceiver extends BroadcastReceiver {
    static final String ACTION_ARM = "vad.dashing.dclinstall.ARM";
    private static final String TAG = "DclInstallBoot";
    private static final long USB_MOUNT_DELAY_MS = 20_000L;
    private static final String STAMP = "boot_fix_boot.txt";

    @Override
    public void onReceive(Context context, Intent intent) {
        final Context app = context.getApplicationContext();
        if (intent == null || !ACTION_ARM.equals(intent.getAction())) {
            scheduleArm(app);
            return;
        }
        final PendingResult pending = goAsync();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    arm(app);
                } catch (Exception e) {
                    Log.e(TAG, "boot fix failed", e);
                } finally {
                    pending.finish();
                }
            }
        }, "dcl-boot-fix").start();
    }

    private static void scheduleArm(Context context) {
        Intent arm = new Intent(ACTION_ARM).setClass(context, BootReceiver.class);
        PendingIntent operation = PendingIntent.getBroadcast(
                context, 0, arm, PendingIntent.FLAG_UPDATE_CURRENT);
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarms.setExact(AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + USB_MOUNT_DELAY_MS, operation);
        Log.i(TAG, "boot fix scheduled");
    }

    private static void arm(Context context) throws Exception {
        if (!isA9()) {
            Log.i(TAG, "not an A9 head unit, boot fix skipped");
            return;
        }
        if (new File(InstallPlan.DEALER_FLAG).isFile()) {
            Log.i(TAG, "dealer visit on, boot fix skipped");
            return;
        }
        if (alreadyThisBoot(context)) {
            Log.i(TAG, "boot fix already armed this boot");
            return;
        }
        String version = DclPackages.dclVersion(context.getPackageManager());
        if (version == null) {
            Log.i(TAG, "no dcl, boot fix skipped");
            return;
        }
        boolean nativeUnlock = InstallPlan.hasNativeUnlock(version);
        boolean systemUiHidden = DclPackages.hidden(context.getPackageManager(), InstallPlan.SYSTEMUI);
        boolean unlockUsb = systemUiHidden && !nativeUnlock;
        if (!RootRun.tryAcquire(RootRun.OWNER_BOOT, RootRun.thisBoot(SystemClock.elapsedRealtime()))) {
            Log.i(TAG, "another root script is running, boot fix skipped");
            return;
        }
        try {
            File helper = new File(context.getFilesDir(), "setwallpaper.dex");
            File unlock = new File(context.getFilesDir(), "unlockusb.dex");
            copyAsset(context, "setwallpaper.dex", helper);
            copyAsset(context, "unlockusb.dex", unlock);
            boolean restoreWallpaper = wallpaperLost(context);
            Log.i(TAG, "restore wallpaper " + restoreWallpaper
                    + " native unlock " + nativeUnlock
                    + " unlock usb " + unlockUsb);
            String script = new InstallPlan("", false, false, false, false, false, false, nativeUnlock)
                    .toBootScript(helper.getAbsolutePath(), unlock.getAbsolutePath(), restoreWallpaper, unlockUsb);
            MbDaemon.run(script, MbDaemon.BOOT_BODY);
            markThisBoot(context);
            Log.i(TAG, "boot fix armed");
        } catch (Exception e) {
            RootRun.release();
            throw e;
        }
    }

    private static boolean isA9() {
        return "9".equals(Build.VERSION.RELEASE) && "MB".equals(prop("ro.wt.solution.provider"));
    }

    private static boolean alreadyThisBoot(Context context) {
        long now = RootRun.bootMark(System.currentTimeMillis(), SystemClock.elapsedRealtime());
        File file = new File(context.getFilesDir(), STAMP);
        if (!file.isFile()) return false;
        try {
            FileInputStream in = new FileInputStream(file);
            try {
                byte[] buf = new byte[64];
                int n = in.read(buf);
                if (n <= 0) return false;
                long saved = Long.parseLong(new String(buf, 0, n, "UTF-8").trim());
                return RootRun.sameBoot(saved, now);
            } finally {
                in.close();
            }
        } catch (Exception e) {
            return false;
        }
    }

    private static void markThisBoot(Context context) throws Exception {
        long now = RootRun.bootMark(System.currentTimeMillis(), SystemClock.elapsedRealtime());
        FileOutputStream out = new FileOutputStream(new File(context.getFilesDir(), STAMP));
        try {
            out.write(Long.toString(now).getBytes("UTF-8"));
        } finally {
            out.close();
        }
    }

    /**
     * Force-stopping this package makes the system drop its wallpaper back to the stock one,
     * which lives in SystemUI. With SystemUI hidden that costs a 30 s wait on every boot.
     * Another live wallpaper is the user's choice and is left alone.
     */
    private static boolean wallpaperLost(Context context) {
        if (!WallpaperStore.modeFile(context).isFile()) return false;
        if (!DclPackages.hidden(context.getPackageManager(), InstallPlan.SYSTEMUI)) return false;
        try {
            return WallpaperManager.getInstance(context).getWallpaperInfo() == null;
        } catch (Throwable t) {
            Log.i(TAG, "wallpaper info failed", t);
            return false;
        }
    }

    private static void copyAsset(Context context, String name, File dest) throws Exception {
        InputStream in = context.getAssets().open(name);
        try {
            FileOutputStream out = new FileOutputStream(dest);
            try {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            } finally {
                out.close();
            }
        } finally {
            in.close();
        }
    }

    private static String prop(String key) {
        try {
            Process process = new ProcessBuilder("getprop", key).start();
            InputStream in = process.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[256];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            process.waitFor();
            return new String(out.toByteArray(), "UTF-8").trim();
        } catch (Exception e) {
            return "";
        }
    }
}
