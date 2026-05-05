package com.mengbo.mbCan.entity;

/* loaded from: classes.dex */
public class MBCanDvrParam {
    private byte[] szDVR_Param;

    public MBCanDvrParam(byte[] bArr) {
        this.szDVR_Param = bArr;
    }

    public byte[] getData() {
        return this.szDVR_Param;
    }

    public String toString() {
        StringBuffer stringBuffer = new StringBuffer("DVR参数 {");
        stringBuffer.append("\nDVR参数=");
        byte[] bArr = this.szDVR_Param;
        stringBuffer.append(bArr != null ? bArr.length : 0);
        stringBuffer.append("\n}");
        return stringBuffer.toString();
    }
}
