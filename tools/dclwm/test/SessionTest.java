package vad.dashing.dclwm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Desktop checks for the stack-list parser and the command session. */
public final class SessionTest {
    public static void main(String[] args) {
        int failed = 0;
        failed += check("parser", parser());
        failed += check("uid", uid());
        failed += check("forbidden", forbidden());
        failed += check("list", list());
        failed += check("minimize", minimize());
        failed += check("resize", resize());
        failed += check("dismiss", dismiss());
        failed += check("launcher", launcherProtected());
        failed += check("arm", arm());
        if (failed != 0) {
            System.exit(1);
        }
        System.out.println("OK");
    }

    private static int check(String name, boolean ok) {
        if (!ok) {
            System.out.println("FAIL " + name);
            return 1;
        }
        return 0;
    }

    private static boolean parser() {
        List<WindowTask> tasks = StackListParser.parse(sample(344, 37, 1920, 1000, 0, 0, 1920, 1080));
        if (tasks.size() != 2) {
            return false;
        }
        WindowTask app = tasks.get(0);
        WindowTask home = tasks.get(1);
        return app.id == 406
                && app.stackId == 4
                && "vad.dashing.tbox".equals(app.packageName)
                && "freeform".equals(app.mode)
                && app.left == 344 && app.bottom == 1000
                && app.visible
                && home.id == 402
                && "fullscreen".equals(home.mode)
                && Protocol.LAUNCHER_PACKAGE.equals(home.packageName);
    }

    private static boolean uid() {
        String text = "package:com.example uid:10\npackage:" + Protocol.LAUNCHER_PACKAGE + " uid:10098\n";
        return LauncherUid.parsePmOutput(text, Protocol.LAUNCHER_PACKAGE) == 10098
                && LauncherUid.parsePmOutput("package:other uid:1\n", Protocol.LAUNCHER_PACKAGE) == -1;
    }

    private static boolean forbidden() {
        Fake am = new Fake();
        String response = new Session(am).handle("LIST", 2000, 10098);
        return "ERR forbidden\n".equals(response) && am.calls.isEmpty();
    }

    private static boolean list() {
        Fake am = new Fake();
        am.outputs.add(sample(344, 37, 1920, 1000, 0, 0, 1920, 1080));
        String response = new Session(am).handle("LIST", 10098, 10098);
        return response.startsWith("OK\n")
                && response.contains("task id=406 stack=4 pkg=vad.dashing.tbox")
                && response.endsWith("END\n")
                && am.calls.size() == 1;
    }

    private static boolean minimize() {
        Fake am = new Fake();
        am.outputs.add(sample(344, 37, 1920, 1000, 0, 0, 1920, 1080));
        am.outputs.add("");
        am.outputs.add(sample(2000, 2000, 2088, 2088, 2000, 2000, 2200, 2200));
        String response = new Session(am).handle("MINIMIZE 406", 0, 10098);
        return "OK\n".equals(response)
                && am.calls.get(1).equals(Arrays.asList(
                        "am", "stack", "resize", "4", "2000", "2000", "2200", "2200"));
    }

    private static boolean resize() {
        Fake am = new Fake();
        am.outputs.add(sample(344, 37, 1920, 1000, 0, 0, 1920, 1080));
        am.outputs.add("");
        am.outputs.add("");
        am.outputs.add(sample(80, 120, 700, 700, 80, 120, 700, 700));
        String response = new Session(am).handle("RESIZE 406 80 120 700 700", 10098, 10098);
        return "OK\n".equals(response)
                && am.calls.get(1).equals(Arrays.asList("am", "stack", "resize", "4", "80", "120", "700", "700"))
                && am.calls.get(2).equals(Arrays.asList("am", "task", "resize", "406", "80", "120", "700", "700"));
    }

    private static boolean dismiss() {
        Fake am = new Fake();
        am.outputs.add(sample(344, 37, 1920, 1000, 0, 0, 1920, 1080));
        am.outputs.add("");
        am.outputs.add(homeOnly());
        String response = new Session(am).handle("DISMISS 406", 0, 10098);
        return "OK\n".equals(response)
                && am.calls.get(1).equals(Arrays.asList("am", "stack", "remove", "4"));
    }

    private static boolean launcherProtected() {
        Fake am = new Fake();
        am.outputs.add(sample(344, 37, 1920, 1000, 0, 0, 1920, 1080));
        String response = new Session(am).handle("MINIMIZE 402", 0, 10098);
        return "ERR launcher\n".equals(response) && am.calls.size() == 1;
    }

    private static boolean missing() {
        Fake am = new Fake();
        am.outputs.add(homeOnly());
        String response = new Session(am).handle("DISMISS 406", 0, 10098);
        return "ERR not_found\n".equals(response) && am.calls.size() == 1;
    }

    private static boolean arm() {
        String script = ArmScript.text("/data/local/tmp/dclwm.dex");
        return script.indexOf('\r') < 0
                && script.startsWith("#!/system/bin/sh\n")
                && script.contains("DEX='/data/local/tmp/dclwm.dex'\n")
                && script.contains("setsid /system/bin/app_process /system/bin vad.dashing.dclwm.Helper");
    }

    private static String sample(
            int left, int top, int right, int bottom,
            int stackLeft, int stackTop, int stackRight, int stackBottom) {
        return "Stack id=4 bounds=[" + stackLeft + "," + stackTop + "][" + stackRight + "," + stackBottom + "] displayId=0 userId=0\n"
                + " configuration={mWindowingMode=freeform mActivityType=standard}\n"
                + "  taskId=406: vad.dashing.tbox/vad.dashing.tbox.MainActivity bounds=["
                + left + "," + top + "][" + right + "," + bottom + "] userId=0 visible=true "
                + "topActivity=ComponentInfo{vad.dashing.tbox/vad.dashing.tbox.MainActivity}\n"
                + homeOnly();
    }

    private static String homeOnly() {
        return "Stack id=0 bounds=[0,0][1920,1080] displayId=0 userId=0\n"
                + " configuration={mWindowingMode=fullscreen mActivityType=home}\n"
                + "  taskId=402: " + Protocol.LAUNCHER_PACKAGE + "/vad.dashing.tbox.LauncherHomeActivity "
                + "bounds=[0,0][1920,1080] userId=0 visible=true "
                + "topActivity=ComponentInfo{" + Protocol.LAUNCHER_PACKAGE + "/vad.dashing.tbox.LauncherHomeActivity}\n";
    }

    private static final class Fake implements Am {
        final List<List<String>> calls = new ArrayList<List<String>>();
        final List<String> outputs = new ArrayList<String>();

        @Override
        public String run(String... args) {
            calls.add(Arrays.asList(args));
            return outputs.get(calls.size() - 1);
        }
    }
}
