package com.mengbo.mbCan.entity;

/* loaded from: classes.dex */
public class MBCanBookChargeTime {
    private byte nIHU_BookChgStartTimeSet_Hour;
    private byte nIHU_BookChgStartTimeSet_Minute;
    private byte nIHU_BookChgStopTimeSet_Hour;
    private byte nIHU_BookchgStopTimeSet_Minute;
    private byte nIHU_ChgModeSet;
    private byte nIHU_Chg_SOC_LimitPointSet;

    public MBCanBookChargeTime() {
        this((byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0, (byte) 0);
    }

    public MBCanBookChargeTime(byte b, byte b2, byte b3, byte b4, byte b5, byte b6) {
        this.nIHU_BookChgStartTimeSet_Hour = b;
        this.nIHU_BookChgStartTimeSet_Minute = b2;
        this.nIHU_BookChgStopTimeSet_Hour = b3;
        this.nIHU_BookchgStopTimeSet_Minute = b4;
        this.nIHU_Chg_SOC_LimitPointSet = b5;
        this.nIHU_ChgModeSet = b6;
    }

    public byte getnIHU_BookChgStartTimeSet_Hour() {
        return this.nIHU_BookChgStartTimeSet_Hour;
    }

    public void setnIHU_BookChgStartTimeSet_Hour(byte b) {
        this.nIHU_BookChgStartTimeSet_Hour = b;
    }

    public byte getnIHU_BookChgStartTimeSet_Minute() {
        return this.nIHU_BookChgStartTimeSet_Minute;
    }

    public void setnIHU_BookChgStartTimeSet_Minute(byte b) {
        this.nIHU_BookChgStartTimeSet_Minute = b;
    }

    public byte getnIHU_BookChgStopTimeSet_Hour() {
        return this.nIHU_BookChgStopTimeSet_Hour;
    }

    public void setnIHU_BookChgStopTimeSet_Hour(byte b) {
        this.nIHU_BookChgStopTimeSet_Hour = b;
    }

    public byte getnIHU_BookchgStopTimeSet_Minute() {
        return this.nIHU_BookchgStopTimeSet_Minute;
    }

    public void setnIHU_BookchgStopTimeSet_Minute(byte b) {
        this.nIHU_BookchgStopTimeSet_Minute = b;
    }

    public byte getnIHU_Chg_SOC_LimitPointSet() {
        return this.nIHU_Chg_SOC_LimitPointSet;
    }

    public void setnIHU_Chg_SOC_LimitPointSet(byte b) {
        this.nIHU_Chg_SOC_LimitPointSet = b;
    }

    public byte getnIHU_ChgModeSet() {
        return this.nIHU_ChgModeSet;
    }

    public void setnIHU_ChgModeSet(byte b) {
        this.nIHU_ChgModeSet = b;
    }

    public String toString() {
        return "MBCanBookChargeTime{nIHU_BookChgStartTimeSet_Hour=" + ((int) this.nIHU_BookChgStartTimeSet_Hour) + ", nIHU_BookChgStartTimeSet_Minute=" + ((int) this.nIHU_BookChgStartTimeSet_Minute) + ", nIHU_BookChgStopTimeSet_Hour=" + ((int) this.nIHU_BookChgStopTimeSet_Hour) + ", nIHU_BookchgStopTimeSet_Minute=" + ((int) this.nIHU_BookchgStopTimeSet_Minute) + ", nIHU_Chg_SOC_LimitPointSet=" + ((int) this.nIHU_Chg_SOC_LimitPointSet) + ", nIHU_ChgModeSet=" + ((int) this.nIHU_ChgModeSet) + '}';
    }
}
