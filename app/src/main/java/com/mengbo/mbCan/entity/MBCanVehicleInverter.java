package com.mengbo.mbCan.entity;

/* loaded from: classes.dex */
public class MBCanVehicleInverter {
    private float fInverterPower;
    private byte n220V_OutputTimeInd;
    private byte nAVH_InhibitSts;
    private byte nDoorOpenParkInhibitSts;
    private byte nInverterEnableSts;
    private byte nInverterFaultSts;
    private byte nInverterTip;
    private byte nPowerOffAutoParkInhibitSts;
    private byte nVCU_ManualToGearP_InhibitSts;

    public MBCanVehicleInverter(float f, byte b, byte b2, byte b3, byte b4, byte b5, byte b6, byte b7, byte b8) {
        this.fInverterPower = f;
        this.nInverterTip = b;
        this.nInverterFaultSts = b2;
        this.n220V_OutputTimeInd = b3;
        this.nPowerOffAutoParkInhibitSts = b4;
        this.nDoorOpenParkInhibitSts = b5;
        this.nAVH_InhibitSts = b6;
        this.nInverterEnableSts = b7;
        this.nVCU_ManualToGearP_InhibitSts = b8;
    }

    public float getInverterPower() {
        return this.fInverterPower;
    }

    public byte getInverterTip() {
        return this.nInverterTip;
    }

    public byte getInverterFaultSts() {
        return this.nInverterFaultSts;
    }

    public byte get220V_OutputTimeInd() {
        return this.n220V_OutputTimeInd;
    }

    public byte getPowerOffAutoParkInhibitSts() {
        return this.nPowerOffAutoParkInhibitSts;
    }

    public byte getDoorOpenParkInhibitSts() {
        return this.nDoorOpenParkInhibitSts;
    }

    public byte getAVH_InhibitSts() {
        return this.nAVH_InhibitSts;
    }

    public byte getInverterEnableSts() {
        return this.nInverterEnableSts;
    }

    public byte getnVCU_ManualToGearP_InhibitSts() {
        return this.nVCU_ManualToGearP_InhibitSts;
    }

    public String toString() {
        StringBuffer stringBuffer = new StringBuffer("逆变器 {");
        stringBuffer.append("\n逆变功率=");
        stringBuffer.append(this.fInverterPower);
        stringBuffer.append("\n220V逆变提示=");
        stringBuffer.append((int) this.nInverterTip);
        stringBuffer.append("\n220V逆变故障状态=");
        stringBuffer.append((int) this.nInverterFaultSts);
        stringBuffer.append("\nn220V_OutputTimeInd=");
        stringBuffer.append((int) this.n220V_OutputTimeInd);
        stringBuffer.append("\n熄火自动锁P档禁用状态=");
        stringBuffer.append((int) this.nPowerOffAutoParkInhibitSts);
        stringBuffer.append("\n开门驻车禁用状态=");
        stringBuffer.append((int) this.nDoorOpenParkInhibitSts);
        stringBuffer.append("\neAVH禁用状态=");
        stringBuffer.append((int) this.nAVH_InhibitSts);
        stringBuffer.append("\n逆变使能激活状态=");
        stringBuffer.append((int) this.nInverterEnableSts);
        stringBuffer.append("\n手动切换P档禁用状态=");
        stringBuffer.append((int) this.nVCU_ManualToGearP_InhibitSts);
        stringBuffer.append("\n}");
        return stringBuffer.toString();
    }
}
