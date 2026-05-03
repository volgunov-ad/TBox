package com.mengbo.mbCan.entity;

/* loaded from: classes.dex */
public class MBCanVehicleBcmStatus {
    byte nACC_Cruise_Control;
    byte nAEBdecActive;
    byte nAlarmMode;
    byte nBCM_AutoBlowActiveReq;
    byte nBCM_AutoCleanActiveReq;
    byte nBCM_AutoWiperInhibitSts;
    byte nBrakePedalSts;
    byte nCrashSts;
    byte nDirectionLightSts;
    byte nDriverMode;
    byte nEPBParkLampSts;
    byte nGSM_GearShiftPos;
    byte nHDCFailSts;
    byte nHMA_Status;
    byte nISS_Sts;
    byte nKeyRemindWarning;
    byte nLaserLightSts;
    byte nParkLightOnWarning;
    byte nPerson_RemindSts;
    byte nRainDetectedSts;
    byte nRearDoorMoveDir;
    byte nReverseGearSwitch;
    byte nSRFclosingConditions;
    byte nS_AutoPanel;
    byte nSunRoof;
    byte nSunRoofAutoCloseSts;
    byte nTBOX_3_BatteryPowerLimit;
    byte nTurnSts;
    byte nWiperSts;
    MBCanVehicleDoor stDoorSts;
    MBCanLightStatus stLightSts;
    MBCanVehicleWindow stWindowSts;

    public MBCanVehicleBcmStatus(MBCanVehicleDoor mBCanVehicleDoor, MBCanLightStatus mBCanLightStatus, MBCanVehicleWindow mBCanVehicleWindow, byte b, byte b2, byte b3, byte b4, byte b5, byte b6, byte b7, byte b8, byte b9, byte b10, byte b11, byte b12, byte b13, byte b14, byte b15, byte b16, byte b17, byte b18, byte b19, byte b20, byte b21, byte b22, byte b23, byte b24, byte b25, byte b26, byte b27, byte b28, byte b29) {
        this.stDoorSts = mBCanVehicleDoor;
        this.stLightSts = mBCanLightStatus;
        this.stWindowSts = mBCanVehicleWindow;
        this.nReverseGearSwitch = b;
        this.nRainDetectedSts = b2;
        this.nWiperSts = b3;
        this.nDriverMode = b4;
        this.nBrakePedalSts = b5;
        this.nAlarmMode = b6;
        this.nCrashSts = b7;
        this.nTurnSts = b8;
        this.nEPBParkLampSts = b9;
        this.nSunRoof = b10;
        this.nRearDoorMoveDir = b11;
        this.nHDCFailSts = b12;
        this.nSunRoofAutoCloseSts = b13;
        this.nAEBdecActive = b14;
        this.nParkLightOnWarning = b15;
        this.nKeyRemindWarning = b16;
        this.nLaserLightSts = b17;
        this.nDirectionLightSts = b18;
        this.nS_AutoPanel = b19;
        this.nSRFclosingConditions = b20;
        this.nISS_Sts = b21;
        this.nPerson_RemindSts = b22;
        this.nHMA_Status = b23;
        this.nACC_Cruise_Control = b24;
        this.nGSM_GearShiftPos = b25;
        this.nTBOX_3_BatteryPowerLimit = b26;
        this.nBCM_AutoBlowActiveReq = b27;
        this.nBCM_AutoCleanActiveReq = b28;
        this.nBCM_AutoWiperInhibitSts = b29;
    }

    public MBCanVehicleDoor getDoorStatus() {
        return this.stDoorSts;
    }

    public MBCanLightStatus getLightStatus() {
        return this.stLightSts;
    }

    public MBCanVehicleWindow getVehicleWindow() {
        return this.stWindowSts;
    }

    public byte getReverseGearSwitch() {
        return this.nReverseGearSwitch;
    }

    public byte getRainDetectedSts() {
        return this.nRainDetectedSts;
    }

    public byte getWiperSts() {
        return this.nWiperSts;
    }

    public byte getDriverMode() {
        return this.nDriverMode;
    }

    public byte getBrakePedalSts() {
        return this.nBrakePedalSts;
    }

    public byte getAlarmMode() {
        return this.nAlarmMode;
    }

    public byte getCrashSts() {
        return this.nCrashSts;
    }

    public byte getTurnSts() {
        return this.nTurnSts;
    }

    public byte getEPBParkLampSts() {
        return this.nEPBParkLampSts;
    }

    public byte getSunRoof() {
        return this.nSunRoof;
    }

    public byte getRearDoorMoveDir() {
        return this.nRearDoorMoveDir;
    }

    public byte getHDCFailSts() {
        return this.nHDCFailSts;
    }

    public byte getAEBdecActive() {
        return this.nAEBdecActive;
    }

    public byte getParkLightOnWarning() {
        return this.nParkLightOnWarning;
    }

    public byte getKeyRemindWarning() {
        return this.nKeyRemindWarning;
    }

    public byte getLaserLightSts() {
        return this.nLaserLightSts;
    }

    public byte getDirectionLightSts() {
        return this.nDirectionLightSts;
    }

    public byte getS_AutoPanel() {
        return this.nS_AutoPanel;
    }

    public byte getSRFclosingConditions() {
        return this.nSRFclosingConditions;
    }

    public byte getISS_Sts() {
        return this.nISS_Sts;
    }

    public byte getPerson_RemindSts() {
        return this.nPerson_RemindSts;
    }

    public byte getHMA_Status() {
        return this.nHMA_Status;
    }

    public byte getACC_Cruise_Control() {
        return this.nACC_Cruise_Control;
    }

    public byte getGSM_GearShiftPos() {
        return this.nGSM_GearShiftPos;
    }

    public byte getnBCM_AutoWiperInhibitSts() {
        return this.nBCM_AutoWiperInhibitSts;
    }

    public byte getTBOX_3_BatteryPowerLimit() {
        return this.nTBOX_3_BatteryPowerLimit;
    }

    public byte getnBCM_AutoBlowActiveReq() {
        return this.nBCM_AutoBlowActiveReq;
    }

    public byte getnBCM_AutoCleanActiveReq() {
        return this.nBCM_AutoCleanActiveReq;
    }

    public String toString() {
        return "MBCanVehicleBcmStatus{stDoorSts=" + this.stDoorSts + ", stLightSts=" + this.stLightSts + ", stWindowSts=" + this.stWindowSts + ", nReverseGearSwitch=" + ((int) this.nReverseGearSwitch) + ", nRainDetectedSts=" + ((int) this.nRainDetectedSts) + ", nWiperSts=" + ((int) this.nWiperSts) + ", nDriverMode=" + ((int) this.nDriverMode) + ", nBrakePedalSts=" + ((int) this.nBrakePedalSts) + ", nAlarmMode=" + ((int) this.nAlarmMode) + ", nCrashSts=" + ((int) this.nCrashSts) + ", nTurnSts=" + ((int) this.nTurnSts) + ", nEPBParkLampSts=" + ((int) this.nEPBParkLampSts) + ", nSunRoof=" + ((int) this.nSunRoof) + ", nRearDoorMoveDir=" + ((int) this.nRearDoorMoveDir) + ", nHDCFailSts=" + ((int) this.nHDCFailSts) + ", nSunRoofAutoCloseSts=" + ((int) this.nSunRoofAutoCloseSts) + ", nAEBdecActive=" + ((int) this.nAEBdecActive) + ", nParkLightOnWarning=" + ((int) this.nParkLightOnWarning) + ", nKeyRemindWarning=" + ((int) this.nKeyRemindWarning) + ", nLaserLightSts=" + ((int) this.nLaserLightSts) + ", nDirectionLightSts=" + ((int) this.nDirectionLightSts) + ", nS_AutoPanel=" + ((int) this.nS_AutoPanel) + ", nSRFclosingConditions=" + ((int) this.nSRFclosingConditions) + ", nISS_Sts=" + ((int) this.nISS_Sts) + ", nPerson_RemindSts=" + ((int) this.nPerson_RemindSts) + ", nHMA_Status=" + ((int) this.nHMA_Status) + ", nACC_Cruise_Control=" + ((int) this.nACC_Cruise_Control) + ", nGSM_GearShiftPos=" + ((int) this.nGSM_GearShiftPos) + ", nTBOX_3_BatteryPowerLimit=" + ((int) this.nTBOX_3_BatteryPowerLimit) + ", nBCM_AutoBlowActiveReq=" + ((int) this.nBCM_AutoBlowActiveReq) + ", nBCM_AutoCleanActiveReq=" + ((int) this.nBCM_AutoCleanActiveReq) + ", nBCM_AutoWiperInhibitSts=" + ((int) this.nBCM_AutoWiperInhibitSts) + '}';
    }
}
