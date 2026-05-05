package com.mengbo.mbclient.defines;

/* loaded from: classes.dex */
public enum MBDClusterRequest {
    BASE_VALUE(56832, "基础值"),
    CLUSTER_TYPE_VERSION(BASE_VALUE.value + 1, "获取仪表版本信息"),
    CLUSTER_TYPE_TIMEFORMAT(BASE_VALUE.value + 3, "请求仪表时间制式"),
    CLUSTER_TYPE_THEME(BASE_VALUE.value + 4, "请求仪表主题"),
    CLUSTER_TYPE_LANGUAGE(BASE_VALUE.value + 5, "仪表语言设置"),
    CLUSTER_TYPE_MAP(BASE_VALUE.value + 7, "发送地图状态");

    private String desc;
    private int value;

    MBDClusterRequest(int i, String str) {
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
