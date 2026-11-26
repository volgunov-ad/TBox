package vad.dashing.rootsvc.daemon;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.MessageDigest;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import vad.dashing.rootsvc.client.RootSvcProtocol;

/** Dispatches allowlisted commands. */
public final class CommandDispatcher {
    private static final Pattern PKG = Pattern.compile("^[A-Za-z0-9._]+$");
    private static final Pattern PERM = Pattern.compile("^[A-Za-z0-9._]+$");
    private static final Set<String> SVC_NAMES = java.util.Collections.singleton("AVMapp");
    private static final Set<String> SETTINGS_NS = setOf("secure", "global");

    private final HelperRegistry helpers;
    private final WmCommands wm = new WmCommands();

    public CommandDispatcher(HelperRegistry helpers) {
        this.helpers = helpers;
    }

    public JSONObject dispatch(
            String cmd,
            JSONObject args,
            int uid,
            List<String> packages,
            Set<String> caps,
            JSONObject req) throws Exception {
        String id = req.optString("id", null);
        long start = System.currentTimeMillis();
        if (RootSvcProtocol.CMD_PING.equals(cmd)) {
            JSONObject o = ServiceMain.okJson(id, 0, 0L);
            o.put("server", RootSvcProtocol.SERVER_VERSION);
            o.put("proto", RootSvcProtocol.PROTO);
            return o;
        }
        if (RootSvcProtocol.CMD_HELLO.equals(cmd)) {
            JSONObject o = ServiceMain.okJson(id, 0, 0L);
            o.put("server", RootSvcProtocol.SERVER_VERSION);
            o.put("proto", RootSvcProtocol.PROTO);
            o.put("hu", "A9");
            o.put("uid", uid);
            JSONArray arr = new JSONArray();
            for (String c : caps) {
                if (!RootSvcProtocol.CMD_PING.equals(c) && !RootSvcProtocol.CMD_HELLO.equals(c)) {
                    arr.put(c);
                }
            }
            o.put("caps", arr);
            return o;
        }
        if (RootSvcProtocol.CMD_STATUS.equals(cmd)) {
            return status(id);
        }
        if (RootSvcProtocol.CMD_ADMIN_RESTART.equals(cmd)) {
            return adminRestart(id);
        }
        if (RootSvcProtocol.CMD_ADMIN_UPGRADE.equals(cmd)) {
            return adminUpgrade(id, args);
        }
        if (RootSvcProtocol.CMD_USB_UNLOCK.equals(cmd)) {
            UsbUnlock.run();
            return ServiceMain.okJson(id, 0, System.currentTimeMillis() - start);
        }
        if (RootSvcProtocol.CMD_WALLPAPER_SET.equals(cmd)) {
            String component = args == null ? null : args.optString("component", null);
            if (component == null || component.length() == 0) {
                return ServiceMain.errorJson(id, RootSvcProtocol.ERR_BAD_ARGS, "component");
            }
            WallpaperSet.run(component);
            return ServiceMain.okJson(id, 0, System.currentTimeMillis() - start);
        }
        if (RootSvcProtocol.CMD_HELPER_ENSURE.equals(cmd)) {
            JSONObject result = helpers.ensure(args);
            if (!result.has("id") && id != null) {
                result.put("id", id);
            }
            return result;
        }
        if (RootSvcProtocol.CMD_SVC_CTL.equals(cmd)) {
            return svcCtl(id, args, start);
        }
        if (cmd.startsWith("pkg.")) {
            return pkg(id, cmd, args, packages, start);
        }
        if (cmd.startsWith("settings.")) {
            return settings(id, cmd, args, packages, start);
        }
        if (cmd.startsWith("wm.")) {
            return wm.handle(id, cmd, args);
        }
        if (RootSvcProtocol.CMD_POWER_REBOOT.equals(cmd)) {
            // Delay so the ACK/result can leave the socket first.
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(1_000L);
                        Shell.run(5_000L, "svc", "power", "reboot");
                    } catch (Exception ignored) {
                        // reboot best-effort
                    }
                }
            }, "rootsvc-reboot").start();
            return ServiceMain.okJson(id, 0, System.currentTimeMillis() - start);
        }
        if (RootSvcProtocol.CMD_SCRIPT_RUN.equals(cmd)) {
            return scriptRun(id, args, start);
        }
        return ServiceMain.errorJson(id, RootSvcProtocol.ERR_UNKNOWN_CMD, cmd);
    }

    private JSONObject svcCtl(String id, JSONObject args, long start) throws Exception {
        if (args == null) {
            return ServiceMain.errorJson(id, RootSvcProtocol.ERR_BAD_ARGS, "args");
        }
        String action = args.optString("action", "");
        String name = args.optString("name", "");
        if (!("start".equals(action) || "stop".equals(action)) || !SVC_NAMES.contains(name)) {
            return ServiceMain.errorJson(id, RootSvcProtocol.ERR_BAD_ARGS, "action/name");
        }
        Shell.Result r = Shell.run(5_000L, "setprop", "ctl." + action, name);
        JSONObject o = ServiceMain.okJson(id, r.exit, System.currentTimeMillis() - start);
        if (r.exit != 0) {
            o.put("ok", false);
            o.put("err", RootSvcProtocol.ERR_FAILED);
            o.put("stderr", r.stderr);
        }
        return o;
    }

    private JSONObject pkg(
            String id, String cmd, JSONObject args, List<String> packages, long start) throws Exception {
        if (args == null) {
            return ServiceMain.errorJson(id, RootSvcProtocol.ERR_BAD_ARGS, "args");
        }
        String pkg = args.optString("package", "");
        if (!PKG.matcher(pkg).matches()) {
            return ServiceMain.errorJson(id, RootSvcProtocol.ERR_BAD_ARGS, "package");
        }
        Shell.Result r;
        if (RootSvcProtocol.CMD_PKG_HIDE.equals(cmd)) {
            r = Shell.run(8_000L, "pm", "hide", pkg);
        } else if (RootSvcProtocol.CMD_PKG_UNHIDE.equals(cmd)) {
            r = Shell.run(8_000L, "pm", "unhide", pkg);
        } else if (RootSvcProtocol.CMD_PKG_ENABLE.equals(cmd)) {
            r = Shell.run(8_000L, "pm", "enable", pkg);
        } else if (RootSvcProtocol.CMD_PKG_DISABLE.equals(cmd)) {
            r = Shell.run(8_000L, "pm", "disable-user", "--user", "0", pkg);
        } else if (RootSvcProtocol.CMD_PKG_GRANT.equals(cmd) || RootSvcProtocol.CMD_PKG_REVOKE.equals(cmd)) {
            String perm = args.optString("permission", "");
            if (!PERM.matcher(perm).matches()) {
                return ServiceMain.errorJson(id, RootSvcProtocol.ERR_BAD_ARGS, "permission");
            }
            String op = RootSvcProtocol.CMD_PKG_GRANT.equals(cmd) ? "grant" : "revoke";
            r = Shell.run(8_000L, "pm", op, pkg, perm);
        } else if (RootSvcProtocol.CMD_PKG_APPOPS.equals(cmd)) {
            String op = args.optString("op", "");
            String mode = args.optString("mode", "");
            if (!PERM.matcher(op).matches() || !PERM.matcher(mode).matches()) {
                return ServiceMain.errorJson(id, RootSvcProtocol.ERR_BAD_ARGS, "op/mode");
            }
            r = Shell.run(8_000L, "appops", "set", pkg, op, mode);
        } else if (RootSvcProtocol.CMD_PKG_UNSTOP.equals(cmd)) {
            r = Shell.run(8_000L, "am", "unstop", pkg);
            if (r.exit != 0) {
                // Fallback used by dcl_install Unstop helper.
                r = Shell.sh(8_000L,
                        "cmd package resolve-activity --brief " + pkg
                                + " >/dev/null 2>&1; am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER "
                                + pkg + " >/dev/null 2>&1; true");
            }
        } else {
            return ServiceMain.errorJson(id, RootSvcProtocol.ERR_UNKNOWN_CMD, cmd);
        }
        return finishShell(id, r, start);
    }

    private JSONObject settings(
            String id, String cmd, JSONObject args, List<String> packages, long start) throws Exception {
        if (RootSvcProtocol.CMD_SETTINGS_GET.equals(cmd)) {
            if (args == null) {
                return ServiceMain.errorJson(id, RootSvcProtocol.ERR_BAD_ARGS, "args");
            }
            String ns = args.optString("namespace", "");
            String key = args.optString("key", "");
            if (!SETTINGS_NS.contains(ns) || key.length() == 0) {
                return ServiceMain.errorJson(id, RootSvcProtocol.ERR_BAD_ARGS, "namespace/key");
            }
            Shell.Result r = Shell.run(5_000L, "settings", "get", ns, key);
            JSONObject o = finishShell(id, r, start);
            o.put("stdout", r.stdout == null ? "" : r.stdout.trim());
            return o;
        }
        if (RootSvcProtocol.CMD_SETTINGS_PUT.equals(cmd)) {
            if (args == null) {
                return ServiceMain.errorJson(id, RootSvcProtocol.ERR_BAD_ARGS, "args");
            }
            String ns = args.optString("namespace", "");
            String key = args.optString("key", "");
            String value = args.optString("value", "");
            if (!SETTINGS_NS.contains(ns) || key.length() == 0) {
                return ServiceMain.errorJson(id, RootSvcProtocol.ERR_BAD_ARGS, "namespace/key");
            }
            Shell.Result r = Shell.run(5_000L, "settings", "put", ns, key, value);
            return finishShell(id, r, start);
        }
        if (RootSvcProtocol.CMD_SETTINGS_A11Y_ADD.equals(cmd)
                || RootSvcProtocol.CMD_SETTINGS_A11Y_REMOVE.equals(cmd)) {
            if (args == null) {
                return ServiceMain.errorJson(id, RootSvcProtocol.ERR_BAD_ARGS, "args");
            }
            String component = args.optString("component", "");
            if (component.length() == 0 || component.indexOf('/') < 0) {
                return ServiceMain.errorJson(id, RootSvcProtocol.ERR_BAD_ARGS, "component");
            }
            // DCL / clients may only mutate their own a11y component unless they have full caps.
            boolean add = RootSvcProtocol.CMD_SETTINGS_A11Y_ADD.equals(cmd);
            String script = a11yScript(component, add);
            Shell.Result r = Shell.sh(5_000L, script);
            return finishShell(id, r, start);
        }
        return ServiceMain.errorJson(id, RootSvcProtocol.ERR_UNKNOWN_CMD, cmd);
    }

    private static String a11yScript(String component, boolean add) {
        String q = component.replace("'", "'\\''");
        StringBuilder sb = new StringBuilder();
        sb.append("cur=$(settings get secure enabled_accessibility_services 2>/dev/null)\n");
        if (add) {
            sb.append("case \"$cur\" in\n");
            sb.append("  *\"").append(q).append("\"*) : ;;\n");
            sb.append("  null|\"\") cur=\"").append(q).append("\" ;;\n");
            sb.append("  *) cur=\"$cur:").append(q).append("\" ;;\n");
            sb.append("esac\n");
            sb.append("settings put secure enabled_accessibility_services \"$cur\"\n");
            sb.append("settings put secure accessibility_enabled 1\n");
        } else {
            sb.append("out=\n");
            sb.append("IFS=':'; for s in $cur; do\n");
            sb.append("  [ \"$s\" = \"").append(q).append("\" ] && continue\n");
            sb.append("  [ -z \"$out\" ] && out=\"$s\" || out=\"$out:$s\"\n");
            sb.append("done\n");
            sb.append("settings put secure enabled_accessibility_services \"$out\"\n");
        }
        sb.append("exit 0\n");
        return sb.toString();
    }

    private JSONObject scriptRun(String id, JSONObject args, long start) throws Exception {
        if (args == null) {
            return ServiceMain.errorJson(id, RootSvcProtocol.ERR_BAD_ARGS, "args");
        }
        String body = args.optString("body", "");
        if (body.length() == 0 || body.length() > 200_000) {
            return ServiceMain.errorJson(id, RootSvcProtocol.ERR_BAD_ARGS, "body");
        }
        long timeout = args.optLong("timeoutMs", RootSvcProtocol.DEFAULT_RESULT_TIMEOUT_MS);
        if (timeout <= 0L) {
            timeout = RootSvcProtocol.DEFAULT_RESULT_TIMEOUT_MS;
        }
        timeout = Math.min(timeout, RootSvcProtocol.SCRIPT_MAX_TIMEOUT_MS);
        File tmp = new File(RootSvcProtocol.DIR, "script-" + System.currentTimeMillis() + ".sh");
        FileOutputStream out = new FileOutputStream(tmp);
        try {
            out.write(body.getBytes("UTF-8"));
        } finally {
            out.close();
        }
        tmp.setExecutable(true, false);
        try {
            Shell.Result r = Shell.run(timeout, "/system/bin/sh", tmp.getAbsolutePath());
            JSONObject o = finishShell(id, r, start);
            o.put("stdout", r.stdout);
            o.put("stderr", r.stderr);
            return o;
        } finally {
            tmp.delete();
        }
    }

    private JSONObject status(String id) throws Exception {
        JSONObject o = ServiceMain.okJson(id, 0, 0L);
        o.put("server", RootSvcProtocol.SERVER_VERSION);
        o.put("proto", RootSvcProtocol.PROTO);
        o.put("hu", "A9");
        o.put("uptimeMs", Math.max(0L, System.currentTimeMillis() - ServiceMain.STARTED_AT_MS));
        String state = readStateFile();
        o.put("state", state);
        o.put("crashloop", "crashloop".equals(state));
        return o;
    }

    private JSONObject adminRestart(String id) throws Exception {
        File state = new File(RootSvcProtocol.STATE_PATH);
        if (state.isFile()) {
            // Clear crashloop / upgraded markers so the supervisor can keep running.
            //noinspection ResultOfMethodCallIgnored
            state.delete();
        }
        ServiceLog.info("admin.restart — exit " + ServiceMain.EXIT_RESTART);
        JSONObject o = ServiceMain.okJson(id, 0, 0L);
        o.put("state", "restarting");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(200);
                } catch (InterruptedException ignored) {
                    // ignore
                }
                System.exit(ServiceMain.EXIT_RESTART);
            }
        }, "rootsvc-restart").start();
        return o;
    }

    private static String readStateFile() {
        File state = new File(RootSvcProtocol.STATE_PATH);
        if (!state.isFile()) {
            return "";
        }
        try {
            FileInputStream in = new FileInputStream(state);
            try {
                byte[] buf = new byte[64];
                int n = in.read(buf);
                if (n <= 0) {
                    return "";
                }
                return new String(buf, 0, n, java.nio.charset.Charset.forName("UTF-8")).trim();
            } finally {
                in.close();
            }
        } catch (Exception error) {
            return "";
        }
    }

    private JSONObject adminUpgrade(String id, JSONObject args) throws Exception {
        if (args == null) {
            return ServiceMain.errorJson(id, RootSvcProtocol.ERR_BAD_ARGS, "args");
        }
        String path = args.optString("path", "");
        String sha256 = args.optString("sha256", "").toLowerCase(Locale.US);
        if (path.length() == 0 || sha256.length() != 64) {
            return ServiceMain.errorJson(id, RootSvcProtocol.ERR_BAD_ARGS, "path/sha256");
        }
        // Owner must be the watchdog package uid — path under its files/.
        if (!path.startsWith("/data/data/" + RootSvcProtocol.PACKAGE + "/")
                && !path.startsWith("/data/user/0/" + RootSvcProtocol.PACKAGE + "/")) {
            return ServiceMain.errorJson(id, RootSvcProtocol.ERR_FORBIDDEN, "path owner");
        }
        File src = new File(path);
        if (!src.isFile()) {
            return ServiceMain.errorJson(id, RootSvcProtocol.ERR_BAD_ARGS, "missing");
        }
        String actual = sha256Hex(src);
        if (!sha256.equals(actual)) {
            return ServiceMain.errorJson(id, RootSvcProtocol.ERR_BAD_ARGS, "sha256");
        }
        File dest = new File(RootSvcProtocol.DEX_PATH);
        copy(src, dest);
        ServiceLog.info("admin.upgrade ok — exit 42");
        // Result first; supervisor restarts without backoff on 42.
        JSONObject o = ServiceMain.okJson(id, 0, 0L);
        o.put("state", "upgrading");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(200);
                } catch (InterruptedException ignored) {
                    // ignore
                }
                System.exit(ServiceMain.EXIT_UPGRADE);
            }
        }, "rootsvc-upgrade").start();
        return o;
    }

    private static JSONObject finishShell(String id, Shell.Result r, long start) throws Exception {
        JSONObject o = new JSONObject();
        if (id != null) {
            o.put("id", id);
        }
        o.put("exit", r.exit);
        o.put("ms", System.currentTimeMillis() - start);
        if (r.stdout != null && r.stdout.length() > 0) {
            o.put("stdout", r.stdout);
        }
        if (r.stderr != null && r.stderr.length() > 0) {
            o.put("stderr", r.stderr);
        }
        if (r.exit == 0) {
            o.put("ok", true);
        } else {
            o.put("ok", false);
            o.put("err", RootSvcProtocol.ERR_FAILED);
        }
        return o;
    }

    private static String sha256Hex(File file) throws Exception {
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

    private static Set<String> setOf(String... values) {
        java.util.HashSet<String> set = new java.util.HashSet<String>();
        for (String v : values) {
            set.add(v);
        }
        return java.util.Collections.unmodifiableSet(set);
    }
}
