package vad.dashing.rootsvc.daemon;

import android.net.Credentials;
import android.net.LocalServerSocket;
import android.net.LocalSocket;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import vad.dashing.rootsvc.client.Allowlist;
import vad.dashing.rootsvc.client.RootSvcProtocol;
import vad.dashing.rootsvc.client.WireCodec;

/**
 * Root app_process entry. Binds abstract {@link RootSvcProtocol#SOCKET}; a second instance
 * gets EADDRINUSE and exits. Exit code {@link RootSvcProtocol#EXIT_UPGRADE} asks the supervisor
 * to refresh the dex immediately; {@link RootSvcProtocol#EXIT_RESTART} is a deliberate restart.
 *
 * <p>Accept thread reads the request and sends {@code ACK} immediately, then hands work to a
 * bounded pool (overflow → {@code busy}). {@code ping}/{@code hello}/{@code status} stay on the
 * accept thread. {@code script.run} is serialized with a semaphore.
 */
public final class ServiceMain {
    private static final Charset UTF8 = Charset.forName("UTF-8");
    public static final int EXIT_UPGRADE = RootSvcProtocol.EXIT_UPGRADE;
    public static final int EXIT_RESTART = RootSvcProtocol.EXIT_RESTART;
    /** Wall-clock start of this daemon process (for {@code status} uptimeMs). */
    static final long STARTED_AT_MS = System.currentTimeMillis();

    private static final AtomicInteger WORKER = new AtomicInteger();
    private static final Semaphore SCRIPT_LOCK = new Semaphore(1);
    private static final ExecutorService POOL = new ThreadPoolExecutor(
            4,
            4,
            0L,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<Runnable>(RootSvcProtocol.WORKER_QUEUE_CAPACITY),
            new ThreadFactory() {
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "rootsvc-cmd-" + WORKER.incrementAndGet());
                    t.setDaemon(true);
                    return t;
                }
            },
            new ThreadPoolExecutor.AbortPolicy());

    public static void main(String[] args) {
        hardenStateDir();
        ServiceLog.open(RootSvcProtocol.LOG_PATH);
        LocalServerSocket server;
        try {
            server = new LocalServerSocket(RootSvcProtocol.SOCKET);
        } catch (Exception error) {
            System.err.println("bind " + error.getMessage());
            System.exit(1);
            return;
        }
        ServiceLog.info("listening uid=" + android.os.Process.myUid()
                + " ver=" + RootSvcProtocol.SERVER_VERSION);
        HelperRegistry helpers = new HelperRegistry();
        CommandDispatcher dispatcher = new CommandDispatcher(helpers);
        long lastWatchdogCheck = 0L;
        while (true) {
            long now = System.currentTimeMillis();
            if (now - lastWatchdogCheck > 60_000L) {
                lastWatchdogCheck = now;
                if (!watchdogInstalled()) {
                    ServiceLog.info("watchdog package missing — exit");
                    System.exit(0);
                    return;
                }
            }
            LocalSocket client = null;
            try {
                client = server.accept();
                final LocalSocket socket = client;
                client = null;
                acceptOne(socket, dispatcher);
            } catch (Exception error) {
                ServiceLog.warn("accept " + error.getMessage());
                if (client != null) {
                    try {
                        client.close();
                    } catch (Exception ignored) {
                        // gone
                    }
                }
            }
        }
    }

    /** Make state / helpers readable to the watchdog, Monitor, and camd (not root-only 0700). */
    static void hardenStateDir() {
        File dir = new File(RootSvcProtocol.DIR);
        if (!dir.exists() && !dir.mkdirs()) {
            System.err.println("mkdir " + RootSvcProtocol.DIR);
            System.exit(1);
            return;
        }
        chmod(RootSvcProtocol.DIR, "711");
        File helpers = new File(RootSvcProtocol.HELPERS_DIR);
        if (!helpers.exists()) {
            //noinspection ResultOfMethodCallIgnored
            helpers.mkdirs();
        }
        chmod(RootSvcProtocol.HELPERS_DIR, "711");
        File state = new File(RootSvcProtocol.STATE_PATH);
        if (state.isFile()) {
            chmod(RootSvcProtocol.STATE_PATH, "644");
        }
    }

    private static void acceptOne(LocalSocket socket, CommandDispatcher dispatcher) {
        String id = null;
        boolean acked = false;
        try {
            int uid = peerUid(socket);
            String line = readLine(socket.getInputStream());
            JSONObject req;
            try {
                req = WireCodec.parseLine(line);
            } catch (Exception bad) {
                write(socket, ackThen(null, errorJson(null, RootSvcProtocol.ERR_BAD_ARGS, "json")));
                closeQuietly(socket);
                return;
            }
            id = req.optString("id", null);
            writeLine(socket, ack(id));
            acked = true;

            int v = req.optInt("v", 0);
            if (v != RootSvcProtocol.PROTO) {
                writeLine(socket, errorJson(id, RootSvcProtocol.ERR_PROTO, "v=" + v).toString());
                closeQuietly(socket);
                return;
            }
            final String cmd = req.optString("cmd", "");
            if (RootSvcProtocol.CMD_PING.equals(cmd)
                    || RootSvcProtocol.CMD_HELLO.equals(cmd)
                    || RootSvcProtocol.CMD_STATUS.equals(cmd)) {
                serveAfterAck(socket, dispatcher, uid, req, /*inline=*/true);
                return;
            }
            final String requestId = id;
            final JSONObject request = req;
            final int peer = uid;
            try {
                POOL.execute(new Runnable() {
                    @Override
                    public void run() {
                        serveAfterAck(socket, dispatcher, peer, request, /*inline=*/false);
                    }
                });
            } catch (RejectedExecutionException busy) {
                try {
                    writeLine(socket, errorJson(requestId, RootSvcProtocol.ERR_BUSY, "queue").toString());
                } catch (Exception ignored) {
                    // peer gone
                } finally {
                    closeQuietly(socket);
                }
            }
        } catch (Exception error) {
            ServiceLog.warn("acceptOne " + error.getMessage());
            if (acked) {
                try {
                    writeLine(socket, errorJson(id, RootSvcProtocol.ERR_FAILED, error.getMessage()).toString());
                } catch (Exception ignored) {
                    // peer gone
                }
            }
            closeQuietly(socket);
        }
    }

    private static void serveAfterAck(
            LocalSocket socket,
            CommandDispatcher dispatcher,
            int uid,
            JSONObject req,
            boolean inline) {
        String id = req.optString("id", null);
        String cmd = req.optString("cmd", "");
        boolean scriptHeld = false;
        try {
            List<String> packages;
            Set<String> caps;
            if (RootSvcProtocol.CMD_PING.equals(cmd)) {
                // Skip pm list for ping — keep the accept-thread path cheap.
                packages = Collections.emptyList();
                caps = Allowlist.pingOnly();
            } else if (uid == 0) {
                packages = Collections.emptyList();
                caps = Allowlist.allKnownCommands();
            } else {
                packages = PeerPackages.forUid(uid);
                if (packages.isEmpty()) {
                    caps = Allowlist.pingOnly();
                } else {
                    caps = Allowlist.capsForPackages(packages);
                }
            }
            if (!RootSvcProtocol.CMD_PING.equals(cmd) && !caps.contains(cmd)) {
                ServiceLog.warn("forbidden uid=" + uid + " pkgs=" + packages + " cmd=" + cmd);
                PeerPackages.invalidate(uid);
                writeLine(socket, errorJson(id, RootSvcProtocol.ERR_FORBIDDEN, "cap " + cmd).toString());
                return;
            }
            if (RootSvcProtocol.CMD_SCRIPT_RUN.equals(cmd)) {
                SCRIPT_LOCK.acquire();
                scriptHeld = true;
            }
            JSONObject args = req.optJSONObject("args");
            JSONObject result;
            try {
                result = dispatcher.dispatch(cmd, args, uid, packages, caps, req);
            } catch (Exception dispatchError) {
                ServiceLog.warn("dispatch " + cmd + " " + dispatchError.getMessage());
                result = errorJson(id, RootSvcProtocol.ERR_FAILED, dispatchError.getMessage());
            }
            writeLine(socket, result.toString());
            ServiceLog.info("uid=" + uid + " cmd=" + cmd + " ok=" + result.optBoolean("ok", false)
                    + " err=" + result.optString("err", "")
                    + (inline ? " inline" : ""));
        } catch (Exception error) {
            ServiceLog.warn("serve " + error.getMessage());
            try {
                writeLine(socket, errorJson(id, RootSvcProtocol.ERR_FAILED, error.getMessage()).toString());
            } catch (Exception ignored) {
                // peer closed — client sees UNKNOWN_OUTCOME
            }
        } finally {
            if (scriptHeld) {
                SCRIPT_LOCK.release();
            }
            closeQuietly(socket);
        }
    }

    private static void closeQuietly(LocalSocket socket) {
        try {
            socket.close();
        } catch (Exception ignored) {
            // peer closed
        }
    }

    private static void write(LocalSocket socket, String payload) throws Exception {
        OutputStream out = socket.getOutputStream();
        out.write(payload.getBytes(UTF8));
        out.flush();
    }

    private static void writeLine(LocalSocket socket, String line) throws Exception {
        write(socket, line.endsWith("\n") ? line : line + "\n");
    }

    private static String ack(String id) throws Exception {
        JSONObject o = new JSONObject();
        if (id != null) {
            o.put("id", id);
        }
        o.put("ack", true);
        return o.toString() + "\n";
    }

    private static String ackThen(String id, JSONObject result) throws Exception {
        return ack(id) + result.toString() + "\n";
    }

    static JSONObject errorJson(String id, String err, String msg) throws Exception {
        JSONObject o = new JSONObject();
        if (id != null) {
            o.put("id", id);
        }
        o.put("ok", false);
        o.put("err", err);
        if (msg != null) {
            o.put("msg", msg);
        }
        return o;
    }

    static JSONObject okJson(String id, int exit, long ms) throws Exception {
        JSONObject o = new JSONObject();
        if (id != null) {
            o.put("id", id);
        }
        o.put("ok", true);
        o.put("exit", exit);
        o.put("ms", ms);
        return o;
    }

    private static int peerUid(LocalSocket client) {
        try {
            Credentials credentials = client.getPeerCredentials();
            if (credentials == null) {
                return -1;
            }
            return credentials.getUid();
        } catch (Exception error) {
            return -1;
        }
    }

    private static String readLine(InputStream in) throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        while (buf.size() < RootSvcProtocol.LINE_LIMIT) {
            int b = in.read();
            if (b < 0 || b == '\n') {
                break;
            }
            if (b != '\r') {
                buf.write(b);
            }
        }
        return new String(buf.toByteArray(), UTF8);
    }

    private static void chmod(String path, String mode) {
        try {
            Process process = new ProcessBuilder("chmod", mode, path)
                    .redirectErrorStream(true)
                    .start();
            process.waitFor();
        } catch (Exception ignored) {
            // best-effort on startup
        }
    }

    private static boolean watchdogInstalled() {
        try {
            Process process = new ProcessBuilder("pm", "path", RootSvcProtocol.PACKAGE)
                    .redirectErrorStream(true)
                    .start();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            InputStream in = process.getInputStream();
            byte[] buf = new byte[256];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            process.waitFor();
            return out.toString("UTF-8").contains("package:");
        } catch (Exception error) {
            return true;
        }
    }
}
