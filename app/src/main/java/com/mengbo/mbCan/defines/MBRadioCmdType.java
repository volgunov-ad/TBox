package com.mengbo.mbCan.defines;

/* loaded from: classes.dex */
public enum MBRadioCmdType {
    eMBRADIO_CMDTYPE_BAND(1),
    eMBRADIO_CMDTYPE_SEEK_UP(2),
    eMBRADIO_CMDTYPE_SEEK_DOWN(3),
    eMBRADIO_CMDTYPE_SEEK_CANCEL(4),
    eMBRADIO_CMDTYPE_SEEK_AUTO(5),
    eMBRADIO_CMDTYPE_SEEK_AUTO_CANCEL(6),
    eMBRADIO_CMDTYPE_STEP_UP(7),
    eMBRADIO_CMDTYPE_STEP_DOWN(8),
    eMBRADIO_CMDTYPE_FREQUENCY_CHECK(9),
    eMBRADIO_CMDTYPE_PARAM(10);

    private int value;

    MBRadioCmdType(int i) {
        this.value = i;
    }

    public int getValue() {
        return this.value;
    }

    public static String getDescription(int i) {
        for (MBRadioCmdType mBRadioCmdType : values()) {
            if (mBRadioCmdType.value == i) {
                return mBRadioCmdType.name();
            }
        }
        return "未知";
    }

    public static int getValueByName(String str) {
        for (MBRadioCmdType mBRadioCmdType : values()) {
            if (mBRadioCmdType.name().equals(str)) {
                return mBRadioCmdType.value;
            }
        }
        return -99;
    }
}
