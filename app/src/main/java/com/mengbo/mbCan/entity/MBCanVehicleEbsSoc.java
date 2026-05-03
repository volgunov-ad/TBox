package com.mengbo.mbCan.entity;

/* loaded from: classes.dex */
public class MBCanVehicleEbsSoc {
    private short bRev2;
    private float fBMSH_BattVolt;
    private float fBMSH_BatteryCurrent;
    private float fBMSH_PackRemainEnergy;
    private float fBMSH_SOCDisp;
    private float fBMSH_SingleChargeEnergy;
    private float fBMSH_SingleDisChargeEnergy;
    private float fBMS_BattCurrDisp;
    private float fBMS_BattVoltDisp;
    private short nBMSH_CellVoltMax;
    private short nBMSH_CellVoltMin;
    private int nBMSH_InsulationResis;
    private short nBMSH_LeftChargeTime;
    private byte nBMSH_PackChargingThermalSt;
    private int nBMSH_PackPowerRealTime;
    private byte nBMSH_SOH;
    private byte nBMSH_TempSensor_MaxTemp;
    private byte nBMSH_TempSensor_MinTemp;
    private byte nBMS_8_CC2Sts;
    private byte nBMS_8_PackDCChargingSt;
    private byte nBookChgSts;
    private byte nChargeSts;
    private byte nChgWireConnectStsDisp;
    private byte nEbs_Soc;
    private byte nEmergencyPowerOffRemindSts;
    private byte nHCU_EnergyFlow;
    private short nHCU_RangeAval;
    private byte nICM_LowSOC_LampSts;
    private byte nOBC_CC_ConnectSts;

    public MBCanVehicleEbsSoc(byte b, byte b2, byte b3, byte b4, byte b5, byte b6, short s, float f, float f2, float f3, float f4, int i, byte b7, byte b8, byte b9, byte b10, float f5, float f6, byte b11, byte b12, short s2, float f7, float f8, byte b13, byte b14, short s3, short s4, short s5, int i2) {
        this.nEbs_Soc = b;
        this.nEmergencyPowerOffRemindSts = b2;
        this.nChgWireConnectStsDisp = b3;
        this.nBookChgSts = b4;
        this.nICM_LowSOC_LampSts = b5;
        this.nHCU_EnergyFlow = b6;
        this.nHCU_RangeAval = s;
        this.fBMS_BattCurrDisp = f;
        this.fBMS_BattVoltDisp = f2;
        this.fBMSH_SOCDisp = f3;
        this.fBMSH_PackRemainEnergy = f4;
        this.nBMSH_PackPowerRealTime = i;
        this.nOBC_CC_ConnectSts = b7;
        this.nBMS_8_CC2Sts = b8;
        this.nBMS_8_PackDCChargingSt = b9;
        this.nBMSH_PackChargingThermalSt = b10;
        this.fBMSH_BatteryCurrent = f5;
        this.fBMSH_BattVolt = f6;
        this.nChargeSts = b11;
        this.nBMSH_SOH = b12;
        this.nBMSH_LeftChargeTime = s2;
        this.fBMSH_SingleChargeEnergy = f7;
        this.fBMSH_SingleDisChargeEnergy = f8;
        this.nBMSH_TempSensor_MaxTemp = b13;
        this.nBMSH_TempSensor_MinTemp = b14;
        this.nBMSH_CellVoltMax = s3;
        this.nBMSH_CellVoltMin = s4;
        this.bRev2 = s5;
        this.nBMSH_InsulationResis = i2;
    }

    public byte getnEbs_Soc() {
        return this.nEbs_Soc;
    }

    public void setnEbs_Soc(byte b) {
        this.nEbs_Soc = b;
    }

    public byte getnEmergencyPowerOffRemindSts() {
        return this.nEmergencyPowerOffRemindSts;
    }

    public void setnEmergencyPowerOffRemindSts(byte b) {
        this.nEmergencyPowerOffRemindSts = b;
    }

    public byte getnChgWireConnectStsDisp() {
        return this.nChgWireConnectStsDisp;
    }

    public void setnChgWireConnectStsDisp(byte b) {
        this.nChgWireConnectStsDisp = b;
    }

    public void setnBookChgSts(byte b) {
        this.nBookChgSts = b;
    }

    public byte getnBookChgSts() {
        return this.nBookChgSts;
    }

    public void setnICM_LowSOC_LampSts(byte b) {
        this.nICM_LowSOC_LampSts = b;
    }

    public byte getnHCU_EnergyFlow() {
        return this.nHCU_EnergyFlow;
    }

    public void setnHCU_EnergyFlow(byte b) {
        this.nHCU_EnergyFlow = b;
    }

    public short getnHCU_RangeAval() {
        return this.nHCU_RangeAval;
    }

    public void setnHCU_RangeAval(short s) {
        this.nHCU_RangeAval = s;
    }

    public void setfBMS_BattCurrDisp(float f) {
        this.fBMS_BattCurrDisp = f;
    }

    public void setfBMS_BattVoltDisp(float f) {
        this.fBMS_BattVoltDisp = f;
    }

    public float getfBMSH_SOCDisp() {
        return this.fBMSH_SOCDisp;
    }

    public void setfBMSH_SOCDisp(float f) {
        this.fBMSH_SOCDisp = f;
    }

    public float getfBMSH_PackRemainEnergy() {
        return this.fBMSH_PackRemainEnergy;
    }

    public void setfBMSH_PackRemainEnergy(float f) {
        this.fBMSH_PackRemainEnergy = f;
    }

    public int getnBMSH_PackPowerRealTime() {
        return this.nBMSH_PackPowerRealTime;
    }

    public void setnBMSH_PackPowerRealTime(int i) {
        this.nBMSH_PackPowerRealTime = i;
    }

    public byte getnOBC_CC_ConnectSts() {
        return this.nOBC_CC_ConnectSts;
    }

    public void setnOBC_CC_ConnectSts(byte b) {
        this.nOBC_CC_ConnectSts = b;
    }

    public byte getnBMS_8_CC2Sts() {
        return this.nBMS_8_CC2Sts;
    }

    public void setnBMS_8_CC2Sts(byte b) {
        this.nBMS_8_CC2Sts = b;
    }

    public byte getnBMS_8_PackDCChargingSt() {
        return this.nBMS_8_PackDCChargingSt;
    }

    public void setnBMS_8_PackDCChargingSt(byte b) {
        this.nBMS_8_PackDCChargingSt = b;
    }

    public byte getnBMSH_PackChargingThermalSt() {
        return this.nBMSH_PackChargingThermalSt;
    }

    public void setnBMSH_PackChargingThermalSt(byte b) {
        this.nBMSH_PackChargingThermalSt = b;
    }

    public float getfBMSH_BatteryCurrent() {
        return this.fBMSH_BatteryCurrent;
    }

    public void setfBMSH_BatteryCurrent(float f) {
        this.fBMSH_BatteryCurrent = f;
    }

    public float getfBMSH_BattVolt() {
        return this.fBMSH_BattVolt;
    }

    public void setfBMSH_BattVolt(float f) {
        this.fBMSH_BattVolt = f;
    }

    public byte getnChargeSts() {
        return this.nChargeSts;
    }

    public void setnChargeSts(byte b) {
        this.nChargeSts = b;
    }

    public byte getnBMSH_SOH() {
        return this.nBMSH_SOH;
    }

    public void setnBMSH_SOH(byte b) {
        this.nBMSH_SOH = b;
    }

    public short getnBMSH_LeftChargeTime() {
        return this.nBMSH_LeftChargeTime;
    }

    public void setnBMSH_LeftChargeTime(short s) {
        this.nBMSH_LeftChargeTime = s;
    }

    public float getfBMSH_SingleChargeEnergy() {
        return this.fBMSH_SingleChargeEnergy;
    }

    public void setfBMSH_SingleChargeEnergy(float f) {
        this.fBMSH_SingleChargeEnergy = f;
    }

    public float getfBMSH_SingleDisChargeEnergy() {
        return this.fBMSH_SingleDisChargeEnergy;
    }

    public void setfBMSH_SingleDisChargeEnergy(float f) {
        this.fBMSH_SingleDisChargeEnergy = f;
    }

    public byte getnBMSH_TempSensor_MaxTemp() {
        return this.nBMSH_TempSensor_MaxTemp;
    }

    public void setnBMSH_TempSensor_MaxTemp(byte b) {
        this.nBMSH_TempSensor_MaxTemp = b;
    }

    public byte getnBMSH_TempSensor_MinTemp() {
        return this.nBMSH_TempSensor_MinTemp;
    }

    public void setnBMSH_TempSensor_MinTemp(byte b) {
        this.nBMSH_TempSensor_MinTemp = b;
    }

    public short getnBMSH_CellVoltMax() {
        return this.nBMSH_CellVoltMax;
    }

    public void setnBMSH_CellVoltMax(short s) {
        this.nBMSH_CellVoltMax = s;
    }

    public short getnBMSH_CellVoltMin() {
        return this.nBMSH_CellVoltMin;
    }

    public void setnBMSH_CellVoltMin(short s) {
        this.nBMSH_CellVoltMin = s;
    }

    public short getbRev2() {
        return this.bRev2;
    }

    public void setbRev2(short s) {
        this.bRev2 = s;
    }

    public int getnBMSH_InsulationResis() {
        return this.nBMSH_InsulationResis;
    }

    public void setnBMSH_InsulationResis(int i) {
        this.nBMSH_InsulationResis = i;
    }

    public String toString() {
        return "MBCanVehicleEbsSoc{nEbs_Soc=" + ((int) this.nEbs_Soc) + ", nEmergencyPowerOffRemindSts=" + ((int) this.nEmergencyPowerOffRemindSts) + ", nChgWireConnectStsDisp=" + ((int) this.nChgWireConnectStsDisp) + ", nBookChgSts=" + ((int) this.nBookChgSts) + ", nICM_LowSOC_LampSts=" + ((int) this.nICM_LowSOC_LampSts) + ", nHCU_EnergyFlow=" + ((int) this.nHCU_EnergyFlow) + ", nHCU_RangeAval=" + ((int) this.nHCU_RangeAval) + ", fBMS_BattCurrDisp=" + this.fBMS_BattCurrDisp + ", fBMS_BattVoltDisp=" + this.fBMS_BattVoltDisp + ", fBMSH_SOCDisp=" + this.fBMSH_SOCDisp + ", fBMSH_PackRemainEnergy=" + this.fBMSH_PackRemainEnergy + ", nBMSH_PackPowerRealTime=" + this.nBMSH_PackPowerRealTime + ", nOBC_CC_ConnectSts=" + ((int) this.nOBC_CC_ConnectSts) + ", nBMS_8_CC2Sts=" + ((int) this.nBMS_8_CC2Sts) + ", nBMS_8_PackDCChargingSt=" + ((int) this.nBMS_8_PackDCChargingSt) + ", nBMSH_PackChargingThermalSt=" + ((int) this.nBMSH_PackChargingThermalSt) + ", fBMSH_BatteryCurrent=" + this.fBMSH_BatteryCurrent + ", fBMSH_BattVolt=" + this.fBMSH_BattVolt + ", nChargeSts=" + ((int) this.nChargeSts) + ", nBMSH_SOH=" + ((int) this.nBMSH_SOH) + ", nBMSH_LeftChargeTime=" + ((int) this.nBMSH_LeftChargeTime) + ", fBMSH_SingleChargeEnergy=" + this.fBMSH_SingleChargeEnergy + ", fBMSH_SingleDisChargeEnergy=" + this.fBMSH_SingleDisChargeEnergy + ", nBMSH_TempSensor_MaxTemp=" + ((int) this.nBMSH_TempSensor_MaxTemp) + ", nBMSH_TempSensor_MinTemp=" + ((int) this.nBMSH_TempSensor_MinTemp) + ", nBMSH_CellVoltMax=" + ((int) this.nBMSH_CellVoltMax) + ", nBMSH_CellVoltMin=" + ((int) this.nBMSH_CellVoltMin) + ", bRev2=" + ((int) this.bRev2) + ", nBMSH_InsulationResis=" + this.nBMSH_InsulationResis + '}';
    }
}
