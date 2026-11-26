package vad.dashing.dclinstall;

import android.content.Context;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import org.json.JSONObject;
import vad.dashing.rootsvc.client.FallbackContract;
import vad.dashing.rootsvc.client.MbDaemonChannel;
import vad.dashing.rootsvc.client.RootSvcClient;
import vad.dashing.rootsvc.client.RootSvcProtocol;
import vad.dashing.rootsvc.client.RootSvcResult;

/**
 * Root entry for dcl_install: prefer {@code rootsvc} ({@code script.run} / typed commands),
 * fall back to {@link MbDaemonChannel} (§3.4). Call off the main thread.
 */
final class RootExec {
    private static final String TAG = "DclRootExec";
    private static final String CLIENT = "vad.dashing.dclinstall";
    private static final String CLIENT_VERSION = "1.24";

    private RootExec() {}

    /**
     * Long scripts (install / setup / compile): channel only.
     * {@code script.run} is capped at 120 s and cannot cover {@code cmd package compile}.
     */
    static void armLong(String script, String bodyPath) throws Exception {
        armChannel(script, bodyPath);
    }

    /**
     * Short scripts: try {@code script.run}, then channel. {@code timeoutMs} ≤ 120 s.
     */
    static void armShort(Context context, String script, String bodyPath, long timeoutMs)
            throws Exception {
        long timeout = Math.min(timeoutMs, RootSvcProtocol.SCRIPT_MAX_TIMEOUT_MS);
        if (timeout <= 0L) {
            armChannel(script, bodyPath);
            return;
        }
        waitBootWindow();
        JSONObject args = new JSONObject()
                .put("body", script)
                .put("timeoutMs", timeout);
        RootSvcResult result = RootSvcClient.call(
                RootSvcProtocol.CMD_SCRIPT_RUN, args, timeout, CLIENT, CLIENT_VERSION);
        if (!FallbackContract.shouldFallback(result, RootSvcProtocol.CMD_SCRIPT_RUN)) {
            if (result.isOk()
                    || (result.kind == RootSvcResult.Kind.FAILED
                            && result.exit != null
                            && result.exit.intValue() == 0)) {
                writeSideFiles(result);
                return;
            }
            if (result.kind == RootSvcResult.Kind.FAILED) {
                writeSideFiles(result);
                throw new IllegalStateException(
                        "rootsvc script failed: "
                                + (result.msg == null ? result.err : result.msg));
            }
            throw new IllegalStateException(
                    "rootsvc rejected: " + result.err + " " + result.msg);
        }
        Log.i(TAG, "script.run fallback: " + result.kind
                + (result.reason == null ? "" : " " + result.reason));
        if (context != null) {
            RootSvcClient.kick(context);
        }
        armChannel(script, bodyPath);
    }

    /**
     * Boot fixups via typed commands when the service is up; otherwise channel + boot script.
     *
     * @return true when the channel path was armed (caller should not mark boot until done),
     *         false when rootsvc handled everything synchronously
     */
    static boolean bootFix(
            Context context,
            String helperDex,
            String unlockDex,
            boolean restoreWallpaper,
            boolean unlockUsb,
            boolean nativeUnlock)
            throws Exception {
        waitBootWindow();
        if (tryBootViaService(context, restoreWallpaper, unlockUsb)) {
            return false;
        }
        if (context != null) {
            RootSvcClient.kick(context);
        }
        String script = new InstallPlan("", false, false, false, false, false, false, nativeUnlock)
                .toBootScript(helperDex, unlockDex, restoreWallpaper, unlockUsb);
        armChannel(script, MbDaemon.BOOT_BODY);
        return true;
    }

    private static boolean tryBootViaService(
            Context context, boolean restoreWallpaper, boolean unlockUsb) {
        if (!RootSvcClient.isUp()) {
            return false;
        }
        try {
            if (unlockUsb) {
                RootSvcResult unlock = RootSvcClient.call(
                        RootSvcProtocol.CMD_USB_UNLOCK, null, 5_000L, CLIENT, CLIENT_VERSION);
                if (FallbackContract.shouldFallback(unlock, RootSvcProtocol.CMD_USB_UNLOCK)) {
                    return false;
                }
                if (!unlock.isOk()) {
                    Log.w(TAG, "usb.unlock " + unlock.err + " " + unlock.msg);
                }
            }
            if (restoreWallpaper) {
                JSONObject args = new JSONObject().put("component", InstallPlan.BOOT_WALLPAPER);
                RootSvcResult wall = RootSvcClient.call(
                        RootSvcProtocol.CMD_WALLPAPER_SET, args, 15_000L, CLIENT, CLIENT_VERSION);
                if (FallbackContract.shouldFallback(wall, RootSvcProtocol.CMD_WALLPAPER_SET)) {
                    return false;
                }
                if (!wall.isOk()) {
                    Log.w(TAG, "wallpaper.set " + wall.err + " " + wall.msg);
                }
            }
            String listeners = InstallPlan.bootListenersOnlyScript();
            JSONObject args = new JSONObject()
                    .put("body", listeners)
                    .put("timeoutMs", 30_000L);
            RootSvcResult script = RootSvcClient.call(
                    RootSvcProtocol.CMD_SCRIPT_RUN, args, 30_000L, CLIENT, CLIENT_VERSION);
            if (FallbackContract.shouldFallback(script, RootSvcProtocol.CMD_SCRIPT_RUN)) {
                return false;
            }
            writeBootSideFiles(script);
            File result = new File(InstallPlan.BOOT_RESULT_FILE);
            if (!result.isFile()) {
                writeText(InstallPlan.BOOT_RESULT_FILE, script.isOk() ? "OK\n" : "FAIL rootsvc\n");
            }
            Log.i(TAG, "boot fix via rootsvc");
            return true;
        } catch (Exception e) {
            Log.w(TAG, "boot via rootsvc failed", e);
            return false;
        }
    }

    private static void armChannel(String script, String bodyPath) throws Exception {
        String nonce = "n" + System.currentTimeMillis();
        String wrapped = MbDaemon.wrapWithNonce(script, nonce);
        MbDaemonChannel channel = new MbDaemonChannel(
                new MbDaemonChannel.ProcessExec(),
                InstallPlan.OWN_MARKER,
                InstallPlan.ARM_MARKER);
        channel.arm(bodyPath, wrapped, nonce);
    }

    private static void waitBootWindow() {
        RootSvcClient.waitBootWindow();
    }

    private static void writeSideFiles(RootSvcResult result) throws Exception {
        String out = result.stdout == null ? "" : result.stdout;
        if (out.length() > 0) {
            writeText(InstallPlan.LOG_FILE, out);
        }
        if (result.isOk()
                || (result.exit != null && result.exit.intValue() == 0)) {
            if (!new File(InstallPlan.RESULT_FILE).isFile()) {
                writeText(InstallPlan.RESULT_FILE, "OK\n");
            }
        } else {
            writeText(InstallPlan.RESULT_FILE, "FAIL rootsvc\n");
        }
    }

    private static void writeBootSideFiles(RootSvcResult result) throws Exception {
        String out = result.stdout == null ? "" : result.stdout;
        if (out.length() > 0) {
            writeText(InstallPlan.BOOT_LOG_FILE, out);
        }
    }

    private static void writeText(String path, String text) throws Exception {
        FileOutputStream out = new FileOutputStream(path);
        try {
            out.write(text.getBytes("UTF-8"));
        } finally {
            out.close();
        }
    }
}
