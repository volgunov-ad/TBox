package com.mengbo.mbclient.defines;

/* loaded from: classes.dex */
public enum MBECarOTARequest {
    BASE_VALUE(60928, "基础值"),
    INSTALL_MCU(BASE_VALUE.value + 1, "升级MCU"),
    INSTALL_CLUSTER(BASE_VALUE.value + 3, "升级仪表"),
    RESTART_MCU(BASE_VALUE.value + 5, "MCU升级完毕重启"),
    GET_MCU_VERSION(BASE_VALUE.value + 7, "获取MCU版本号"),
    GET_CLUSTER_VERSION(BASE_VALUE.value + 9, "获取仪表版本号"),
    INSTALL_IVIDISP(BASE_VALUE.value + 11, "升级中控屏幕"),
    GET_IVIDISP_VERSION(BASE_VALUE.value + 13, "获取中控屏幕版本号"),
    INSTALL_CLUSTERDISP(BASE_VALUE.value + 15, "升级仪表屏幕"),
    GET_CLUSTERDISP_VERSION(BASE_VALUE.value + 17, "获取仪表屏幕版本号");

    private String desc;
    private int value;

    MBECarOTARequest(int i, String str) {
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
