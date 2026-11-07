package com.adayo.mcucommproxy;

/**
 * JNI entry to the head-unit MCU binder. {@code libmcucommproxy_jni} is on the public
 * library list of the Android 10 head unit and binds these exact method names.
 */
public class McuCommManager {
    static {
        System.loadLibrary("mcucommproxy_jni");
    }

    private McuCommManager() {
    }

    public static native boolean connectMcuCommServer();

    public static native boolean registMcuCommCallback(IMcuCommCallback callback, byte functionId);

    public static native boolean unregistMcuCommCallback(IMcuCommCallback callback, byte functionId);
}
