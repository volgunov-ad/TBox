import android.os.IBinder;
import java.lang.reflect.Method;

/** Runs as root via app_process. Clears the stopped flag so BOOT_COMPLETED is delivered. */
public class Unstop {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("FAIL usage: Unstop package");
            System.exit(1);
        }
        try {
            Class<?> sm = Class.forName("android.os.ServiceManager");
            IBinder binder = (IBinder) sm.getMethod("getService", String.class).invoke(null, "package");
            if (binder == null) {
                System.out.println("FAIL package service missing");
                System.exit(1);
            }
            Class<?> stub = Class.forName("android.content.pm.IPackageManager$Stub");
            Object pm = stub.getMethod("asInterface", IBinder.class).invoke(null, binder);
            Method method = Class.forName("android.content.pm.IPackageManager")
                    .getMethod("setPackageStoppedState", String.class, boolean.class, int.class);
            method.invoke(pm, args[0], Boolean.FALSE, Integer.valueOf(0));
            System.out.println("OK unstop " + args[0]);
            System.exit(0);
        } catch (Throwable t) {
            t.printStackTrace(System.out);
            System.out.println("FAIL " + t);
            System.exit(1);
        }
    }
}
