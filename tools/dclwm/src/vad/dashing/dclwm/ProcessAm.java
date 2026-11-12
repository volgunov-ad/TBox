package vad.dashing.dclwm;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/** ProcessBuilder wrapper. The argument list is executed as-is, with no shell. */
public final class ProcessAm implements Am {
    private static final int TIMEOUT_MS = 8000;

    @Override
    public String run(String... args) throws AmFailure {
        Process process;
        try {
            process = new ProcessBuilder(args).redirectErrorStream(true).start();
        } catch (Exception error) {
            throw new AmFailure(error.getMessage());
        }
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        Thread reader = new Thread(new Drain(process.getInputStream(), buf), "dclwm-am");
        reader.setDaemon(true);
        reader.start();
        int code;
        try {
            if (!waitFor(process, TIMEOUT_MS)) {
                process.destroy();
                reader.join(500);
                throw new AmFailure("timeout");
            }
            code = process.exitValue();
            reader.join(500);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            process.destroy();
            throw new AmFailure("interrupted");
        }
        String text = new String(buf.toByteArray(), java.nio.charset.Charset.forName("UTF-8"));
        if (code != 0) {
            throw new AmFailure(text);
        }
        return text;
    }

    private static boolean waitFor(Process process, int timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (true) {
            try {
                process.exitValue();
                return true;
            } catch (IllegalThreadStateException running) {
                if (System.currentTimeMillis() >= deadline) {
                    return false;
                }
                Thread.sleep(40);
            }
        }
    }

    private static final class Drain implements Runnable {
        private final InputStream in;
        private final ByteArrayOutputStream out;

        Drain(InputStream in, ByteArrayOutputStream out) {
            this.in = in;
            this.out = out;
        }

        @Override
        public void run() {
            byte[] buf = new byte[1024];
            try {
                int n;
                while ((n = in.read(buf)) >= 0) {
                    synchronized (out) {
                        out.write(buf, 0, n);
                    }
                }
            } catch (Exception ignored) {
                // The process was destroyed or the pipe closed.
            }
        }
    }
}
