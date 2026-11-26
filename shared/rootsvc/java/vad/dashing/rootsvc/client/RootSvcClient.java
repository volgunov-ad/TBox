package vad.dashing.rootsvc.client;

import android.content.Context;
import android.content.Intent;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.Build;
import android.os.SystemClock;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Blocking client for the rootsvc abstract socket. Call off the main thread.
 * Pure Java (+ Android LocalSocket) so DCL can copy this class as-is.
 */
public final class RootSvcClient {
    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final AtomicReference<Cache> CACHE = new AtomicReference<Cache>(Cache.unknown());

    private RootSvcClient() {
    }

    public static boolean isUp() {
        Cache cached = CACHE.get();
        long now = System.currentTimeMillis();
        if (cached.up && now - cached.atMs < RootSvcProtocol.IS_UP_CACHE_MS) {
            return true;
        }
        if (!cached.up && cached.known && now - cached.atMs < RootSvcProtocol.DOWN_CACHE_MS) {
            // During the boot window the service may still be starting — do not trust a fresh "down".
            if (SystemClock.elapsedRealtime() >= RootSvcProtocol.BOOT_WINDOW_MS) {
                return false;
            }
        }
        RootSvcResult r = call(RootSvcProtocol.CMD_PING, null, 1_000L, null, null);
        boolean up = r.isOk();
        CACHE.set(new Cache(up, true, System.currentTimeMillis()));
        return up;
    }

    /**
     * In the first {@link RootSvcProtocol#BOOT_WINDOW_MS} after boot, poll the socket for up to
     * {@link RootSvcProtocol#BOOT_WAIT_MS} before treating the service as missing. No-op after
     * the window. Safe to call repeatedly.
     */
    public static void waitBootWindow() {
        if (SystemClock.elapsedRealtime() >= RootSvcProtocol.BOOT_WINDOW_MS) {
            return;
        }
        long deadline = System.currentTimeMillis() + RootSvcProtocol.BOOT_WAIT_MS;
        while (System.currentTimeMillis() < deadline) {
            clearCache();
            RootSvcResult r = call(RootSvcProtocol.CMD_PING, null, 1_000L, null, null);
            if (r.isOk()) {
                CACHE.set(new Cache(true, true, System.currentTimeMillis()));
                return;
            }
            try {
                Thread.sleep(200L);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    public static void kick(Context context) {
        kick(context, false);
    }

    /**
     * Ask the watchdog to {@code ensure()}. With {@code force}, the watchdog arms even after
     * supervisor {@code crashloop} (Monitor restart button).
     */
    public static void kick(Context context, boolean force) {
        if (context == null) {
            return;
        }
        Intent intent = new Intent(RootSvcProtocol.ENSURE_ACTION);
        intent.setPackage(RootSvcProtocol.PACKAGE);
        if (force) {
            intent.putExtra(RootSvcProtocol.ENSURE_EXTRA_FORCE, true);
        }
        try {
            context.sendBroadcast(intent);
        } catch (Exception ignored) {
            // Watchdog may be missing — caller falls back to legacy arm.
        }
    }

    public static RootSvcResult call(String cmd, JSONObject args, long resultTimeoutMs) {
        return call(cmd, args, resultTimeoutMs, null, null);
    }

    /**
     * {@link #waitBootWindow()} then {@link #call}. Prefer this from early-boot clients
     * (camd, dcl_install) so a late socket does not look like {@code UNAVAILABLE}.
     */
    public static RootSvcResult callWithBootWait(
            String cmd, JSONObject args, long resultTimeoutMs) {
        return callWithBootWait(cmd, args, resultTimeoutMs, null, null);
    }

    public static RootSvcResult callWithBootWait(
            String cmd, JSONObject args, long resultTimeoutMs, String client, String clientVersion) {
        waitBootWindow();
        return call(cmd, args, resultTimeoutMs, client, clientVersion);
    }

    public static RootSvcResult call(
            String cmd, JSONObject args, long resultTimeoutMs, String client, String clientVersion) {
        long timeout = FallbackContract.resultTimeoutFor(cmd, resultTimeoutMs);
        String id = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        LocalSocket socket = new LocalSocket();
        try {
            connect(socket);
        } catch (Exception error) {
            CACHE.set(new Cache(false, true, System.currentTimeMillis()));
            return RootSvcResult.unavailable("connect: " + error.getMessage());
        }
        boolean acked = false;
        try {
            String line = WireCodec.requestLine(id, cmd, args, client, clientVersion);
            OutputStream out = socket.getOutputStream();
            out.write(line.getBytes(UTF8));
            out.flush();

            InputStream in = socket.getInputStream();
            String ackLine = readLine(in, RootSvcProtocol.ACK_TIMEOUT_MS);
            if (ackLine == null) {
                CACHE.set(new Cache(false, true, System.currentTimeMillis()));
                return RootSvcResult.unavailable("ack_timeout");
            }
            JSONObject ack = WireCodec.parseLine(ackLine);
            if (!WireCodec.isAck(ack)) {
                return WireCodec.parseResult(ack);
            }
            acked = true;

            String resultLine = readLine(in, timeout);
            if (resultLine == null) {
                return RootSvcResult.unknownOutcome(id, "result_timeout");
            }
            RootSvcResult result = WireCodec.parseResult(WireCodec.parseLine(resultLine));
            if (result.isOk() || result.kind == RootSvcResult.Kind.FAILED) {
                CACHE.set(new Cache(true, true, System.currentTimeMillis()));
            }
            return result;
        } catch (Exception error) {
            // After ACK the command may already be running — never map to UNAVAILABLE
            // (that would trigger mb_daemon fallback / double execution).
            return mapIoError(acked, id, error);
        } finally {
            try {
                socket.close();
            } catch (Exception ignored) {
                // Peer already gone.
            }
        }
    }

    /** Maps a mid-call IO failure; after ACK this must not become {@code UNAVAILABLE}. */
    public static RootSvcResult mapIoError(boolean acked, String id, Exception error) {
        String detail = error == null ? "io" : "io: " + error.getMessage();
        if (acked) {
            return RootSvcResult.unknownOutcome(id, detail);
        }
        return RootSvcResult.unavailable(detail);
    }

    /** Clear the isUp cache (tests / after kick). */
    public static void clearCache() {
        CACHE.set(Cache.unknown());
    }

    /**
     * Abstract sockets answer immediately. Timed {@code connect(addr, timeout)} throws
     * {@link UnsupportedOperationException} on API 28 (A9); use the one-arg form there.
     */
    static void connect(LocalSocket socket) throws Exception {
        LocalSocketAddress addr =
                new LocalSocketAddress(RootSvcProtocol.SOCKET, LocalSocketAddress.Namespace.ABSTRACT);
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                socket.connect(addr, (int) RootSvcProtocol.CONNECT_TIMEOUT_MS);
                return;
            } catch (UnsupportedOperationException unsupported) {
                // Some firmwares advertise API 29+ but still reject timed connect.
            }
        }
        socket.connect(addr);
    }

    private static String readLine(InputStream in, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        while (buf.size() < RootSvcProtocol.LINE_LIMIT) {
            long left = deadline - System.currentTimeMillis();
            if (left <= 0L) {
                return null;
            }
            int available = in.available();
            if (available <= 0) {
                Thread.sleep(Math.min(20L, left));
                continue;
            }
            int b = in.read();
            if (b < 0) {
                break;
            }
            if (b == '\n') {
                break;
            }
            if (b != '\r') {
                buf.write(b);
            }
        }
        if (buf.size() == 0) {
            return null;
        }
        // Cap reached without newline — treat as truncated / unparseable for the caller.
        if (buf.size() >= RootSvcProtocol.LINE_LIMIT) {
            throw new IllegalStateException("line_too_long");
        }
        return new String(buf.toByteArray(), UTF8);
    }

    private static final class Cache {
        final boolean up;
        final boolean known;
        final long atMs;

        Cache(boolean up, boolean known, long atMs) {
            this.up = up;
            this.known = known;
            this.atMs = atMs;
        }

        static Cache unknown() {
            return new Cache(false, false, 0L);
        }
    }
}
