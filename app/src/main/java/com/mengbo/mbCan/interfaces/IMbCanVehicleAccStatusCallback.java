package com.mengbo.mbCan.interfaces;

import com.mengbo.mbCan.entity.MBCanDvrStatus;
import com.mengbo.mbCan.entity.MBCanVehicleBcmStatus;
import com.mengbo.mbCan.entity.MBCanVehicleEngine;
import com.mengbo.mbCan.entity.MBCanWpcStatus;

/* loaded from: classes.dex */
public interface IMbCanVehicleAccStatusCallback {
    void onDoorChange(int i, int i2);

    void onMcuUpdate(int i);

    void onTotalOdometerChange(double d);

    void onVehicleAccStatusChange(byte b, byte b2, byte b3);

    void onVehicleBcmStatusChange(MBCanVehicleBcmStatus mBCanVehicleBcmStatus);

    void onVehicleDVRStatusChange(MBCanDvrStatus mBCanDvrStatus);

    void onVehicleEngineStatusChange(MBCanVehicleEngine mBCanVehicleEngine);

    void onVehicleGearStatusChange(byte b);

    void onVehicleSeatStatusChange(int i);

    void onVehicleSystemModeChange(byte b);

    void onWpcStatusChange(MBCanWpcStatus mBCanWpcStatus);
}
