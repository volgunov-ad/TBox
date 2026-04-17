package com.mengbo.mbclient.defines;

/* loaded from: classes.dex */
public enum MBBTBoxRequest {
    BASE_VALUE(48640, "基础值"),
    TBOX_INFO(BASE_VALUE.value + 1, "获取TBOX信息");

    private String desc;
    private int value;

    MBBTBoxRequest(int i, String str) {
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
