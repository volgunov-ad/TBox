package com.adayo.mcucommproxy;

/**
 * Concrete JNI callback for {@code PROTOCOL_CMD_DEV_STATUS}. Kept in this package so R8
 * {@code -keep class com.adayo.mcucommproxy.**} preserves {@link #mcuCommCallback} for
 * native {@code GetMethodID}. Do not replace with a Kotlin SAM lambda.
 */
public final class DevStatusMcuCallback implements IMcuCommCallback {
    public interface Listener {
        void onDevStatus(byte[] msgbuf, int length);
    }

    private final Listener listener;

    public DevStatusMcuCallback(Listener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener");
        }
        this.listener = listener;
    }

    @Override
    public void mcuCommCallback(byte[] msgbuf, char len) {
        if (msgbuf == null) {
            return;
        }
        try {
            listener.onDevStatus(msgbuf, (int) len);
        } catch (Throwable ignored) {
            // Native binder thread: never let decode bugs kill the process.
        }
    }
}
