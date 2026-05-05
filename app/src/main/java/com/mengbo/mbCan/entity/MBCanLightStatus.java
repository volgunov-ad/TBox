package com.mengbo.mbCan.entity;

/* loaded from: classes.dex */
public class MBCanLightStatus {
    byte nDRLSts;
    byte nFrontFogLightSts;
    byte nHazardLightSts;
    byte nHighBeamSts;
    byte nLowBeamSts;
    byte nParkTailLightSts;
    byte nRearFogLightSts;
    byte nRev;

    public MBCanLightStatus(byte b, byte b2, byte b3, byte b4, byte b5, byte b6, byte b7, byte b8) {
        this.nHighBeamSts = b;
        this.nLowBeamSts = b2;
        this.nHazardLightSts = b3;
        this.nRearFogLightSts = b4;
        this.nFrontFogLightSts = b5;
        this.nParkTailLightSts = b6;
        this.nDRLSts = b7;
        this.nRev = b8;
    }

    public byte getHighBeamSts() {
        return this.nHighBeamSts;
    }

    public byte getLowBeamSts() {
        return this.nLowBeamSts;
    }

    public byte getHazardLightSts() {
        return this.nHazardLightSts;
    }

    public byte getRearFogLightSts() {
        return this.nRearFogLightSts;
    }

    public byte getFrontFogLightSts() {
        return this.nFrontFogLightSts;
    }

    public byte getParkTailLightSts() {
        return this.nParkTailLightSts;
    }

    public byte getDRLSts() {
        return this.nDRLSts;
    }

    public byte getRev() {
        return this.nRev;
    }

    public String toString() {
        StringBuffer stringBuffer = new StringBuffer("MBCanLightStatus{");
        stringBuffer.append("\nnHighBeamSts=");
        stringBuffer.append((int) this.nHighBeamSts);
        stringBuffer.append("\nnLowBeamSts=");
        stringBuffer.append((int) this.nLowBeamSts);
        stringBuffer.append("\nnHazardLightSts=");
        stringBuffer.append((int) this.nHazardLightSts);
        stringBuffer.append("\nnRearFogLightSts=");
        stringBuffer.append((int) this.nRearFogLightSts);
        stringBuffer.append("\nnFrontFogLightSts=");
        stringBuffer.append((int) this.nFrontFogLightSts);
        stringBuffer.append("\nnParkTailLightSts=");
        stringBuffer.append((int) this.nParkTailLightSts);
        stringBuffer.append("\nnDRLSts=");
        stringBuffer.append((int) this.nDRLSts);
        stringBuffer.append("\nnRev=");
        stringBuffer.append((int) this.nRev);
        stringBuffer.append("\n}");
        return stringBuffer.toString();
    }
}
