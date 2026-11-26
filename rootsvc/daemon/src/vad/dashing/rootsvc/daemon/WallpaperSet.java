package vad.dashing.rootsvc.daemon;

import android.content.ComponentName;
import android.os.IBinder;

import java.lang.reflect.Method;

/** Root wallpaper component setter (from tools/dcl_install SetWallpaper). */
public final class WallpaperSet {
    private WallpaperSet() {
    }

    public static void run(String componentOrDefault) throws Exception {
        boolean useDefault = "default".equals(componentOrDefault);
        ComponentName component = useDefault ? null : ComponentName.unflattenFromString(componentOrDefault);
        if (!useDefault && component == null) {
            throw new IllegalArgumentException("bad component " + componentOrDefault);
        }
        Class<?> sm = Class.forName("android.os.ServiceManager");
        IBinder binder = (IBinder) sm.getMethod("getService", String.class).invoke(null, "wallpaper");
        if (binder == null) {
            throw new IllegalStateException("wallpaper service missing");
        }
        Class<?> stub = Class.forName("android.app.IWallpaperManager$Stub");
        Object wallpaper = stub.getMethod("asInterface", IBinder.class).invoke(null, binder);
        invoke(wallpaper, component);
    }

    private static void invoke(Object wallpaper, ComponentName component) throws Exception {
        Method[] methods = wallpaper.getClass().getMethods();
        if (call(wallpaper, methods, "setWallpaperComponent",
                new Class<?>[]{ComponentName.class},
                new Object[]{component})) {
            return;
        }
        if (call(wallpaper, methods, "setWallpaperComponent",
                new Class<?>[]{ComponentName.class, int.class},
                new Object[]{component, Integer.valueOf(0)})) {
            return;
        }
        if (call(wallpaper, methods, "setWallpaperComponentChecked",
                new Class<?>[]{ComponentName.class, String.class, int.class},
                new Object[]{component, "android", Integer.valueOf(0)})) {
            return;
        }
        throw new IllegalStateException("no setWallpaperComponent method");
    }

    private static boolean call(
            Object wallpaper, Method[] methods, String name, Class<?>[] types, Object[] values) {
        for (int i = 0; i < methods.length; i++) {
            Method method = methods[i];
            if (!method.getName().equals(name)) {
                continue;
            }
            Class<?>[] actual = method.getParameterTypes();
            if (actual.length != types.length) {
                continue;
            }
            boolean same = true;
            for (int p = 0; p < types.length; p++) {
                if (actual[p] != types[p]) {
                    same = false;
                }
            }
            if (!same) {
                continue;
            }
            try {
                method.invoke(wallpaper, values);
                return true;
            } catch (Throwable ignored) {
                // try next signature
            }
        }
        return false;
    }
}
