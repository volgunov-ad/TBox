package com.mengbo.mbCan.entity;

/* loaded from: classes.dex */
public class MBHardKey {
    private short keyCode;
    private byte keyStatus;
    private byte keyType;

    public MBHardKey(short s, byte b, byte b2) {
        this.keyCode = s;
        this.keyStatus = b;
        this.keyType = b2;
    }

    public short getKeyCode() {
        return this.keyCode;
    }

    public byte getKeyStatus() {
        return this.keyStatus;
    }

    public byte getKeyType() {
        return this.keyType;
    }

    public String toString() {
        StringBuffer stringBuffer = new StringBuffer("MBHardKey{");
        stringBuffer.append("\nkeyCode=");
        stringBuffer.append((int) this.keyCode);
        stringBuffer.append("\nkeyStatus=");
        stringBuffer.append((int) this.keyStatus);
        stringBuffer.append("\nkeyType=");
        stringBuffer.append((int) this.keyType);
        stringBuffer.append("\n}");
        return stringBuffer.toString();
    }
}
