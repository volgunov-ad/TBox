package vad.dashing.rootsvc.daemon;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

/** Process helpers used by command handlers. */
public final class Shell {
    public static final class Result {
        public final int exit;
        public final String stdout;
        public final String stderr;
        public final long ms;

        public Result(int exit, String stdout, String stderr, long ms) {
            this.exit = exit;
            this.stdout = stdout;
            this.stderr = stderr;
            this.ms = ms;
        }
    }

    private Shell() {
    }

    public static Result run(long timeoutMs, String... command) throws Exception {
        long start = System.currentTimeMillis();
        ProcessBuilder pb = new ProcessBuilder(command);
        Process process = pb.start();
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        drain(process.getInputStream(), stdout);
        drain(process.getErrorStream(), stderr);
        boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroy();
            process.waitFor(1, TimeUnit.SECONDS);
            return new Result(124, stdout.toString("UTF-8"), "timeout", System.currentTimeMillis() - start);
        }
        return new Result(
                process.exitValue(),
                stdout.toString("UTF-8"),
                stderr.toString("UTF-8"),
                System.currentTimeMillis() - start);
    }

    public static Result sh(long timeoutMs, String script) throws Exception {
        return run(timeoutMs, "/system/bin/sh", "-c", script);
    }

    private static void drain(final InputStream in, final ByteArrayOutputStream out) {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    byte[] buf = new byte[4096];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        synchronized (out) {
                            out.write(buf, 0, n);
                        }
                    }
                } catch (Exception ignored) {
                    // process ended
                }
            }
        }, "rootsvc-drain");
        t.setDaemon(true);
        t.start();
    }
}
