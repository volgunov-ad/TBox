package vad.dashing.dclwm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads the `am stack list` text this head unit prints on Android 9. */
public final class StackListParser {
    private static final Pattern STACK = Pattern.compile(
            "Stack id=(\\d+) bounds=\\[(-?\\d+),(-?\\d+)\\]\\[(-?\\d+),(-?\\d+)\\]");
    private static final Pattern MODE = Pattern.compile("mWindowingMode=([A-Za-z0-9_]+)");
    private static final Pattern TASK = Pattern.compile(
            "taskId=(\\d+): ([^/\\s]+)/(\\S+) bounds=\\[(-?\\d+),(-?\\d+)\\]\\[(-?\\d+),(-?\\d+)\\].*visible=(true|false)");

    private StackListParser() {
    }

    public static List<WindowTask> parse(String text) {
        if (text == null || text.isEmpty()) {
            return Collections.emptyList();
        }
        List<WindowTask> tasks = new ArrayList<WindowTask>();
        int stackId = -1;
        int stackLeft = 0;
        int stackTop = 0;
        int stackRight = 0;
        int stackBottom = 0;
        String mode = "unknown";
        String[] lines = text.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            Matcher stack = STACK.matcher(line);
            if (stack.find()) {
                stackId = Integer.parseInt(stack.group(1));
                stackLeft = Integer.parseInt(stack.group(2));
                stackTop = Integer.parseInt(stack.group(3));
                stackRight = Integer.parseInt(stack.group(4));
                stackBottom = Integer.parseInt(stack.group(5));
                mode = "unknown";
                continue;
            }
            if (stackId < 0) {
                continue;
            }
            Matcher modeMatch = MODE.matcher(line);
            if (modeMatch.find()) {
                mode = modeMatch.group(1);
            }
            Matcher task = TASK.matcher(line);
            if (!task.find()) {
                continue;
            }
            tasks.add(new WindowTask(
                    Integer.parseInt(task.group(1)),
                    stackId,
                    task.group(2),
                    task.group(3),
                    mode,
                    Integer.parseInt(task.group(4)),
                    Integer.parseInt(task.group(5)),
                    Integer.parseInt(task.group(6)),
                    Integer.parseInt(task.group(7)),
                    stackLeft,
                    stackTop,
                    stackRight,
                    stackBottom,
                    "true".equals(task.group(8))));
        }
        return tasks;
    }

    public static WindowTask find(List<WindowTask> tasks, int taskId) {
        for (int i = 0; i < tasks.size(); i++) {
            WindowTask task = tasks.get(i);
            if (task.id == taskId) {
                return task;
            }
        }
        return null;
    }
}
