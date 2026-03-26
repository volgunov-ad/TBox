package com.mengbo.mbCan.entity;

import com.mengbo.mbCan.interfaces.ICanBaseCallback;

/**
 * Subscription entry: {@code subscribeType} is {@link com.mengbo.mbCan.defines.MBCanDataType} ordinal.
 */
public class MBCanSubscribeBase {

    public ICanBaseCallback callback;
    public int interval;
    public int subscribeType;

    public MBCanSubscribeBase(int subscribeType, int interval) {
        this(subscribeType, interval, null);
    }

    public MBCanSubscribeBase(int subscribeType, int interval, ICanBaseCallback callback) {
        this.subscribeType = subscribeType;
        this.interval = interval;
        this.callback = callback;
    }

    public ICanBaseCallback getCallback() {
        return callback;
    }

    public int getInterval() {
        return interval;
    }

    public int getSubscribeType() {
        return subscribeType;
    }
}
