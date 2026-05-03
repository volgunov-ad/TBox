package com.mengbo.mbCan.interfaces;

import com.mengbo.mbCan.entity.MBCanVehicleEbsSoc;
import com.mengbo.mbCan.entity.MBCanVehicleFuelLevel;

/* loaded from: classes.dex */
public interface IMbCanVehicleCarControlCallback {
    void onCanVehicleFuelLevel(MBCanVehicleFuelLevel mBCanVehicleFuelLevel);

    void onVehicleEbsSocChange(MBCanVehicleEbsSoc mBCanVehicleEbsSoc);
}
