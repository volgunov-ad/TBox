package vad.dashing.dclinstall;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.concurrent.TimeUnit;

/**
 * Runs a shell script as root through mb_daemon. The body goes to its own file and
 * /sdcard/autorun.sh only execs it: UsbMount rewrites autorun.sh on boot, and the shell
 * reads a script while it runs, so a rewrite in the middle would corrupt a long script.
 */
final class MbDaemon {
    static final String INSTALL_BODY = "/sdcard/dcl_install.sh";
    static final String BOOT_BODY = "/sdcard/dcl_boot.sh";
    static final String APPOPS_BODY = "/sdcard/dcl_appops.sh";
    /** Both bodies live under this prefix: an autorun.sh naming it is our own leftover. */
    private static final String OWN_MARKER = "/sdcard/dcl_";
    /**
     * autorun.sh is shared with the DCL launcher, UsbMount and camd; mb_daemon deletes it after
     * running it, so an existing foreign file is a script still waiting or running.
     */
    static final long FOREIGN_WAIT_MS = 10_000L;

    private MbDaemon() {}

    static void run(String script, String bodyPath) throws Exception {
        waitForeignAutorun();
        write(bodyPath, script);
        write(InstallPlan.AUTORUN, "#!/system/bin/sh\nexec /system/bin/sh " + InstallPlan.q(bodyPath) + "\n");
        exec("chmod", "777", bodyPath);
        exec("chmod", "777", InstallPlan.AUTORUN);
        new File(InstallPlan.FLAG).createNewFile();
        exec("setprop", InstallPlan.PROP, "");
        Thread.sleep(300);
        exec("setprop", InstallPlan.PROP, InstallPlan.FLAG);
    }

    /** Bounded: a script nobody triggered is never deleted. */
    static void waitForeignAutorun() throws InterruptedException {
        File autorun = new File(InstallPlan.AUTORUN);
        long deadline = System.currentTimeMillis() + FOREIGN_WAIT_MS;
        while (System.currentTimeMillis() < deadline) {
            if (!isForeign(autorun.exists() ? read(autorun) : null)) return;
            Thread.sleep(200);
        }
    }

    static boolean isForeign(String autorunText) {
        return autorunText != null && !autorunText.contains(OWN_MARKER);
    }

    private static String read(File file) {
        try {
            FileInputStream in = new FileInputStream(file);
            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                }
                return out.toString("UTF-8");
            } finally {
                in.close();
            }
        } catch (Exception e) {
            return "";
        }
    }

    private static void write(String path, String text) throws Exception {
        FileOutputStream out = new FileOutputStream(path);
        try {
            out.write(text.getBytes("UTF-8"));
        } finally {
            out.close();
        }
    }

    private static void exec(String... cmd) throws Exception {
        Process process = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        if (!process.waitFor(8, TimeUnit.SECONDS)) {
            process.destroy();
            throw new IllegalStateException("Таймаут: " + cmd[0]);
        }
        if (process.exitValue() != 0) {
            throw new IllegalStateException(cmd[0] + " завершилась с кодом " + process.exitValue());
        }
    }
}
