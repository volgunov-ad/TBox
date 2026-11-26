package vad.dashing.rootsvc.client;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * JSON line codec for rootsvc v1. Pure org.json — available in app_process and unit tests.
 */
public final class WireCodec {
    private WireCodec() {
    }

    public static JSONObject request(String id, String cmd, JSONObject args, String client, String clientVersion)
            throws JSONException {
        JSONObject o = new JSONObject();
        o.put("v", RootSvcProtocol.PROTO);
        o.put("id", id);
        o.put("cmd", cmd);
        if (client != null) {
            o.put("client", client);
        }
        if (clientVersion != null) {
            o.put("clientVersion", clientVersion);
        }
        if (args != null) {
            o.put("args", args);
        }
        return o;
    }

    public static String requestLine(String id, String cmd, JSONObject args, String client, String clientVersion)
            throws JSONException {
        return request(id, cmd, args, client, clientVersion).toString() + "\n";
    }

    public static boolean isAck(JSONObject o) {
        return o != null && o.optBoolean("ack", false);
    }

    public static RootSvcResult parseResult(JSONObject o) {
        if (o == null) {
            return RootSvcResult.unavailable("null_result");
        }
        String id = o.optString("id", null);
        if (o.has("err") && !o.isNull("err")) {
            String err = o.optString("err", RootSvcProtocol.ERR_FAILED);
            String msg = o.optString("msg", null);
            if (RootSvcProtocol.ERR_FAILED.equals(err)) {
                Integer exit = o.has("exit") ? Integer.valueOf(o.optInt("exit")) : null;
                return RootSvcResult.failed(
                        id,
                        exit,
                        optNullable(o, "stdout"),
                        optNullable(o, "stderr"),
                        o.has("ms") ? Long.valueOf(o.optLong("ms")) : null,
                        msg,
                        o.toString());
            }
            return RootSvcResult.rejected(id, err, msg, o.toString());
        }
        if (o.optBoolean("ok", false)) {
            Integer exit = o.has("exit") ? Integer.valueOf(o.optInt("exit")) : Integer.valueOf(0);
            return RootSvcResult.ok(
                    id,
                    exit,
                    optNullable(o, "stdout"),
                    optNullable(o, "stderr"),
                    o.has("ms") ? Long.valueOf(o.optLong("ms")) : null,
                    o.toString());
        }
        return RootSvcResult.rejected(id, RootSvcProtocol.ERR_FAILED, "missing ok/err", o.toString());
    }

    public static JSONObject parseLine(String line) throws JSONException {
        if (line == null) {
            throw new JSONException("null");
        }
        String trimmed = line.trim();
        if (trimmed.isEmpty()) {
            throw new JSONException("empty");
        }
        if (trimmed.length() > RootSvcProtocol.LINE_LIMIT) {
            throw new JSONException("too_long");
        }
        return new JSONObject(trimmed);
    }

    private static String optNullable(JSONObject o, String key) {
        if (!o.has(key) || o.isNull(key)) {
            return null;
        }
        return o.optString(key, null);
    }
}
