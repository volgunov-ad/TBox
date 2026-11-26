package vad.dashing.rootsvc.client;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses the formatted {@code am stack list} lines used by rootsvc {@code wm.list}. */
public final class WmStackListParser {
    private static final Pattern LINE = Pattern.compile(
            "^task id=(\\d+) stack=(\\d+) pkg=(\\S+) activity=(\\S+) mode=(\\S+) "
                    + "bounds=(-?\\d+),(-?\\d+),(-?\\d+),(-?\\d+) visible=(\\d+)");

    private WmStackListParser() {
    }

    public static List<Task> parse(String text) {
        List<Task> out = new ArrayList<Task>();
        if (text == null) {
            return out;
        }
        String[] lines = text.split("\n");
        for (int i = 0; i < lines.length; i++) {
            Matcher m = LINE.matcher(lines[i].trim());
            if (!m.find()) {
                continue;
            }
            Task t = new Task();
            t.id = Integer.parseInt(m.group(1));
            t.stackId = Integer.parseInt(m.group(2));
            t.pkg = m.group(3);
            t.activity = m.group(4);
            t.mode = m.group(5);
            t.left = Integer.parseInt(m.group(6));
            t.top = Integer.parseInt(m.group(7));
            t.right = Integer.parseInt(m.group(8));
            t.bottom = Integer.parseInt(m.group(9));
            t.visible = "1".equals(m.group(10));
            out.add(t);
        }
        return out;
    }

    public static final class Task {
        public int id;
        public int stackId;
        public String pkg;
        public String activity;
        public String mode;
        public int left;
        public int top;
        public int right;
        public int bottom;
        public boolean visible;
    }
}
