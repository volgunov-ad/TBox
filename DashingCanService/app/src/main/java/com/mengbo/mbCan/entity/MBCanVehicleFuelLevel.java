package com.mengbo.mbCan.entity;

/* loaded from: classes.dex */
public class MBCanVehicleFuelLevel {
    private float fDistenceToEmpty;
    private byte nBrake_Level;
    private byte nLevel;
    private short nRev;

    public MBCanVehicleFuelLevel(float f, byte b, byte b2, short s) {
        this.fDistenceToEmpty = f;
        this.nLevel = b;
        this.nBrake_Level = b2;
        this.nRev = s;
    }

    public byte getFuelLevel() {
        return this.nLevel;
    }

    public byte getBrake_Level() {
        return this.nBrake_Level;
    }

    public float getDistenceToEmpty() {
        return this.fDistenceToEmpty;
    }

    public short getRev() {
        return this.nRev;
    }

    public String toString() {
        StringBuffer stringBuffer = new StringBuffer("MBCanVehicleFuelLevel{");
        stringBuffer.append("\nfDistenceToEmpty=");
        stringBuffer.append(this.fDistenceToEmpty);
        stringBuffer.append("\nnLevel=");
        stringBuffer.append((int) this.nLevel);
        stringBuffer.append("\nnBrake_Level=");
        stringBuffer.append((int) this.nBrake_Level);
        stringBuffer.append("\nnRev=");
        stringBuffer.append((int) this.nRev);
        stringBuffer.append("\n}");
        return stringBuffer.toString();
    }
}
