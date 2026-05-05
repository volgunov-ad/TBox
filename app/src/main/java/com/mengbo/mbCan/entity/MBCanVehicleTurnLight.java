package com.mengbo.mbCan.entity;

/* loaded from: classes.dex */
public class MBCanVehicleTurnLight {
    private byte nLeftLightState;
    private byte nRightLightState;

    public MBCanVehicleTurnLight(byte b, byte b2) {
        this.nLeftLightState = b;
        this.nRightLightState = b2;
    }

    public byte getLeftLightState() {
        return this.nLeftLightState;
    }

    public byte getRightLightState() {
        return this.nRightLightState;
    }

    public String toString() {
        StringBuffer stringBuffer = new StringBuffer("MBCanVehicleTurnLight{");
        stringBuffer.append("\nnLeftLightState=");
        stringBuffer.append((int) this.nLeftLightState);
        stringBuffer.append("\nnRightLightState=");
        stringBuffer.append((int) this.nRightLightState);
        stringBuffer.append("\n}");
        return stringBuffer.toString();
    }
}
