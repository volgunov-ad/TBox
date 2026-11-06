import android.content.ComponentName;
import android.os.IBinder;
import java.lang.reflect.Method;

/**
 * Runs as root via app_process. Shell has no SET_WALLPAPER_COMPONENT;
 * uid 0 passes that check. Tries the Android 9 binder methods in order.
 */
public class SetWallpaper {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("FAIL usage: SetWallpaper pkg/cls");
            System.exit(1);
        }
        ComponentName component = ComponentName.unflattenFromString(args[0]);
        if (component == null) {
            System.out.println("FAIL bad component " + args[0]);
            System.exit(1);
        }
        try {
            Class<?> sm = Class.forName("android.os.ServiceManager");
            IBinder binder = (IBinder) sm.getMethod("getService", String.class).invoke(null, "wallpaper");
            if (binder == null) {
                System.out.println("FAIL wallpaper service missing");
                System.exit(1);
            }
            Class<?> stub = Class.forName("android.app.IWallpaperManager$Stub");
            Object wallpaper = stub.getMethod("asInterface", IBinder.class).invoke(null, binder);
            invoke(wallpaper, component);
            System.out.println("OK " + component.flattenToString());
            System.exit(0);
        } catch (Throwable t) {
            t.printStackTrace(System.out);
            System.out.println("FAIL " + t);
            System.exit(1);
        }
    }

    private static void invoke(Object wallpaper, ComponentName component) throws Exception {
        Method[] methods = wallpaper.getClass().getMethods();
        if (call(wallpaper, methods, "setWallpaperComponent",
                new Class<?>[] {ComponentName.class},
                new Object[] {component})) {
            return;
        }
        if (call(wallpaper, methods, "setWallpaperComponent",
                new Class<?>[] {ComponentName.class, int.class},
                new Object[] {component, Integer.valueOf(0)})) {
            return;
        }
        if (call(wallpaper, methods, "setWallpaperComponentChecked",
                new Class<?>[] {ComponentName.class, String.class, int.class},
                new Object[] {component, "android", Integer.valueOf(0)})) {
            return;
        }
        StringBuilder known = new StringBuilder();
        for (int i = 0; i < methods.length; i++) {
            String name = methods[i].getName();
            if (name.indexOf("omponent") >= 0 || name.indexOf("allpaper") >= 0) {
                known.append(name).append(' ');
            }
        }
        throw new IllegalStateException("no setWallpaperComponent method: " + known);
    }

    private static boolean call(
            Object wallpaper,
            Method[] methods,
            String name,
            Class<?>[] types,
            Object[] values) {
        for (int i = 0; i < methods.length; i++) {
            Method method = methods[i];
            if (!method.getName().equals(name)) continue;
            Class<?>[] actual = method.getParameterTypes();
            if (actual.length != types.length) continue;
            boolean same = true;
            for (int p = 0; p < types.length; p++) {
                if (actual[p] != types[p]) same = false;
            }
            if (!same) continue;
            try {
                method.invoke(wallpaper, values);
                System.out.println("called " + name);
                return true;
            } catch (Throwable t) {
                Throwable cause = t.getCause() == null ? t : t.getCause();
                System.out.println("miss " + name + " " + cause);
            }
        }
        return false;
    }
}
