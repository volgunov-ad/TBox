package com.mengbo.mbCan.entity;

/* loaded from: classes.dex */
public class MBCanVehicleExternalTemp {
    private byte nExternalTemperatureRaw;

    public MBCanVehicleExternalTemp(byte b) {
        this.nExternalTemperatureRaw = b;
    }

    public byte getExternalTemperatureRaw() {
        return this.nExternalTemperatureRaw;
    }

    public String toString() {
        StringBuffer stringBuffer = new StringBuffer("MBCanVehicleExternalTemp{");
        stringBuffer.append("\nnExternalTemperatureRaw=");
        stringBuffer.append((int) this.nExternalTemperatureRaw);
        stringBuffer.append("\n}");
        return stringBuffer.toString();
    }
}
