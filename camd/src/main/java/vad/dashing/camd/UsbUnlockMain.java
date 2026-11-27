package vad.dashing.camd;

/**
 * Root entry point for the mb_daemon fallback: {@code CLASSPATH=<camd base.apk> app_process
 * /system/bin vad.dashing.camd.UsbUnlockMain}. Plain Java and reflection only, because it runs
 * outside the app process. Same binder calls as rootsvc {@code usb.unlock}.
 */
public final class UsbUnlockMain {
    private UsbUnlockMain() {
    }

    public static void main(String[] args) {
        try {
            Class<?> am = Class.forName("android.app.ActivityManager");
            Object iam = am.getMethod("getService").invoke(null);
            iam.getClass()
                    .getMethod("setLockScreenShown", boolean.class, boolean.class, int.class)
                    .invoke(iam, Boolean.FALSE, Boolean.FALSE, Integer.valueOf(-1));
            System.out.println("setLockScreenShown ok");
        } catch (Throwable t) {
            System.out.println("setLockScreenShown FAIL " + t);
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
            System.out.println("vold.onSecureKeyguardStateChanged(false) ok");
        } catch (Throwable t) {
            System.out.println("vold FAIL " + t);
        }
    }
}
