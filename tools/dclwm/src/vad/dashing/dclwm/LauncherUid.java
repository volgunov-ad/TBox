package vad.dashing.dclwm;

/** `pm list packages -U <package>` → uid, or -1 when the line is absent. */
public final class LauncherUid {
    private LauncherUid() {
    }

    public static int parsePmOutput(String text, String packageName) {
        if (text == null || packageName == null) {
            return -1;
        }
        String[] lines = text.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (!line.contains(packageName)) {
                continue;
            }
            int marker = line.indexOf("uid:");
            if (marker < 0) {
                continue;
            }
            int start = marker + 4;
            int end = start;
            while (end < line.length() && Character.isDigit(line.charAt(end))) {
                end++;
            }
            if (end == start) {
                continue;
            }
            try {
                return Integer.parseInt(line.substring(start, end));
            } catch (NumberFormatException ignored) {
                return -1;
            }
        }
        return -1;
    }
}
