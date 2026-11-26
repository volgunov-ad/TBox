package vad.dashing.rootsvc.client;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Package-name allowlist (locked decision: no signature check). Pure Java for unit tests.
 * uid 0 is always allowed outside this table.
 */
public final class Allowlist {
    public static final String PKG_TBOX = "vad.dashing.tbox";
    public static final String PKG_CAMD = "vad.dashing.camd";
    public static final String PKG_DCLINSTALL = "vad.dashing.dclinstall";
    public static final String PKG_CAMDBENCH = "vad.dashing.camdbench";
    public static final String PKG_ROOTSVC = "vad.dashing.rootsvc";
    public static final String PKG_DCL = "ras.dashing.tbox.launcher";

    private static final Set<String> ALL_CAPS = unmodifiable(
            RootSvcProtocol.CMD_PING,
            RootSvcProtocol.CMD_HELLO,
            RootSvcProtocol.CMD_ADMIN_UPGRADE,
            RootSvcProtocol.CMD_PKG_HIDE,
            RootSvcProtocol.CMD_PKG_UNHIDE,
            RootSvcProtocol.CMD_PKG_ENABLE,
            RootSvcProtocol.CMD_PKG_DISABLE,
            RootSvcProtocol.CMD_PKG_GRANT,
            RootSvcProtocol.CMD_PKG_REVOKE,
            RootSvcProtocol.CMD_PKG_APPOPS,
            RootSvcProtocol.CMD_PKG_UNSTOP,
            RootSvcProtocol.CMD_SETTINGS_GET,
            RootSvcProtocol.CMD_SETTINGS_PUT,
            RootSvcProtocol.CMD_SETTINGS_A11Y_ADD,
            RootSvcProtocol.CMD_SETTINGS_A11Y_REMOVE,
            RootSvcProtocol.CMD_SVC_CTL,
            RootSvcProtocol.CMD_USB_UNLOCK,
            RootSvcProtocol.CMD_WALLPAPER_SET,
            RootSvcProtocol.CMD_HELPER_ENSURE,
            RootSvcProtocol.CMD_WM_LIST,
            RootSvcProtocol.CMD_WM_RESIZE,
            RootSvcProtocol.CMD_WM_MINIMIZE,
            RootSvcProtocol.CMD_WM_DISMISS,
            RootSvcProtocol.CMD_POWER_REBOOT,
            RootSvcProtocol.CMD_SCRIPT_RUN);

    /** DCL: window/USB/helper caps plus script.run (locked: script.run for all allowlisted). */
    private static final Set<String> DCL_CAPS = unmodifiable(
            RootSvcProtocol.CMD_PING,
            RootSvcProtocol.CMD_HELLO,
            RootSvcProtocol.CMD_USB_UNLOCK,
            RootSvcProtocol.CMD_HELPER_ENSURE,
            RootSvcProtocol.CMD_SETTINGS_A11Y_ADD,
            RootSvcProtocol.CMD_SETTINGS_A11Y_REMOVE,
            RootSvcProtocol.CMD_PKG_APPOPS,
            RootSvcProtocol.CMD_WM_LIST,
            RootSvcProtocol.CMD_WM_RESIZE,
            RootSvcProtocol.CMD_WM_MINIMIZE,
            RootSvcProtocol.CMD_WM_DISMISS,
            RootSvcProtocol.CMD_SCRIPT_RUN);

    private static final Set<String> PING_ONLY = unmodifiable(RootSvcProtocol.CMD_PING);

    private static final Map<String, Set<String>> BY_PACKAGE;

    static {
        Map<String, Set<String>> map = new HashMap<String, Set<String>>();
        map.put(PKG_TBOX, ALL_CAPS);
        map.put(PKG_CAMD, ALL_CAPS);
        map.put(PKG_DCLINSTALL, ALL_CAPS);
        map.put(PKG_CAMDBENCH, ALL_CAPS);
        map.put(PKG_ROOTSVC, ALL_CAPS);
        map.put(PKG_DCL, DCL_CAPS);
        BY_PACKAGE = Collections.unmodifiableMap(map);
    }

    private Allowlist() {
    }

    public static boolean isKnownPackage(String packageName) {
        return packageName != null && BY_PACKAGE.containsKey(packageName);
    }

    /**
     * Intersection of caps for every package sharing the uid. Empty packages → deny
     * (except the caller should short-circuit uid 0).
     */
    public static Set<String> capsForPackages(Iterable<String> packages) {
        Set<String> result = null;
        boolean any = false;
        for (String pkg : packages) {
            any = true;
            Set<String> caps = BY_PACKAGE.get(pkg);
            if (caps == null) {
                return Collections.emptySet();
            }
            if (result == null) {
                result = new HashSet<String>(caps);
            } else {
                result.retainAll(caps);
            }
        }
        if (!any || result == null) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(result);
    }

    public static boolean allows(Iterable<String> packages, String cmd) {
        if (RootSvcProtocol.CMD_PING.equals(cmd)) {
            return true;
        }
        return capsForPackages(packages).contains(cmd);
    }

    public static Set<String> pingOnly() {
        return PING_ONLY;
    }

    public static Set<String> allKnownCommands() {
        return ALL_CAPS;
    }

    private static Set<String> unmodifiable(String... values) {
        return Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(values)));
    }
}
