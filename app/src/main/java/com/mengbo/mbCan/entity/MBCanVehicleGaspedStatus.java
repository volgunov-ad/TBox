package com.mengbo.mbCan.entity;

/* loaded from: classes.dex */
public class MBCanVehicleGaspedStatus {
    private float fGasPedalPosition;
    private byte nCruiseControlStatus;
    private byte nGasPedalPositionInvalidData;
    private byte nRev;
    private byte nVCU_SpdLimitSts;

    public MBCanVehicleGaspedStatus(float f, byte b, byte b2, byte b3, byte b4) {
        this.fGasPedalPosition = f;
        this.nGasPedalPositionInvalidData = b;
        this.nCruiseControlStatus = b2;
        this.nVCU_SpdLimitSts = b3;
        this.nRev = b4;
    }

    public float getfGasPedalPosition() {
        return this.fGasPedalPosition;
    }

    public byte getnGasPedalPositionInvalidData() {
        return this.nGasPedalPositionInvalidData;
    }

    public byte getnCruiseControlStatus() {
        return this.nCruiseControlStatus;
    }

    public byte getnVCU_SpdLimitSts() {
        return this.nVCU_SpdLimitSts;
    }

    public byte getnRev() {
        return this.nRev;
    }

    public String toString() {
        return "MBCanVehicleGaspedStatus{fGasPedalPosition=" + this.fGasPedalPosition + ", nGasPedalPositionInvalidData=" + ((int) this.nGasPedalPositionInvalidData) + ", nCruiseControlStatus=" + ((int) this.nCruiseControlStatus) + ", nVCU_SpdLimitSts=" + ((int) this.nVCU_SpdLimitSts) + ", nRev=" + ((int) this.nRev) + '}';
    }
}
