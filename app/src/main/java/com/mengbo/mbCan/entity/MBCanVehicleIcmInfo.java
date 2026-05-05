package com.mengbo.mbCan.entity;

/* loaded from: classes.dex */
public class MBCanVehicleIcmInfo {
    private float nICM_4_AverageFuelConsume;
    private int nICM_4_AverageVehicleSpeed;
    private byte nICM_4_Brake_Fuel_Level;
    private byte nICM_4_Engine_Oil_Pressure;

    public MBCanVehicleIcmInfo(byte b, byte b2, float f, int i) {
        this.nICM_4_Brake_Fuel_Level = b;
        this.nICM_4_Engine_Oil_Pressure = b2;
        this.nICM_4_AverageFuelConsume = f;
        this.nICM_4_AverageVehicleSpeed = i;
    }

    public byte getICM_4_Brake_Fuel_Level() {
        return this.nICM_4_Brake_Fuel_Level;
    }

    public byte getICM_4_Engine_Oil_Pressure() {
        return this.nICM_4_Engine_Oil_Pressure;
    }

    public float getICM_4_AverageFuelConsume() {
        return this.nICM_4_AverageFuelConsume;
    }

    public int getICM_4_AverageVehicleSpeed() {
        return this.nICM_4_AverageVehicleSpeed;
    }

    public String toString() {
        StringBuffer stringBuffer = new StringBuffer("MBCanVehicleIcmInfo{");
        stringBuffer.append("\n制动液位=");
        stringBuffer.append((int) this.nICM_4_Brake_Fuel_Level);
        stringBuffer.append("\n机油压力=");
        stringBuffer.append((int) this.nICM_4_Engine_Oil_Pressure);
        stringBuffer.append("\n平均油耗=");
        stringBuffer.append(this.nICM_4_AverageFuelConsume);
        stringBuffer.append("\n平均车速=");
        stringBuffer.append(this.nICM_4_AverageVehicleSpeed);
        stringBuffer.append("\n}");
        return stringBuffer.toString();
    }
}
