package com.mengbo.mbCan.entity;

/* loaded from: classes.dex */
public class MBCanVehicleFrag {
    private byte nFRAG_1_LINResponseError;
    private byte nFRAG_BlowerMotorSt;
    private byte nFRAG_Error;
    private short nFRAG_FraganceBoxALoad;
    private short nFRAG_FraganceBoxBLoad;
    private short nFRAG_FraganceBoxCLoad;
    private byte nFRAG_FraganceTaste1RemanentRatio;
    private byte nFRAG_FraganceTaste2RemanentRatio;
    private byte nFRAG_FraganceTaste3RemanentRatio;
    private byte nFRAG_StepperMotorStepErr;
    private byte nFRAG_VoltageSt;
    private short nRev1;

    public MBCanVehicleFrag(byte b, byte b2, byte b3, byte b4, byte b5, byte b6, short s, short s2, short s3, byte b7, byte b8, short s4) {
        this.nFRAG_FraganceTaste1RemanentRatio = b;
        this.nFRAG_1_LINResponseError = b2;
        this.nFRAG_Error = b3;
        this.nFRAG_StepperMotorStepErr = b4;
        this.nFRAG_FraganceTaste2RemanentRatio = b5;
        this.nFRAG_FraganceTaste3RemanentRatio = b6;
        this.nFRAG_FraganceBoxALoad = s;
        this.nFRAG_FraganceBoxBLoad = s2;
        this.nFRAG_FraganceBoxCLoad = s3;
        this.nFRAG_BlowerMotorSt = b7;
        this.nFRAG_VoltageSt = b8;
        this.nRev1 = s4;
    }

    public byte getFRAG_FraganceTaste1RemanentRatio() {
        return this.nFRAG_FraganceTaste1RemanentRatio;
    }

    public byte getFRAG_1_LINResponseError() {
        return this.nFRAG_1_LINResponseError;
    }

    public byte getFRAG_Error() {
        return this.nFRAG_Error;
    }

    public byte getFRAG_StepperMotorStepErr() {
        return this.nFRAG_StepperMotorStepErr;
    }

    public byte getFRAG_FraganceTaste2RemanentRatio() {
        return this.nFRAG_FraganceTaste2RemanentRatio;
    }

    public byte getFRAG_FraganceTaste3RemanentRatio() {
        return this.nFRAG_FraganceTaste3RemanentRatio;
    }

    public short getFRAG_FraganceBoxALoad() {
        return this.nFRAG_FraganceBoxALoad;
    }

    public short getFRAG_FraganceBoxBLoad() {
        return this.nFRAG_FraganceBoxBLoad;
    }

    public short getFRAG_FraganceBoxCLoad() {
        return this.nFRAG_FraganceBoxCLoad;
    }

    public byte getFRAG_BlowerMotorSt() {
        return this.nFRAG_BlowerMotorSt;
    }

    public byte getFRAG_VoltageSt() {
        return this.nFRAG_VoltageSt;
    }

    public short getRev1() {
        return this.nRev1;
    }

    public String toString() {
        StringBuffer stringBuffer = new StringBuffer("香氛信息{");
        stringBuffer.append("\n香氛盒1 香料剩余量=");
        stringBuffer.append((int) this.nFRAG_FraganceTaste1RemanentRatio);
        stringBuffer.append("\nLIN响应错误=");
        stringBuffer.append((int) this.nFRAG_1_LINResponseError);
        stringBuffer.append("\n香氛故障状态=");
        stringBuffer.append((int) this.nFRAG_Error);
        stringBuffer.append("\n香氛故障=");
        stringBuffer.append((int) this.nFRAG_StepperMotorStepErr);
        stringBuffer.append("\n香氛盒2 香料剩余量=");
        stringBuffer.append((int) this.nFRAG_FraganceTaste2RemanentRatio);
        stringBuffer.append("\n香氛盒3 香料剩余量=");
        stringBuffer.append((int) this.nFRAG_FraganceTaste3RemanentRatio);
        stringBuffer.append("\n香氛1 香氛盒ID=");
        stringBuffer.append((int) this.nFRAG_FraganceBoxALoad);
        stringBuffer.append("\n香氛2 香氛盒ID=");
        stringBuffer.append((int) this.nFRAG_FraganceBoxBLoad);
        stringBuffer.append("\n香氛3 香氛盒ID=");
        stringBuffer.append((int) this.nFRAG_FraganceBoxCLoad);
        stringBuffer.append("\n香氛出风口状态=");
        stringBuffer.append((int) this.nFRAG_BlowerMotorSt);
        stringBuffer.append("\n电压状态=");
        stringBuffer.append((int) this.nFRAG_VoltageSt);
        stringBuffer.append("\n保留=");
        stringBuffer.append((int) this.nRev1);
        stringBuffer.append("\n}");
        return stringBuffer.toString();
    }
}
