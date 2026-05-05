package com.mengbo.mbCan.entity;

/* loaded from: classes.dex */
public class MBCanRadioFrequencyInfo {
    private int nFrequency;
    private byte nLevel;
    private byte nRev;
    private byte nStatus;
    private byte nValid;

    public MBCanRadioFrequencyInfo(int i, byte b, byte b2, byte b3, byte b4) {
        this.nFrequency = i;
        this.nStatus = b;
        this.nValid = b2;
        this.nLevel = b3;
        this.nRev = b4;
    }

    public int getFrequency() {
        return this.nFrequency;
    }

    public byte getStatus() {
        return this.nStatus;
    }

    public byte getValid() {
        return this.nValid;
    }

    public byte getLevel() {
        return this.nLevel;
    }

    public byte getRev() {
        return this.nRev;
    }

    public String toString() {
        StringBuffer stringBuffer = new StringBuffer("MBCanRadioFrequencyInfo{");
        stringBuffer.append("\nnFrequency=");
        stringBuffer.append(this.nFrequency);
        stringBuffer.append("\nnStatus=");
        stringBuffer.append((int) this.nStatus);
        stringBuffer.append("\nnValid=");
        stringBuffer.append((int) this.nValid);
        stringBuffer.append("\nnLevel=");
        stringBuffer.append((int) this.nLevel);
        stringBuffer.append("\nnRev=");
        stringBuffer.append((int) this.nRev);
        stringBuffer.append("\n}");
        return stringBuffer.toString();
    }
}
