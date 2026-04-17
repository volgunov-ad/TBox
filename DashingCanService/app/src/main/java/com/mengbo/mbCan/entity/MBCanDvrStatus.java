package com.mengbo.mbCan.entity;

/* loaded from: classes.dex */
public class MBCanDvrStatus {
    byte nContinuousPhotographResult;
    byte nPhotographResult;
    byte nSDcardSts;
    byte nSystemSts;

    public MBCanDvrStatus(byte b, byte b2, byte b3, byte b4) {
        this.nSDcardSts = b;
        this.nSystemSts = b2;
        this.nPhotographResult = b3;
        this.nContinuousPhotographResult = b4;
    }

    public byte getSDcardSts() {
        return this.nSDcardSts;
    }

    public byte getSystemSts() {
        return this.nSystemSts;
    }

    public byte getPhotographResult() {
        return this.nPhotographResult;
    }

    public byte getContinuousPhotographResult() {
        return this.nContinuousPhotographResult;
    }

    public String toString() {
        StringBuffer stringBuffer = new StringBuffer("MBCanDvrStatus{");
        stringBuffer.append("\nnSDcardSts=");
        stringBuffer.append((int) this.nSDcardSts);
        stringBuffer.append("\nnSystemSts=");
        stringBuffer.append((int) this.nSystemSts);
        stringBuffer.append("\nnPhotographResult=");
        stringBuffer.append((int) this.nPhotographResult);
        stringBuffer.append("\nnContinuousPhotographResult=");
        stringBuffer.append((int) this.nContinuousPhotographResult);
        stringBuffer.append("\n}");
        return stringBuffer.toString();
    }
}
