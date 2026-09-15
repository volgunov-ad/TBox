# JNI push-поля из libmbCan.so

Имена полей push-структур (`GetFieldID`), извлечённые из `mbcan/libmbCan.so`.
Это **телеметрия subscribe/parseCanData**, не каталог `canGetVehicleParam(id)`.

Уникальных имён: **342**, групп: **198**.

См. [MBCAN_LIB_SO_REVERSE_RU.md](MBCAN_LIB_SO_REVERSE_RU.md), [MBCAN_OEM_FULL_CATALOG_RU.md](MBCAN_OEM_FULL_CATALOG_RU.md).

## `ICM` (62)

```
nICM_ABSFailSts
nICM_ACCmode
nICM_AVHAvailable
nICM_AVHWarningMessage
nICM_AirBagFailSts
nICM_Average_fuel_consumption
nICM_BattFaultLampSts
nICM_Brakefluid
nICM_BrkPsd
nICM_Cameratextinfo
nICM_ChargeFault
nICM_CruiseControlStsForDisplay
nICM_DistanceWarning
nICM_Drivingtime
nICM_EBDFailSts
nICM_EPBDispLargeslopeparking
nICM_EPBDispState
nICM_EPBDispWithoutBrake
nICM_EPSFailSts
nICM_ESPoffSwitchStatus
nICM_EngineOil
nICM_Fuel
nICM_FunctionSts
nICM_GBCFailSts
nICM_GBCInfoDisplay
nICM_GBFaultstatus
nICM_GSMWarninfo
nICM_HCUPTReady
nICM_HDCFailSts
nICM_HighTemperature
nICM_HvSysFltStopReq
nICM_LowSOC
nICM_LowSOC_LampSts
nICM_Maintenance
nICM_PHEVGBFaultstatus
nICM_PackThermalRunawayLight
nICM_ParkLightOnWarning
nICM_PowerModeChangeFail
nICM_RWUCDPActive
nICM_RecoverableFault
nICM_RegenerateLevel
nICM_SOCDisp
nICM_SmartSystemWarning
nICM_SmartSystemWarning1_0
nICM_SmartSystemWarning1_3
nICM_SmartSystemWarning2_1
nICM_SmartSystemWarning3_0
nICM_SmartSystemWarning3_1
nICM_SmartSystemWarning3_2
nICM_SmartSystemWarning4_0
nICM_SmartSystemWarning4_2
nICM_SmartSystemWarning4_3
nICM_SmartSystemWarning5_0
nICM_SmartSystemWarning5_1
nICM_Speeding
nICM_SysFault
nICM_TJAICATextinfo
nICM_Textinfo
nICM_TirePositionWarning
nICM_Trip1
nICM_Trip2
nICM_VDCTCSFailSts
```

## `BMSH` (15)

```
fBMSH_BattVolt
fBMSH_BatteryCurrent
fBMSH_PackRemainEnergy
fBMSH_SOCDisp
fBMSH_SingleChargeEnergy
fBMSH_SingleDisChargeEnergy
nBMSH_CellVoltMax
nBMSH_CellVoltMin
nBMSH_InsulationResis
nBMSH_LeftChargeTime
nBMSH_PackChargingThermalSt
nBMSH_PackPowerRealTime
nBMSH_SOH
nBMSH_TempSensor_MaxTemp
nBMSH_TempSensor_MinTemp
```

## `FCM_2` (12)

```
nFCM_2_ADAS_TakeoverReq
nFCM_2_Camera_Textinfo
nFCM_2_HMA_Status
nFCM_2_LDW_LKA_LeftVisualization
nFCM_2_LDW_LKA_RightVisualization
nFCM_2_LDW_LKA_Status
nFCM_2_SLAOnOffsts
nFCM_2_SLASpdlimit
nFCM_2_SLASpdlimitWarning
nFCM_2_SLAState
nFCM_2_TJA_ICA_Mode
nFCM_2_TJA_ICA_Textinfo
```

## `FRM_3` (12)

```
nFRM_3_ACCMode
nFRM_3_AEBMode
nFRM_3_DistanceWarning
nFRM_3_DxTarObj
nFRM_3_FCWMode
nFRM_3_FCW_PreWarning
nFRM_3_Obiect_Dx
nFRM_3_ObjValid
nFRM_3_TakeOverReq
nFRM_3_Textinfo
nFRM_3_TimeGapSet_ICM
nFRM_3_VSetDis
```

## `FRAG` (10)

```
nFRAG_BlowerMotorSt
nFRAG_Error
nFRAG_FraganceBoxALoad
nFRAG_FraganceBoxBLoad
nFRAG_FraganceBoxCLoad
nFRAG_FraganceTaste1RemanentRatio
nFRAG_FraganceTaste2RemanentRatio
nFRAG_FraganceTaste3RemanentRatio
nFRAG_StepperMotorStepErr
nFRAG_VoltageSt
```

## `Loudness` (7)

```
nLoudness_1000HZ
nLoudness_120HZ
nLoudness_1500HZ
nLoudness_2000HZ
nLoudness_250HZ
nLoudness_500HZ
nLoudness_6000HZ
```

## `AQS` (6)

```
nAQS_Air_quality
nAQS_COStatus
nAQS_NH3Status
nAQS_No2xStatu
nAQS_NoxStatus
nAQS_ResponseError
```

## `ICM_2` (6)

```
nICM_2_ACC_Kilometre_Mile
nICM_2_DriverSeatBeltWarningSts
nICM_2_PassengerSeatBeltWarningSts
nICM_2_RLSeatBeltWarningSts
nICM_2_RMSeatBeltWarningSts
nICM_2_RRSeatBeltWarningSts
```

## `IHU` (6)

```
nIHU_BookChgStartTimeSet_Hour
nIHU_BookChgStartTimeSet_Minute
nIHU_BookChgStopTimeSet_Hour
nIHU_BookchgStopTimeSet_Minute
nIHU_ChgModeSet
nIHU_Chg_SOC_LimitPointSet
```

## `ICM_6` (5)

```
nICM_6_AverageFuelConsume
nICM_6_Display
nICM_6_Drvingtime1
nICM_6_Maintenance_tips
nICM_6_Trip1
```

## `ICM_4` (4)

```
nICM_4_AverageFuelConsume
nICM_4_AverageVehicleSpeed
nICM_4_Brake_Fuel_Level
nICM_4_Engine_Oil_Pressure
```

## `BCM` (3)

```
nBCM_AutoBlowActiveReq
nBCM_AutoCleanActiveReq
nBCM_AutoWiperInhibitSts
```

## `EPB` (3)

```
nEPB_ActuatorSts
nEPB_AutoApplyDisableSts
nEPB_HMI_RemindReq
```

## `BMS` (2)

```
fBMS_BattCurrDisp
fBMS_BattVoltDisp
```

## `BMS_8` (2)

```
nBMS_8_CC2Sts
nBMS_8_PackDCChargingSt
```

## `HCU` (2)

```
nHCU_EnergyFlow
nHCU_RangeAval
```

## `Rev2` (2)

```
bRev2
nRev2
```

## `VCU` (2)

```
nVCU_ManualToGearP_InhibitSts
nVCU_SpdLimitSts
```

## `WPC` (2)

```
fWPC_Electricity
nWPC_PhoneDetection_Status
```

## `ACC` (1)

```
nACC_Cruise_Control
```

## `ACRequestCommand` (1)

```
nACRequestCommand
```

## `AEBdecActive` (1)

```
nAEBdecActive
```

## `AVH` (1)

```
nAVH_InhibitSts
```

## `AirIOutQLevel` (1)

```
nAirIOutQLevel
```

## `AirInQLevel` (1)

```
nAirInQLevel
```

## `AlarmMode` (1)

```
nAlarmMode
```

## `Angle` (1)

```
fAngle
```

## `AngleSpeed` (1)

```
fAngleSpeed
```

## `ApaSearchSts` (1)

```
nApaSearchSts
```

## `ApaSystemSts` (1)

```
nApaSystemSts
```

## `AudibleBeepRate` (1)

```
nAudibleBeepRate
```

## `AvgElecCns` (1)

```
fAvgElecCns
```

## `AvgEnergyCns` (1)

```
fAvgEnergyCns
```

## `AvgFuCns` (1)

```
fAvgFuCns
```

## `BlowSpeedLevel` (1)

```
nBlowSpeedLevel_Req
```

## `BookChgSts` (1)

```
nBookChgSts
```

## `Brake` (1)

```
nBrake_Level
```

## `BrakePedalSts` (1)

```
nBrakePedalSts
```

## `CHANNEL` (1)

```
nCHANNEL_REV_FRROM_MCU
```

## `ChargSts` (1)

```
nChargSts
```

## `ChargeSts` (1)

```
nChargeSts
```

## `ChgWireConnectStsDisp` (1)

```
nChgWireConnectStsDisp
```

## `CirculationMode` (1)

```
nCirculationMode_Req
```

## `ContinuousPhotographResult` (1)

```
nContinuousPhotographResult
```

## `CrashSts` (1)

```
nCrashSts
```

## `CruiseControlStatus` (1)

```
nCruiseControlStatus
```

## `DRLSts` (1)

```
nDRLSts
```

## `DirectionLightSts` (1)

```
nDirectionLightSts
```

## `DisplaySts` (1)

```
nDisplaySts
```

## `DisplayVehiceSpeed` (1)

```
nDisplayVehiceSpeed
```

## `DistenceToEmpty` (1)

```
fDistenceToEmpty
```

## `DoorOpenParkInhibitSts` (1)

```
nDoorOpenParkInhibitSts
```

## `DriverDoorSts` (1)

```
nDriverDoorSts
```

## `DriverMode` (1)

```
nDriverMode
```

## `DriverWarning` (1)

```
nDriverWarning
```

## `ECAver` (1)

```
fECAver
```

## `ECHis` (1)

```
fECHis
```

## `EPBParkLampSts` (1)

```
nEPBParkLampSts
```

## `Ebs` (1)

```
nEbs_Soc
```

## `EmergencyPowerOffRemindSts` (1)

```
nEmergencyPowerOffRemindSts
```

## `EngRunFuCns` (1)

```
fEngRunFuCns
```

## `Enter` (1)

```
nEnter_super_fast_charge
```

## `ExternalTemperatureRaw` (1)

```
nExternalTemperatureRaw
```

## `FLWindow` (1)

```
nFLWindow
```

## `FRAG_1` (1)

```
nFRAG_1_LINResponseError
```

## `FRWindow` (1)

```
nFRWindow
```

## `FactoryMode` (1)

```
nFactoryMode
```

## `FaultSts` (1)

```
nFaultSts
```

## `FeedbackSts` (1)

```
nFeedbackSts
```

## `FreDeviation` (1)

```
nFreDeviation
```

## `Frequency` (1)

```
nFrequency
```

## `FrontCameraSts` (1)

```
nFrontCameraSts
```

## `FrontFogLightSts` (1)

```
nFrontFogLightSts
```

## `FuelRollingCounter` (1)

```
nFuelRollingCounter
```

## `GSM` (1)

```
nGSM_GearShiftPos
```

## `GasPedalPosition` (1)

```
fGasPedalPosition
```

## `GasPedalPositionInvalidData` (1)

```
nGasPedalPositionInvalidData
```

## `Gear` (1)

```
nGear
```

## `GearValidSts` (1)

```
nGearValidSts
```

## `HDCFailSts` (1)

```
nHDCFailSts
```

## `HMA` (1)

```
nHMA_Status
```

## `HazardLightSts` (1)

```
nHazardLightSts
```

## `HighBeamSts` (1)

```
nHighBeamSts
```

## `HoodSts` (1)

```
nHoodSts
```

## `ICM_7` (1)

```
nICM_7_InfoDisplay
```

## `ISS` (1)

```
nISS_Sts
```

## `IgnSts` (1)

```
nIgnSts
```

## `InverterEnableSts` (1)

```
nInverterEnableSts
```

## `InverterFaultSts` (1)

```
nInverterFaultSts
```

## `InverterPower` (1)

```
fInverterPower
```

## `InverterTip` (1)

```
nInverterTip
```

## `Item` (1)

```
nItem
```

## `KeyRemindWarning` (1)

```
nKeyRemindWarning
```

## `L` (1)

```
nL_Set_Temperature
```

## `LHF` (1)

```
nLHF_Distance
```

## `LHFPulseCounter` (1)

```
nLHFPulseCounter
```

## `LHMF` (1)

```
nLHMF_Distance
```

## `LHMR` (1)

```
nLHMR_Distance
```

## `LHR` (1)

```
nLHR_Distance
```

## `LHRPulseCounter` (1)

```
nLHRPulseCounter
```

## `LHRdoorSts` (1)

```
nLHRdoorSts
```

## `LHSF` (1)

```
nLHSF_Distance
```

## `LHSR` (1)

```
nLHSR_Distance
```

## `LaserLightSts` (1)

```
nLaserLightSts
```

## `LateraAccaleleration` (1)

```
fLateraAccaleleration
```

## `LeftCameraSts` (1)

```
nLeftCameraSts
```

## `LeftLightState` (1)

```
nLeftLightState
```

## `LeftSts` (1)

```
nLeftSts
```

## `Level` (1)

```
nLevel
```

## `LidSts` (1)

```
nLidSts
```

## `LidSystemFailureSts` (1)

```
nLidSystemFailureSts
```

## `LowBeamSts` (1)

```
nLowBeamSts
```

## `MCU` (1)

```
nMCU_RECV_TOTAL
```

## `Mileage` (1)

```
fMileage
```

## `Mode` (1)

```
nMode
```

## `Modular` (1)

```
nModular
```

## `MsgId` (1)

```
nMsgId
```

## `MultiPath` (1)

```
nMultiPath
```

## `NFCFastConnectBMSts` (1)

```
nNFCFastConnectBMSts
```

## `NFCFastConnectBTSts` (1)

```
nNFCFastConnectBTSts
```

## `NFCSts` (1)

```
nNFCSts
```

## `Noise` (1)

```
nNoise
```

## `OBC` (1)

```
nOBC_CC_ConnectSts
```

## `PM25ErrSts` (1)

```
nPM25ErrSts
```

## `PM25Indensity` (1)

```
nPM25Indensity
```

## `PM25Sts` (1)

```
nPM25Sts
```

## `PM25outdensity` (1)

```
nPM25outdensity
```

## `ParkLightOnWarning` (1)

```
nParkLightOnWarning
```

## `ParkTailLightSts` (1)

```
nParkTailLightSts
```

## `PassengerWarning` (1)

```
nPassengerWarning
```

## `Person` (1)

```
nPerson_RemindSts
```

## `PhotographResult` (1)

```
nPhotographResult
```

## `PowerOffAutoParkInhibitSts` (1)

```
nPowerOffAutoParkInhibitSts
```

## `PowerReadySts` (1)

```
nPowerReadySts
```

## `PowerSts` (1)

```
nPowerSts
```

## `Pressure` (1)

```
fPressure
```

## `PressureConditionReached` (1)

```
nPressureConditionReached
```

## `PressureSystemSts` (1)

```
nPressureSystemSts
```

## `PsngrDoorSts` (1)

```
nPsngrDoorSts
```

## `R` (1)

```
nR_Set_Temperature
```

## `RCWWarning` (1)

```
nRCWWarning
```

## `RHF` (1)

```
nRHF_Distance
```

## `RHFPulseCounter` (1)

```
nRHFPulseCounter
```

## `RHMF` (1)

```
nRHMF_Distance
```

## `RHMR` (1)

```
nRHMR_Distance
```

## `RHR` (1)

```
nRHR_Distance
```

## `RHRDoorSts` (1)

```
nRHRDoorSts
```

## `RHRPulseCounter` (1)

```
nRHRPulseCounter
```

## `RHSF` (1)

```
nRHSF_Distance
```

## `RHSR` (1)

```
nRHSR_Distance
```

## `RLWindow` (1)

```
nRLWindow
```

## `RRWindow` (1)

```
nRRWindow
```

## `RadarDetectSts` (1)

```
nRadarDetectSts
```

## `RadarWorkSts` (1)

```
nRadarWorkSts
```

## `RadioType` (1)

```
nRadioType
```

## `RainDetectedSts` (1)

```
nRainDetectedSts
```

## `RearCameraSts` (1)

```
nRearCameraSts
```

## `RearDoorMoveDir` (1)

```
nRearDoorMoveDir
```

## `RearFogLightSts` (1)

```
nRearFogLightSts
```

## `Reply` (1)

```
nReply
```

## `Rev1` (1)

```
nRev1
```

## `ReverseGearSwitch` (1)

```
nReverseGearSwitch
```

## `RightCameraSts` (1)

```
nRightCameraSts
```

## `RightLightState` (1)

```
nRightLightState
```

## `RightSts` (1)

```
nRightSts
```

## `S` (1)

```
nS_AutoPanel
```

## `SDcardSts` (1)

```
nSDcardSts
```

## `SRF` (1)

```
nSRF_OpreateSts
```

## `SRFclosingConditions` (1)

```
nSRFclosingConditions
```

## `SRR_1` (1)

```
nSRR_1_SystemState
```

## `SetSeat` (1)

```
bSetSeat
```

## `Speed` (1)

```
fSpeed
```

## `SpeedValidSts` (1)

```
nSpeedValidSts
```

## `StartHour` (1)

```
nStartHour
```

## `StartMinute` (1)

```
nStartMinute
```

## `Status` (1)

```
nStatus
```

## `StopHour` (1)

```
nStopHour
```

## `StopMinute` (1)

```
nStopMinute
```

## `SumEgyCns` (1)

```
fSumEgyCns
```

## `SumElecCns` (1)

```
fSumElecCns
```

## `SumFuCns` (1)

```
fSumFuCns
```

## `SunRoof` (1)

```
nSunRoof
```

## `SunRoofAutoCloseSts` (1)

```
nSunRoofAutoCloseSts
```

## `SystemSts` (1)

```
nSystemSts
```

## `TBOX_3` (1)

```
nTBOX_3_BatteryPowerLimit
```

## `Temperature` (1)

```
nTemperature
```

## `Temperture` (1)

```
fTemperture
```

## `TirePressureWarningLampSts` (1)

```
nTirePressureWarningLampSts
```

## `TrunkSts` (1)

```
nTrunkSts
```

## `TurnSts` (1)

```
nTurnSts
```

## `Type` (1)

```
nType
```

## `Valid` (1)

```
nValid
```

## `Value` (1)

```
nValue
```

## `WCM` (1)

```
nWCM_ForeignBodyDetectedWaring
```

## `WarningBuzzerSts` (1)

```
nWarningBuzzerSts
```

## `WarningSts` (1)

```
nWarningSts
```

## `WiperSts` (1)

```
nWiperSts
```

## `WorkingReq` (1)

```
nWorkingReq
```

