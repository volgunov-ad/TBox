package com.mengbo.mbCan.interfaces;

import com.mengbo.mbCan.entity.MBCanVehicleEngine;
import com.mengbo.mbCan.entity.MBCanVehicleGaspedStatus;
import com.mengbo.mbCan.entity.MBCanVehicleSpeed;

/* loaded from: classes.dex */
public interface IMBCanTrackListener {
    void onCanVehicleSpeed(MBCanVehicleSpeed mBCanVehicleSpeed);

    void onVehicleEngineStatusChange(MBCanVehicleEngine mBCanVehicleEngine);

    void onVehicleGaspedStatus(MBCanVehicleGaspedStatus mBCanVehicleGaspedStatus);
}
