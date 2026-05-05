package com.mengbo.mbCan.entity;

/* loaded from: classes.dex */
public class MBCanVehicleLkaSlaStatus {
    private byte nFCM_2_ADAS_TakeoverReq;
    private byte nFCM_2_Camera_Textinfo;
    private byte nFCM_2_HMA_Status;
    private byte nFCM_2_LDW_LKA_LeftVisualization;
    private byte nFCM_2_LDW_LKA_RightVisualization;
    private byte nFCM_2_LDW_LKA_Status;
    private byte nFCM_2_SLAOnOffsts;
    private byte nFCM_2_SLASpdlimit;
    private byte nFCM_2_SLASpdlimitWarning;
    private byte nFCM_2_SLAState;
    private byte nFCM_2_TJA_ICA_Mode;
    private byte nFCM_2_TJA_ICA_Textinfo;

    public MBCanVehicleLkaSlaStatus(byte b, byte b2, byte b3, byte b4, byte b5, byte b6, byte b7, byte b8, byte b9, byte b10, byte b11, byte b12) {
        this.nFCM_2_HMA_Status = b;
        this.nFCM_2_LDW_LKA_LeftVisualization = b2;
        this.nFCM_2_LDW_LKA_RightVisualization = b3;
        this.nFCM_2_LDW_LKA_Status = b4;
        this.nFCM_2_ADAS_TakeoverReq = b5;
        this.nFCM_2_SLAOnOffsts = b6;
        this.nFCM_2_SLAState = b7;
        this.nFCM_2_SLASpdlimit = b8;
        this.nFCM_2_SLASpdlimitWarning = b9;
        this.nFCM_2_Camera_Textinfo = b10;
        this.nFCM_2_TJA_ICA_Textinfo = b11;
        this.nFCM_2_TJA_ICA_Mode = b12;
    }

    public byte getFCM_2_HMA_Status() {
        return this.nFCM_2_HMA_Status;
    }

    public byte getFCM_2_LDW_LKA_LeftVisualization() {
        return this.nFCM_2_LDW_LKA_LeftVisualization;
    }

    public byte getFCM_2_LDW_LKA_RightVisualization() {
        return this.nFCM_2_LDW_LKA_RightVisualization;
    }

    public byte getFCM_2_LDW_LKA_Status() {
        return this.nFCM_2_LDW_LKA_Status;
    }

    public byte getFCM_2_ADAS_TakeoverReq() {
        return this.nFCM_2_ADAS_TakeoverReq;
    }

    public byte getFCM_2_SLAOnOffsts() {
        return this.nFCM_2_SLAOnOffsts;
    }

    public byte getFCM_2_SLAState() {
        return this.nFCM_2_SLAState;
    }

    public byte getFCM_2_SLASpdlimit() {
        return this.nFCM_2_SLASpdlimit;
    }

    public byte getFCM_2_SLASpdlimitWarning() {
        return this.nFCM_2_SLASpdlimitWarning;
    }

    public byte getFCM_2_Camera_Textinfo() {
        return this.nFCM_2_Camera_Textinfo;
    }

    public byte getFCM_2_TJA_ICA_Textinfo() {
        return this.nFCM_2_TJA_ICA_Textinfo;
    }

    public byte getFCM_2_TJA_ICA_Mode() {
        return this.nFCM_2_TJA_ICA_Mode;
    }

    public String toString() {
        StringBuffer stringBuffer = new StringBuffer("MBCanVehicleAqsStatus{");
        stringBuffer.append("\n自适应远光灯状态=");
        stringBuffer.append((int) this.nFCM_2_HMA_Status);
        stringBuffer.append("\n左侧车道线捕捉状态=");
        stringBuffer.append((int) this.nFCM_2_LDW_LKA_LeftVisualization);
        stringBuffer.append("\n右侧车道线捕捉状态=");
        stringBuffer.append((int) this.nFCM_2_LDW_LKA_RightVisualization);
        stringBuffer.append("\n车道偏离预警_车道保持状态=");
        stringBuffer.append((int) this.nFCM_2_LDW_LKA_Status);
        stringBuffer.append("\nLDW_LKA模式下驾驶员脱手检测=");
        stringBuffer.append((int) this.nFCM_2_ADAS_TakeoverReq);
        stringBuffer.append("\n限速标识识别=");
        stringBuffer.append((int) this.nFCM_2_SLAOnOffsts);
        stringBuffer.append("\nSLA状态=");
        stringBuffer.append((int) this.nFCM_2_SLAState);
        stringBuffer.append("\nSLA限速设置=");
        stringBuffer.append((int) this.nFCM_2_SLASpdlimit);
        stringBuffer.append("\n限速报警=");
        stringBuffer.append((int) this.nFCM_2_SLASpdlimitWarning);
        stringBuffer.append("\nADAS摄像头信息反馈=");
        stringBuffer.append((int) this.nFCM_2_Camera_Textinfo);
        stringBuffer.append("\n交通拥堵辅助—自适应巡航文字提示=");
        stringBuffer.append((int) this.nFCM_2_TJA_ICA_Textinfo);
        stringBuffer.append("\n交通拥堵辅助—自适应巡航模式=");
        stringBuffer.append((int) this.nFCM_2_TJA_ICA_Mode);
        stringBuffer.append("\n}");
        return stringBuffer.toString();
    }
}
