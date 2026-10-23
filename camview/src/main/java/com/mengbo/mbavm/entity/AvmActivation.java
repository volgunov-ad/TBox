package com.mengbo.mbavm.entity;

public class AvmActivation {
    public short nUUID_Len;
    public String szUUID_Number;
    public short nSerialNumber_Len;
    public String szSerialNumber;
    public short nVerificationCode_Len;
    public String szVerificationCode;

    public AvmActivation(
            short nUUID_Len,
            String szUUID_Number,
            short nSerialNumber_Len,
            String szSerialNumber,
            short nVerificationCode_Len,
            String szVerificationCode
    ) {
        this.nUUID_Len = nUUID_Len;
        this.szUUID_Number = szUUID_Number;
        this.nSerialNumber_Len = nSerialNumber_Len;
        this.szSerialNumber = szSerialNumber;
        this.nVerificationCode_Len = nVerificationCode_Len;
        this.szVerificationCode = szVerificationCode;
    }
}
