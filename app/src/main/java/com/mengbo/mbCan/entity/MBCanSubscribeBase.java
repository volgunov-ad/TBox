package com.mengbo.mbCan.entity;

import com.mengbo.mbCan.interfaces.ICanBaseCallback;

/* loaded from: classes.dex */
public class MBCanSubscribeBase {
    ICanBaseCallback callback;
    int interval;
    int subscribeType;

    public MBCanSubscribeBase(int i, int i2) {
        this(i, i2, null);
    }

    public MBCanSubscribeBase(int i, int i2, ICanBaseCallback iCanBaseCallback) {
        this.subscribeType = i;
        this.interval = i2;
        this.callback = iCanBaseCallback;
    }

    public int getSubscribeType() {
        return this.subscribeType;
    }

    public int getInterval() {
        return this.interval;
    }

    public void setInterval(int i) {
        this.interval = i;
    }

    public ICanBaseCallback getCallback() {
        return this.callback;
    }

    public void setCallback(ICanBaseCallback iCanBaseCallback) {
        this.callback = iCanBaseCallback;
    }

    public String toString() {
        StringBuffer stringBuffer = new StringBuffer("MBCanSubscribeBase{");
        stringBuffer.append("\nsubscribeType=");
        stringBuffer.append(this.subscribeType);
        stringBuffer.append("\ninterval=");
        stringBuffer.append(this.interval);
        stringBuffer.append("\ncallback=");
        stringBuffer.append(this.callback);
        stringBuffer.append("\n}");
        return stringBuffer.toString();
    }
}
