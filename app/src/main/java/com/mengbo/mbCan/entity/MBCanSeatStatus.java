package com.mengbo.mbCan.entity;

/* loaded from: classes.dex */
public class MBCanSeatStatus {
    private byte bSetSeat;
    private short nRev;
    private byte nStatus;

    public MBCanSeatStatus(byte b, byte b2, short s) {
        this.nStatus = b;
        this.bSetSeat = b2;
        this.nRev = s;
    }

    public byte getSeatStatus() {
        return this.nStatus;
    }

    public byte getSetSeat() {
        return this.bSetSeat;
    }

    public short getRev() {
        return this.nRev;
    }

    public String toString() {
        StringBuffer stringBuffer = new StringBuffer("MBCanSeatStatus{");
        stringBuffer.append("\nnStatus=");
        stringBuffer.append((int) this.nStatus);
        stringBuffer.append("\nbSetSeat=");
        stringBuffer.append((int) this.bSetSeat);
        stringBuffer.append("\nnRev=");
        stringBuffer.append((int) this.nRev);
        stringBuffer.append("\n}");
        return stringBuffer.toString();
    }
}
