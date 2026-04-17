package com.mengbo.mbCan.entity;

/* loaded from: classes.dex */
public class MBCanCmdReply {
    private int nMsgId;
    private byte nReply;

    public MBCanCmdReply(int i, byte b) {
        this.nMsgId = i;
        this.nReply = b;
    }

    public int getMsgId() {
        return this.nMsgId;
    }

    public byte getReply() {
        return this.nReply;
    }

    public String toString() {
        StringBuffer stringBuffer = new StringBuffer("MBCanCmdReply{");
        stringBuffer.append("\nnMsgId=");
        stringBuffer.append(this.nMsgId);
        stringBuffer.append("\nnReply=");
        stringBuffer.append((int) this.nReply);
        stringBuffer.append("\n}");
        return stringBuffer.toString();
    }
}
