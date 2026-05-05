package com.mengbo.mbCan.entity;

/* loaded from: classes.dex */
public class MBCanTireInfo {
    float fPressure;
    short nRev;
    byte nTemperature;
    byte nWarningSts;

    public MBCanTireInfo(byte b, byte b2, short s, float f) {
        this.nTemperature = b;
        this.nWarningSts = b2;
        this.nRev = s;
        this.fPressure = f;
    }

    public byte getTemperature() {
        return this.nTemperature;
    }

    public byte getWarningSts() {
        return this.nWarningSts;
    }

    public short getRev() {
        return this.nRev;
    }

    public float getPressure() {
        return this.fPressure;
    }

    public String toString() {
        StringBuffer stringBuffer = new StringBuffer("MBCanTireInfo{");
        stringBuffer.append("\nnTemperature=");
        stringBuffer.append((int) this.nTemperature);
        stringBuffer.append("\nnWarningSts=");
        stringBuffer.append((int) this.nWarningSts);
        stringBuffer.append("\nnRev=");
        stringBuffer.append((int) this.nRev);
        stringBuffer.append("\nfPressure=");
        stringBuffer.append(this.fPressure);
        stringBuffer.append("\n}");
        return stringBuffer.toString();
    }
}
