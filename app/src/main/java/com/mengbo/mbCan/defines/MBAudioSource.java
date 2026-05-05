package com.mengbo.mbCan.defines;

/* loaded from: classes.dex */
public enum MBAudioSource {
    eAUDIO_SOURCE_NULL(0),
    eAUDIO_SOURCE_MEDIA(1),
    eAUDIO_SOURCE_RADIO(2),
    eAUDIO_SOURCE_HANDSFREE(3),
    eAUDIO_SOURCE_BCALL(4),
    eAUDIO_SOURCE_VR(5);

    private int value;

    MBAudioSource(int i) {
        this.value = i;
    }

    public int getValue() {
        return this.value;
    }
}
