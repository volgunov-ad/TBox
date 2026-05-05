package com.mengbo.mbCan.interfaces;

import com.mengbo.mbCan.entity.MBCanEpsStatus;
import com.mengbo.mbCan.entity.MBCanVehicleInverter;

/* loaded from: classes.dex */
public interface IMbCanS51AutoWashCallBack {
    void onCanEpsStatus(MBCanEpsStatus mBCanEpsStatus);

    void onVehicleInverterStatus(MBCanVehicleInverter mBCanVehicleInverter);
}
