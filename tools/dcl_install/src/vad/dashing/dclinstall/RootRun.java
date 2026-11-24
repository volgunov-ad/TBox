package vad.dashing.dclinstall;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

/**
 * One root script at a time. The lock file holds the owner, {@code elapsedRealtime} at
 * acquire, and a boot id. A lock from another boot is abandoned immediately, so a power
 * cut in the middle of an install does not block the next boot fix. Age is the change in
 * elapsed time, not the wall clock: an SNTP jump cannot make a live lock look stale.
 * A lock older than {@link #STALE_MS} on the same boot is abandoned too.
 * Pure Java so the installer tests can run it without Android. Callers pass the boot id
 * ({@code Settings.Global.BOOT_COUNT}, or the wall-clock fallback when that is 0).
 */
final class RootRun {
    static final String OWNER_INSTALL = "install";
    static final String OWNER_BOOT = "boot";
    static final String OWNER_APPOPS = "appops";
    /** Longer than the on-screen wait, so a live compile is not treated as abandoned. */
    static final long STALE_MS = 16L * 60L * 1000L;
    /**
     * Boot counts stay far below this. Larger ids are the wall-clock fallback
     * ({@link #bootMark}), which drifts by a few seconds and is compared with a window.
     */
    private static final long WALL_MARK_MIN = 1_000_000L;

    private RootRun() {}

    static boolean inProgress(File lock, long elapsedNowMs, long currentBootId) {
        if (lock == null || !lock.isFile()) return false;
        if (!sameBoot(readBootMark(lock), currentBootId)) return false;
        long started = readElapsed(lock);
        if (started <= 0L) return false;
        long age = elapsedNowMs - started;
        return age >= 0L && age < STALE_MS;
    }

    static synchronized boolean tryAcquire(String owner, long elapsedNowMs, long bootId) {
        return tryAcquire(new File(InstallPlan.LOCK_FILE), owner, elapsedNowMs, bootId);
    }

    static synchronized boolean tryAcquire(File lock, String owner, long elapsedNowMs, long bootId) {
        if (inProgress(lock, elapsedNowMs, bootId)) return false;
        try {
            File parent = lock.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) return false;
            FileOutputStream out = new FileOutputStream(lock);
            try {
                String text = (owner == null ? "" : owner) + "\n" + elapsedNowMs + "\n" + bootId + "\n";
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
        return readLine(lock, 0);
    }

    /** Second line: {@code elapsedRealtime} when the lock was taken. Missing is 0. */
    static long readElapsed(File lock) {
        return parseLong(readLine(lock, 1));
    }

    /** Third line: boot id. Missing or unreadable is 0, which {@link #sameBoot} treats as stale. */
    static long readBootMark(File lock) {
        return parseLong(readLine(lock, 2));
    }

    static void release() {
        new File(InstallPlan.LOCK_FILE).delete();
    }

    /**
     * Wall clock minus elapsed time. Used only when {@code BOOT_COUNT} is missing.
     * Two readings from the same boot stay within a few seconds while the clock is stable.
     */
    static long bootMark(long wallMs, long elapsedMs) {
        return wallMs - elapsedMs;
    }

    /** Boot counts must match. A wall-clock fallback may differ by a few seconds. Zero never matches. */
    static boolean sameBoot(long savedBootId, long nowBootId) {
        if (savedBootId == 0L || nowBootId == 0L) return false;
        if (savedBootId == nowBootId) return true;
        if (savedBootId < WALL_MARK_MIN || nowBootId < WALL_MARK_MIN) return false;
        long delta = savedBootId - nowBootId;
        if (delta < 0L) delta = -delta;
        return delta < 15_000L;
    }

    private static long parseLong(String line) {
        if (line == null || line.length() == 0) return 0L;
        try {
            return Long.parseLong(line);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static String readLine(File lock, int index) {
        if (lock == null || !lock.isFile()) return "";
        try {
            FileInputStream in = new FileInputStream(lock);
            try {
                byte[] buf = new byte[256];
                int n = in.read(buf);
                if (n <= 0) return "";
                String text = new String(buf, 0, n, "UTF-8");
                int start = 0;
                for (int i = 0; i < index; i++) {
                    int nl = text.indexOf('\n', start);
                    if (nl < 0) return "";
                    start = nl + 1;
                }
                int end = text.indexOf('\n', start);
                return text.substring(start, end < 0 ? text.length() : end).trim();
            } finally {
                in.close();
            }
        } catch (Exception e) {
            return "";
        }
    }
}
