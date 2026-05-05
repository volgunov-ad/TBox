package com.mengbo.mbCan.entity;

/* loaded from: classes.dex */
public class MBCanChargingReserve {
    private byte nMode;
    private byte nStartHour;
    private byte nStartMinute;
    private byte nStopHour;
    private byte nStopMinute;

    public MBCanChargingReserve(byte b, byte b2, byte b3, byte b4, byte b5) {
        this.nMode = b;
        this.nStartHour = b2;
        this.nStartMinute = b3;
        this.nStopHour = b4;
        this.nStopMinute = b5;
    }

    public byte getnMode() {
        return this.nMode;
    }

    public void setnMode(byte b) {
        this.nMode = b;
    }

    public byte getnStartHour() {
        return this.nStartHour;
    }

    public void setnStartHour(byte b) {
        this.nStartHour = b;
    }

    public byte getnStartMinute() {
        return this.nStartMinute;
    }

    public void setnStartMinute(byte b) {
        this.nStartMinute = b;
    }

    public byte getnStopHour() {
        return this.nStopHour;
    }

    public void setnStopHour(byte b) {
        this.nStopHour = b;
    }

    public byte getnStopMinute() {
        return this.nStopMinute;
    }

    public void setnStopMinute(byte b) {
        this.nStopMinute = b;
    }

    public String toString() {
        return "MBCanChargingReserve{nMode=" + ((int) this.nMode) + ", nStartHour=" + ((int) this.nStartHour) + ", nStartMinute=" + ((int) this.nStartMinute) + ", nStopHour=" + ((int) this.nStopHour) + ", nStopMinute=" + ((int) this.nStopMinute) + '}';
    }
}
