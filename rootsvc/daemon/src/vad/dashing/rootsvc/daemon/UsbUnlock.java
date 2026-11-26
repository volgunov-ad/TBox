package vad.dashing.rootsvc.daemon;

/**
 * Same binder calls as tools/usb_mount UnlockUsb: leave vold's secure-keyguard
 * so NTFS/exFAT sticks are scanned with SystemUI hidden.
 */
public final class UsbUnlock {
    private UsbUnlock() {
    }

    public static void run() throws Exception {
        try {
            Class<?> am = Class.forName("android.app.ActivityManager");
            Object iam = am.getMethod("getService").invoke(null);
            iam.getClass()
                    .getMethod("setLockScreenShown", boolean.class, boolean.class, int.class)
                    .invoke(iam, Boolean.FALSE, Boolean.FALSE, Integer.valueOf(-1));
        } catch (Throwable t) {
            throw new Exception("setLockScreenShown: " + t, t);
        }
        try {
            Class<?> sm = Class.forName("android.os.ServiceManager");
            Object binder = sm.getMethod("getService", String.class).invoke(null, "vold");
            Class<?> ibinder = Class.forName("android.os.IBinder");
            Class<?> stub = Class.forName("android.os.IVold$Stub");
            Object vold = stub.getMethod("asInterface", ibinder).invoke(null, binder);
            vold.getClass()
                    .getMethod("onSecureKeyguardStateChanged", boolean.class)
                    .invoke(vold, Boolean.FALSE);
        } catch (Throwable t) {
            throw new Exception("vold: " + t, t);
        }
    }
}
