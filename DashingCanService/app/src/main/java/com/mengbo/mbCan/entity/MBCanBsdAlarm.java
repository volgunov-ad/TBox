package com.mengbo.mbCan.entity;

/* loaded from: classes.dex */
public class MBCanBsdAlarm {
    private byte nLeftSts;
    private byte nRev;
    private byte nRightSts;
    private byte nSRR_1_SystemState;

    public MBCanBsdAlarm(byte b, byte b2, byte b3, byte b4) {
        this.nLeftSts = b;
        this.nRightSts = b2;
        this.nSRR_1_SystemState = b3;
        this.nRev = b4;
    }

    public byte getLeftSts() {
        return this.nLeftSts;
    }

    public byte getRightSts() {
        return this.nRightSts;
    }

    public byte getSRR_1_SystemState() {
        return this.nSRR_1_SystemState;
    }

    public byte getRev() {
        return this.nRev;
    }

    public String toString() {
        StringBuffer stringBuffer = new StringBuffer("BSD报警信息{");
        stringBuffer.append("\n左侧报警状态=");
        stringBuffer.append((int) this.nLeftSts);
        stringBuffer.append("\n右侧报警状态=");
        stringBuffer.append((int) this.nRightSts);
        stringBuffer.append("\n系统状态=");
        stringBuffer.append((int) this.nSRR_1_SystemState);
        stringBuffer.append("\nnRev=");
        stringBuffer.append((int) this.nRev);
        stringBuffer.append("\n}");
        return stringBuffer.toString();
    }
}
