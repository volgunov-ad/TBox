package vad.dashing.rootsvc.client;

import android.net.LocalServerSocket;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.Charset;
import java.util.concurrent.TimeUnit;

/**
 * Shared mb_daemon channel-lock protocol (plan §3.4). Bind abstract
 * {@link RootSvcProtocol#LOCK_SOCKET}, write body to a private file, atomically replace
 * {@code autorun.sh} with a one-line {@code exec}, then wait for a nonce marker.
 * The script must not delete {@code autorun.sh}, the shared flag, or clear the property.
 */
public final class MbDaemonChannel {
    private static final Charset UTF8 = Charset.forName("UTF-8");
    public static final long LOCK_WAIT_MS = 15_000L;
    public static final long FOREIGN_WAIT_MS = 10_000L;
    public static final long ANSWER_WAIT_MS = 8_000L;
    private static final long LOCK_RETRY_MS = 100L;

    public interface Exec {
        void run(String... command) throws Exception;
    }

    private final Exec exec;
    private final String ownMarker;
    private final String markerPath;

    public MbDaemonChannel(Exec exec, String ownMarker, String markerPath) {
        this.exec = exec;
        this.ownMarker = ownMarker;
        this.markerPath = markerPath;
    }

    /** Default A9 channel for rootsvc boot scripts. */
    public static MbDaemonChannel rootsvc(Exec exec) {
        return new MbDaemonChannel(exec, RootSvcProtocol.OWN_MARKER, RootSvcProtocol.ARM_MARKER);
    }

    public void arm(String bodyPath, String scriptBody, String nonce) throws Exception {
        LocalServerSocket lock = acquireLock();
        try {
            waitForeignOrOwn();
            write(bodyPath, scriptBody);
            String autorun = "#!/system/bin/sh\nexec /system/bin/sh " + shellQuote(bodyPath) + "\n";
            atomicWrite(RootSvcProtocol.AUTORUN, autorun);
            exec.run("chmod", "777", bodyPath);
            exec.run("chmod", "777", RootSvcProtocol.AUTORUN);
            File flag = new File(RootSvcProtocol.AUTORUN_FLAG);
            if (!flag.exists()) {
                flag.createNewFile();
            }
            exec.run("chmod", "666", RootSvcProtocol.AUTORUN_FLAG);
            exec.run("setprop", RootSvcProtocol.AUTORUN_PROP, "");
            Thread.sleep(300);
            exec.run("setprop", RootSvcProtocol.AUTORUN_PROP, RootSvcProtocol.AUTORUN_FLAG);
            waitNonce(nonce);
        } finally {
            try {
                lock.close();
            } catch (Exception ignored) {
                // Lock name is released when the binder dies.
            }
        }
    }

    private LocalServerSocket acquireLock() throws Exception {
        long deadline = System.currentTimeMillis() + LOCK_WAIT_MS;
        Exception last = null;
        while (System.currentTimeMillis() < deadline) {
            try {
                return new LocalServerSocket(RootSvcProtocol.LOCK_SOCKET);
            } catch (Exception error) {
                last = error;
                Thread.sleep(LOCK_RETRY_MS);
            }
        }
        throw new IllegalStateException(
                "mb_daemon lock busy: " + (last == null ? "timeout" : last.getMessage()));
    }

    private void waitForeignOrOwn() throws InterruptedException {
        File autorun = new File(RootSvcProtocol.AUTORUN);
        long deadline = System.currentTimeMillis() + FOREIGN_WAIT_MS;
        while (System.currentTimeMillis() < deadline) {
            String text = autorun.exists() ? read(autorun) : null;
            if (text == null || text.length() == 0) {
                return;
            }
            if (text.contains(ownMarker)) {
                // Our leftover: rewrite immediately.
                return;
            }
            Thread.sleep(200);
        }
    }

    private void waitNonce(String nonce) throws Exception {
        File marker = new File(markerPath);
        long deadline = System.currentTimeMillis() + ANSWER_WAIT_MS;
        while (System.currentTimeMillis() < deadline) {
            String text = read(marker);
            if (text.contains(nonce)) {
                return;
            }
            Thread.sleep(200);
        }
        throw new IllegalStateException("mb_daemon did not answer with nonce " + nonce);
    }

    static String shellQuote(String path) {
        return "'" + path.replace("'", "'\\''") + "'";
    }

    static void atomicWrite(String path, String text) throws Exception {
        File target = new File(path);
        File parent = target.getParentFile();
        File tmp = File.createTempFile("autorun", ".tmp", parent);
        try {
            write(tmp.getAbsolutePath(), text);
            if (!tmp.renameTo(target)) {
                // sdcardfs may refuse rename across some mounts — fall back to overwrite.
                write(path, text);
                tmp.delete();
            }
        } catch (Exception error) {
            tmp.delete();
            throw error;
        }
    }

    static void write(String path, String text) throws Exception {
        FileOutputStream out = new FileOutputStream(path);
        try {
            out.write(text.replace("\r\n", "\n").replace('\r', '\n').getBytes(UTF8));
        } finally {
            out.close();
        }
    }

    static String read(File file) {
        try {
            if (!file.canRead()) {
                return "";
            }
            FileInputStream in = new FileInputStream(file);
            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                }
                return out.toString("UTF-8");
            } finally {
                in.close();
            }
        } catch (Exception ignored) {
            return "";
        }
    }

    /** ProcessBuilder-backed exec used by the watchdog APK. */
    public static final class ProcessExec implements Exec {
        @Override
        public void run(String... command) throws Exception {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            process.getInputStream().close();
            if (!process.waitFor(8, TimeUnit.SECONDS)) {
                process.destroy();
                throw new IllegalStateException("timeout " + command[0]);
            }
        }
    }
}
