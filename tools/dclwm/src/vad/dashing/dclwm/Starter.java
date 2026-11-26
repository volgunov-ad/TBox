package vad.dashing.dclwm;

import android.content.Context;
import android.net.LocalServerSocket;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * Starts the helper from the launcher process. Call {@link #ensure(Context)}
 * from a background thread at process start and on BOOT_COMPLETED.
 * Uses the shared mb_daemon channel-lock protocol (abstract socket
 * {@link Protocol#LOCK_SOCKET}): wait for a foreign autorun, write the body to a
 * private file, replace autorun.sh with a one-line exec, never delete the shared
 * flag or clear the property.
 */
public final class Starter {
    private static final int WAIT_MS = 8000;
    private static final long LOCK_WAIT_MS = 15_000L;
    private static final long FOREIGN_WAIT_MS = 10_000L;
    private static final String BODY = "/sdcard/dclwm_boot.sh";
    private static final String OWN_MARKER = "dclwm";

    private Starter() {
    }

    public static void ensure(Context context) throws Exception {
        File dex = new File(context.getFilesDir(), Protocol.DEX_ASSET);
        copyAsset(context, dex);
        String nonce = "n" + System.currentTimeMillis();
        String script = ArmScript.text(dex.getAbsolutePath(), nonce);
        LocalServerSocket lock = acquireLock();
        try {
            waitForeign();
            writeLf(new File(BODY), script);
            String autorun = "#!/system/bin/sh\nexec /system/bin/sh '"
                    + BODY.replace("'", "'\\''") + "'\n";
            atomicWrite(new File(Protocol.AUTORUN), autorun);
            exec("chmod", "777", BODY);
            exec("chmod", "777", Protocol.AUTORUN);
            File flag = new File(Protocol.AUTORUN_FLAG);
            if (!flag.exists()) {
                flag.createNewFile();
            }
            exec("chmod", "666", Protocol.AUTORUN_FLAG);
            exec("setprop", Protocol.AUTORUN_PROP, "");
            Thread.sleep(300);
            exec("setprop", Protocol.AUTORUN_PROP, Protocol.AUTORUN_FLAG);
            waitNonce(nonce);
        } finally {
            try {
                lock.close();
            } catch (Exception ignored) {
                // Lock name is released when the binder dies.
            }
        }
    }

    private static LocalServerSocket acquireLock() throws Exception {
        long deadline = System.currentTimeMillis() + LOCK_WAIT_MS;
        Exception last = null;
        while (System.currentTimeMillis() < deadline) {
            try {
                return new LocalServerSocket(Protocol.LOCK_SOCKET);
            } catch (Exception error) {
                last = error;
                Thread.sleep(100);
            }
        }
        throw new IllegalStateException(
                "mb_daemon lock busy: " + (last == null ? "timeout" : last.getMessage()));
    }

    private static void waitForeign() throws InterruptedException {
        File autorun = new File(Protocol.AUTORUN);
        long deadline = System.currentTimeMillis() + FOREIGN_WAIT_MS;
        while (System.currentTimeMillis() < deadline) {
            String text = autorun.exists() ? readIfAny(autorun) : null;
            if (text == null || text.length() == 0) {
                return;
            }
            if (text.contains(OWN_MARKER) || text.contains(BODY)) {
                return;
            }
            Thread.sleep(200);
        }
    }

    private static void waitNonce(String nonce) throws Exception {
        File marker = new File(Protocol.ARM_MARKER);
        long deadline = System.currentTimeMillis() + WAIT_MS;
        while (System.currentTimeMillis() < deadline) {
            String text = readIfAny(marker);
            if (text.contains(nonce)) {
                if (text.contains("missing") || text.contains("failed")) {
                    throw new IllegalStateException("dclwm did not stay up, see " + Protocol.LOG_FILE);
                }
                return;
            }
            Thread.sleep(200);
        }
        throw new IllegalStateException("mb_daemon did not start dclwm");
    }

    private static void copyAsset(Context context, File dest) throws Exception {
        InputStream in = context.getAssets().open(Protocol.DEX_ASSET);
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(dest);
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) >= 0) {
                out.write(buf, 0, n);
            }
        } finally {
            in.close();
            if (out != null) {
                out.close();
            }
        }
    }

    private static void atomicWrite(File target, String text) throws Exception {
        File parent = target.getParentFile();
        File tmp = File.createTempFile("autorun", ".tmp", parent);
        try {
            writeLf(tmp, text);
            if (!tmp.renameTo(target)) {
                writeLf(target, text);
                tmp.delete();
            }
        } catch (Exception error) {
            tmp.delete();
            throw error;
        }
    }

    private static void writeLf(File file, String text) throws Exception {
        FileOutputStream out = new FileOutputStream(file);
        try {
            out.write(text.replace("\r\n", "\n").replace('\r', '\n').getBytes("UTF-8"));
        } finally {
            out.close();
        }
    }

    private static String readIfAny(File file) {
        try {
            if (!file.canRead()) {
                return "";
            }
            java.io.FileInputStream in = new java.io.FileInputStream(file);
            try {
                byte[] buf = new byte[(int) Math.min(file.length(), 200)];
                int n = in.read(buf);
                if (n <= 0) {
                    return "";
                }
                return new String(buf, 0, n, "UTF-8");
            } finally {
                in.close();
            }
        } catch (Exception ignored) {
            return "";
        }
    }

    private static void exec(String... args) throws Exception {
        Process process = new ProcessBuilder(args).redirectErrorStream(true).start();
        process.waitFor();
    }
}
