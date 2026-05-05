package com.mengbo.mbCan.interfaces;

import com.mengbo.mbCan.entity.MBCanVehicleIcmTripInfo;
import com.mengbo.mbCan.entity.MBVehicleIcmAlarmInfo;

/* loaded from: classes.dex */
public interface IMbCanICMAlarmInfoCallback {
    void onAlarmInfo(MBVehicleIcmAlarmInfo mBVehicleIcmAlarmInfo);

    void onTripInfo(MBCanVehicleIcmTripInfo mBCanVehicleIcmTripInfo);
}
