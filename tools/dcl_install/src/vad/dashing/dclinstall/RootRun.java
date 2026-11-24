package vad.dashing.dclinstall;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

/**
 * One root script at a time. The lock file holds the owner, the wall time and the boot
 * mark, and the script removes it on every exit. A lock from another boot is abandoned
 * immediately, so a power cut in the middle of an install does not block the next boot
 * fix. A lock older than {@link #STALE_MS} on the same boot is abandoned too.
 * Pure Java so the installer tests can run it without Android.
 */
final class RootRun {
    static final String OWNER_INSTALL = "install";
    static final String OWNER_BOOT = "boot";
    static final String OWNER_APPOPS = "appops";
    /** Longer than the on-screen wait, so a live compile is not treated as abandoned. */
    static final long STALE_MS = 16L * 60L * 1000L;

    private RootRun() {}

    /** Boot mark for the device that is calling. Pass {@code SystemClock.elapsedRealtime()}. */
    static long thisBoot(long elapsedRealtimeMs) {
        return bootMark(System.currentTimeMillis(), elapsedRealtimeMs);
    }

    static boolean inProgress(File lock, long nowMs, long currentBootMark) {
        if (lock == null || !lock.isFile()) return false;
        if (!sameBoot(readBootMark(lock), currentBootMark)) return false;
        long modified = lock.lastModified();
        if (modified <= 0L) return false;
        long age = nowMs - modified;
        return age >= 0L && age < STALE_MS;
    }

    static synchronized boolean tryAcquire(String owner, long bootMark) {
        return tryAcquire(new File(InstallPlan.LOCK_FILE), owner, System.currentTimeMillis(), bootMark);
    }

    static synchronized boolean tryAcquire(File lock, String owner, long nowMs, long bootMark) {
        if (inProgress(lock, nowMs, bootMark)) return false;
        try {
            File parent = lock.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) return false;
            FileOutputStream out = new FileOutputStream(lock);
            try {
                String text = (owner == null ? "" : owner) + "\n" + nowMs + "\n" + bootMark + "\n";
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
                byte[] buf = new byte[256];
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

    /** Third line of the lock. Missing or unreadable is 0, which {@link #sameBoot} treats as stale. */
    static long readBootMark(File lock) {
        if (lock == null || !lock.isFile()) return 0L;
        try {
            FileInputStream in = new FileInputStream(lock);
            try {
                byte[] buf = new byte[256];
                int n = in.read(buf);
                if (n <= 0) return 0L;
                String text = new String(buf, 0, n, "UTF-8");
                int first = text.indexOf('\n');
                if (first < 0) return 0L;
                int second = text.indexOf('\n', first + 1);
                if (second < 0) return 0L;
                int third = text.indexOf('\n', second + 1);
                String line = text.substring(second + 1, third < 0 ? text.length() : third).trim();
                if (line.length() == 0) return 0L;
                return Long.parseLong(line);
            } finally {
                in.close();
            }
        } catch (Exception e) {
            return 0L;
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
