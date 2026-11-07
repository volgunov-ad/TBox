package vad.dashing.dclinstall;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.TimeUnit;

/**
 * After unlock, put DCL's notification listener and accessibility service back.
 * Boot removes them because the launcher is not directBootAware. Waits so
 * UsbMount can use /sdcard/autorun.sh first.
 */
public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "DclInstallBoot";
    private static final long USB_MOUNT_DELAY_MS = 20_000L;

    @Override
    public void onReceive(Context context, Intent intent) {
        final PendingResult pending = goAsync();
        final Context app = context.getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(USB_MOUNT_DELAY_MS);
                    arm(app);
                } catch (Exception e) {
                    Log.e(TAG, "regrant failed", e);
                } finally {
                    pending.finish();
                }
            }
        }, "dcl-listener-regrant").start();
    }

    private static void arm(Context context) throws Exception {
        String script = new InstallPlan("", false, false, false, false, false, false).toListenerScript();
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
        Log.i(TAG, "listener regrant armed");
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
