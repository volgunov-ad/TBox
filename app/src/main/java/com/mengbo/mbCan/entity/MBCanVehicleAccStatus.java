package com.mengbo.mbCan.entity;

/* loaded from: classes.dex */
public class MBCanVehicleAccStatus {
    private byte nIgnSts;
    private byte nMode;
    private byte nPowerSts;
    private byte nStatus;

    public MBCanVehicleAccStatus(byte b, byte b2, byte b3, byte b4) {
        this.nStatus = b;
        this.nMode = b2;
        this.nPowerSts = b3;
        this.nIgnSts = b4;
    }

    public byte getAccStatus() {
        return this.nStatus;
    }

    public byte getSystemMode() {
        return this.nMode;
    }

    public byte getPowerSts() {
        return this.nPowerSts;
    }

    public byte getIgnSts() {
        return this.nIgnSts;
    }

    public String toString() {
        StringBuffer stringBuffer = new StringBuffer("MBCanVehicleAccStatus{");
        stringBuffer.append("nStatus=");
        stringBuffer.append((int) this.nStatus);
        stringBuffer.append("nMode=");
        stringBuffer.append((int) this.nMode);
        stringBuffer.append("nPowerSts=");
        stringBuffer.append((int) this.nPowerSts);
        stringBuffer.append("nIgnSts=");
        stringBuffer.append((int) this.nIgnSts);
        stringBuffer.append("}");
        return stringBuffer.toString();
    }
}
