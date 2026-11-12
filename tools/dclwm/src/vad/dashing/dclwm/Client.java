package vad.dashing.dclwm;

import android.net.LocalSocket;
import android.net.LocalSocketAddress;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One command per connection. Copy this class into the launcher. */
public final class Client {
    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final int LINE_LIMIT = 400;

    public void ping() throws Exception {
        String response = command("PING");
        if (!"OK".equals(response.trim())) {
            throw new IllegalStateException(response.trim());
        }
    }

    public List<WindowTask> list() throws Exception {
        String response = command("LIST");
        String[] lines = response.split("\n");
        if (lines.length == 0 || !"OK".equals(lines[0].trim())) {
            throw new IllegalStateException(response.trim());
        }
        List<WindowTask> tasks = new ArrayList<WindowTask>();
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty() || "END".equals(line)) {
                continue;
            }
            if (line.startsWith("ERR")) {
                throw new IllegalStateException(line);
            }
            WindowTask task = parseTask(line);
            if (task != null) {
                tasks.add(task);
            }
        }
        return Collections.unmodifiableList(tasks);
    }

    public void resize(int taskId, int left, int top, int right, int bottom) throws Exception {
        ok(command("RESIZE " + taskId + " " + left + " " + top + " " + right + " " + bottom));
    }

    public void minimize(int taskId) throws Exception {
        ok(command("MINIMIZE " + taskId));
    }

    public void dismiss(int taskId) throws Exception {
        ok(command("DISMISS " + taskId));
    }

    public static void main(String[] args) throws Exception {
        String drop = System.getenv("DCLWM_UID");
        if (drop != null) {
            java.lang.reflect.Method setUid = android.os.Process.class.getDeclaredMethod("setUid", int.class);
            setUid.invoke(null, Integer.parseInt(drop));
        }
        if (args.length == 0) {
            System.out.println("PING | LIST | MINIMIZE <task> | DISMISS <task> | RESIZE <task> <l> <t> <r> <b>");
            return;
        }
        Client client = new Client();
        String op = args[0];
        if ("PING".equals(op)) {
            client.ping();
            System.out.println("OK");
        } else if ("LIST".equals(op)) {
            List<WindowTask> tasks = client.list();
            for (int i = 0; i < tasks.size(); i++) {
                System.out.println(tasks.get(i).format());
            }
        } else if ("MINIMIZE".equals(op) && args.length == 2) {
            client.minimize(Integer.parseInt(args[1]));
            System.out.println("OK");
        } else if ("DISMISS".equals(op) && args.length == 2) {
            client.dismiss(Integer.parseInt(args[1]));
            System.out.println("OK");
        } else if ("RESIZE".equals(op) && args.length == 6) {
            client.resize(
                    Integer.parseInt(args[1]),
                    Integer.parseInt(args[2]),
                    Integer.parseInt(args[3]),
                    Integer.parseInt(args[4]),
                    Integer.parseInt(args[5]));
            System.out.println("OK");
        } else {
            System.out.println("ERR bad_args");
            System.exit(2);
        }
    }

    private static void ok(String response) {
        if (!"OK".equals(response.trim())) {
            throw new IllegalStateException(response.trim());
        }
    }

    private static String command(String line) throws Exception {
        LocalSocket socket = new LocalSocket();
        try {
            socket.connect(new LocalSocketAddress(Protocol.SOCKET, LocalSocketAddress.Namespace.ABSTRACT));
            OutputStream out = socket.getOutputStream();
            out.write((line + "\n").getBytes(UTF8));
            out.flush();
            return readMessage(socket.getInputStream(), line.startsWith("LIST"));
        } finally {
            try {
                socket.close();
            } catch (Exception ignored) {
                // Closing a finished command is best-effort.
            }
        }
    }

    private static String readMessage(InputStream in, boolean list) throws Exception {
        String first = readLine(in);
        if (first == null) {
            throw new IllegalStateException("ERR closed");
        }
        if (first.startsWith("ERR") || !list) {
            return first + "\n";
        }
        StringBuilder all = new StringBuilder(first).append('\n');
        while (true) {
            String line = readLine(in);
            if (line == null) {
                return all.toString();
            }
            all.append(line).append('\n');
            if ("END".equals(line) || line.startsWith("ERR")) {
                return all.toString();
            }
        }
    }

    /** @return the line without newline, or null on EOF before any byte */
    private static String readLine(InputStream in) throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        while (buf.size() < LINE_LIMIT) {
            int b = in.read();
            if (b < 0) {
                if (buf.size() == 0) {
                    return null;
                }
                break;
            }
            if (b == '\n') {
                break;
            }
            if (b != '\r') {
                buf.write(b);
            }
        }
        return new String(buf.toByteArray(), UTF8);
    }

    static WindowTask parseTask(String line) {
        if (!line.startsWith("task ")) {
            return null;
        }
        int id = fieldInt(line, "id");
        int stack = fieldInt(line, "stack");
        String pkg = field(line, "pkg");
        String activity = field(line, "activity");
        String mode = field(line, "mode");
        String bounds = field(line, "bounds");
        int visible = fieldInt(line, "visible");
        if (pkg == null || activity == null || mode == null || bounds == null) {
            return null;
        }
        String[] box = bounds.split(",");
        if (box.length != 4) {
            return null;
        }
        return new WindowTask(
                id,
                stack,
                pkg,
                activity,
                mode,
                Integer.parseInt(box[0]),
                Integer.parseInt(box[1]),
                Integer.parseInt(box[2]),
                Integer.parseInt(box[3]),
                0,
                0,
                0,
                0,
                visible == 1);
    }

    private static String field(String line, String key) {
        String needle = key + "=";
        int start = line.indexOf(needle);
        if (start < 0) {
            return null;
        }
        start += needle.length();
        int end = line.indexOf(' ', start);
        if (end < 0) {
            end = line.length();
        }
        return line.substring(start, end);
    }

    private static int fieldInt(String line, String key) {
        String value = field(line, key);
        if (value == null) {
            return -1;
        }
        return Integer.parseInt(value);
    }
}
