package vad.dashing.dclinstall;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.WallpaperManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.SystemClock;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * After unlock, let vold scan USB sticks, put DCL's notification listener
 * and accessibility service back, and restore the boot wallpaper if the system
 * dropped it. BOOT_COMPLETED is ordered: holding it would delay every later
 * receiver, UsbMount included. So boot only sets an alarm, and the alarm arms
 * the script once UsbMount has used /sdcard/autorun.sh.
 */
public class BootReceiver extends BroadcastReceiver {
    static final String ACTION_ARM = "vad.dashing.dclinstall.ARM";
    private static final String TAG = "DclInstallBoot";
    private static final long USB_MOUNT_DELAY_MS = 20_000L;

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
        File helper = new File(context.getFilesDir(), "setwallpaper.dex");
        File unlock = new File(context.getFilesDir(), "unlockusb.dex");
        copyAsset(context, "setwallpaper.dex", helper);
        copyAsset(context, "unlockusb.dex", unlock);
        boolean restoreWallpaper = wallpaperLost(context);
        Log.i(TAG, "restore wallpaper " + restoreWallpaper);
        String script = new InstallPlan("", false, false, false, false, false, false)
                .toBootScript(helper.getAbsolutePath(), unlock.getAbsolutePath(), restoreWallpaper);
        MbDaemon.run(script, MbDaemon.BOOT_BODY);
        Log.i(TAG, "boot fix armed");
    }

    /**
     * Force-stopping this package makes the system drop its wallpaper back to the stock one,
     * which lives in SystemUI. With SystemUI hidden that costs a 30 s wait on every boot.
     * Another live wallpaper is the user's choice and is left alone.
     */
    private static boolean wallpaperLost(Context context) {
        if (!WallpaperStore.modeFile(context).isFile()) return false;
        try {
            context.getPackageManager().getPackageInfo(InstallPlan.SYSTEMUI, 0);
            return false;
        } catch (PackageManager.NameNotFoundException hidden) {
            // SystemUI is hidden, so the stock wallpaper cannot draw.
        }
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
}
