package vad.dashing.rootsvc.daemon;

import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Simple rotating log under /data/local/vad-rootsvc (1 MiB × 2). */
public final class ServiceLog {
    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final long MAX_BYTES = 1L * 1024L * 1024L;
    private static final Object LOCK = new Object();
    private static String path;

    private ServiceLog() {
    }

    public static void open(String logPath) {
        path = logPath;
    }

    public static void info(String msg) {
        write("I", msg);
    }

    public static void warn(String msg) {
        write("W", msg);
    }

    private static void write(String level, String msg) {
        if (path == null) {
            System.err.println(level + " " + msg);
            return;
        }
        synchronized (LOCK) {
            try {
                rotateIfNeeded();
                String line = stamp() + " " + level + " " + msg + "\n";
                FileOutputStream out = new FileOutputStream(path, true);
                try {
                    out.write(line.getBytes(UTF8));
                } finally {
                    out.close();
                }
            } catch (Exception error) {
                System.err.println("log " + error.getMessage());
            }
        }
        System.err.println(level + " " + msg);
    }

    private static void rotateIfNeeded() throws Exception {
        File file = new File(path);
        if (!file.exists() || file.length() < MAX_BYTES) {
            return;
        }
        File bak = new File(path + ".1");
        if (bak.exists()) {
            bak.delete();
        }
        if (!file.renameTo(bak)) {
            // Truncate in place if rename fails.
            RandomAccessFile raf = new RandomAccessFile(file, "rw");
            try {
                raf.setLength(0);
            } finally {
                raf.close();
            }
        }
    }

    private static String stamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
    }
}
