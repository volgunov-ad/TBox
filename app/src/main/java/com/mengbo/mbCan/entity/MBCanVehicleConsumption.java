package com.mengbo.mbCan.entity;

/* loaded from: classes.dex */
public class MBCanVehicleConsumption {
    private float fAvgElecCns;
    private float fAvgEnergyCns;
    private float fAvgFuCns;
    private float fECAver;
    private float fECHis;
    private float fEngRunFuCns;
    private float fSumEgyCns;
    private float fSumElecCns;
    private float fSumFuCns;

    public MBCanVehicleConsumption(float f, float f2, float f3, float f4, float f5, float f6, float f7, float f8, float f9) {
        this.fAvgFuCns = f;
        this.fAvgElecCns = f2;
        this.fAvgEnergyCns = f3;
        this.fSumEgyCns = f4;
        this.fEngRunFuCns = f5;
        this.fECAver = f6;
        this.fECHis = f7;
        this.fSumFuCns = f8;
        this.fSumElecCns = f9;
    }

    public float getfAvgFuCns() {
        return this.fAvgFuCns;
    }

    public void setfAvgFuCns(float f) {
        this.fAvgFuCns = f;
    }

    public float getfAvgElecCns() {
        return this.fAvgElecCns;
    }

    public void setfAvgElecCns(float f) {
        this.fAvgElecCns = f;
    }

    public float getfAvgEnergyCns() {
        return this.fAvgEnergyCns;
    }

    public void setfAvgEnergyCns(float f) {
        this.fAvgEnergyCns = f;
    }

    public float getfSumEgyCns() {
        return this.fSumEgyCns;
    }

    public void setfSumEgyCns(float f) {
        this.fSumEgyCns = f;
    }

    public float getfEngRunFuCns() {
        return this.fEngRunFuCns;
    }

    public void setfEngRunFuCns(float f) {
        this.fEngRunFuCns = f;
    }

    public float getfECAver() {
        return this.fECAver;
    }

    public void setfECAver(float f) {
        this.fECAver = f;
    }

    public float getfECHis() {
        return this.fECHis;
    }

    public void setfECHis(float f) {
        this.fECHis = f;
    }

    public float getfSumFuCns() {
        return this.fSumFuCns;
    }

    public void setfSumFuCns(float f) {
        this.fSumFuCns = f;
    }

    public float getfSumElecCns() {
        return this.fSumElecCns;
    }

    public void setfSumElecCns(float f) {
        this.fSumElecCns = f;
    }

    public String toString() {
        return "MBCanVehicleConsumption{fAvgFuCns=" + this.fAvgFuCns + ", fAvgElecCns=" + this.fAvgElecCns + ", fAvgEnergyCns=" + this.fAvgEnergyCns + ", fSumEgyCns=" + this.fSumEgyCns + ", fEngRunFuCns=" + this.fEngRunFuCns + ", fECAver=" + this.fECAver + ", fECHis=" + this.fECHis + ", fSumFuCns=" + this.fSumFuCns + ", fSumElecCns=" + this.fSumElecCns + '}';
    }
}
