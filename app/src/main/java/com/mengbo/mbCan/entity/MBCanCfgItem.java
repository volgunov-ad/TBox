package com.mengbo.mbCan.entity;

/* loaded from: classes.dex */
public class MBCanCfgItem {
    short nItem;
    byte nModular;
    byte nRev;
    int nValue;

    public MBCanCfgItem(byte b, byte b2, short s, int i) {
        this.nModular = b;
        this.nRev = b2;
        this.nItem = s;
        this.nValue = i;
    }

    public byte getModular() {
        return this.nModular;
    }

    public byte getRev() {
        return this.nRev;
    }

    public short getItem() {
        return this.nItem;
    }

    public int getValue() {
        return this.nValue;
    }

    public String toString() {
        StringBuffer stringBuffer = new StringBuffer("MBCanCfgItem{");
        stringBuffer.append("\nnModular=");
        stringBuffer.append((int) this.nModular);
        stringBuffer.append("\nnRev=");
        stringBuffer.append((int) this.nRev);
        stringBuffer.append("\nnItem=");
        stringBuffer.append((int) this.nItem);
        stringBuffer.append("\nnValue=");
        stringBuffer.append(this.nValue);
        stringBuffer.append("\n}");
        return stringBuffer.toString();
    }
}
