package com.mengbo.mbCan.entity;

/* loaded from: classes.dex */
public class MBCanTotalOdometer {
    private float fMileage;

    public MBCanTotalOdometer(float f) {
        this.fMileage = f;
    }

    public float getOdometer() {
        return this.fMileage;
    }

    public String toString() {
        StringBuffer stringBuffer = new StringBuffer("MBCanTotalOdometer{");
        stringBuffer.append("\nfMileage=");
        stringBuffer.append(this.fMileage);
        stringBuffer.append("\n}");
        return stringBuffer.toString();
    }
}
