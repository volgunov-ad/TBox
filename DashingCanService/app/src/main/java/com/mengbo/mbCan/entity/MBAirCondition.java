package com.mengbo.mbCan.entity;

/* loaded from: classes.dex */
public class MBAirCondition {
    private byte nACRequestCommand;
    private byte nBlowSpeedLevel_Req;
    private byte nCirculationMode_Req;
    private byte nL_Set_Temperature;
    private byte nR_Set_Temperature;
    private byte nWorkingReq;

    public MBAirCondition(byte b, byte b2, byte b3, byte b4, byte b5, byte b6) {
        this.nBlowSpeedLevel_Req = b;
        this.nCirculationMode_Req = b2;
        this.nACRequestCommand = b3;
        this.nWorkingReq = b4;
        this.nL_Set_Temperature = b5;
        this.nR_Set_Temperature = b6;
    }

    public byte getBlowSpeedLevel_Req() {
        return this.nBlowSpeedLevel_Req;
    }

    public byte getCirculationMode_Req() {
        return this.nCirculationMode_Req;
    }

    public byte getACRequestCommand() {
        return this.nACRequestCommand;
    }

    public byte getWorkingReq() {
        return this.nWorkingReq;
    }

    public byte getL_Set_Temperature() {
        return this.nL_Set_Temperature;
    }

    public byte getR_Set_Temperature() {
        return this.nR_Set_Temperature;
    }

    public String toString() {
        StringBuffer stringBuffer = new StringBuffer("MBAirCondition{");
        stringBuffer.append("\nnBlowSpeedLevel_Req=");
        stringBuffer.append((int) this.nBlowSpeedLevel_Req);
        stringBuffer.append("\nnCirculationMode_Req=");
        stringBuffer.append((int) this.nCirculationMode_Req);
        stringBuffer.append("\nnACRequestCommand=");
        stringBuffer.append((int) this.nACRequestCommand);
        stringBuffer.append("\nnWorkingReq=");
        stringBuffer.append((int) this.nWorkingReq);
        stringBuffer.append("\nnL_Set_Temperature=");
        stringBuffer.append((int) this.nL_Set_Temperature);
        stringBuffer.append("\nnR_Set_Temperature=");
        stringBuffer.append((int) this.nR_Set_Temperature);
        stringBuffer.append("\n}");
        return stringBuffer.toString();
    }
}
