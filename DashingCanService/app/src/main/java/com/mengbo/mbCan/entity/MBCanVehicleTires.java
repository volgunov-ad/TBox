package com.mengbo.mbCan.entity;

/* loaded from: classes.dex */
public class MBCanVehicleTires {
    byte nPressureSystemSts;
    byte nRev;
    byte nTirePressureWarningLampSts;
    byte nWarningBuzzerSts;
    MBCanTireInfo[] vstTire;

    public MBCanVehicleTires(byte b, byte b2, byte b3, byte b4, MBCanTireInfo[] mBCanTireInfoArr) {
        this.nPressureSystemSts = b;
        this.nWarningBuzzerSts = b2;
        this.nTirePressureWarningLampSts = b3;
        this.nRev = b4;
        this.vstTire = mBCanTireInfoArr;
    }

    public byte getPressureSystemSts() {
        return this.nPressureSystemSts;
    }

    public byte getWarningBuzzerSts() {
        return this.nWarningBuzzerSts;
    }

    public byte getTirePressureWarningLampSts() {
        return this.nTirePressureWarningLampSts;
    }

    public byte getRev() {
        return this.nRev;
    }

    public MBCanTireInfo[] getVstTire() {
        return this.vstTire;
    }

    public String toString() {
        StringBuffer stringBuffer = new StringBuffer("MBCanVehicleTires{");
        stringBuffer.append("\nnPressureSystemSts=");
        stringBuffer.append((int) this.nPressureSystemSts);
        stringBuffer.append("\nnWarningBuzzerSts=");
        stringBuffer.append((int) this.nWarningBuzzerSts);
        stringBuffer.append("\nnTirePressureWarningLampSts=");
        stringBuffer.append((int) this.nTirePressureWarningLampSts);
        stringBuffer.append("\nnRev=");
        stringBuffer.append((int) this.nRev);
        stringBuffer.append("\n}");
        return stringBuffer.toString();
    }
}
