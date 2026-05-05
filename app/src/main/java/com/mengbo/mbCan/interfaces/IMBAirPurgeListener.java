package com.mengbo.mbCan.interfaces;

import com.mengbo.mbCan.entity.MBCanPM25;
import com.mengbo.mbCan.entity.MBCanVehicleAqsStatus;
import com.mengbo.mbCan.entity.MBCanVehicleBcmStatus;
import com.mengbo.mbCan.entity.MBCanVehicleFrag;

/* loaded from: classes.dex */
public interface IMBAirPurgeListener {
    void onCanVehicleAqsStatus(MBCanVehicleAqsStatus mBCanVehicleAqsStatus);

    void onPMChanged(MBCanPM25 mBCanPM25);

    void onVehicleBcmStatusChange(MBCanVehicleBcmStatus mBCanVehicleBcmStatus);

    void onVehicleLkaFrag(MBCanVehicleFrag mBCanVehicleFrag);
}
