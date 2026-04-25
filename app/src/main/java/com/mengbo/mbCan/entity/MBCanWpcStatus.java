package com.mengbo.mbCan.entity;

/* loaded from: classes.dex */
public class MBCanWpcStatus {
    private float fWPC_Electricity;
    private byte nChargSts;
    private byte nEnter_super_fast_charge;
    private byte nNFCFastConnectBMSts;
    private byte nNFCFastConnectBTSts;
    private byte nNFCSts;
    private short nRev;
    private byte nWCM_ForeignBodyDetectedWaring;
    private byte nWPC_PhoneDetection_Status;

    public MBCanWpcStatus(byte b, byte b2, byte b3, byte b4, byte b5, byte b6, short s, float f, byte b7) {
        this.nNFCSts = b;
        this.nChargSts = b2;
        this.nNFCFastConnectBMSts = b3;
        this.nNFCFastConnectBTSts = b4;
        this.nWPC_PhoneDetection_Status = b5;
        this.nEnter_super_fast_charge = b6;
        this.nRev = s;
        this.fWPC_Electricity = f;
        this.nWCM_ForeignBodyDetectedWaring = b7;
    }

    public byte getnWCM_ForeignBodyDetectedWaring() {
        return this.nWCM_ForeignBodyDetectedWaring;
    }

    public byte getNFCStatus() {
        return this.nNFCSts;
    }

    public byte getChargStatus() {
        return this.nChargSts;
    }

    public byte getNFCFastConnectBMSts() {
        return this.nNFCFastConnectBMSts;
    }

    public byte getNFCFastConnectBTSts() {
        return this.nNFCFastConnectBTSts;
    }

    public byte getWPC_PhoneDetection_Status() {
        return this.nWPC_PhoneDetection_Status;
    }

    public byte getEnter_super_fast_charge() {
        return this.nEnter_super_fast_charge;
    }

    public short getRev() {
        return this.nRev;
    }

    public float getWPC_Electricity() {
        return this.fWPC_Electricity;
    }

    public String toString() {
        return "MBCanWpcStatus{nNFCSts=" + ((int) this.nNFCSts) + ", nChargSts=" + ((int) this.nChargSts) + ", nNFCFastConnectBMSts=" + ((int) this.nNFCFastConnectBMSts) + ", nNFCFastConnectBTSts=" + ((int) this.nNFCFastConnectBTSts) + ", nWPC_PhoneDetection_Status=" + ((int) this.nWPC_PhoneDetection_Status) + ", nEnter_super_fast_charge=" + ((int) this.nEnter_super_fast_charge) + ", nRev=" + ((int) this.nRev) + ", fWPC_Electricity=" + this.fWPC_Electricity + ", nWCM_ForeignBodyDetectedWaring=" + ((int) this.nWCM_ForeignBodyDetectedWaring) + '}';
    }
}
