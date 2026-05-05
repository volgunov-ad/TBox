package com.mengbo.mbCan.entity;

/* loaded from: classes.dex */
public class MBMusicCloundness {
    private byte nLoudness_1000HZ;
    private byte nLoudness_120HZ;
    private byte nLoudness_1500HZ;
    private byte nLoudness_2000HZ;
    private byte nLoudness_250HZ;
    private byte nLoudness_500HZ;
    private byte nLoudness_6000HZ;

    public MBMusicCloundness(byte b, byte b2, byte b3, byte b4, byte b5, byte b6, byte b7) {
        this.nLoudness_120HZ = b;
        this.nLoudness_250HZ = b2;
        this.nLoudness_500HZ = b3;
        this.nLoudness_1000HZ = b4;
        this.nLoudness_1500HZ = b5;
        this.nLoudness_2000HZ = b6;
        this.nLoudness_6000HZ = b7;
    }

    public byte getnLoudness_120HZ() {
        return this.nLoudness_120HZ;
    }

    public byte getnLoudness_250HZ() {
        return this.nLoudness_250HZ;
    }

    public byte getnLoudness_500HZ() {
        return this.nLoudness_500HZ;
    }

    public byte getnLoudness_1000HZ() {
        return this.nLoudness_1000HZ;
    }

    public byte getnLoudness_1500HZ() {
        return this.nLoudness_1500HZ;
    }

    public byte getnLoudness_2000HZ() {
        return this.nLoudness_2000HZ;
    }

    public byte getnLoudness_6000HZ() {
        return this.nLoudness_6000HZ;
    }

    public String toString() {
        StringBuffer stringBuffer = new StringBuffer("MBMusicCloundness{");
        stringBuffer.append("\nnLoudness_120HZ=");
        stringBuffer.append((int) this.nLoudness_120HZ);
        stringBuffer.append("\nnLoudness_250HZ=");
        stringBuffer.append((int) this.nLoudness_250HZ);
        stringBuffer.append("\nnLoudness_500HZ=");
        stringBuffer.append((int) this.nLoudness_500HZ);
        stringBuffer.append("\nnLoudness_1000HZ=");
        stringBuffer.append((int) this.nLoudness_1000HZ);
        stringBuffer.append("\nnLoudness_1500HZ=");
        stringBuffer.append((int) this.nLoudness_1500HZ);
        stringBuffer.append("\nnLoudness_2000HZ=");
        stringBuffer.append((int) this.nLoudness_2000HZ);
        stringBuffer.append("\nnLoudness_6000HZ=");
        stringBuffer.append((int) this.nLoudness_6000HZ);
        stringBuffer.append("\n}");
        return stringBuffer.toString();
    }
}
