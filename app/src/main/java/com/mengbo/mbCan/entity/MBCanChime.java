package com.mengbo.mbCan.entity;

/* loaded from: classes.dex */
public class MBCanChime {
    private byte nICM_2_ACC_Kilometre_Mile;
    private byte nICM_2_DriverSeatBeltWarningSts;
    private byte nICM_2_PassengerSeatBeltWarningSts;
    private byte nICM_2_RLSeatBeltWarningSts;
    private byte nICM_2_RMSeatBeltWarningSts;
    private byte nICM_2_RRSeatBeltWarningSts;
    private short nRev1;
    private int nType;

    public MBCanChime(int i, byte b, byte b2, byte b3, byte b4, byte b5, byte b6, short s) {
        this.nType = i;
        this.nICM_2_DriverSeatBeltWarningSts = b;
        this.nICM_2_PassengerSeatBeltWarningSts = b2;
        this.nICM_2_RLSeatBeltWarningSts = b3;
        this.nICM_2_RRSeatBeltWarningSts = b4;
        this.nICM_2_RMSeatBeltWarningSts = b5;
        this.nICM_2_ACC_Kilometre_Mile = b6;
        this.nRev1 = s;
    }

    public int getType() {
        return this.nType;
    }

    public byte getICM_2_DriverSeatBeltWarningSts() {
        return this.nICM_2_DriverSeatBeltWarningSts;
    }

    public byte getICM_2_PassengerSeatBeltWarningSts() {
        return this.nICM_2_PassengerSeatBeltWarningSts;
    }

    public byte getICM_2_RLSeatBeltWarningSts() {
        return this.nICM_2_RLSeatBeltWarningSts;
    }

    public byte getICM_2_RRSeatBeltWarningSts() {
        return this.nICM_2_RRSeatBeltWarningSts;
    }

    public byte getICM_2_RMSeatBeltWarningSts() {
        return this.nICM_2_RMSeatBeltWarningSts;
    }

    public byte getICM_2_ACC_Kilometre_Mile() {
        return this.nICM_2_ACC_Kilometre_Mile;
    }

    public short getRev1() {
        return this.nRev1;
    }

    public String toString() {
        StringBuffer stringBuffer = new StringBuffer("MBCanChime{");
        stringBuffer.append("\nnType=");
        stringBuffer.append(this.nType);
        stringBuffer.append("\n驾驶员安全带状态=");
        stringBuffer.append((int) this.nICM_2_DriverSeatBeltWarningSts);
        stringBuffer.append("\n副驾安全带状态=");
        stringBuffer.append((int) this.nICM_2_PassengerSeatBeltWarningSts);
        stringBuffer.append("\n后左座椅安全带报警=");
        stringBuffer.append((int) this.nICM_2_RLSeatBeltWarningSts);
        stringBuffer.append("\n后右座椅安全带报警=");
        stringBuffer.append((int) this.nICM_2_RRSeatBeltWarningSts);
        stringBuffer.append("\n后中座椅安全带报警=");
        stringBuffer.append((int) this.nICM_2_RMSeatBeltWarningSts);
        stringBuffer.append("\n公里or英里=");
        stringBuffer.append((int) this.nICM_2_ACC_Kilometre_Mile);
        stringBuffer.append("\n保留 =");
        stringBuffer.append((int) this.nRev1);
        stringBuffer.append("\n}");
        return stringBuffer.toString();
    }
}
