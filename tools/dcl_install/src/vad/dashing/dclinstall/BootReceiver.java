package vad.dashing.dclinstall;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

/**
 * After unlock, let vold scan USB sticks and put DCL's notification listener
 * and accessibility service back. Waits on boot so UsbMount can use
 * /sdcard/autorun.sh first; this script then unlocks USB again.
 */
public class BootReceiver extends BroadcastReceiver {
    static final String ACTION_ARM = "vad.dashing.dclinstall.ARM";
    private static final String TAG = "DclInstallBoot";
    private static final long USB_MOUNT_DELAY_MS = 20_000L;

    @Override
    public void onReceive(Context context, Intent intent) {
        final PendingResult pending = goAsync();
        final Context app = context.getApplicationContext();
        final boolean fromBoot = intent != null && !ACTION_ARM.equals(intent.getAction());
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (fromBoot) Thread.sleep(USB_MOUNT_DELAY_MS);
                    arm(app);
                } catch (Exception e) {
                    Log.e(TAG, "boot fix failed", e);
                } finally {
                    pending.finish();
                }
            }
        }, "dcl-boot-fix").start();
    }

    private static void arm(Context context) throws Exception {
        File helper = new File(context.getFilesDir(), "setwallpaper.dex");
        File unlock = new File(context.getFilesDir(), "unlockusb.dex");
        copyAsset(context, "setwallpaper.dex", helper);
        copyAsset(context, "unlockusb.dex", unlock);
        String script = new InstallPlan("", false, false, false, false, false, false)
                .toBootScript(helper.getAbsolutePath(), unlock.getAbsolutePath());
        FileOutputStream out = new FileOutputStream(InstallPlan.AUTORUN);
        try {
            out.write(script.getBytes("UTF-8"));
        } finally {
            out.close();
        }
        exec(new String[] {"chmod", "777", InstallPlan.AUTORUN});
        new File(InstallPlan.FLAG).createNewFile();
        exec(new String[] {"setprop", InstallPlan.PROP, ""});
        Thread.sleep(300);
        exec(new String[] {"setprop", InstallPlan.PROP, InstallPlan.FLAG});
        Log.i(TAG, "boot fix armed");
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

    private static void exec(String[] cmd) throws Exception {
        Process process = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        if (!process.waitFor(8, TimeUnit.SECONDS)) {
            process.destroy();
            throw new IllegalStateException("timeout: " + cmd[0]);
        }
        if (process.exitValue() != 0) {
            throw new IllegalStateException(cmd[0] + " exit " + process.exitValue());
        }
    }
}
