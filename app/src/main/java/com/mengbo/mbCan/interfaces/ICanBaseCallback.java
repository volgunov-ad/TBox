package com.mengbo.mbCan.interfaces;

/**
 * Matches the callback type used by {@code libmbCan.so} (from Mengbo MB-CAN stack).
 */
public interface ICanBaseCallback {
    void onCanDataCallback(int dataType, Object data);
}
