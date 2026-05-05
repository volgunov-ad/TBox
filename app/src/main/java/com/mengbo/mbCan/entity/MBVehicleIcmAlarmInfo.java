package com.mengbo.mbCan.entity;

/* loaded from: classes.dex */
public class MBVehicleIcmAlarmInfo {
    private byte nICM_7_InfoDisplay;
    private byte nRev;
    private short nRev2;

    public byte getnICM_7_InfoDisplay() {
        return this.nICM_7_InfoDisplay;
    }

    public byte getnRev() {
        return this.nRev;
    }

    public short getnRev2() {
        return this.nRev2;
    }

    public MBVehicleIcmAlarmInfo(byte b, byte b2, short s) {
        this.nICM_7_InfoDisplay = b;
        this.nRev = b2;
        this.nRev2 = s;
    }

    public String toString() {
        return "MBVehicleIcmAlarmInfo{nICM_7_InfoDisplay=" + ((int) this.nICM_7_InfoDisplay) + ", nRev=" + ((int) this.nRev) + ", nRev2=" + ((int) this.nRev2) + '}';
    }
}
