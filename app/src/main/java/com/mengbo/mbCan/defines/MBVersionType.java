package com.mengbo.mbCan.defines;

/* loaded from: classes.dex */
public enum MBVersionType {
    eVERSION_HARDWARE(1),
    eVERSION_MCU(2),
    eVERSION_CAN_SERVICE(3),
    eVERSION_AMP(4),
    eVERSION_CAN_CLIENT(255);

    private int value;

    MBVersionType(int i) {
        this.value = i;
    }

    public int getValue() {
        return this.value;
    }
}
