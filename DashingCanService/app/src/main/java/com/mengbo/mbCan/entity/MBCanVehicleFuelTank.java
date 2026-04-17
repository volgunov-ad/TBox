package com.mengbo.mbCan.entity;

/* loaded from: classes.dex */
public class MBCanVehicleFuelTank {
    byte nLidSts;
    byte nLidSystemFailureSts;
    byte nPressureConditionReached;

    public MBCanVehicleFuelTank(byte b, byte b2, byte b3) {
        this.nLidSts = b;
        this.nPressureConditionReached = b2;
        this.nLidSystemFailureSts = b3;
    }

    public byte getLidSts() {
        return this.nLidSts;
    }

    public byte getPressureConditionReached() {
        return this.nPressureConditionReached;
    }

    public byte getnLidSystemFailureSts() {
        return this.nLidSystemFailureSts;
    }

    public void setnLidSystemFailureSts(byte b) {
        this.nLidSystemFailureSts = b;
    }

    public String toString() {
        return "MBCanVehicleFuelTank{nLidSts=" + ((int) this.nLidSts) + ", nPressureConditionReached=" + ((int) this.nPressureConditionReached) + ", nLidSystemFailureSts=" + ((int) this.nLidSystemFailureSts) + '}';
    }
}
