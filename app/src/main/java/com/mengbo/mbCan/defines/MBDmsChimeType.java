package com.mengbo.mbCan.defines;

/* loaded from: classes.dex */
public enum MBDmsChimeType {
    eCHIME_DMS_FACE_RECOGNIZE(1),
    eCHIME_DMS_OWNER(2),
    eCHIME_DMS_FRIEND(3),
    eCHIME_DMS_VISITOR(4),
    eCHIME_DMS_SEATTIPS(5),
    eCHIME_DMS_LEGACY(6),
    eCHIME_DMS_STARTCAR(7),
    eCHIME_DMS_DROWSINESS_MEDIUM(8),
    eCHIME_DMS_DROWSINESS_HEAVEY(9),
    eCHIME_DMS_DISTRACTION_MEDIUM(10),
    eCHIME_DMS_DISTRACTION_HEAVEY(11);

    private int value;

    MBDmsChimeType(int i) {
        this.value = i;
    }

    public int getValue() {
        return this.value;
    }
}
