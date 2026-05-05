package com.mengbo.mbCan.entity;

/* loaded from: classes.dex */
public class MBCanVehicleAqsStatus {
    private byte nAQS_Air_quality;
    private byte nAQS_COStatus;
    private byte nAQS_NH3Status;
    private byte nAQS_No2xStatu;
    private byte nAQS_NoxStatus;
    private byte nAQS_ResponseError;
    private short nRev;

    public MBCanVehicleAqsStatus(byte b, byte b2, byte b3, byte b4, byte b5, byte b6, short s) {
        this.nAQS_Air_quality = b;
        this.nAQS_ResponseError = b2;
        this.nAQS_NoxStatus = b3;
        this.nAQS_COStatus = b4;
        this.nAQS_NH3Status = b5;
        this.nAQS_No2xStatu = b6;
        this.nRev = s;
    }

    public byte getnAQS_Air_quality() {
        return this.nAQS_Air_quality;
    }

    public byte getnAQS_ResponseError() {
        return this.nAQS_ResponseError;
    }

    public byte getnAQS_NoxStatus() {
        return this.nAQS_NoxStatus;
    }

    public byte getnAQS_COStatus() {
        return this.nAQS_COStatus;
    }

    public byte getnAQS_NH3Status() {
        return this.nAQS_NH3Status;
    }

    public byte getnAQS_No2xStatu() {
        return this.nAQS_No2xStatu;
    }

    public short getnRev() {
        return this.nRev;
    }

    public String toString() {
        return "MBCanVehicleAqsStatus{nAQS_Air_quality=" + ((int) this.nAQS_Air_quality) + ", nAQS_ResponseError=" + ((int) this.nAQS_ResponseError) + ", nAQS_NoxStatus=" + ((int) this.nAQS_NoxStatus) + ", nAQS_COStatus=" + ((int) this.nAQS_COStatus) + ", nAQS_NH3Status=" + ((int) this.nAQS_NH3Status) + ", nAQS_No2xStatu=" + ((int) this.nAQS_No2xStatu) + ", nRev=" + ((int) this.nRev) + '}';
    }
}
