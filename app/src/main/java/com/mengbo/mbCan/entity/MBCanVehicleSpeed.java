package com.mengbo.mbCan.entity;

/**
 * Populated by native code for {@code eMBCAN_VEHICLE_SPEED} (field names match Mengbo SDK).
 */
public class MBCanVehicleSpeed {

    private float fLateraAccaleleration;
    private float fSpeed;
    private float fYAS_2_LongitudinalAcceleration;
    private byte nGear;
    private byte nGearValidSts;
    private byte nPowerReadySts;
    private byte nSpeedValidSts;

    public MBCanVehicleSpeed(
            float fSpeed,
            byte nGear,
            byte nSpeedValidSts,
            byte nGearValidSts,
            byte nPowerReadySts,
            float fYAS_2_LongitudinalAcceleration,
            float fLateraAccaleleration
    ) {
        this.fSpeed = fSpeed;
        this.nGear = nGear;
        this.nSpeedValidSts = nSpeedValidSts;
        this.nGearValidSts = nGearValidSts;
        this.nPowerReadySts = nPowerReadySts;
        this.fYAS_2_LongitudinalAcceleration = fYAS_2_LongitudinalAcceleration;
        this.fLateraAccaleleration = fLateraAccaleleration;
    }

    public float getSpeed() {
        return fSpeed;
    }

    public byte getGear() {
        return nGear;
    }

    public byte getSpeedValidSts() {
        return nSpeedValidSts;
    }

    public byte getGearValidSts() {
        return nGearValidSts;
    }

    public byte getPowerReadySts() {
        return nPowerReadySts;
    }

    public float getLongitudinalAcceleration() {
        return fYAS_2_LongitudinalAcceleration;
    }

    public float getLateralAcceleration() {
        return fLateraAccaleleration;
    }
}
