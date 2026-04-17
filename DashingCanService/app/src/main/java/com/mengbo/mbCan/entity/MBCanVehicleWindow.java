package com.mengbo.mbCan.entity;

/* loaded from: classes.dex */
public class MBCanVehicleWindow {
    byte nFLWindow;
    byte nFRWindow;
    byte nRLWindow;
    byte nRRWindow;

    /* loaded from: classes.dex */
    public static class VehicleWindowBuilder {
        private byte fRWindow = 0;
        private byte fLWindow = 0;
        private byte rRWindow = 0;
        private byte rLWindow = 0;

        public VehicleWindowBuilder fRWindow(byte b) {
            this.fRWindow = b;
            return this;
        }

        public VehicleWindowBuilder fLWindow(byte b) {
            this.fLWindow = b;
            return this;
        }

        public VehicleWindowBuilder rRWindow(byte b) {
            this.rRWindow = b;
            return this;
        }

        public VehicleWindowBuilder rLWindow(byte b) {
            this.rLWindow = b;
            return this;
        }

        public MBCanVehicleWindow build() {
            return new MBCanVehicleWindow(this);
        }
    }

    public MBCanVehicleWindow(VehicleWindowBuilder vehicleWindowBuilder) {
        this.nFRWindow = vehicleWindowBuilder.fRWindow;
        this.nFLWindow = vehicleWindowBuilder.fLWindow;
        this.nRRWindow = vehicleWindowBuilder.rRWindow;
        this.nRLWindow = vehicleWindowBuilder.rLWindow;
    }

    public MBCanVehicleWindow(byte b, byte b2, byte b3, byte b4) {
        this.nFRWindow = b;
        this.nFLWindow = b2;
        this.nRRWindow = b3;
        this.nRLWindow = b4;
    }

    public byte getFRWindow() {
        return this.nFRWindow;
    }

    public byte getFLWindow() {
        return this.nFLWindow;
    }

    public byte getRRWindow() {
        return this.nRRWindow;
    }

    public byte getRLWindow() {
        return this.nRLWindow;
    }

    public String toString() {
        StringBuffer stringBuffer = new StringBuffer("MBCanVehicleWindow{");
        stringBuffer.append("\nnFRWindow=");
        stringBuffer.append((int) this.nFRWindow);
        stringBuffer.append("\nnFLWindow=");
        stringBuffer.append((int) this.nFLWindow);
        stringBuffer.append("\nnRRWindow=");
        stringBuffer.append((int) this.nRRWindow);
        stringBuffer.append("\nnRLWindow=");
        stringBuffer.append((int) this.nRLWindow);
        stringBuffer.append("\n}");
        return stringBuffer.toString();
    }
}
