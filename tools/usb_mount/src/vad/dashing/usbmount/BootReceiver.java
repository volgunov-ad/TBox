package vad.dashing.usbmount;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

/**
 * SystemUI is hidden, so WTKeyguard never calls setLockScreenShown(false).
 * vold then keeps its default "secure keyguard" and refuses to scan USB disks,
 * which is why NTFS/exFAT sticks never appear. mb_daemon runs /sdcard/autorun.sh
 * as root when vendor.autorun.sdcard.logfile points at an existing flag file.
 * Root can clear that vold flag; this process cannot.
 */
public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "UsbMountFix";
    private static final String PROP = "vendor.autorun.sdcard.logfile";
    private static final String SCRIPT = "/sdcard/autorun.sh";
    private static final String FLAG = "/sdcard/autorun.flag";

    @Override
    public void onReceive(Context context, Intent intent) {
        final PendingResult pending = goAsync();
        final Context app = context.getApplicationContext();
        new Thread(new ArmTask(app, pending), "usb-mount-fix").start();
    }

    private static final class ArmTask implements Runnable {
        private final Context app;
        private final PendingResult pending;

        ArmTask(Context app, PendingResult pending) {
            this.app = app;
            this.pending = pending;
        }

        @Override
        public void run() {
            try {
                arm(app);
            } catch (Exception e) {
                Log.e(TAG, "arm failed", e);
            } finally {
                pending.finish();
            }
        }
    }

    private static void arm(Context context) throws Exception {
        File dex = new File(context.getFilesDir(), "unlockusb.dex");
        copyAsset(context, "unlockusb.dex", dex);
        String script = "#!/system/bin/sh\n"
                + "CLASSPATH=" + dex.getAbsolutePath()
                + " app_process /system/bin UnlockUsb > /data/local/tmp/unlockusb_boot.txt 2>&1\n";
        FileOutputStream scriptOut = new FileOutputStream(SCRIPT);
        try {
            scriptOut.write(script.getBytes("UTF-8"));
        } finally {
            scriptOut.close();
        }
        exec(new String[]{"chmod", "777", SCRIPT});
        new File(FLAG).createNewFile();
        exec(new String[]{"setprop", PROP, ""});
        Thread.sleep(300);
        exec(new String[]{"setprop", PROP, FLAG});
        Log.i(TAG, "autorun armed");
    }

    private static void copyAsset(Context context, String name, File dest) throws Exception {
        InputStream in = context.getAssets().open(name);
        try {
            FileOutputStream out = new FileOutputStream(dest);
            try {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                }
            } finally {
                out.close();
            }
        } finally {
            in.close();
        }
    }

    private static void exec(String[] cmd) throws Exception {
        Process process = new ProcessBuilder(cmd).start();
        if (!process.waitFor(5, TimeUnit.SECONDS)) {
            process.destroy();
            throw new IllegalStateException("timeout: " + cmd[0]);
        }
        if (process.exitValue() != 0) {
            throw new IllegalStateException(cmd[0] + " exit " + process.exitValue());
        }
    }
}
