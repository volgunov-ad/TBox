package vad.dashing.rootsvc.client;

import java.util.ArrayList;
import java.util.List;

/** Pure parser for {@code pm list packages -U} (unit-tested without a device). */
public final class PmListParser {
    private PmListParser() {
    }

    public static List<String> forUid(String text, int uid) {
        List<String> out = new ArrayList<String>();
        if (text == null || text.length() == 0 || uid < 0) {
            return out;
        }
        String[] lines = text.split("\n");
        String needle = "uid:" + uid;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            // package:name uid:10123   or package:name uid:10123 user:0
            if (!line.startsWith("package:")) {
                continue;
            }
            if (!line.contains(needle)) {
                continue;
            }
            // Avoid matching uid:1012 when looking for uid:101.
            int idx = line.indexOf(needle);
            int after = idx + needle.length();
            if (after < line.length()) {
                char c = line.charAt(after);
                if (c >= '0' && c <= '9') {
                    continue;
                }
            }
            String name = line.substring("package:".length()).trim();
            int space = name.indexOf(' ');
            if (space > 0) {
                name = name.substring(0, space);
            }
            int tab = name.indexOf('\t');
            if (tab > 0) {
                name = name.substring(0, tab);
            }
            if (name.length() > 0) {
                out.add(name);
            }
        }
        return out;
    }
}
