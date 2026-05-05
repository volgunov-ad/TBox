package com.mengbo.mbclient.entity;

/* loaded from: classes.dex */
public class TBoxInfoEntity {
    private String certId;
    private String firmwareVer;
    private String hardnNum;
    private String iccId;
    private String sofetVer;
    private String softNum;
    private String tuId;

    public String getFirmwareVer() {
        return this.firmwareVer;
    }

    public void setFirmwareVer(String str) {
        this.firmwareVer = str;
    }

    public String getSofetVer() {
        return this.sofetVer;
    }

    public void setSofetVer(String str) {
        this.sofetVer = str;
    }

    public String getSoftNum() {
        return this.softNum;
    }

    public void setSoftNum(String str) {
        this.softNum = str;
    }

    public String getHardnNum() {
        return this.hardnNum;
    }

    public void setHardnNum(String str) {
        this.hardnNum = str;
    }

    public String getTuId() {
        return this.tuId;
    }

    public void setTuId(String str) {
        this.tuId = str;
    }

    public String getCertId() {
        return this.certId;
    }

    public void setCertId(String str) {
        this.certId = str;
    }

    public String getIccId() {
        return this.iccId;
    }

    public void setIccId(String str) {
        this.iccId = str;
    }
}
