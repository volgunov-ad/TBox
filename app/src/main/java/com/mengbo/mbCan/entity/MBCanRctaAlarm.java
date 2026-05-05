package com.mengbo.mbCan.entity;

/* loaded from: classes.dex */
public class MBCanRctaAlarm {
    private byte nLeftSts;
    private byte nRCWWarning;
    private byte nRightSts;

    public MBCanRctaAlarm(byte b, byte b2, byte b3) {
        this.nLeftSts = b;
        this.nRightSts = b2;
        this.nRCWWarning = b3;
    }

    public byte getLeftSts() {
        return this.nLeftSts;
    }

    public byte getRightSts() {
        return this.nRightSts;
    }

    public byte getRCWWarning() {
        return this.nRCWWarning;
    }

    public String toString() {
        StringBuffer stringBuffer = new StringBuffer("RCTA报警信息{");
        stringBuffer.append("\n左侧报警状态=");
        stringBuffer.append((int) this.nLeftSts);
        stringBuffer.append("\n右侧报警状态=");
        stringBuffer.append((int) this.nRightSts);
        stringBuffer.append("\nRCW 报警=");
        stringBuffer.append((int) this.nRCWWarning);
        stringBuffer.append("\n}");
        return stringBuffer.toString();
    }
}
