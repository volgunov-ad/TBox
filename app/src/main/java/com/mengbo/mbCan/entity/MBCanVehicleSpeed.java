package com.mengbo.mbCan.entity;

/* loaded from: classes.dex */
public class MBCanVehicleSpeed {
    private float fLateraAccaleleration;
    private float fSpeed;
    private float fYAS_2_LongitudinalAcceleration;
    private byte nGear;
    private byte nGearValidSts;
    private byte nPowerReadySts;
    private byte nSpeedValidSts;

    public float getfSpeed() {
        return this.fSpeed;
    }

    public void setfSpeed(float f) {
        this.fSpeed = f;
    }

    public byte getnGear() {
        return this.nGear;
    }

    public void setnGear(byte b) {
        this.nGear = b;
    }

    public byte getnSpeedValidSts() {
        return this.nSpeedValidSts;
    }

    public void setnSpeedValidSts(byte b) {
        this.nSpeedValidSts = b;
    }

    public byte getnGearValidSts() {
        return this.nGearValidSts;
    }

    public void setnGearValidSts(byte b) {
        this.nGearValidSts = b;
    }

    public byte getnPowerReadySts() {
        return this.nPowerReadySts;
    }

    public void setnPowerReadySts(byte b) {
        this.nPowerReadySts = b;
    }

    public void setfYAS_2_LongitudinalAcceleration(float f) {
        this.fYAS_2_LongitudinalAcceleration = f;
    }

    public float getfLateraAccaleleration() {
        return this.fLateraAccaleleration;
    }

    public void setfLateraAccaleleration(float f) {
        this.fLateraAccaleleration = f;
    }

    public MBCanVehicleSpeed(float f, byte b, byte b2, byte b3, byte b4, float f2, float f3) {
        this.fSpeed = f;
        this.nGear = b;
        this.nSpeedValidSts = b2;
        this.nGearValidSts = b3;
        this.nPowerReadySts = b4;
        this.fYAS_2_LongitudinalAcceleration = f2;
        this.fLateraAccaleleration = f3;
    }

    public float getSpeed() {
        return this.fSpeed;
    }

    public byte getGear() {
        return this.nGear;
    }

    public float getfYAS_2_LongitudinalAcceleration() {
        return this.fYAS_2_LongitudinalAcceleration;
    }

    public byte getSpeedValidSts() {
        return this.nSpeedValidSts;
    }

    public byte getGearValidSts() {
        return this.nGearValidSts;
    }

    public byte getPowerReadySts() {
        return this.nPowerReadySts;
    }

    public String toString() {
        StringBuffer stringBuffer = new StringBuffer("MBCanVehicleSpeed {");
        stringBuffer.append("\nСкорость=");
        stringBuffer.append(this.fSpeed);
        stringBuffer.append("\nПередача=");
        stringBuffer.append((int) this.nGear);
        stringBuffer.append("\nДостоверность скорости=");
        stringBuffer.append((int) this.nSpeedValidSts);
        stringBuffer.append("\nДостоверность передачи=");
        stringBuffer.append((int) this.nGearValidSts);
        stringBuffer.append("\nГотовность силовой установки=");
        stringBuffer.append((int) this.nPowerReadySts);
        stringBuffer.append("\nПродольное ускорение=");
        stringBuffer.append(this.fYAS_2_LongitudinalAcceleration);
        stringBuffer.append("\nПоперечное ускорение=");
        stringBuffer.append(this.fLateraAccaleleration);
        stringBuffer.append("\n}");
        return stringBuffer.toString();
    }
}
