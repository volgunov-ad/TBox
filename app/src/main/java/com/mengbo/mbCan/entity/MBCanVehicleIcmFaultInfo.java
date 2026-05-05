package com.mengbo.mbCan.entity;

/* loaded from: classes.dex */
public class MBCanVehicleIcmFaultInfo {
    private byte nICM_Average_fuel_consumption;
    private byte nICM_BattFaultLampSts;
    private byte nICM_BrkPsd;
    private byte nICM_ChargeFault;
    private int nICM_Drivingtime;
    private byte nICM_Fuel;
    private byte nICM_FunctionSts;
    private byte nICM_HighTemperature;
    private byte nICM_HvSysFltStopReq;
    private byte nICM_LowSOC;
    private byte nICM_PHEVGBFaultstatus;
    private byte nICM_PackThermalRunawayLight;
    private byte nICM_PowerModeChangeFail;
    private byte nICM_RegenerateLevel;
    private byte nICM_SOCDisp;
    private byte nICM_Speeding;
    private byte nICM_SysFault;
    private float nICM_Trip1;
    private float nICM_Trip2;

    public MBCanVehicleIcmFaultInfo(float f, byte b, byte b2, byte b3, byte b4, byte b5, byte b6, float f2, byte b7, byte b8, byte b9, byte b10, int i, byte b11, byte b12, byte b13, byte b14, byte b15, byte b16) {
        this.nICM_Trip1 = f;
        this.nICM_PowerModeChangeFail = b;
        this.nICM_BrkPsd = b2;
        this.nICM_HvSysFltStopReq = b3;
        this.nICM_ChargeFault = b4;
        this.nICM_SysFault = b5;
        this.nICM_BattFaultLampSts = b6;
        this.nICM_Trip2 = f2;
        this.nICM_SOCDisp = b7;
        this.nICM_LowSOC = b8;
        this.nICM_RegenerateLevel = b9;
        this.nICM_PHEVGBFaultstatus = b10;
        this.nICM_Drivingtime = i;
        this.nICM_PackThermalRunawayLight = b11;
        this.nICM_FunctionSts = b12;
        this.nICM_Speeding = b13;
        this.nICM_HighTemperature = b14;
        this.nICM_Average_fuel_consumption = b15;
        this.nICM_Fuel = b16;
    }

    public float getICM_Trip1() {
        return this.nICM_Trip1;
    }

    public byte getICM_PowerModeChangeFail() {
        return this.nICM_PowerModeChangeFail;
    }

    public byte getICM_BrkPsd() {
        return this.nICM_BrkPsd;
    }

    public byte getICM_HvSysFltStopReq() {
        return this.nICM_HvSysFltStopReq;
    }

    public byte getICM_ChargeFault() {
        return this.nICM_ChargeFault;
    }

    public byte getICM_SysFault() {
        return this.nICM_SysFault;
    }

    public byte getICM_BattFaultLampSts() {
        return this.nICM_BattFaultLampSts;
    }

    public float getICM_Trip2() {
        return this.nICM_Trip2;
    }

    public byte getICM_SOCDisp() {
        return this.nICM_SOCDisp;
    }

    public byte getICM_LowSOC() {
        return this.nICM_LowSOC;
    }

    public byte getICM_RegenerateLevel() {
        return this.nICM_RegenerateLevel;
    }

    public byte getICM_PHEVGBFaultstatus() {
        return this.nICM_PHEVGBFaultstatus;
    }

    public int getICM_Drivingtime() {
        return this.nICM_Drivingtime;
    }

    public byte getICM_PackThermalRunawayLight() {
        return this.nICM_PackThermalRunawayLight;
    }

    public byte getICM_FunctionSts() {
        return this.nICM_FunctionSts;
    }

    public byte getICM_Speeding() {
        return this.nICM_Speeding;
    }

    public byte getICM_HighTemperature() {
        return this.nICM_HighTemperature;
    }

    public byte getICM_Average_fuel_consumption() {
        return this.nICM_Average_fuel_consumption;
    }

    public byte getICM_Fuel() {
        return this.nICM_Fuel;
    }

    public String toString() {
        StringBuffer stringBuffer = new StringBuffer("MBCanVehicleIcmFaultInfo{");
        stringBuffer.append("\n本次里程=");
        stringBuffer.append(this.nICM_Trip1);
        stringBuffer.append("\n文字提示“当前条件不满足，无法切换模式”关联=");
        stringBuffer.append((int) this.nICM_PowerModeChangeFail);
        stringBuffer.append("\n文字提示“请踩刹车”关联=");
        stringBuffer.append((int) this.nICM_BrkPsd);
        stringBuffer.append("\n文字提示“严重高压故障，请停在安全处并联系4S店”关联=");
        stringBuffer.append((int) this.nICM_HvSysFltStopReq);
        stringBuffer.append("\n文字提示“请检查充电系统”关联=");
        stringBuffer.append((int) this.nICM_ChargeFault);
        stringBuffer.append("\n文字提示“请检查电驱系统”关联=");
        stringBuffer.append((int) this.nICM_SysFault);
        stringBuffer.append("\n文字提示“请检查动力电池系统”关联=");
        stringBuffer.append((int) this.nICM_BattFaultLampSts);
        stringBuffer.append("\n里程2: 从加油开始到油用完=");
        stringBuffer.append(this.nICM_Trip2);
        stringBuffer.append("\n文字提示“动力电池严重亏电”关联=");
        stringBuffer.append((int) this.nICM_SOCDisp);
        stringBuffer.append("\nnICM_LowSOC=");
        stringBuffer.append((int) this.nICM_LowSOC);
        stringBuffer.append("\nnICM_RegenerateLevel=");
        stringBuffer.append((int) this.nICM_RegenerateLevel);
        stringBuffer.append("\nnICM_PHEVGBFaultstatus=");
        stringBuffer.append((int) this.nICM_PHEVGBFaultstatus);
        stringBuffer.append("\n行驶时间=");
        stringBuffer.append(this.nICM_Drivingtime);
        stringBuffer.append("\n文字提示“电池过热，请立即停车并远离车辆“=");
        stringBuffer.append((int) this.nICM_PackThermalRunawayLight);
        stringBuffer.append("\n文字提示“车辆行人提示音已关闭”=");
        stringBuffer.append((int) this.nICM_FunctionSts);
        stringBuffer.append("\n文字提示“您已超速，请注意安全”=");
        stringBuffer.append((int) this.nICM_Speeding);
        stringBuffer.append("\n文字提示“发动机水温高，请关闭发动机”=");
        stringBuffer.append((int) this.nICM_HighTemperature);
        stringBuffer.append("\n从拿到车开始计算的平均油耗=");
        stringBuffer.append((int) this.nICM_Average_fuel_consumption);
        stringBuffer.append("\nnICM_Fuel=");
        stringBuffer.append((int) this.nICM_Fuel);
        stringBuffer.append("\n}");
        return stringBuffer.toString();
    }
}
