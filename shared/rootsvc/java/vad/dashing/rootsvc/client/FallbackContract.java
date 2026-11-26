package vad.dashing.rootsvc.client;

/**
 * Pure helpers for the client fallback table (plan §6). Unit-tested without Android.
 */
public final class FallbackContract {
    private FallbackContract() {
    }

    /** Commands safe to retry via the legacy mb_daemon path after UNKNOWN_OUTCOME. */
    public static boolean isIdempotent(String cmd) {
        if (cmd == null) {
            return false;
        }
        return RootSvcProtocol.CMD_SVC_CTL.equals(cmd)
                || RootSvcProtocol.CMD_USB_UNLOCK.equals(cmd)
                || RootSvcProtocol.CMD_PKG_APPOPS.equals(cmd)
                || RootSvcProtocol.CMD_HELPER_ENSURE.equals(cmd)
                || RootSvcProtocol.CMD_SETTINGS_A11Y_ADD.equals(cmd)
                || RootSvcProtocol.CMD_SETTINGS_A11Y_REMOVE.equals(cmd);
    }

    /**
     * Whether the client should fall back to the legacy arm path.
     * {@code true} = go legacy; {@code false} = surface the error.
     */
    public static boolean shouldFallback(RootSvcResult result, String cmd) {
        if (result == null) {
            return true;
        }
        switch (result.kind) {
            case UNAVAILABLE:
                return true;
            case REJECTED:
                return result.isErr(RootSvcProtocol.ERR_UNSUPPORTED)
                        || result.isErr(RootSvcProtocol.ERR_PROTO);
            case UNKNOWN_OUTCOME:
                return isIdempotent(cmd);
            case FAILED:
            case OK:
            default:
                return false;
        }
    }

    public static long resultTimeoutFor(String cmd, long requestedMs) {
        if (RootSvcProtocol.CMD_SCRIPT_RUN.equals(cmd)) {
            if (requestedMs <= 0L) {
                return RootSvcProtocol.DEFAULT_RESULT_TIMEOUT_MS;
            }
            return Math.min(requestedMs, RootSvcProtocol.SCRIPT_MAX_TIMEOUT_MS);
        }
        if (RootSvcProtocol.CMD_HELPER_ENSURE.equals(cmd)
                || RootSvcProtocol.CMD_WALLPAPER_SET.equals(cmd)
                || RootSvcProtocol.CMD_ADMIN_UPGRADE.equals(cmd)) {
            return Math.max(requestedMs, RootSvcProtocol.HELPER_TIMEOUT_MS);
        }
        if (requestedMs <= 0L) {
            return RootSvcProtocol.DEFAULT_RESULT_TIMEOUT_MS;
        }
        return requestedMs;
    }
}
