package com.mengbo.mbclient.defines;

/* loaded from: classes.dex */
public enum MBFCommonRequest {
    BASE_VALUE(65024, "基础值"),
    OBSERVER_APP_START(BASE_VALUE.value + 1, "启动APP通知");

    private String desc;
    private int value;

    MBFCommonRequest(int i, String str) {
        this.value = i;
        this.desc = str;
    }

    public int getValue() {
        return this.value;
    }

    public String getDesc() {
        return this.desc;
    }
}
