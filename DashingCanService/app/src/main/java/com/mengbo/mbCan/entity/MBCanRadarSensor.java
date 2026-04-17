package com.mengbo.mbCan.entity;

/* loaded from: classes.dex */
public class MBCanRadarSensor {
    byte nAudibleBeepRate;
    int nLHF_Distance;
    int nLHMF_Distance;
    int nLHMR_Distance;
    int nLHR_Distance;
    int nLHSF_Distance;
    int nLHSR_Distance;
    int nRHF_Distance;
    int nRHMF_Distance;
    int nRHMR_Distance;
    int nRHR_Distance;
    int nRHSF_Distance;
    int nRHSR_Distance;
    byte nRadarDetectSts;
    byte nRadarWorkSts;
    byte nRev;

    public MBCanRadarSensor(int i, int i2, int i3, int i4, int i5, int i6, int i7, int i8, byte b, byte b2, byte b3, int i9, int i10, int i11, int i12, byte b4) {
        this.nRHR_Distance = i;
        this.nLHR_Distance = i2;
        this.nRHMR_Distance = i3;
        this.nLHMR_Distance = i4;
        this.nLHF_Distance = i5;
        this.nRHF_Distance = i6;
        this.nRHMF_Distance = i7;
        this.nLHMF_Distance = i8;
        this.nAudibleBeepRate = b;
        this.nRadarDetectSts = b2;
        this.nRadarWorkSts = b3;
        this.nRHSR_Distance = i9;
        this.nLHSR_Distance = i10;
        this.nRHSF_Distance = i11;
        this.nLHSF_Distance = i12;
        this.nRev = b4;
    }

    public int getRHR_Distance() {
        return this.nRHR_Distance;
    }

    public int getLHR_Distance() {
        return this.nLHR_Distance;
    }

    public int getRHMR_Distance() {
        return this.nRHMR_Distance;
    }

    public int getLHMR_Distance() {
        return this.nLHMR_Distance;
    }

    public int getLHF_Distance() {
        return this.nLHF_Distance;
    }

    public int getRHF_Distance() {
        return this.nRHF_Distance;
    }

    public int getRHMF_Distance() {
        return this.nRHMF_Distance;
    }

    public int getLHMF_Distance() {
        return this.nLHMF_Distance;
    }

    public byte getAudibleBeepRate() {
        return this.nAudibleBeepRate;
    }

    public byte getRadarDetectSts() {
        return this.nRadarDetectSts;
    }

    public byte getRadarWorkSts() {
        return this.nRadarWorkSts;
    }

    public int getRHSR_Distance() {
        return this.nRHSR_Distance;
    }

    public int getLHSR_Distance() {
        return this.nLHSR_Distance;
    }

    public int getRHSF_Distance() {
        return this.nRHSF_Distance;
    }

    public int getLHSF_Distance() {
        return this.nLHSF_Distance;
    }

    public byte getRev() {
        return this.nRev;
    }

    public String toString() {
        StringBuffer stringBuffer = new StringBuffer("MBCanRadarSensor{");
        stringBuffer.append("\nnRHR_Distance=");
        stringBuffer.append(this.nRHR_Distance);
        stringBuffer.append("\nnLHR_Distance=");
        stringBuffer.append(this.nLHR_Distance);
        stringBuffer.append("\nnRHMR_Distance=");
        stringBuffer.append(this.nRHMR_Distance);
        stringBuffer.append("\nnLHMR_Distance=");
        stringBuffer.append(this.nLHMR_Distance);
        stringBuffer.append("\nnLHF_Distance=");
        stringBuffer.append(this.nLHF_Distance);
        stringBuffer.append("\nnRHF_Distance=");
        stringBuffer.append(this.nRHF_Distance);
        stringBuffer.append("\nnRHMF_Distance=");
        stringBuffer.append(this.nRHMF_Distance);
        stringBuffer.append("\nnLHMF_Distance=");
        stringBuffer.append(this.nLHMF_Distance);
        stringBuffer.append("\nnAudibleBeepRate=");
        stringBuffer.append((int) this.nAudibleBeepRate);
        stringBuffer.append("\nnRadarDetectSts=");
        stringBuffer.append((int) this.nRadarDetectSts);
        stringBuffer.append("\nnRadarWorkSts=");
        stringBuffer.append((int) this.nRadarWorkSts);
        stringBuffer.append("\nnRHSR_Distance=");
        stringBuffer.append(this.nRHSR_Distance);
        stringBuffer.append("\nnLHSR_Distance=");
        stringBuffer.append(this.nLHSR_Distance);
        stringBuffer.append("\nnRHSF_Distance=");
        stringBuffer.append(this.nRHSF_Distance);
        stringBuffer.append("\nnLHSF_Distance=");
        stringBuffer.append(this.nLHSF_Distance);
        stringBuffer.append("\nnRev=");
        stringBuffer.append((int) this.nRev);
        stringBuffer.append("\n}");
        return stringBuffer.toString();
    }
}
