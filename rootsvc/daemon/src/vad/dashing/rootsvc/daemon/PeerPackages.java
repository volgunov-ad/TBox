package vad.dashing.rootsvc.daemon;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import vad.dashing.rootsvc.client.PmListParser;
import vad.dashing.rootsvc.client.RootSvcProtocol;

/** Resolves package names for a uid via {@code pm list packages -U}, with a short TTL cache. */
public final class PeerPackages {
    private static final ConcurrentHashMap<Integer, CacheEntry> CACHE =
            new ConcurrentHashMap<Integer, CacheEntry>();

    private PeerPackages() {
    }

    public static List<String> forUid(int uid) {
        if (uid < 0) {
            return Collections.emptyList();
        }
        long now = System.currentTimeMillis();
        CacheEntry cached = CACHE.get(Integer.valueOf(uid));
        if (cached != null && now - cached.atMs < RootSvcProtocol.PEER_CACHE_TTL_MS) {
            return cached.packages;
        }
        List<String> looked = lookup(uid);
        List<String> frozen = Collections.unmodifiableList(new ArrayList<String>(looked));
        CACHE.put(Integer.valueOf(uid), new CacheEntry(frozen, now));
        return frozen;
    }

    /** Drop cached packages for {@code uid} (e.g. after an unexpected allowlist miss). */
    public static void invalidate(int uid) {
        CACHE.remove(Integer.valueOf(uid));
    }

    public static void clearCache() {
        CACHE.clear();
    }

    private static List<String> lookup(int uid) {
        try {
            Process process = new ProcessBuilder("pm", "list", "packages", "-U")
                    .redirectErrorStream(true)
                    .start();
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            InputStream in = process.getInputStream();
            byte[] tmp = new byte[4096];
            int n;
            while ((n = in.read(tmp)) > 0) {
                buf.write(tmp, 0, n);
            }
            process.waitFor();
            return PmListParser.forUid(buf.toString("UTF-8"), uid);
        } catch (Exception error) {
            ServiceLog.warn("pm list " + error.getMessage());
            return new ArrayList<String>();
        }
    }

    private static final class CacheEntry {
        final List<String> packages;
        final long atMs;

        CacheEntry(List<String> packages, long atMs) {
            this.packages = packages;
            this.atMs = atMs;
        }
    }
}
