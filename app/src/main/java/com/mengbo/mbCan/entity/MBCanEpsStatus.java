package com.mengbo.mbCan.entity;

/* loaded from: classes.dex */
public class MBCanEpsStatus {
    private byte nEPB_ActuatorSts;
    private byte nEPB_AutoApplyDisableSts;
    private byte nEPB_HMI_RemindReq;
    private byte nRev;

    public byte getnEPB_HMI_RemindReq() {
        return this.nEPB_HMI_RemindReq;
    }

    public byte getnEPB_ActuatorSts() {
        return this.nEPB_ActuatorSts;
    }

    public byte getnEPB_AutoApplyDisableSts() {
        return this.nEPB_AutoApplyDisableSts;
    }

    public byte getnRev() {
        return this.nRev;
    }

    public MBCanEpsStatus(byte b, byte b2, byte b3, byte b4) {
        this.nEPB_HMI_RemindReq = b;
        this.nEPB_ActuatorSts = b2;
        this.nEPB_AutoApplyDisableSts = b3;
        this.nRev = b4;
    }

    public String toString() {
        return "MBCanEpsStatus{nEPB_HMI_RemindReq=" + ((int) this.nEPB_HMI_RemindReq) + ", nEPB_ActuatorSts=" + ((int) this.nEPB_ActuatorSts) + ", nEPB_AutoApplyDisableSts=" + ((int) this.nEPB_AutoApplyDisableSts) + ", nRev=" + ((int) this.nRev) + '}';
    }
}
