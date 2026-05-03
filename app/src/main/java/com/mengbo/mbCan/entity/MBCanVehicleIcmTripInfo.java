package com.mengbo.mbCan.entity;

/* loaded from: classes.dex */
public class MBCanVehicleIcmTripInfo {
    float nICM_6_AverageFuelConsume;
    byte nICM_6_Display;
    short nICM_6_Drvingtime1;
    short nICM_6_Maintenance_tips;
    private short nICM_6_Trip1;

    public MBCanVehicleIcmTripInfo(short s, byte b, short s2, float f, short s3) {
        this.nICM_6_Trip1 = s;
        this.nICM_6_Display = b;
        this.nICM_6_Maintenance_tips = s2;
        this.nICM_6_AverageFuelConsume = f;
        this.nICM_6_Drvingtime1 = s3;
    }

    public short getICM_6_Trip1() {
        return this.nICM_6_Trip1;
    }

    public byte getICM_6_Display() {
        return this.nICM_6_Display;
    }

    public short getICM_6_Maintenance_tips() {
        return this.nICM_6_Maintenance_tips;
    }

    public float getnICM_6_AverageFuelConsume() {
        return this.nICM_6_AverageFuelConsume;
    }

    public short getICM_6_Drvingtime1() {
        return this.nICM_6_Drvingtime1;
    }

    public String toString() {
        StringBuffer stringBuffer = new StringBuffer("仪表里程信息信息{");
        stringBuffer.append("\n 从本次开始行车到停车为止=");
        stringBuffer.append((int) this.nICM_6_Trip1);
        stringBuffer.append("\n 报警信息=");
        stringBuffer.append((int) this.nICM_6_Display);
        stringBuffer.append("\n 保养提示=");
        stringBuffer.append((int) this.nICM_6_Maintenance_tips);
        stringBuffer.append("\n 平均油耗_1=");
        stringBuffer.append(this.nICM_6_AverageFuelConsume);
        stringBuffer.append("\n 小计里程1行驶时间=");
        stringBuffer.append((int) this.nICM_6_Drvingtime1);
        stringBuffer.append("\n}");
        return stringBuffer.toString();
    }
}
