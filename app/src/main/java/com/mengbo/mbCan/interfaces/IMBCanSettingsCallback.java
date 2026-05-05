package com.mengbo.mbCan.interfaces;

import com.mengbo.mbCan.entity.MBCanChargingReserve;
import com.mengbo.mbCan.entity.MBCanTotalOdometer;
import com.mengbo.mbCan.entity.MBCanVehicleAccStatus;
import com.mengbo.mbCan.entity.MBCanVehicleAqsStatus;
import com.mengbo.mbCan.entity.MBCanVehicleBcmStatus;
import com.mengbo.mbCan.entity.MBCanVehicleConsumption;
import com.mengbo.mbCan.entity.MBCanVehicleEbsSoc;
import com.mengbo.mbCan.entity.MBCanVehicleEngine;
import com.mengbo.mbCan.entity.MBCanVehicleFrag;
import com.mengbo.mbCan.entity.MBCanVehicleFuelLevel;
import com.mengbo.mbCan.entity.MBCanVehicleFuelTank;
import com.mengbo.mbCan.entity.MBCanVehicleIcmFaultInfo;
import com.mengbo.mbCan.entity.MBCanVehicleIcmInfo;
import com.mengbo.mbCan.entity.MBCanVehicleIcmTripInfo;
import com.mengbo.mbCan.entity.MBCanVehicleInverter;
import com.mengbo.mbCan.entity.MBCanVehicleLkaSlaStatus;
import com.mengbo.mbCan.entity.MBCanVehicleSpeed;
import com.mengbo.mbCan.entity.MBCanVehicleTires;
import com.mengbo.mbCan.entity.MBCanWpcStatus;

/* loaded from: classes.dex */
public interface IMBCanSettingsCallback {
    void onCanVehicleAqsStatus(MBCanVehicleAqsStatus mBCanVehicleAqsStatus);

    void onCanVehicleFuelLevel(MBCanVehicleFuelLevel mBCanVehicleFuelLevel);

    void onCanVehicleSpeed(MBCanVehicleSpeed mBCanVehicleSpeed);

    void onCanVehicleTires(MBCanVehicleTires mBCanVehicleTires);

    void onChargingReserveChange(MBCanChargingReserve mBCanChargingReserve);

    void onVehicleAccStatusChange(MBCanVehicleAccStatus mBCanVehicleAccStatus);

    void onVehicleBcmStatusChange(MBCanVehicleBcmStatus mBCanVehicleBcmStatus);

    void onVehicleConsumptionChange(MBCanVehicleConsumption mBCanVehicleConsumption);

    void onVehicleEbsSocChange(MBCanVehicleEbsSoc mBCanVehicleEbsSoc);

    void onVehicleEngineStatusChange(MBCanVehicleEngine mBCanVehicleEngine);

    void onVehicleFuelTank(MBCanVehicleFuelTank mBCanVehicleFuelTank);

    void onVehicleIcmFaultInfoChange(MBCanVehicleIcmFaultInfo mBCanVehicleIcmFaultInfo);

    void onVehicleIcmInfoChange(MBCanVehicleIcmInfo mBCanVehicleIcmInfo);

    void onVehicleIcmTripInfoChange(MBCanVehicleIcmTripInfo mBCanVehicleIcmTripInfo);

    void onVehicleInverterStatus(MBCanVehicleInverter mBCanVehicleInverter);

    void onVehicleLkaFrag(MBCanVehicleFrag mBCanVehicleFrag);

    void onVehicleLkaSlaStatus(MBCanVehicleLkaSlaStatus mBCanVehicleLkaSlaStatus);

    void onVehicleTotalOdoMeterChange(MBCanTotalOdometer mBCanTotalOdometer);

    void onWpcStatusChange(MBCanWpcStatus mBCanWpcStatus);
}
