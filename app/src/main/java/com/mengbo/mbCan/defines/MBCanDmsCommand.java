package com.mengbo.mbCan.defines;

/* loaded from: classes.dex */
public enum MBCanDmsCommand {
    eCAN_DMSCMD_SETUSER(1),
    eCAN_DMSCMD_DELUSER(2),
    eCAN_DMSCMD_SEAT_AUTOSET_SWITCH(3),
    eCAN_DMSCMD_ATMO_LIGHT_SWITCH(4),
    eCAN_DMSCMD_FACE_DETECT(5),
    eCAN_DMSCMD_USERTYPE(6),
    eCAN_DMSCMD_DROWSINESS_LEVEL(7),
    eCAN_DMSCMD_DISTRACTION_LEVEL(8),
    eCAN_DMSCMD_CHILD_LEGACY(9),
    eCAN_DMSCMD_CHIME(10),
    eCAN_DMSCMD_SET_HUD(11),
    eCAN_DMSCMD_COUNT(12);

    private int value;

    MBCanDmsCommand(int i) {
        this.value = i;
    }

    public int getValue() {
        return this.value;
    }

    public static String getDescription(int i) {
        for (MBCanDmsCommand mBCanDmsCommand : values()) {
            if (mBCanDmsCommand.value == i) {
                return mBCanDmsCommand.name();
            }
        }
        return "未知";
    }

    public static int getValueByName(String str) {
        for (MBCanDmsCommand mBCanDmsCommand : values()) {
            if (mBCanDmsCommand.name().equals(str)) {
                return mBCanDmsCommand.value;
            }
        }
        return -99;
    }
}
