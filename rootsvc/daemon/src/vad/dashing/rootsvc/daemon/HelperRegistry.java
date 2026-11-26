package vad.dashing.rootsvc.daemon;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import vad.dashing.rootsvc.client.RootSvcProtocol;

/**
 * Tracks long-lived helpers started via {@code helper.ensure} (camd_grab, dclwm, …).
 */
public final class HelperRegistry {
    private final Map<String, Helper> helpers = new HashMap<String, Helper>();

    public synchronized JSONObject ensure(JSONObject args) throws Exception {
        if (args == null) {
            return ServiceMain.errorJson(null, RootSvcProtocol.ERR_BAD_ARGS, "args");
        }
        String name = args.optString("name", "");
        String path = args.optString("path", "");
        String sha256 = args.optString("sha256", "").toLowerCase(Locale.US);
        String version = args.optString("version", "");
        String kind = args.optString("kind", "exec"); // exec | dex
        String mainClass = args.optString("mainClass", "");
        if (!safeName(name) || path.length() == 0 || sha256.length() != 64) {
            return ServiceMain.errorJson(null, RootSvcProtocol.ERR_BAD_ARGS, "name/path/sha256");
        }
        File src = new File(path);
        if (!src.isFile()) {
            return ServiceMain.errorJson(null, RootSvcProtocol.ERR_BAD_ARGS, "missing path");
        }
        String actual = sha256(src);
        if (!sha256.equals(actual)) {
            return ServiceMain.errorJson(null, RootSvcProtocol.ERR_BAD_ARGS, "sha256 mismatch");
        }
        File destDir = new File(RootSvcProtocol.DIR + "/helpers");
        if (!destDir.exists() && !destDir.mkdirs()) {
            return ServiceMain.errorJson(null, RootSvcProtocol.ERR_FAILED, "mkdir helpers");
        }
        File dest = new File(destDir, name);
        File verFile = new File(destDir, name + ".ver");
        Helper existing = helpers.get(name);
        boolean needStart = existing == null || !existing.alive()
                || !version.equals(read(verFile))
                || !dest.isFile();
        if (needStart) {
            killByName(name);
            copy(src, dest);
            dest.setExecutable(true, false);
            write(verFile, version);
            Process process;
            if ("dex".equals(kind)) {
                if (mainClass.length() == 0) {
                    return ServiceMain.errorJson(null, RootSvcProtocol.ERR_BAD_ARGS, "mainClass");
                }
                ProcessBuilder pb = new ProcessBuilder(
                        "/system/bin/app_process",
                        "/system/bin",
                        mainClass);
                pb.environment().put("CLASSPATH", dest.getAbsolutePath());
                pb.redirectErrorStream(true);
                pb.redirectOutput(new File(RootSvcProtocol.DIR + "/helpers/" + name + ".log"));
                process = pb.start();
            } else {
                ProcessBuilder pb = new ProcessBuilder(dest.getAbsolutePath());
                pb.redirectErrorStream(true);
                pb.redirectOutput(new File(RootSvcProtocol.DIR + "/helpers/" + name + ".log"));
                process = pb.start();
            }
            helpers.put(name, new Helper(name, process, version));
            JSONObject o = new JSONObject();
            o.put("ok", true);
            o.put("state", "started");
            o.put("pid", processPid(process));
            return o;
        }
        JSONObject o = new JSONObject();
        o.put("ok", true);
        o.put("state", "running");
        o.put("pid", processPid(existing.process));
        return o;
    }

    private static boolean safeName(String name) {
        if (name == null || name.length() == 0 || name.length() > 64) {
            return false;
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!(c >= 'a' && c <= 'z')
                    && !(c >= 'A' && c <= 'Z')
                    && !(c >= '0' && c <= '9')
                    && c != '_' && c != '-' && c != '.') {
                return false;
            }
        }
        return true;
    }

    private static void killByName(String name) {
        try {
            Shell.sh(3_000L,
                    "ps -A | grep " + name + " | grep -v grep | while read u p r; do kill -9 $p; done");
        } catch (Exception ignored) {
            // best-effort
        }
    }

    private static String sha256(File file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        FileInputStream in = new FileInputStream(file);
        try {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                md.update(buf, 0, n);
            }
        } finally {
            in.close();
        }
        byte[] dig = md.digest();
        StringBuilder sb = new StringBuilder(dig.length * 2);
        for (int i = 0; i < dig.length; i++) {
            sb.append(String.format(Locale.US, "%02x", dig[i] & 0xff));
        }
        return sb.toString();
    }

    private static void copy(File src, File dest) throws Exception {
        FileInputStream in = new FileInputStream(src);
        try {
            FileOutputStream out = new FileOutputStream(dest);
            try {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                }
            } finally {
                out.close();
            }
        } finally {
            in.close();
        }
    }

    private static void write(File file, String text) throws Exception {
        FileOutputStream out = new FileOutputStream(file);
        try {
            out.write(text.getBytes("UTF-8"));
        } finally {
            out.close();
        }
    }

    private static String read(File file) {
        try {
            if (!file.isFile()) {
                return "";
            }
            FileInputStream in = new FileInputStream(file);
            try {
                byte[] buf = new byte[(int) Math.min(file.length(), 128)];
                int n = in.read(buf);
                if (n <= 0) {
                    return "";
                }
                return new String(buf, 0, n, "UTF-8").trim();
            } finally {
                in.close();
            }
        } catch (Exception ignored) {
            return "";
        }
    }

    private static int processPid(Process process) {
        try {
            java.lang.reflect.Field f = process.getClass().getDeclaredField("pid");
            f.setAccessible(true);
            return f.getInt(process);
        } catch (Exception error) {
            return -1;
        }
    }

    private static final class Helper {
        final String name;
        final Process process;
        final String version;

        Helper(String name, Process process, String version) {
            this.name = name;
            this.process = process;
            this.version = version;
        }

        boolean alive() {
            try {
                process.exitValue();
                return false;
            } catch (IllegalThreadStateException still) {
                return true;
            }
        }
    }
}
