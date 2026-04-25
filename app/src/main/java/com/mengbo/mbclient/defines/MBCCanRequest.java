package com.mengbo.mbclient.defines;

/* loaded from: classes.dex */
public enum MBCCanRequest {
    BASE_VALUE(52736, "基础值"),
    CAN_VIN_CODE(BASE_VALUE.value + 7, "VIN码获取"),
    CAN_SOFT_CONFIG(BASE_VALUE.value + 9, "软件配置码获取"),
    CAN_GEAR(BASE_VALUE.value + 11, "获取档位"),
    CAN_OPEN_WINDOW(BASE_VALUE.value + 13, "打开车窗"),
    CAN_GET_ALARM_MODE(BASE_VALUE.value + 15, "获取当前设防状态"),
    CAN_RESET_SOC_FACTORY(BASE_VALUE.value + 17, "恢复出厂设置"),
    CAN_SET_FRAGRANCE(BASE_VALUE.value + 19, "设置香氛信号"),
    CAN_GET_FRAGRANCE(BASE_VALUE.value + 21, "获取香氛信号"),
    CAN_PLAY_POP_SOUND(BASE_VALUE.value + 23, "通过canservice播放声音"),
    CAN_GET_WINDOW_STATE(BASE_VALUE.value + 25, "获取车窗状态"),
    CAN_SET_VEHICLE_PROPERTY(BASE_VALUE.value + 27, "设置车辆相关CAN信号"),
    CAN_GET_VEHICLE_PROPERTY(BASE_VALUE.value + 29, "获取车辆相关CAN信号");

    private String desc;
    private int value;

    MBCCanRequest(int i, String str) {
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
