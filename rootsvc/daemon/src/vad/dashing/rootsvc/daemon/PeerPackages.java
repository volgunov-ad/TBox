package vad.dashing.rootsvc.daemon;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/** Resolves package names for a uid via {@code pm list packages -U}. */
public final class PeerPackages {
    private PeerPackages() {
    }

    public static List<String> forUid(int uid) {
        List<String> out = new ArrayList<String>();
        if (uid < 0) {
            return out;
        }
        try {
            Process process = new ProcessBuilder("pm", "list", "packages", "-U")
                    .redirectErrorStream(true)
                    .start();
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            InputStream in = process.getInputStream();
            byte[] tmp = new byte[4096];
            int n;
            while ((n = in.read(tmp)) > 0) {
                buf.write(tmp, 0, n);
            }
            process.waitFor();
            String text = buf.toString("UTF-8");
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
        } catch (Exception error) {
            ServiceLog.warn("pm list " + error.getMessage());
        }
        return out;
    }
}
