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
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import vad.dashing.rootsvc.client.Allowlist;
import vad.dashing.rootsvc.client.RootSvcProtocol;
import vad.dashing.rootsvc.client.WireCodec;

/**
 * Root app_process entry. Binds abstract {@link RootSvcProtocol#SOCKET}; a second instance
 * gets EADDRINUSE and exits. Exit code 42 asks the supervisor to refresh the dex immediately.
 */
public final class ServiceMain {
    private static final Charset UTF8 = Charset.forName("UTF-8");
    public static final int EXIT_UPGRADE = 42;
    /** Wall-clock start of this daemon process (for {@code status} uptimeMs). */
    static final long STARTED_AT_MS = System.currentTimeMillis();

    private static final AtomicInteger WORKER = new AtomicInteger();
    private static final ExecutorService POOL = Executors.newFixedThreadPool(4, new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "rootsvc-cmd-" + WORKER.incrementAndGet());
            t.setDaemon(true);
            return t;
        }
    });

    public static void main(String[] args) {
        File dir = new File(RootSvcProtocol.DIR);
        if (!dir.exists() && !dir.mkdirs()) {
            System.err.println("mkdir " + RootSvcProtocol.DIR);
            System.exit(1);
            return;
        }
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
                try {
                    POOL.execute(new Runnable() {
                        @Override
                        public void run() {
                            serve(socket, dispatcher);
                        }
                    });
                } catch (RejectedExecutionException busy) {
                    rejectBusy(socket);
                }
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

    private static void serve(LocalSocket socket, CommandDispatcher dispatcher) {
        try {
            int uid = peerUid(socket);
            String line = readLine(socket.getInputStream());
            JSONObject req;
            try {
                req = WireCodec.parseLine(line);
            } catch (Exception bad) {
                write(socket, ackThen(null, errorJson(null, RootSvcProtocol.ERR_BAD_ARGS, "json")));
                return;
            }
            String id = req.optString("id", null);
            writeLine(socket, ack(id));

            int v = req.optInt("v", 0);
            if (v != RootSvcProtocol.PROTO) {
                writeLine(socket, errorJson(id, RootSvcProtocol.ERR_PROTO, "v=" + v).toString());
                return;
            }
            String cmd = req.optString("cmd", "");
            List<String> packages = PeerPackages.forUid(uid);
            Set<String> caps;
            if (uid == 0) {
                caps = Allowlist.allKnownCommands();
            } else if (packages.isEmpty()) {
                caps = Allowlist.pingOnly();
            } else {
                caps = Allowlist.capsForPackages(packages);
            }
            if (!RootSvcProtocol.CMD_PING.equals(cmd) && !caps.contains(cmd)) {
                ServiceLog.warn("forbidden uid=" + uid + " pkgs=" + packages + " cmd=" + cmd);
                writeLine(socket, errorJson(id, RootSvcProtocol.ERR_FORBIDDEN, "cap " + cmd).toString());
                return;
            }
            JSONObject args = req.optJSONObject("args");
            JSONObject result = dispatcher.dispatch(cmd, args, uid, packages, caps, req);
            writeLine(socket, result.toString());
            ServiceLog.info("uid=" + uid + " cmd=" + cmd + " ok=" + result.optBoolean("ok", false)
                    + " err=" + result.optString("err", ""));
        } catch (Exception error) {
            ServiceLog.warn("serve " + error.getMessage());
        } finally {
            try {
                socket.close();
            } catch (Exception ignored) {
                // peer closed
            }
        }
    }

    private static void rejectBusy(LocalSocket socket) {
        try {
            write(socket, ackThen(null, errorJson(null, RootSvcProtocol.ERR_BUSY, "queue")));
        } catch (Exception ignored) {
            // ignore
        } finally {
            try {
                socket.close();
            } catch (Exception ignored) {
                // ignore
            }
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
