package com.mengbo.mbCan.interfaces;

/* loaded from: classes.dex */
public interface IMBVehicleListener {
    void onGear(double d, byte b);

    void onPull(int i, int i2, int i3, int i4);

    void onSpeed(double d, byte b);

    void onSteeringWheel(double d, double d2);

    void onVehicleTurnLightChange(byte b, byte b2);
}
