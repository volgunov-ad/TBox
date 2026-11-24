package vad.dashing.dclinstall;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

/**
 * One root script at a time. The lock file holds the owner name and is removed by the
 * script on every exit. A lock older than {@link #STALE_MS} is treated as abandoned.
 * Pure Java so the installer tests can run it without Android.
 */
final class RootRun {
    static final String OWNER_INSTALL = "install";
    static final String OWNER_BOOT = "boot";
    static final String OWNER_APPOPS = "appops";
    /** Longer than the on-screen wait, so a live compile is not treated as abandoned. */
    static final long STALE_MS = 16L * 60L * 1000L;

    private RootRun() {}

    static boolean inProgress(File lock, long nowMs) {
        if (lock == null || !lock.isFile()) return false;
        long modified = lock.lastModified();
        if (modified <= 0L) return false;
        long age = nowMs - modified;
        return age >= 0L && age < STALE_MS;
    }

    static synchronized boolean tryAcquire(String owner) {
        return tryAcquire(new File(InstallPlan.LOCK_FILE), owner, System.currentTimeMillis());
    }

    static synchronized boolean tryAcquire(File lock, String owner, long nowMs) {
        if (inProgress(lock, nowMs)) return false;
        try {
            File parent = lock.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) return false;
            FileOutputStream out = new FileOutputStream(lock);
            try {
                String text = (owner == null ? "" : owner) + "\n" + nowMs + "\n";
                out.write(text.getBytes("UTF-8"));
            } finally {
                out.close();
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    static String owner(File lock) {
        if (lock == null || !lock.isFile()) return "";
        try {
            FileInputStream in = new FileInputStream(lock);
            try {
                byte[] buf = new byte[128];
                int n = in.read(buf);
                if (n <= 0) return "";
                String text = new String(buf, 0, n, "UTF-8");
                int nl = text.indexOf('\n');
                return (nl < 0 ? text : text.substring(0, nl)).trim();
            } finally {
                in.close();
            }
        } catch (Exception e) {
            return "";
        }
    }

    static void release() {
        new File(InstallPlan.LOCK_FILE).delete();
    }

    /** Wall clock minus elapsed time. Two readings from the same boot stay within a few seconds. */
    static long bootMark(long wallMs, long elapsedMs) {
        return wallMs - elapsedMs;
    }

    static boolean sameBoot(long savedBootMark, long nowBootMark) {
        if (savedBootMark == 0L || nowBootMark == 0L) return false;
        long delta = savedBootMark - nowBootMark;
        if (delta < 0L) delta = -delta;
        return delta < 15_000L;
    }
}
