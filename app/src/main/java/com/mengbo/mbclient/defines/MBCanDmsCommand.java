package com.mengbo.mbclient.defines;

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
    eCAN_DMSCMD_COUNT(11);

    private int value;

    MBCanDmsCommand(int i) {
        this.value = i;
    }

    public int getValue() {
        return this.value;
    }
}
