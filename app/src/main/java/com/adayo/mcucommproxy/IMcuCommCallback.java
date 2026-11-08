package com.adayo.mcucommproxy;

/**
 * Callback shape expected by the system {@code libmcucommproxy_jni} on Android 10 head units.
 * The native method name is fixed to this class.
 *
 * {@code msgbuf} may be null from JNI; implementations must tolerate that.
 */
public interface IMcuCommCallback {
    void mcuCommCallback(byte[] msgbuf, char len);
}
