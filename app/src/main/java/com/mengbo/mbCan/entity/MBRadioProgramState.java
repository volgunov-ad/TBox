package com.mengbo.mbCan.entity;

/* loaded from: classes.dex */
public class MBRadioProgramState {
    private byte nFreDeviation;
    private byte nLevel;
    private byte nMultiPath;
    private short nNoise;
    private byte nRadioType;
    private short nRev;

    public MBRadioProgramState(byte b, byte b2, short s, byte b3, byte b4, short s2) {
        this.nLevel = b;
        this.nRadioType = b2;
        this.nNoise = s;
        this.nMultiPath = b3;
        this.nFreDeviation = b4;
        this.nRev = s2;
    }

    public byte getLevel() {
        return this.nLevel;
    }

    public byte getRadioType() {
        return this.nRadioType;
    }

    public short getNoise() {
        return this.nNoise;
    }

    public byte getMultiPath() {
        return this.nMultiPath;
    }

    public byte getFreDeviation() {
        return this.nFreDeviation;
    }

    public short getRev() {
        return this.nRev;
    }

    public String toString() {
        StringBuffer stringBuffer = new StringBuffer("MBRadioProgramState{");
        stringBuffer.append("\nnLevel=");
        stringBuffer.append((int) this.nLevel);
        stringBuffer.append("\nnRadioType=");
        stringBuffer.append((int) this.nRadioType);
        stringBuffer.append("\nnNoise=");
        stringBuffer.append((int) this.nNoise);
        stringBuffer.append("\nnMultiPath=");
        stringBuffer.append((int) this.nMultiPath);
        stringBuffer.append("\nnFreDeviation=");
        stringBuffer.append((int) this.nFreDeviation);
        stringBuffer.append("\nnRev=");
        stringBuffer.append((int) this.nRev);
        stringBuffer.append("\n}");
        return stringBuffer.toString();
    }
}
