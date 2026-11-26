package vad.dashing.rootsvc.daemon;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import vad.dashing.rootsvc.client.Allowlist;
import vad.dashing.rootsvc.client.RootSvcProtocol;
import vad.dashing.rootsvc.client.WmStackListParser;

/**
 * Window-stack commands absorbed from dclwm ({@code wm.list/resize/minimize/dismiss}).
 * Off-screen rect matches tools/dclwm Protocol.
 */
public final class WmCommands {
    private static final int OFF_LEFT = 2000;
    private static final int OFF_TOP = 2000;
    private static final int OFF_RIGHT = 2200;
    private static final int OFF_BOTTOM = 2200;

    private static final Pattern TASK = Pattern.compile(
            "taskId=(\\d+).*?stackId=(\\d+).*?topActivity=ComponentInfo\\{([^/]+)/([^}]+)\\}"
                    + ".*?mBounds=Rect\\((-?\\d+), (-?\\d+) - (-?\\d+), (-?\\d+)\\)"
                    + ".*?mWindowingMode=(\\w+)",
            Pattern.DOTALL);

    public JSONObject handle(String id, String cmd, JSONObject args) throws Exception {
        if (RootSvcProtocol.CMD_WM_LIST.equals(cmd)) {
            return list(id);
        }
        if (RootSvcProtocol.CMD_WM_RESIZE.equals(cmd)) {
            return resize(id, args);
        }
        if (RootSvcProtocol.CMD_WM_MINIMIZE.equals(cmd)) {
            return minimize(id, args);
        }
        if (RootSvcProtocol.CMD_WM_DISMISS.equals(cmd)) {
            return dismiss(id, args);
        }
        return ServiceMain.errorJson(id, RootSvcProtocol.ERR_UNKNOWN_CMD, cmd);
    }

    private JSONObject list(String id) throws Exception {
        List<Task> tasks = read();
        JSONObject o = ServiceMain.okJson(id, 0, 0L);
        JSONArray arr = new JSONArray();
        for (int i = 0; i < tasks.size(); i++) {
            Task t = tasks.get(i);
            JSONObject row = new JSONObject();
            row.put("id", t.id);
            row.put("stack", t.stackId);
            row.put("pkg", t.pkg);
            row.put("activity", t.activity);
            row.put("mode", t.mode);
            row.put("left", t.left);
            row.put("top", t.top);
            row.put("right", t.right);
            row.put("bottom", t.bottom);
            row.put("visible", t.visible);
            arr.put(row);
        }
        o.put("tasks", arr);
        return o;
    }

    private JSONObject resize(String id, JSONObject args) throws Exception {
        if (args == null) {
            return ServiceMain.errorJson(id, RootSvcProtocol.ERR_BAD_ARGS, "args");
        }
        int taskId = args.optInt("taskId", -1);
        int left = args.optInt("left", Integer.MIN_VALUE);
        int top = args.optInt("top", Integer.MIN_VALUE);
        int right = args.optInt("right", Integer.MIN_VALUE);
        int bottom = args.optInt("bottom", Integer.MIN_VALUE);
        if (taskId < 0 || left >= right || top >= bottom
                || outOfRange(left) || outOfRange(top) || outOfRange(right) || outOfRange(bottom)) {
            return ServiceMain.errorJson(id, RootSvcProtocol.ERR_BAD_ARGS, "bounds");
        }
        Task task = find(read(), taskId);
        if (task == null) {
            return ServiceMain.errorJson(id, RootSvcProtocol.ERR_FAILED, "not_found");
        }
        if (Allowlist.PKG_DCL.equals(task.pkg)) {
            return ServiceMain.errorJson(id, RootSvcProtocol.ERR_FORBIDDEN, "launcher");
        }
        Shell.run(5_000L, "am", "stack", "resize",
                Integer.toString(task.stackId),
                Integer.toString(left), Integer.toString(top),
                Integer.toString(right), Integer.toString(bottom));
        Shell.run(5_000L, "am", "task", "resize",
                Integer.toString(taskId),
                Integer.toString(left), Integer.toString(top),
                Integer.toString(right), Integer.toString(bottom));
        return ServiceMain.okJson(id, 0, 0L);
    }

    private JSONObject minimize(String id, JSONObject args) throws Exception {
        if (args == null) {
            return ServiceMain.errorJson(id, RootSvcProtocol.ERR_BAD_ARGS, "args");
        }
        int taskId = args.optInt("taskId", -1);
        Task task = find(read(), taskId);
        if (task == null) {
            return ServiceMain.errorJson(id, RootSvcProtocol.ERR_FAILED, "not_found");
        }
        if (Allowlist.PKG_DCL.equals(task.pkg)) {
            return ServiceMain.errorJson(id, RootSvcProtocol.ERR_FORBIDDEN, "launcher");
        }
        Shell.run(5_000L, "am", "stack", "resize",
                Integer.toString(task.stackId),
                Integer.toString(OFF_LEFT), Integer.toString(OFF_TOP),
                Integer.toString(OFF_RIGHT), Integer.toString(OFF_BOTTOM));
        return ServiceMain.okJson(id, 0, 0L);
    }

    private JSONObject dismiss(String id, JSONObject args) throws Exception {
        if (args == null) {
            return ServiceMain.errorJson(id, RootSvcProtocol.ERR_BAD_ARGS, "args");
        }
        int taskId = args.optInt("taskId", -1);
        Task task = find(read(), taskId);
        if (task == null) {
            return ServiceMain.errorJson(id, RootSvcProtocol.ERR_FAILED, "not_found");
        }
        if (Allowlist.PKG_DCL.equals(task.pkg)) {
            return ServiceMain.errorJson(id, RootSvcProtocol.ERR_FORBIDDEN, "launcher");
        }
        Shell.run(5_000L, "am", "stack", "remove", Integer.toString(task.stackId));
        return ServiceMain.okJson(id, 0, 0L);
    }

    private List<Task> read() throws Exception {
        // Prefer the dclwm-style formatted dump via am stack list.
        Shell.Result r = Shell.run(5_000L, "am", "stack", "list");
        List<WmStackListParser.Task> parsed = WmStackListParser.parse(r.stdout);
        if (!parsed.isEmpty()) {
            List<Task> out = new ArrayList<Task>();
            for (int i = 0; i < parsed.size(); i++) {
                out.add(fromParsed(parsed.get(i)));
            }
            return out;
        }
        return parseDumpsys(r.stdout);
    }

    private static Task fromParsed(WmStackListParser.Task p) {
        Task t = new Task();
        t.id = p.id;
        t.stackId = p.stackId;
        t.pkg = p.pkg;
        t.activity = p.activity;
        t.mode = p.mode;
        t.left = p.left;
        t.top = p.top;
        t.right = p.right;
        t.bottom = p.bottom;
        t.visible = p.visible;
        return t;
    }

    static List<Task> parseDumpsys(String text) {
        List<Task> out = new ArrayList<Task>();
        if (text == null) {
            return out;
        }
        Matcher m = TASK.matcher(text);
        while (m.find()) {
            Task t = new Task();
            t.id = Integer.parseInt(m.group(1));
            t.stackId = Integer.parseInt(m.group(2));
            t.pkg = m.group(3);
            t.activity = m.group(4);
            t.left = Integer.parseInt(m.group(5));
            t.top = Integer.parseInt(m.group(6));
            t.right = Integer.parseInt(m.group(7));
            t.bottom = Integer.parseInt(m.group(8));
            t.mode = m.group(9);
            t.visible = true;
            out.add(t);
        }
        return out;
    }

    private static Task find(List<Task> tasks, int id) {
        for (int i = 0; i < tasks.size(); i++) {
            if (tasks.get(i).id == id) {
                return tasks.get(i);
            }
        }
        return null;
    }

    private static boolean outOfRange(int v) {
        return v < -100 || v > 4000;
    }

    static final class Task {
        int id;
        int stackId;
        String pkg;
        String activity;
        String mode;
        int left;
        int top;
        int right;
        int bottom;
        boolean visible;
    }
}
