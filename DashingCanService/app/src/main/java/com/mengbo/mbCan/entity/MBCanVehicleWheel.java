package com.mengbo.mbCan.entity;

/* loaded from: classes.dex */
public class MBCanVehicleWheel {
    private int nLHFPulseCounter;
    private int nLHRPulseCounter;
    private int nRHFPulseCounter;
    private int nRHRPulseCounter;

    public MBCanVehicleWheel(int i, int i2, int i3, int i4) {
        this.nLHFPulseCounter = i;
        this.nRHFPulseCounter = i2;
        this.nLHRPulseCounter = i3;
        this.nRHRPulseCounter = i4;
    }

    public int getLHFPulseCounter() {
        return this.nLHFPulseCounter;
    }

    public int getRHFPulseCounter() {
        return this.nRHFPulseCounter;
    }

    public int getLHRPulseCounter() {
        return this.nLHRPulseCounter;
    }

    public int getRHRPulseCounter() {
        return this.nRHRPulseCounter;
    }

    public String toString() {
        StringBuffer stringBuffer = new StringBuffer("MBCanVehicleWheel{");
        stringBuffer.append("\nnLHFPulseCounter=");
        stringBuffer.append(this.nLHFPulseCounter);
        stringBuffer.append("\nnRHFPulseCounter=");
        stringBuffer.append(this.nRHFPulseCounter);
        stringBuffer.append("\nnLHRPulseCounter=");
        stringBuffer.append(this.nLHRPulseCounter);
        stringBuffer.append("\nnRHRPulseCounter=");
        stringBuffer.append(this.nRHRPulseCounter);
        stringBuffer.append("\n}");
        return stringBuffer.toString();
    }
}
