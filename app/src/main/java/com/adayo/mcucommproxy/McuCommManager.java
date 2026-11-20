package com.adayo.mcucommproxy;

/**
 * JNI entry to the head-unit MCU binder. {@code libmcucommproxy_jni} is on the public
 * library list of the Android 10 head unit and binds these exact method names.
 *
 * Library load is lazy (not a static initializer) so a missing {@code .so} becomes a
 * caught {@link UnsatisfiedLinkError} instead of a permanent
 * {@link ExceptionInInitializerError} on the class.
 */
public class McuCommManager {
    private static final Object LOAD_LOCK = new Object();
    private static boolean loadAttempted;
    private static boolean loadOk;

    private McuCommManager() {
    }

    /** Loads {@code mcucommproxy_jni} once. Returns false when the library is absent. */
    public static boolean ensureLibraryLoaded() {
        synchronized (LOAD_LOCK) {
            if (loadAttempted) {
                return loadOk;
            }
            loadAttempted = true;
            try {
                System.loadLibrary("mcucommproxy_jni");
                loadOk = true;
            } catch (UnsatisfiedLinkError | ExceptionInInitializerError | SecurityException error) {
                loadOk = false;
            }
            return loadOk;
        }
    }

    public static native boolean connectMcuCommServer();

    public static native boolean registMcuCommCallback(IMcuCommCallback callback, byte functionId);

    public static native boolean unregistMcuCommCallback(IMcuCommCallback callback, byte functionId);
}
