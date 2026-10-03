package vad.dashing.tbox.hotspot;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

/**
 * Shell entry (uid 2000) for the head-unit SoftAP card.
 *
 * <pre>
 * CLASSPATH=&lt;app.apk&gt; app_process /system/bin vad.dashing.tbox.hotspot.HuSoftApMain get|up|down
 * </pre>
 *
 * {@code get} reads the stored config. {@code up}/{@code down} only start or stop
 * tethering and do not call {@code setWifiApConfiguration} (on Android 9 that call
 * replaces the password). Always {@link System#exit(int)} so adb does not hang.
 */
public final class HuSoftApMain {
    private static final int WIFI_AP_STATE_ENABLED = 13;
    private static final int TETHERING_WIFI = 0;

    private HuSoftApMain() {
    }

    public static void main(String[] args) {
        int code = 1;
        try {
            Looper.prepareMainLooper();
            String action = (args != null && args.length > 0) ? args[0] : "get";
            Context context = systemContext();
            if ("up".equals(action)) {
                startTethering(context);
                System.out.println("RESULT ok=1 action=up");
            } else if ("down".equals(action)) {
                stopTethering(context);
                System.out.println("RESULT ok=1 action=down");
            } else if ("get".equals(action)) {
                System.out.println(readLine(context));
            } else if ("band24".equals(action)) {
                retune24(context);
                System.out.println("RESULT ok=1 action=band24");
            } else {
                System.out.println("RESULT ok=0 err=bad-action");
                code = 2;
                return;
            }
            code = 0;
        } catch (Throwable error) {
            System.out.println("RESULT ok=0 err=" + errorCode(error));
        } finally {
            System.exit(code);
        }
    }

    private static String readLine(Context context) throws Exception {
        WifiManager wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        int state = wifiApState(wifi);
        int enabled = state == WIFI_AP_STATE_ENABLED ? 1 : 0;
        WifiConfiguration config = wifiApConfiguration(wifi);
        String ssid = config != null && config.SSID != null ? config.SSID : "";
        String psk = config != null && config.preSharedKey != null ? config.preSharedKey : "";
        int band = config != null ? intField(config, "apBand", -1) : -1;
        int channel = config != null ? intField(config, "apChannel", 0) : 0;
        int auth = authType(config);
        return "RESULT ok=1 enabled=" + enabled
                + " band=" + band
                + " channel=" + channel
                + " auth=" + auth
                + " ssidB64=" + b64(ssid)
                + " pskB64=" + b64(psk)
                + " live=" + liveFrequencyMhz();
    }

    private static void startTethering(Context context) throws Exception {
        ConnectivityManager connectivity =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        Class<?> callbackClass = Class.forName(
                "android.net.ConnectivityManager$OnStartTetheringCallback");
        Object callback = allocate(callbackClass);
        Method start = ConnectivityManager.class.getMethod(
                "startTethering",
                int.class,
                boolean.class,
                callbackClass,
                Handler.class);
        start.invoke(connectivity, TETHERING_WIFI, false, callback, new Handler(Looper.getMainLooper()));
    }

    /**
     * A9 only. Writes 2.4 GHz with ACS ({@code apChannel=0}) and restarts hostapd.
     * {@code setWifiApConfiguration} replaces the password; the caller must {@code get} afterwards.
     * The settings write goes through the {@code settings} shell tool: {@code Settings.Global}
     * from this process is denied, while uid 2000's {@code settings put} is allowed.
     */
    private static void retune24(Context context) throws Exception {
        WifiManager wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        WifiConfiguration config = wifiApConfiguration(wifi);
        if (config == null) {
            throw new IllegalStateException("no-config");
        }
        setIntField(config, "apBand", 0);
        setIntField(config, "apChannel", 0);
        Method set = WifiManager.class.getMethod("setWifiApConfiguration", WifiConfiguration.class);
        try {
            set.invoke(wifi, config);
        } catch (Throwable error) {
            throw new IllegalStateException("set-" + errorCode(error));
        }
        try {
            putHostapdState("0");
            Thread.sleep(2500L);
            putHostapdState("1");
        } catch (Throwable error) {
            throw new IllegalStateException("settings-" + errorCode(error));
        }
    }

    private static void putHostapdState(String value) throws Exception {
        Process process = Runtime.getRuntime().exec(new String[]{
                "settings", "put", "global", "ro.mb.hostapd.state", value
        });
        int code = process.waitFor();
        process.destroy();
        if (code != 0) {
            throw new IllegalStateException("settings-failed");
        }
    }

    private static void setIntField(WifiConfiguration config, String name, int value) throws Exception {
        Field field = WifiConfiguration.class.getField(name);
        field.setInt(config, value);
    }

    private static void stopTethering(Context context) throws Exception {
        ConnectivityManager connectivity =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        Method stop = ConnectivityManager.class.getMethod("stopTethering", int.class);
        stop.invoke(connectivity, TETHERING_WIFI);
    }

    private static Context systemContext() throws Exception {
        Class<?> activityThread = Class.forName("android.app.ActivityThread");
        Object thread = activityThread.getMethod("systemMain").invoke(null);
        Object context = activityThread.getMethod("getSystemContext").invoke(thread);
        if (!(context instanceof Context)) {
            throw new IllegalStateException("no-context");
        }
        return (Context) context;
    }

    private static int wifiApState(WifiManager wifi) throws Exception {
        Method method = WifiManager.class.getMethod("getWifiApState");
        Object value = method.invoke(wifi);
        return value instanceof Integer ? (Integer) value : -1;
    }

    private static WifiConfiguration wifiApConfiguration(WifiManager wifi) throws Exception {
        Method method = WifiManager.class.getMethod("getWifiApConfiguration");
        Object value = method.invoke(wifi);
        return value instanceof WifiConfiguration ? (WifiConfiguration) value : null;
    }

    private static int intField(WifiConfiguration config, String name, int fallback) {
        try {
            Field field = WifiConfiguration.class.getField(name);
            return field.getInt(config);
        } catch (ReflectiveOperationException ignored) {
            return fallback;
        }
    }

    private static int authType(WifiConfiguration config) {
        if (config == null) {
            return -1;
        }
        try {
            Method method = WifiConfiguration.class.getMethod("getAuthType");
            Object value = method.invoke(config);
            return value instanceof Integer ? (Integer) value : -1;
        } catch (ReflectiveOperationException ignored) {
            return -1;
        }
    }

    private static Object allocate(Class<?> type) throws Exception {
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Field field = unsafeClass.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        Object unsafe = field.get(null);
        Method allocate = unsafeClass.getMethod("allocateInstance", Class.class);
        return allocate.invoke(unsafe, type);
    }

    /** Running SoftAP frequency. The stored {@code apBand} can stay 0 while hostapd is still on 5 GHz. */
    private static int liveFrequencyMhz() {
        Process process = null;
        try {
            process = Runtime.getRuntime().exec(new String[]{
                    "sh", "-c", "dumpsys wifi | grep mReportedFrequency"
            });
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            int freq = 0;
            while ((line = reader.readLine()) != null) {
                int mark = line.indexOf("mReportedFrequency:");
                if (mark < 0) continue;
                String rest = line.substring(mark + "mReportedFrequency:".length()).trim();
                int end = 0;
                while (end < rest.length() && Character.isDigit(rest.charAt(end))) end++;
                if (end > 0) freq = Integer.parseInt(rest.substring(0, end));
            }
            process.waitFor();
            return freq;
        } catch (Exception ignored) {
            return 0;
        } finally {
            if (process != null) process.destroy();
        }
    }

    private static String b64(String value) {
        if (value.isEmpty()) {
            return "-";
        }
        return Base64.encodeToString(value.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
    }

    private static String errorCode(Throwable error) {
        Throwable current = error;
        while (current instanceof InvocationTargetException && current.getCause() != null) {
            current = current.getCause();
        }
        if (current instanceof SecurityException) {
            return "denied";
        }
        String message = current.getMessage();
        if (message != null && (message.startsWith("no-") || message.startsWith("set-") || message.startsWith("settings-"))) {
            return message;
        }
        return "failed";
    }
}
