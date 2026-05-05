package com.mengbo.mbCan.entity;

/* loaded from: classes.dex */
public class MBCanPM25 {
    byte nAirIOutQLevel;
    byte nAirInQLevel;
    byte nPM25ErrSts;
    short nPM25Indensity;
    byte nPM25Sts;
    short nPM25outdensity;

    public MBCanPM25(short s, short s2, byte b, byte b2, byte b3, byte b4) {
        this.nPM25Indensity = s;
        this.nPM25outdensity = s2;
        this.nAirInQLevel = b;
        this.nAirIOutQLevel = b2;
        this.nPM25Sts = b3;
        this.nPM25ErrSts = b4;
    }

    public short getPM25Indensity() {
        return this.nPM25Indensity;
    }

    public short getPM25outdensity() {
        return this.nPM25outdensity;
    }

    public byte getAirInQLevel() {
        return this.nAirInQLevel;
    }

    public byte getAirIOutQLevel() {
        return this.nAirIOutQLevel;
    }

    public byte getPM25Sts() {
        return this.nPM25Sts;
    }

    public byte getPM25ErrSts() {
        return this.nPM25ErrSts;
    }

    public String toString() {
        StringBuffer stringBuffer = new StringBuffer("MBCanPM25{");
        stringBuffer.append("\n室内空气 PM2.5浓度:");
        stringBuffer.append((int) this.nPM25Indensity);
        stringBuffer.append("\n室外空气 PM2.5浓度:");
        stringBuffer.append((int) this.nPM25outdensity);
        stringBuffer.append("\n室内空气质量等级:");
        stringBuffer.append((int) this.nAirInQLevel);
        stringBuffer.append("\n室外空气质量等级:");
        stringBuffer.append((int) this.nAirIOutQLevel);
        stringBuffer.append("\n传感器工作状态:");
        stringBuffer.append((int) this.nPM25Sts);
        stringBuffer.append("\n传感器报错状态:");
        stringBuffer.append((int) this.nPM25ErrSts);
        stringBuffer.append("\n}");
        return stringBuffer.toString();
    }
}
