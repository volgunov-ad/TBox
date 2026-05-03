package com.mengbo.mbCan.entity;

/* loaded from: classes.dex */
public class MBCanVehicleEngine {
    float fSpeed;
    float fTemperture;
    short nDisplayVehiceSpeed;
    short nFuelRollingCounter;
    byte nGear;
    short nIntakeAirPressure;
    byte nStatus;

    public MBCanVehicleEngine(float f, float f2, byte b, byte b2, short s, short s2, short s3) {
        this.fSpeed = f;
        this.fTemperture = f2;
        this.nStatus = b;
        this.nGear = b2;
        this.nFuelRollingCounter = s;
        this.nIntakeAirPressure = s2;
        this.nDisplayVehiceSpeed = s3;
    }

    public float getfSpeed() {
        return this.fSpeed;
    }

    public float getfTemperture() {
        return this.fTemperture;
    }

    public byte getStatus() {
        return this.nStatus;
    }

    public byte getGear() {
        return this.nGear;
    }

    public short getFuelRollingCounter() {
        return this.nFuelRollingCounter;
    }

    public short getIntakeAirPressure() {
        return this.nIntakeAirPressure;
    }

    public short getnDisplayVehiceSpeed() {
        return this.nDisplayVehiceSpeed;
    }

    public String toString() {
        return "MBCanVehicleEngine{fSpeed=" + this.fSpeed + ", fTemperture=" + this.fTemperture + ", nStatus=" + ((int) this.nStatus) + ", nGear=" + ((int) this.nGear) + ", nFuelRollingCounter=" + ((int) this.nFuelRollingCounter) + ", nIntakeAirPressure=" + ((int) this.nIntakeAirPressure) + ", nDisplayVehiceSpeed=" + ((int) this.nDisplayVehiceSpeed) + '}';
    }
}
