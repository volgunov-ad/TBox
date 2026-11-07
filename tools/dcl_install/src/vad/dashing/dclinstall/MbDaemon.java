package vad.dashing.dclinstall;

import java.io.File;
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

    private MbDaemon() {}

    static void run(String script, String bodyPath) throws Exception {
        write(bodyPath, script);
        write(InstallPlan.AUTORUN, "#!/system/bin/sh\nexec /system/bin/sh " + InstallPlan.q(bodyPath) + "\n");
        exec("chmod", "777", bodyPath);
        exec("chmod", "777", InstallPlan.AUTORUN);
        new File(InstallPlan.FLAG).createNewFile();
        exec("setprop", InstallPlan.PROP, "");
        Thread.sleep(300);
        exec("setprop", InstallPlan.PROP, InstallPlan.FLAG);
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
