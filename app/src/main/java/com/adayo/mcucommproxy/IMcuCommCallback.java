package com.adayo.mcucommproxy;

/**
 * Callback shape expected by the system {@code libmcucommproxy_jni} on Android 10 head units.
 * The native method name is fixed to this class.
 */
public interface IMcuCommCallback {
    void mcuCommCallback(byte[] msgbuf, char len);
}
