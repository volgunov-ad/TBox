package com.mengbo.mbCan.entity;

/* loaded from: classes.dex */
public class MBCanDowAlarm {
    private byte nLeftSts;
    private byte nRightSts;

    public MBCanDowAlarm(byte b, byte b2) {
        this.nLeftSts = b;
        this.nRightSts = b2;
    }

    public byte getLeftSts() {
        return this.nLeftSts;
    }

    public byte getRightSts() {
        return this.nRightSts;
    }

    public String toString() {
        StringBuffer stringBuffer = new StringBuffer("Dow报警信息{");
        stringBuffer.append("\n左侧报警状态=");
        stringBuffer.append((int) this.nLeftSts);
        stringBuffer.append("\n右侧报警状态=");
        stringBuffer.append((int) this.nRightSts);
        stringBuffer.append("\n}");
        return stringBuffer.toString();
    }
}
