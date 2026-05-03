package com.mengbo.mbCan.defines;

/* loaded from: classes.dex */
public enum MBHardKeyCode {
    eMBHARDKEY_OK(28),
    eMBHARDKEY_UP(103),
    eMBHARDKEY_LEFT(105),
    eMBHARDKEY_RIGHT(106),
    eMBHARDKEY_DOWN(108),
    eMBHARDKEY_POWER(116),
    eMBHARDKEY_SET(141),
    eMBHARDKEY_RETURN(158),
    eMBHARDKEY_HOME(587),
    eMBHARDKEY_ANTICLOCKWISE(114),
    eMBHARDKEY_CLOCKWISE(115);

    private int value;

    MBHardKeyCode(int i) {
        this.value = i;
    }

    public int getValue() {
        return this.value;
    }

    public static String getDescription(int i) {
        for (MBHardKeyCode mBHardKeyCode : values()) {
            if (mBHardKeyCode.value == i) {
                return mBHardKeyCode.name();
            }
        }
        return "未知按键";
    }
}
