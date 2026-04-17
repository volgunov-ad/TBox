package com.mengbo.mbCan.entity;

/* loaded from: classes.dex */
public class MBCanSeatBeltWarning {
    private byte nDriverWarning;
    private byte nPassengerWarning;

    public MBCanSeatBeltWarning() {
    }

    public MBCanSeatBeltWarning(byte b, byte b2) {
        this.nDriverWarning = b;
        this.nPassengerWarning = b2;
    }

    public byte getDriverWarning() {
        return this.nDriverWarning;
    }

    public byte getPassengerWarning() {
        return this.nPassengerWarning;
    }

    public String toString() {
        StringBuffer stringBuffer = new StringBuffer("MBCanSeatBeltWarning{");
        stringBuffer.append("\nnDriverWarning=");
        stringBuffer.append((int) this.nDriverWarning);
        stringBuffer.append("\nnPassengerWarning=");
        stringBuffer.append((int) this.nPassengerWarning);
        stringBuffer.append("\n}");
        return stringBuffer.toString();
    }
}
