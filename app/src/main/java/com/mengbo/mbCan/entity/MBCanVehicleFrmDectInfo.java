package com.mengbo.mbCan.entity;

/* loaded from: classes.dex */
public class MBCanVehicleFrmDectInfo {
    private byte nFRM_3_ACCMode;
    private byte nFRM_3_AEBMode;
    private byte nFRM_3_DistanceWarning;
    private byte nFRM_3_DxTarObj;
    private byte nFRM_3_FCWMode;
    private byte nFRM_3_FCW_PreWarning;
    private byte nFRM_3_FrontObject_Type;
    private byte nFRM_3_Obiect_Dx;
    private byte nFRM_3_ObjValid;
    private byte nFRM_3_TakeOverReq;
    private byte nFRM_3_Textinfo;
    private byte nFRM_3_TimeGapSet_ICM;
    private byte nFRM_3_VSetDis;

    public MBCanVehicleFrmDectInfo(byte b, byte b2, byte b3, byte b4, byte b5, byte b6, byte b7, byte b8, byte b9, byte b10, byte b11, byte b12, byte b13) {
        this.nFRM_3_VSetDis = b;
        this.nFRM_3_DxTarObj = b2;
        this.nFRM_3_ObjValid = b3;
        this.nFRM_3_FrontObject_Type = b4;
        this.nFRM_3_Textinfo = b5;
        this.nFRM_3_TakeOverReq = b6;
        this.nFRM_3_ACCMode = b7;
        this.nFRM_3_Obiect_Dx = b8;
        this.nFRM_3_FCWMode = b9;
        this.nFRM_3_AEBMode = b10;
        this.nFRM_3_DistanceWarning = b11;
        this.nFRM_3_FCW_PreWarning = b12;
        this.nFRM_3_TimeGapSet_ICM = b13;
    }

    public byte getFRM_3_VSetDis() {
        return this.nFRM_3_VSetDis;
    }

    public byte getFRM_3_DxTarObj() {
        return this.nFRM_3_DxTarObj;
    }

    public byte getFRM_3_ObjValid() {
        return this.nFRM_3_ObjValid;
    }

    public byte getFRM_3_FrontObject_Type() {
        return this.nFRM_3_FrontObject_Type;
    }

    public byte getFRM_3_Textinfo() {
        return this.nFRM_3_Textinfo;
    }

    public byte getFRM_3_TakeOverReq() {
        return this.nFRM_3_TakeOverReq;
    }

    public byte getFRM_3_ACCMode() {
        return this.nFRM_3_ACCMode;
    }

    public byte getFRM_3_Obiect_Dx() {
        return this.nFRM_3_Obiect_Dx;
    }

    public byte getFRM_3_FCWMode() {
        return this.nFRM_3_FCWMode;
    }

    public byte getFRM_3_AEBMode() {
        return this.nFRM_3_AEBMode;
    }

    public byte getFRM_3_DistanceWarning() {
        return this.nFRM_3_DistanceWarning;
    }

    public byte getFRM_3_FCW_PreWarning() {
        return this.nFRM_3_FCW_PreWarning;
    }

    public byte getFRM_3_TimeGapSet_ICM() {
        return this.nFRM_3_TimeGapSet_ICM;
    }

    public String toString() {
        StringBuffer stringBuffer = new StringBuffer("MBCanVehicleFrmDectInfo{");
        stringBuffer.append("\n期望车速=");
        stringBuffer.append((int) this.nFRM_3_VSetDis);
        stringBuffer.append("\n与其他车距距离=");
        stringBuffer.append((int) this.nFRM_3_DxTarObj);
        stringBuffer.append("\n发现目标车辆=");
        stringBuffer.append((int) this.nFRM_3_ObjValid);
        stringBuffer.append("\n触发报警时刻的目标车辆类型=");
        stringBuffer.append((int) this.nFRM_3_FrontObject_Type);
        stringBuffer.append("\n文字提示=");
        stringBuffer.append((int) this.nFRM_3_Textinfo);
        stringBuffer.append("\n驾驶员接管请求=");
        stringBuffer.append((int) this.nFRM_3_TakeOverReq);
        stringBuffer.append("\nACC模式=");
        stringBuffer.append((int) this.nFRM_3_ACCMode);
        stringBuffer.append("\n目标车辆在X向的位置=");
        stringBuffer.append((int) this.nFRM_3_Obiect_Dx);
        stringBuffer.append("\nFDW模式=");
        stringBuffer.append((int) this.nFRM_3_FCWMode);
        stringBuffer.append("\nAEB模式=");
        stringBuffer.append((int) this.nFRM_3_AEBMode);
        stringBuffer.append("\nFDW报警=");
        stringBuffer.append((int) this.nFRM_3_DistanceWarning);
        stringBuffer.append("\nFDW预警=");
        stringBuffer.append((int) this.nFRM_3_FCW_PreWarning);
        stringBuffer.append("\n时距设置=");
        stringBuffer.append((int) this.nFRM_3_TimeGapSet_ICM);
        stringBuffer.append("\n}");
        return stringBuffer.toString();
    }
}
