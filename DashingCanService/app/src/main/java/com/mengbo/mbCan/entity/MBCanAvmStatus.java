package com.mengbo.mbCan.entity;

/* loaded from: classes.dex */
public class MBCanAvmStatus {
    private byte nApaSearchSts;
    private byte nApaSystemSts;
    private byte nDisplaySts;
    private byte nFactoryMode;
    private byte nFaultSts;
    private byte nFeedbackSts;
    private byte nFrontCameraSts;
    private byte nLeftCameraSts;
    private byte nRearCameraSts;
    private byte nRev;
    private byte nRightCameraSts;

    public MBCanAvmStatus(byte b, byte b2, byte b3, byte b4, byte b5, byte b6, byte b7, byte b8, byte b9, byte b10, byte b11) {
        this.nDisplaySts = b;
        this.nFeedbackSts = b2;
        this.nFactoryMode = b3;
        this.nFrontCameraSts = b4;
        this.nLeftCameraSts = b5;
        this.nRearCameraSts = b6;
        this.nRightCameraSts = b7;
        this.nFaultSts = b8;
        this.nApaSystemSts = b9;
        this.nApaSearchSts = b10;
        this.nRev = b11;
    }

    public byte getDisplaySts() {
        return this.nDisplaySts;
    }

    public byte getFeedbackSts() {
        return this.nFeedbackSts;
    }

    public byte getFactoryMode() {
        return this.nFactoryMode;
    }

    public byte getFrontCameraSts() {
        return this.nFrontCameraSts;
    }

    public byte getLeftCameraSts() {
        return this.nLeftCameraSts;
    }

    public byte getRearCameraSts() {
        return this.nRearCameraSts;
    }

    public byte getRightCameraSts() {
        return this.nRightCameraSts;
    }

    public byte getFaultSts() {
        return this.nFaultSts;
    }

    public byte getApaSystemSts() {
        return this.nApaSystemSts;
    }

    public byte getApaSearchSts() {
        return this.nApaSearchSts;
    }

    public byte getRev() {
        return this.nRev;
    }

    public String toString() {
        StringBuffer stringBuffer = new StringBuffer("MBCanCfgItem{");
        stringBuffer.append("\nnDisplaySts=");
        stringBuffer.append((int) this.nDisplaySts);
        stringBuffer.append("\nnFeedbackSts=");
        stringBuffer.append((int) this.nFeedbackSts);
        stringBuffer.append("\nnFactoryMode=");
        stringBuffer.append((int) this.nFactoryMode);
        stringBuffer.append("\nnFrontCameraSts=");
        stringBuffer.append((int) this.nFrontCameraSts);
        stringBuffer.append("\nnLeftCameraSts=");
        stringBuffer.append((int) this.nLeftCameraSts);
        stringBuffer.append("\nnRearCameraSts=");
        stringBuffer.append((int) this.nRearCameraSts);
        stringBuffer.append("\nnRightCameraSts=");
        stringBuffer.append((int) this.nRightCameraSts);
        stringBuffer.append("\nnFaultSts=");
        stringBuffer.append((int) this.nFaultSts);
        stringBuffer.append("\nnApaSystemSts=");
        stringBuffer.append((int) this.nApaSystemSts);
        stringBuffer.append("\nnApaSearchSts=");
        stringBuffer.append((int) this.nApaSearchSts);
        stringBuffer.append("\nnRev=");
        stringBuffer.append((int) this.nRev);
        stringBuffer.append("\n}");
        return stringBuffer.toString();
    }
}
