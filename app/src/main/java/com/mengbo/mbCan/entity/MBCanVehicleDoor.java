package com.mengbo.mbCan.entity;

/* loaded from: classes.dex */
public class MBCanVehicleDoor {
    byte nDriverDoorLockSts;
    byte nDriverDoorSts;
    byte nHoodSts;
    byte nLHRdoorSts;
    byte nPsngrDoorSts;
    byte nRHRDoorSts;
    byte nSRF_OpreateSts;
    byte nTrunkSts;

    public MBCanVehicleDoor(byte b, byte b2, byte b3, byte b4, byte b5, byte b6, byte b7, byte b8) {
        this.nDriverDoorSts = b;
        this.nPsngrDoorSts = b2;
        this.nLHRdoorSts = b3;
        this.nRHRDoorSts = b4;
        this.nTrunkSts = b5;
        this.nHoodSts = b6;
        this.nSRF_OpreateSts = b7;
        this.nDriverDoorLockSts = b8;
    }

    public byte getDriverDoorSts() {
        return this.nDriverDoorSts;
    }

    public byte getPsngrDoorSts() {
        return this.nPsngrDoorSts;
    }

    public byte getLHRdoorSts() {
        return this.nLHRdoorSts;
    }

    public byte getRHRDoorSts() {
        return this.nRHRDoorSts;
    }

    public byte getTrunkSts() {
        return this.nTrunkSts;
    }

    public byte getHoodSts() {
        return this.nHoodSts;
    }

    public byte getSRF_OpreateSts() {
        return this.nSRF_OpreateSts;
    }

    public byte getDriverDoorLockSts() {
        return this.nDriverDoorLockSts;
    }

    public String toString() {
        StringBuffer stringBuffer = new StringBuffer("MBCanVehicleDoor{");
        stringBuffer.append("\nnDriverDoorSts=");
        stringBuffer.append((int) this.nDriverDoorSts);
        stringBuffer.append("\nnPsngrDoorSts=");
        stringBuffer.append((int) this.nPsngrDoorSts);
        stringBuffer.append("\nnLHRdoorSts=");
        stringBuffer.append((int) this.nLHRdoorSts);
        stringBuffer.append("\nnRHRDoorSts=");
        stringBuffer.append((int) this.nRHRDoorSts);
        stringBuffer.append("\nnTrunkSts=");
        stringBuffer.append((int) this.nTrunkSts);
        stringBuffer.append("\nnHoodSts=");
        stringBuffer.append((int) this.nHoodSts);
        stringBuffer.append("\nnSRF_OpreateSts=");
        stringBuffer.append((int) this.nSRF_OpreateSts);
        stringBuffer.append("\nnDriverDoorLockSts=");
        stringBuffer.append((int) this.nDriverDoorLockSts);
        stringBuffer.append("\n}");
        return stringBuffer.toString();
    }
}
