package com.mengbo.mbCan.entity;

/* loaded from: classes.dex */
public class MBCanVehicleSteeringAngle {
    private float fAngle;
    private float fAngleSpeed;

    public MBCanVehicleSteeringAngle(float f, float f2) {
        this.fAngle = f;
        this.fAngleSpeed = f2;
    }

    public float getSteeringAngle() {
        return this.fAngle;
    }

    public float getSteeringAngleSpeed() {
        return this.fAngleSpeed;
    }

    public String toString() {
        StringBuffer stringBuffer = new StringBuffer("MBCanVehicleSteeringAngle{");
        stringBuffer.append("\nfAngle=");
        stringBuffer.append(this.fAngle);
        stringBuffer.append("\nfAngleSpeed=");
        stringBuffer.append(this.fAngleSpeed);
        stringBuffer.append("\n}");
        return stringBuffer.toString();
    }
}
