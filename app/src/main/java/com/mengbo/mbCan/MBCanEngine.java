package com.mengbo.mbCan;

import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import com.mengbo.mbCan.defines.MBAudioProperty;
import com.mengbo.mbCan.defines.MBCanDataType;
import com.mengbo.mbCan.defines.MBCanDmsCommand;
import com.mengbo.mbCan.defines.MBVehicleProperty;
import com.mengbo.mbCan.entity.MBAirCondition;
import com.mengbo.mbCan.entity.MBCanAvmStatus;
import com.mengbo.mbCan.entity.MBCanBookChargeTime;
import com.mengbo.mbCan.entity.MBCanBsdAlarm;
import com.mengbo.mbCan.entity.MBCanCfgItem;
import com.mengbo.mbCan.entity.MBCanChargingReserve;
import com.mengbo.mbCan.entity.MBCanChime;
import com.mengbo.mbCan.entity.MBCanDowAlarm;
import com.mengbo.mbCan.entity.MBCanDvrParam;
import com.mengbo.mbCan.entity.MBCanDvrStatus;
import com.mengbo.mbCan.entity.MBCanEpsStatus;
import com.mengbo.mbCan.entity.MBCanPM25;
import com.mengbo.mbCan.entity.MBCanRadarSensor;
import com.mengbo.mbCan.entity.MBCanRadioFrequencyInfo;
import com.mengbo.mbCan.entity.MBCanRctaAlarm;
import com.mengbo.mbCan.entity.MBCanSeatBeltWarning;
import com.mengbo.mbCan.entity.MBCanSeatStatus;
import com.mengbo.mbCan.entity.MBCanSubscribeBase;
import com.mengbo.mbCan.entity.MBCanTotalOdometer;
import com.mengbo.mbCan.entity.MBCanVehicleAccStatus;
import com.mengbo.mbCan.entity.MBCanVehicleAqsStatus;
import com.mengbo.mbCan.entity.MBCanVehicleBcmStatus;
import com.mengbo.mbCan.entity.MBCanVehicleConsumption;
import com.mengbo.mbCan.entity.MBCanVehicleDoor;
import com.mengbo.mbCan.entity.MBCanVehicleEbsSoc;
import com.mengbo.mbCan.entity.MBCanVehicleEngine;
import com.mengbo.mbCan.entity.MBCanVehicleFrag;
import com.mengbo.mbCan.entity.MBCanVehicleFrmDectInfo;
import com.mengbo.mbCan.entity.MBCanVehicleFuelLevel;
import com.mengbo.mbCan.entity.MBCanVehicleFuelTank;
import com.mengbo.mbCan.entity.MBCanVehicleGaspedStatus;
import com.mengbo.mbCan.entity.MBCanVehicleIcmFaultInfo;
import com.mengbo.mbCan.entity.MBCanVehicleIcmInfo;
import com.mengbo.mbCan.entity.MBCanVehicleIcmTripInfo;
import com.mengbo.mbCan.entity.MBCanVehicleInverter;
import com.mengbo.mbCan.entity.MBCanVehicleLkaSlaStatus;
import com.mengbo.mbCan.entity.MBCanVehicleSpeed;
import com.mengbo.mbCan.entity.MBCanVehicleSteeringAngle;
import com.mengbo.mbCan.entity.MBCanVehicleTires;
import com.mengbo.mbCan.entity.MBCanVehicleTurnLight;
import com.mengbo.mbCan.entity.MBCanVehicleWheel;
import com.mengbo.mbCan.entity.MBCanVehicleWindow;
import com.mengbo.mbCan.entity.MBCanWpcStatus;
import com.mengbo.mbCan.entity.MBHardKey;
import com.mengbo.mbCan.entity.MBMusicCloundness;
import com.mengbo.mbCan.entity.MBRadioProgramState;
import com.mengbo.mbCan.entity.MBVehicleIcmAlarmInfo;
import com.mengbo.mbCan.interfaces.ICanBaseCallback;
import com.mengbo.mbCan.interfaces.IMBAirPurgeListener;
import com.mengbo.mbCan.interfaces.IMBCanChimeStatusCallback;
import com.mengbo.mbCan.interfaces.IMBCanSettingsCallback;
import com.mengbo.mbCan.interfaces.IMBCanTrackListener;
import com.mengbo.mbCan.interfaces.IMBCanVehicleFrmDectInfoCallback;
import com.mengbo.mbCan.interfaces.IMBCanVehicleGaspedStatusCallback;
import com.mengbo.mbCan.interfaces.IMBCanVehicleLkaSlaStatusCallback;
import com.mengbo.mbCan.interfaces.IMBCanVehicleTiresCallback;
import com.mengbo.mbCan.interfaces.IMBCmdListener;
import com.mengbo.mbCan.interfaces.IMBHardKeyListener;
import com.mengbo.mbCan.interfaces.IMBLogListener;
import com.mengbo.mbCan.interfaces.IMBVehicleListener;
import com.mengbo.mbCan.interfaces.IMbCanAvmStatusCallback;
import com.mengbo.mbCan.interfaces.IMbCanBsdAlarmCallback;
import com.mengbo.mbCan.interfaces.IMbCanDVRParamCallback;
import com.mengbo.mbCan.interfaces.IMbCanDowAlarmCallback;
import com.mengbo.mbCan.interfaces.IMbCanICMAlarmInfoCallback;
import com.mengbo.mbCan.interfaces.IMbCanRCTAAlarmCallback;
import com.mengbo.mbCan.interfaces.IMbCanRadarSensorCallback;
import com.mengbo.mbCan.interfaces.IMbCanS51AutoWashCallBack;
import com.mengbo.mbCan.interfaces.IMbCanVehicleAccStatusCallback;
import com.mengbo.mbCan.interfaces.IMbCanVehicleCarControlCallback;
import com.mengbo.mbCan.interfaces.IMbCanVehicleDoorCallback;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/* loaded from: classes.dex */
public class MBCanEngine extends MBCanClient {
    private static final String TAG = "MENGBO_" + MBCanEngine.class.getSimpleName();
    private static volatile MBCanEngine instance;
    private static boolean isCanClientInited;
    private Handler canHandler;
    private HandlerThread canHandlerThread;
    private IMbCanBsdAlarmCallback mBsdAlarmCallback;
    private IMBCanSettingsCallback mCanSettingsCallback;
    private IMBCanChimeStatusCallback mChimeStatusCallback;
    private IMbCanDowAlarmCallback mDowAlarmCallback;
    private IMBCanVehicleFrmDectInfoCallback mFrmDectInfoCalback;
    private IMBCanVehicleGaspedStatusCallback mGaspedStatusCallback;
    private IMBHardKeyListener mHardKeyListener;
    private IMBLogListener mIMBLogListener;
    private IMBCanVehicleLkaSlaStatusCallback mLkaSlaStatusCallback;
    private IMbCanRCTAAlarmCallback mRCTAAlarmCallback;
    private List<MBCanSubscribeBase> mSubscribeBases;
    private List<MBCanDataType> mSubscribedTypeList;
    private IMBCanVehicleTiresCallback mVehicleTiresCallback;
    private IMBVehicleListener mVehicletener;
    private IMBAirPurgeListener mbAirPurgeListener;
    private IMbCanAvmStatusCallback mbCanAvmStatusCallback;
    private IMbCanDVRParamCallback mbCanDVRParamInfoCallback;
    private IMbCanICMAlarmInfoCallback mbCanICMAlarmInfoCallback;
    private IMbCanRadarSensorCallback mbCanRadarSensorCallback;
    private IMbCanS51AutoWashCallBack mbCanS51AutoWashCallBack;
    private IMBCanTrackListener mbCanTrackListener;
    private IMbCanVehicleAccStatusCallback mbCanVehicleAccStatusCallback;
    private IMbCanVehicleCarControlCallback mbCanVehicleCarControlCallback;
    private IMbCanVehicleDoorCallback mbCanVehicleDoorCallback;
    private Map<MBCanDataType, List<IMBCmdListener>> mCachedIMBCmdListeners = new HashMap();
    private List<IMBCmdListener> mCachedAudioIMBCMDListenerList = new ArrayList();
    private List<IMBCmdListener> mCachedDMSIMBCMDListenerList = new ArrayList();
    private List<IMBCmdListener> mCachedVehicleIMBCMDListenerList = new ArrayList();
    private ICanBaseCallback mCanCallback = new ICanBaseCallback() { // from class: com.mengbo.mbCan.MBCanEngine.1
        @Override // com.mengbo.mbCan.interfaces.ICanBaseCallback
        public void onCanDataCallback(final int i, final Object obj) {
            MBCanEngine.this.canHandler.postDelayed(new Runnable() { // from class: com.mengbo.mbCan.MBCanEngine.1.1
                @Override // java.lang.Runnable
                public void run() {
                    if (obj != null) {
                        String str = MBCanEngine.TAG;
                        Log.d(str, MBCanEngine.this.mIMBLogListener + "run: " + i + ",data =" + obj.toString());
                        if (MBCanEngine.this.mIMBLogListener != null) {
                            MBCanEngine.this.mIMBLogListener.onLog(obj.toString());
                        }
                    }
                }
            }, 1L);
            if (MBCanDataType.eMBCAN_VEHICLE_SPEED.getValue() == i) {
                MBCanEngine.this.canHandler.postDelayed(new Runnable() { // from class: com.mengbo.mbCan.MBCanEngine.1.2
                    @Override // java.lang.Runnable
                    public void run() {
                        if (obj != null) {
                            if (MBCanEngine.this.mVehicletener != null) {
                                MBCanEngine.this.mVehicletener.onSpeed(((MBCanVehicleSpeed) obj).getSpeed(), ((MBCanVehicleSpeed) obj).getGear());
                            }
                            if (MBCanEngine.this.mVehicletener != null) {
                                MBCanEngine.this.mVehicletener.onSpeed(((MBCanVehicleSpeed) obj).getSpeed(), ((MBCanVehicleSpeed) obj).getGear());
                            }
                            if (MBCanEngine.this.mCanSettingsCallback != null) {
                                MBCanEngine.this.mCanSettingsCallback.onCanVehicleSpeed((MBCanVehicleSpeed) obj);
                            }
                            if (MBCanEngine.this.mbCanTrackListener != null) {
                                MBCanEngine.this.mbCanTrackListener.onCanVehicleSpeed((MBCanVehicleSpeed) obj);
                            }
                        }
                    }
                }, 1L);
            } else if (MBCanDataType.eMBCAN_VEHICLE_TURNLIGHT.getValue() == i) {
                MBCanEngine.this.canHandler.postDelayed(new Runnable() { // from class: com.mengbo.mbCan.MBCanEngine.1.3
                    @Override // java.lang.Runnable
                    public void run() {
                        if (obj == null || MBCanEngine.this.mVehicletener == null) {
                            return;
                        }
                        MBCanEngine.this.mVehicletener.onVehicleTurnLightChange(((MBCanVehicleTurnLight) obj).getLeftLightState(), ((MBCanVehicleTurnLight) obj).getRightLightState());
                    }
                }, 1L);
            } else if (MBCanDataType.eMBCAN_VEHICLE_STEERING_ANGLE.getValue() == i) {
                MBCanEngine.this.canHandler.postDelayed(new Runnable() { // from class: com.mengbo.mbCan.MBCanEngine.1.4
                    @Override // java.lang.Runnable
                    public void run() {
                        if (obj == null || MBCanEngine.this.mVehicletener == null) {
                            return;
                        }
                        MBCanEngine.this.mVehicletener.onSteeringWheel(((MBCanVehicleSteeringAngle) obj).getSteeringAngle(), ((MBCanVehicleSteeringAngle) obj).getSteeringAngleSpeed());
                    }
                }, 1L);
            } else if (MBCanDataType.eMBCAN_VEHICLE_WHEEL.getValue() == i) {
                MBCanEngine.this.canHandler.postDelayed(new Runnable() { // from class: com.mengbo.mbCan.MBCanEngine.1.5
                    @Override // java.lang.Runnable
                    public void run() {
                        if (obj == null || MBCanEngine.this.mVehicletener == null) {
                            return;
                        }
                        MBCanEngine.this.mVehicletener.onPull(((MBCanVehicleWheel) obj).getLHFPulseCounter(), ((MBCanVehicleWheel) obj).getRHFPulseCounter(), ((MBCanVehicleWheel) obj).getLHRPulseCounter(), ((MBCanVehicleWheel) obj).getRHRPulseCounter());
                    }
                }, 1L);
            } else if (MBCanDataType.eMBCAN_VEHICLE_DOOR.getValue() == i) {
                MBCanEngine.this.canHandler.postDelayed(new Runnable() { // from class: com.mengbo.mbCan.MBCanEngine.1.6
                    @Override // java.lang.Runnable
                    public void run() {
                        if (obj == null || MBCanEngine.this.mbCanVehicleAccStatusCallback == null) {
                            return;
                        }
                        MBCanEngine.this.mbCanVehicleAccStatusCallback.onDoorChange(((MBCanVehicleDoor) obj).getDriverDoorSts(), ((MBCanVehicleDoor) obj).getDriverDoorLockSts());
                    }
                }, 1L);
            } else if (MBCanDataType.eMBCAN_VEHICLE_ACCSTATUS.getValue() == i) {
                MBCanEngine.this.canHandler.postDelayed(new Runnable() { // from class: com.mengbo.mbCan.MBCanEngine.1.7
                    @Override // java.lang.Runnable
                    public void run() {
                        if (obj != null) {
                            if (MBCanEngine.this.mbCanVehicleAccStatusCallback != null) {
                                MBCanEngine.this.mbCanVehicleAccStatusCallback.onVehicleAccStatusChange(((MBCanVehicleAccStatus) obj).getAccStatus(), ((MBCanVehicleAccStatus) obj).getIgnSts(), ((MBCanVehicleAccStatus) obj).getPowerSts());
                            }
                            if (MBCanEngine.this.mCanSettingsCallback != null) {
                                MBCanEngine.this.mCanSettingsCallback.onVehicleAccStatusChange((MBCanVehicleAccStatus) obj);
                            }
                        }
                    }
                }, 1L);
            } else if (MBCanDataType.eMBCAN_RADARSENSOR.getValue() == i) {
                MBCanEngine.this.canHandler.postDelayed(new Runnable() { // from class: com.mengbo.mbCan.MBCanEngine.1.8
                    @Override // java.lang.Runnable
                    public void run() {
                        if (obj == null || MBCanEngine.this.mbCanRadarSensorCallback == null) {
                            return;
                        }
                        MBCanEngine.this.mbCanRadarSensorCallback.onRadarSensorChange((MBCanRadarSensor) obj);
                    }
                }, 1L);
            } else if (MBCanDataType.eMBCAN_SYSTEMMODE.getValue() == i) {
                MBCanEngine.this.canHandler.postDelayed(new Runnable() { // from class: com.mengbo.mbCan.MBCanEngine.1.9
                    @Override // java.lang.Runnable
                    public void run() {
                        if (obj == null || MBCanEngine.this.mbCanVehicleAccStatusCallback == null) {
                            return;
                        }
                        MBCanEngine.this.mbCanVehicleAccStatusCallback.onVehicleSystemModeChange(((MBCanVehicleAccStatus) obj).getSystemMode());
                    }
                }, 1L);
            } else if (MBCanDataType.eMBCAN_HARDKEY.getValue() == i) {
                MBCanEngine.this.canHandler.postDelayed(new Runnable() { // from class: com.mengbo.mbCan.MBCanEngine.1.10
                    @Override // java.lang.Runnable
                    public void run() {
                        Log.e("chitin", "onCanDataCallback" + obj);
                        if (obj == null || MBCanEngine.this.mHardKeyListener == null) {
                            return;
                        }
                        MBCanEngine.this.mHardKeyListener.onHardKey(((MBHardKey) obj).getKeyCode(), ((MBHardKey) obj).getKeyStatus(), ((MBHardKey) obj).getKeyType());
                    }
                }, 1L);
            } else if (MBCanDataType.eMBCAN_SEAT_STATUS.getValue() == i) {
                MBCanEngine.this.canHandler.postDelayed(new Runnable() { // from class: com.mengbo.mbCan.MBCanEngine.1.11
                    @Override // java.lang.Runnable
                    public void run() {
                        if (obj == null || MBCanEngine.this.mbCanVehicleAccStatusCallback == null) {
                            return;
                        }
                        MBCanEngine.this.mbCanVehicleAccStatusCallback.onVehicleSeatStatusChange(((MBCanSeatStatus) obj).getSeatStatus());
                    }
                }, 1L);
            } else if (MBCanDataType.eMBCAN_RCTA_ALARM.getValue() == i) {
                MBCanEngine.this.canHandler.postDelayed(new Runnable() { // from class: com.mengbo.mbCan.MBCanEngine.1.12
                    @Override // java.lang.Runnable
                    public void run() {
                        if (obj == null || MBCanEngine.this.mRCTAAlarmCallback == null) {
                            return;
                        }
                        MBCanEngine.this.mRCTAAlarmCallback.onRctaAlarmChange((MBCanRctaAlarm) obj);
                    }
                }, 1L);
            } else if (MBCanDataType.eMBCAN_BSD_ALARM.getValue() == i) {
                MBCanEngine.this.canHandler.postDelayed(new Runnable() { // from class: com.mengbo.mbCan.MBCanEngine.1.13
                    @Override // java.lang.Runnable
                    public void run() {
                        if (obj == null || MBCanEngine.this.mBsdAlarmCallback == null) {
                            return;
                        }
                        MBCanEngine.this.mBsdAlarmCallback.onBsdAlarm((MBCanBsdAlarm) obj);
                    }
                }, 1L);
            } else if (MBCanDataType.eMBCAN_DOW_ALARM.getValue() == i) {
                MBCanEngine.this.canHandler.postDelayed(new Runnable() { // from class: com.mengbo.mbCan.MBCanEngine.1.14
                    @Override // java.lang.Runnable
                    public void run() {
                        if (obj == null || MBCanEngine.this.mDowAlarmCallback == null) {
                            return;
                        }
                        MBCanEngine.this.mDowAlarmCallback.onDowAlarm((MBCanDowAlarm) obj);
                    }
                }, 1L);
            } else if (MBCanDataType.eMBCAN_VEHICLE_FUELLEVEL.getValue() == i) {
                MBCanEngine.this.canHandler.postDelayed(new Runnable() { // from class: com.mengbo.mbCan.MBCanEngine.1.15
                    @Override // java.lang.Runnable
                    public void run() {
                        if (obj != null) {
                            if (MBCanEngine.this.mCanSettingsCallback != null) {
                                MBCanEngine.this.mCanSettingsCallback.onCanVehicleFuelLevel((MBCanVehicleFuelLevel) obj);
                            }
                            if (MBCanEngine.this.mbCanVehicleCarControlCallback != null) {
                                MBCanEngine.this.mbCanVehicleCarControlCallback.onCanVehicleFuelLevel((MBCanVehicleFuelLevel) obj);
                            }
                        }
                    }
                }, 1L);
            } else if (MBCanDataType.eMBCAN_DVR_STATUS.getValue() == i) {
                MBCanEngine.this.canHandler.postDelayed(new Runnable() { // from class: com.mengbo.mbCan.MBCanEngine.1.16
                    @Override // java.lang.Runnable
                    public void run() {
                        if (obj == null || MBCanEngine.this.mbCanVehicleAccStatusCallback == null) {
                            return;
                        }
                        MBCanEngine.this.mbCanVehicleAccStatusCallback.onVehicleDVRStatusChange((MBCanDvrStatus) obj);
                    }
                }, 1L);
            } else if (MBCanDataType.eMBCAN_WPC_STATUS.getValue() == i) {
                MBCanEngine.this.canHandler.postDelayed(new Runnable() { // from class: com.mengbo.mbCan.MBCanEngine.1.17
                    @Override // java.lang.Runnable
                    public void run() {
                        if (obj != null) {
                            if (MBCanEngine.this.mbCanVehicleAccStatusCallback != null) {
                                MBCanEngine.this.mbCanVehicleAccStatusCallback.onWpcStatusChange((MBCanWpcStatus) obj);
                            }
                            if (MBCanEngine.this.mCanSettingsCallback != null) {
                                MBCanEngine.this.mCanSettingsCallback.onWpcStatusChange((MBCanWpcStatus) obj);
                            }
                        }
                    }
                }, 1L);
            } else if (MBCanDataType.eMBCAN_SEAT_BELT_STATUS.getValue() == i) {
                MBCanEngine.this.canHandler.postDelayed(new Runnable() { // from class: com.mengbo.mbCan.MBCanEngine.1.18
                    @Override // java.lang.Runnable
                    public void run() {
                    }
                }, 1L);
            } else if (MBCanDataType.eMBCAN_VEHICLE_TOTALODOMETER.getValue() == i) {
                MBCanEngine.this.canHandler.postDelayed(new Runnable() { // from class: com.mengbo.mbCan.MBCanEngine.1.19
                    @Override // java.lang.Runnable
                    public void run() {
                        if (obj != null) {
                            if (MBCanEngine.this.mbCanVehicleAccStatusCallback != null) {
                                MBCanEngine.this.mbCanVehicleAccStatusCallback.onTotalOdometerChange(((MBCanTotalOdometer) obj).getOdometer());
                            }
                            if (MBCanEngine.this.mCanSettingsCallback != null) {
                                MBCanEngine.this.mCanSettingsCallback.onVehicleTotalOdoMeterChange((MBCanTotalOdometer) obj);
                            }
                        }
                    }
                }, 1L);
            } else if (MBCanDataType.eMBCAN_AVM_STATUS.getValue() == i) {
                MBCanEngine.this.canHandler.postDelayed(new Runnable() { // from class: com.mengbo.mbCan.MBCanEngine.1.20
                    @Override // java.lang.Runnable
                    public void run() {
                        if (obj == null || MBCanEngine.this.mIMBLogListener == null) {
                            return;
                        }
                        MBCanEngine.this.mIMBLogListener.onLog(((MBCanAvmStatus) obj).toString());
                    }
                }, 1L);
            } else if (MBCanDataType.eMBCAN_CHIME_STATUS.getValue() == i) {
                MBCanEngine.this.canHandler.postDelayed(new Runnable() { // from class: com.mengbo.mbCan.MBCanEngine.1.21
                    @Override // java.lang.Runnable
                    public void run() {
                        if (obj == null || MBCanEngine.this.mChimeStatusCallback == null) {
                            return;
                        }
                        MBCanEngine.this.mChimeStatusCallback.onChimeStatusChange((MBCanChime) obj);
                    }
                }, 1L);
            } else if (MBCanDataType.eMBCAN_RADIO_FREQUENCYINFO.getValue() == i) {
            } else {
                if (MBCanDataType.eMBCAN_VEHICLE_GEAR.getValue() == i) {
                    MBCanEngine.this.canHandler.postDelayed(new Runnable() { // from class: com.mengbo.mbCan.MBCanEngine.1.22
                        @Override // java.lang.Runnable
                        public void run() {
                            if (obj != null) {
                                if (MBCanEngine.this.mVehicletener != null) {
                                    MBCanEngine.this.mVehicletener.onGear(((MBCanVehicleSpeed) obj).getSpeed(), ((MBCanVehicleSpeed) obj).getGear());
                                }
                                if (MBCanEngine.this.mbCanVehicleAccStatusCallback != null) {
                                    MBCanEngine.this.mbCanVehicleAccStatusCallback.onVehicleGearStatusChange(((MBCanVehicleSpeed) obj).getGear());
                                }
                                if (MBCanEngine.this.mbCanTrackListener != null) {
                                    MBCanEngine.this.mbCanTrackListener.onCanVehicleSpeed((MBCanVehicleSpeed) obj);
                                }
                                if (MBCanEngine.this.mCanSettingsCallback != null) {
                                    MBCanEngine.this.mCanSettingsCallback.onCanVehicleSpeed((MBCanVehicleSpeed) obj);
                                }
                            }
                        }
                    }, 1L);
                } else if (MBCanDataType.eMBCAN_VEHICLE_BCM_STATUS.getValue() == i) {
                    MBCanEngine.this.canHandler.postDelayed(new Runnable() { // from class: com.mengbo.mbCan.MBCanEngine.1.23
                        @Override // java.lang.Runnable
                        public void run() {
                            if (obj != null) {
                                if (MBCanEngine.this.mbCanVehicleAccStatusCallback != null) {
                                    MBCanEngine.this.mbCanVehicleAccStatusCallback.onVehicleBcmStatusChange((MBCanVehicleBcmStatus) obj);
                                }
                                if (MBCanEngine.this.mCanSettingsCallback != null) {
                                    MBCanEngine.this.mCanSettingsCallback.onVehicleBcmStatusChange((MBCanVehicleBcmStatus) obj);
                                }
                                if (MBCanEngine.this.mbAirPurgeListener != null) {
                                    MBCanEngine.this.mbAirPurgeListener.onVehicleBcmStatusChange((MBCanVehicleBcmStatus) obj);
                                }
                                if (MBCanEngine.this.mbCanVehicleDoorCallback != null) {
                                    MBCanEngine.this.mbCanVehicleDoorCallback.onVehicleDoorChange(((MBCanVehicleBcmStatus) obj).getDoorStatus());
                                }
                            }
                        }
                    }, 1L);
                } else if (MBCanDataType.eMBCAN_VEHICLE_ENGINE.getValue() == i) {
                    if (obj != null) {
                        if (MBCanEngine.this.mCanSettingsCallback != null) {
                            MBCanEngine.this.mCanSettingsCallback.onVehicleEngineStatusChange((MBCanVehicleEngine) obj);
                        }
                        if (MBCanEngine.this.mbCanTrackListener != null) {
                            MBCanEngine.this.mbCanTrackListener.onVehicleEngineStatusChange((MBCanVehicleEngine) obj);
                        }
                    }
                } else if (MBCanDataType.eMBCAN_CFG_AUDIO.getValue() == i) {
                    MBCanEngine.this.canHandler.postDelayed(new Runnable() { // from class: com.mengbo.mbCan.MBCanEngine.1.24
                        @Override // java.lang.Runnable
                        public void run() {
                            if (obj == null || MBCanEngine.this.mCachedAudioIMBCMDListenerList == null || MBCanEngine.this.mCachedAudioIMBCMDListenerList.size() <= 0) {
                                return;
                            }
                            for (int i2 = 0; i2 < MBCanEngine.this.mCachedAudioIMBCMDListenerList.size(); i2++) {
                                IMBCmdListener iMBCmdListener = (IMBCmdListener) MBCanEngine.this.mCachedAudioIMBCMDListenerList.get(i2);
                                if (iMBCmdListener != null) {
                                    iMBCmdListener.onCmdChanged(((MBCanCfgItem) obj).getModular(), ((MBCanCfgItem) obj).getRev(), ((MBCanCfgItem) obj).getItem(), ((MBCanCfgItem) obj).getValue());
                                }
                            }
                        }
                    }, 1L);
                } else if (MBCanDataType.eMBCAN_CFG_VEHICLE.getValue() == i) {
                    MBCanEngine.this.canHandler.postDelayed(new Runnable() { // from class: com.mengbo.mbCan.MBCanEngine.1.25
                        @Override // java.lang.Runnable
                        public void run() {
                            if (obj == null || MBCanEngine.this.mCachedVehicleIMBCMDListenerList == null || MBCanEngine.this.mCachedVehicleIMBCMDListenerList.size() <= 0) {
                                return;
                            }
                            for (int i2 = 0; i2 < MBCanEngine.this.mCachedVehicleIMBCMDListenerList.size(); i2++) {
                                IMBCmdListener iMBCmdListener = (IMBCmdListener) MBCanEngine.this.mCachedVehicleIMBCMDListenerList.get(i2);
                                if (iMBCmdListener != null) {
                                    iMBCmdListener.onCmdChanged(((MBCanCfgItem) obj).getModular(), ((MBCanCfgItem) obj).getRev(), ((MBCanCfgItem) obj).getItem(), ((MBCanCfgItem) obj).getValue());
                                }
                            }
                        }
                    }, 1L);
                } else if (MBCanDataType.eMBCAN_CFG_DMS.getValue() == i) {
                    MBCanEngine.this.canHandler.postDelayed(new Runnable() { // from class: com.mengbo.mbCan.MBCanEngine.1.26
                        @Override // java.lang.Runnable
                        public void run() {
                            if (obj != null) {
                                if (MBCanEngine.this.mIMBLogListener != null) {
                                    MBCanEngine.this.mIMBLogListener.onLog(obj.toString());
                                }
                                if (MBCanEngine.this.mCachedDMSIMBCMDListenerList == null || MBCanEngine.this.mCachedDMSIMBCMDListenerList.size() <= 0) {
                                    return;
                                }
                                for (int i2 = 0; i2 < MBCanEngine.this.mCachedDMSIMBCMDListenerList.size(); i2++) {
                                    IMBCmdListener iMBCmdListener = (IMBCmdListener) MBCanEngine.this.mCachedDMSIMBCMDListenerList.get(i2);
                                    if (iMBCmdListener != null) {
                                        iMBCmdListener.onCmdChanged(((MBCanCfgItem) obj).getModular(), ((MBCanCfgItem) obj).getRev(), ((MBCanCfgItem) obj).getItem(), ((MBCanCfgItem) obj).getValue());
                                    }
                                }
                            }
                        }
                    }, 1L);
                } else if (MBCanDataType.eMBCAN_UPGRADE_PROGRESS.getValue() == i) {
                    MBCanEngine.this.canHandler.postDelayed(new Runnable() { // from class: com.mengbo.mbCan.MBCanEngine.1.27
                        @Override // java.lang.Runnable
                        public void run() {
                            if (obj == null || MBCanEngine.this.mbCanVehicleAccStatusCallback == null) {
                                return;
                            }
                            MBCanEngine.this.mbCanVehicleAccStatusCallback.onMcuUpdate(((Integer) obj).intValue());
                        }
                    }, 1L);
                } else if (MBCanDataType.eMBCAN_DTC.getValue() == i) {
                } else {
                    if (MBCanDataType.eMBCAN_PM25INFO.getValue() == i) {
                        MBCanEngine.this.canHandler.postDelayed(new Runnable() { // from class: com.mengbo.mbCan.MBCanEngine.1.28
                            @Override // java.lang.Runnable
                            public void run() {
                                if (obj == null || MBCanEngine.this.mbAirPurgeListener == null) {
                                    return;
                                }
                                MBCanEngine.this.mbAirPurgeListener.onPMChanged((MBCanPM25) obj);
                            }
                        }, 1L);
                    } else if (MBCanDataType.eMBCAN_VEHICLE_ENGINE_GEAR.getValue() == i) {
                        MBCanEngine.this.canHandler.postDelayed(new Runnable() { // from class: com.mengbo.mbCan.MBCanEngine.1.29
                            @Override // java.lang.Runnable
                            public void run() {
                                if (obj != null) {
                                    if (MBCanEngine.this.mbCanVehicleAccStatusCallback != null) {
                                        MBCanEngine.this.mbCanVehicleAccStatusCallback.onVehicleEngineStatusChange((MBCanVehicleEngine) obj);
                                    }
                                    if (MBCanEngine.this.mCanSettingsCallback != null) {
                                        MBCanEngine.this.mCanSettingsCallback.onVehicleEngineStatusChange((MBCanVehicleEngine) obj);
                                        if (MBCanEngine.this.mbCanTrackListener != null) {
                                            MBCanEngine.this.mbCanTrackListener.onVehicleEngineStatusChange((MBCanVehicleEngine) obj);
                                        }
                                    }
                                }
                            }
                        }, 1L);
                    } else if (MBCanDataType.eMBCAN_RADIO_PROGRAMSTATE.getValue() == i || MBCanDataType.eMBCAN_INSTRUMENT_CMDREPLY.getValue() == i || MBCanDataType.eMBCAN_USB_DEVICE.getValue() == i || MBCanDataType.eMBCAN_UART_TEST_RESULT.getValue() == i) {
                    } else {
                        if (MBCanDataType.eMBCAN_VEHICLE_TIRE.getValue() == i) {
                            MBCanEngine.this.canHandler.postDelayed(new Runnable() { // from class: com.mengbo.mbCan.MBCanEngine.1.30
                                @Override // java.lang.Runnable
                                public void run() {
                                    if (MBCanEngine.this.mVehicleTiresCallback != null) {
                                        MBCanEngine.this.mVehicleTiresCallback.onVehicleDoorChange((MBCanVehicleTires) obj);
                                    }
                                    if (MBCanEngine.this.mCanSettingsCallback != null) {
                                        MBCanEngine.this.mCanSettingsCallback.onCanVehicleTires((MBCanVehicleTires) obj);
                                    }
                                }
                            }, 1L);
                        } else if (MBCanDataType.eMBCAN_VEHICLE_FUELTANK.getValue() == i) {
                            if (MBCanEngine.this.mCanSettingsCallback != null) {
                                MBCanEngine.this.mCanSettingsCallback.onVehicleFuelTank((MBCanVehicleFuelTank) obj);
                            }
                        } else if (MBCanDataType.eMBCAN_VEHICLE_GASPED_STATUS.getValue() == i) {
                            MBCanEngine.this.canHandler.postDelayed(new Runnable() { // from class: com.mengbo.mbCan.MBCanEngine.1.31
                                @Override // java.lang.Runnable
                                public void run() {
                                    if (obj != null) {
                                        if (MBCanEngine.this.mGaspedStatusCallback != null) {
                                            MBCanEngine.this.mGaspedStatusCallback.onVehicleGaspedStatus((MBCanVehicleGaspedStatus) obj);
                                        }
                                        if (MBCanEngine.this.mbCanTrackListener != null) {
                                            MBCanEngine.this.mbCanTrackListener.onVehicleGaspedStatus((MBCanVehicleGaspedStatus) obj);
                                        }
                                    }
                                }
                            }, 1L);
                        } else if (MBCanDataType.eMBCAN_VEHICLE_AQS_STATUS.getValue() == i) {
                            MBCanEngine.this.canHandler.postDelayed(new Runnable() { // from class: com.mengbo.mbCan.MBCanEngine.1.32
                                @Override // java.lang.Runnable
                                public void run() {
                                    if (obj != null) {
                                        if (MBCanEngine.this.mCanSettingsCallback != null) {
                                            MBCanEngine.this.mCanSettingsCallback.onCanVehicleAqsStatus((MBCanVehicleAqsStatus) obj);
                                        }
                                        if (MBCanEngine.this.mbAirPurgeListener != null) {
                                            MBCanEngine.this.mbAirPurgeListener.onCanVehicleAqsStatus((MBCanVehicleAqsStatus) obj);
                                        }
                                    }
                                }
                            }, 1L);
                        } else if (MBCanDataType.eMBCAN_VEHICLE_EXTERNAL_TEMP_RAW.getValue() == i) {
                        } else {
                            if (MBCanDataType.eMBCAN_VEHICLE_EBS_SOC.getValue() == i) {
                                MBCanEngine.this.canHandler.postDelayed(new Runnable() { // from class: com.mengbo.mbCan.MBCanEngine.1.33
                                    @Override // java.lang.Runnable
                                    public void run() {
                                        if (obj != null) {
                                            if (MBCanEngine.this.mCanSettingsCallback != null) {
                                                MBCanEngine.this.mCanSettingsCallback.onVehicleEbsSocChange((MBCanVehicleEbsSoc) obj);
                                            }
                                            if (MBCanEngine.this.mbCanVehicleCarControlCallback != null) {
                                                MBCanEngine.this.mbCanVehicleCarControlCallback.onVehicleEbsSocChange((MBCanVehicleEbsSoc) obj);
                                            }
                                        }
                                    }
                                }, 1L);
                            } else if (MBCanDataType.eMBCAN_VEHICLE_LKA_STATUS.getValue() == i) {
                                MBCanEngine.this.canHandler.postDelayed(new Runnable() { // from class: com.mengbo.mbCan.MBCanEngine.1.34
                                    @Override // java.lang.Runnable
                                    public void run() {
                                        if (obj != null) {
                                            if (MBCanEngine.this.mLkaSlaStatusCallback != null) {
                                                MBCanEngine.this.mLkaSlaStatusCallback.onVehicleLkaSlaStatus((MBCanVehicleLkaSlaStatus) obj);
                                            }
                                            if (MBCanEngine.this.mCanSettingsCallback != null) {
                                                MBCanEngine.this.mCanSettingsCallback.onVehicleLkaSlaStatus((MBCanVehicleLkaSlaStatus) obj);
                                            }
                                        }
                                    }
                                }, 1L);
                            } else if (MBCanDataType.eMBCAN_VEHICLE_FRM_INFO.getValue() == i) {
                                MBCanEngine.this.canHandler.postDelayed(new Runnable() { // from class: com.mengbo.mbCan.MBCanEngine.1.35
                                    @Override // java.lang.Runnable
                                    public void run() {
                                        if (obj == null || MBCanEngine.this.mFrmDectInfoCalback == null) {
                                            return;
                                        }
                                        MBCanEngine.this.mFrmDectInfoCalback.onCanVehicleFrmInfo((MBCanVehicleFrmDectInfo) obj);
                                    }
                                }, 1L);
                            } else if (MBCanDataType.eMBCAN_VEHICLE_ICM_INFO.getValue() == i) {
                                if (obj == null || MBCanEngine.this.mCanSettingsCallback == null) {
                                    return;
                                }
                                MBCanEngine.this.mCanSettingsCallback.onVehicleIcmInfoChange((MBCanVehicleIcmInfo) obj);
                            } else if (MBCanDataType.eMBCAN_VEHICLE_ICM_FAULT_INFO.getValue() == i) {
                                if (obj == null || MBCanEngine.this.mCanSettingsCallback == null) {
                                    return;
                                }
                                MBCanEngine.this.mCanSettingsCallback.onVehicleIcmFaultInfoChange((MBCanVehicleIcmFaultInfo) obj);
                            } else if (MBCanDataType.eMBCAN_VEHICLE_ICM_DRIVE_INFO.getValue() == i) {
                            } else {
                                if (MBCanDataType.eMBCAN_ICM_TRIP_INFO.getValue() == i) {
                                    if (obj != null) {
                                        if (MBCanEngine.this.mCanSettingsCallback != null) {
                                            MBCanEngine.this.mCanSettingsCallback.onVehicleIcmTripInfoChange((MBCanVehicleIcmTripInfo) obj);
                                        }
                                        if (MBCanEngine.this.mbCanICMAlarmInfoCallback != null) {
                                            MBCanEngine.this.mbCanICMAlarmInfoCallback.onTripInfo((MBCanVehicleIcmTripInfo) obj);
                                        }
                                    }
                                } else if (MBCanDataType.eMBCAN_VEHICLE_CEM_FRAG.getValue() == i) {
                                    if (obj != null) {
                                        if (MBCanEngine.this.mCanSettingsCallback != null) {
                                            MBCanEngine.this.mCanSettingsCallback.onVehicleLkaFrag((MBCanVehicleFrag) obj);
                                        }
                                        if (MBCanEngine.this.mbAirPurgeListener != null) {
                                            MBCanEngine.this.mbAirPurgeListener.onVehicleLkaFrag((MBCanVehicleFrag) obj);
                                        }
                                    }
                                } else if (MBCanDataType.eMBCAN_ICM_ALARM_INFO.getValue() == i) {
                                    if (obj == null || MBCanEngine.this.mbCanICMAlarmInfoCallback == null) {
                                        return;
                                    }
                                    MBCanEngine.this.mbCanICMAlarmInfoCallback.onAlarmInfo((MBVehicleIcmAlarmInfo) obj);
                                } else if (MBCanDataType.eMBCAN_VEHICLE_INVERTER_STATUS.getValue() == i) {
                                    if (obj != null) {
                                        if (MBCanEngine.this.mCanSettingsCallback != null) {
                                            MBCanEngine.this.mCanSettingsCallback.onVehicleInverterStatus((MBCanVehicleInverter) obj);
                                        }
                                        if (MBCanEngine.this.mbCanS51AutoWashCallBack != null) {
                                            MBCanEngine.this.mbCanS51AutoWashCallBack.onVehicleInverterStatus((MBCanVehicleInverter) obj);
                                        }
                                    }
                                } else if (MBCanDataType.eMBCAN_VEHICLE_DVR_PARAM.getValue() == i) {
                                    if (obj == null || MBCanEngine.this.mbCanDVRParamInfoCallback == null) {
                                        return;
                                    }
                                    MBCanEngine.this.mbCanDVRParamInfoCallback.onCanDvrParamChange((MBCanDvrParam) obj);
                                } else if (MBCanDataType.eMBCAN_VEHICLE_EPB_STATUS.getValue() == i) {
                                    if (obj == null || MBCanEngine.this.mbCanS51AutoWashCallBack == null) {
                                        return;
                                    }
                                    MBCanEngine.this.mbCanS51AutoWashCallBack.onCanEpsStatus((MBCanEpsStatus) obj);
                                } else if (MBCanDataType.eMBCAN_VEHICLE_CONSUMPTION.getValue() == i) {
                                    if (obj == null || MBCanEngine.this.mCanSettingsCallback == null) {
                                        return;
                                    }
                                    MBCanEngine.this.mCanSettingsCallback.onVehicleConsumptionChange((MBCanVehicleConsumption) obj);
                                } else if (MBCanDataType.eMBCAN_CHARGING_RESERVE.getValue() != i || obj == null || MBCanEngine.this.mCanSettingsCallback == null) {
                                } else {
                                    MBCanEngine.this.mCanSettingsCallback.onChargingReserveChange((MBCanChargingReserve) obj);
                                }
                            }
                        }
                    }
                }
            }
        }
    };

    private byte getAsciiKey(byte b) {
        int i;
        if (b < 48 || b > 59) {
            byte b2 = 65;
            if (b < 65 || b > 70) {
                b2 = 97;
                if (b < 97 || b > 102) {
                    return (byte) 0;
                }
            }
            i = (b - b2) + 10;
        } else {
            i = b - 48;
        }
        return (byte) i;
    }

    static {
        System.loadLibrary("mbcanclient");
        System.loadLibrary("mbCan");
    }

    private MBCanEngine() {
        this.canHandlerThread = null;
        this.canHandler = null;
        HandlerThread handlerThread = new HandlerThread("can-thread");
        this.canHandlerThread = handlerThread;
        handlerThread.start();
        this.canHandler = new Handler(this.canHandlerThread.getLooper());
        initSubscribe();
        isCanClientInited = canClientInit() == 0;
        Map<MBCanDataType, List<IMBCmdListener>> map = this.mCachedIMBCmdListeners;
        if (map != null) {
            map.put(MBCanDataType.eMBCAN_CFG_AUDIO, this.mCachedAudioIMBCMDListenerList);
            this.mCachedIMBCmdListeners.put(MBCanDataType.eMBCAN_CFG_DMS, this.mCachedDMSIMBCMDListenerList);
            this.mCachedIMBCmdListeners.put(MBCanDataType.eMBCAN_CFG_VEHICLE, this.mCachedVehicleIMBCMDListenerList);
        }
    }

    private void destroy() {
        Handler handler = this.canHandler;
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
        HandlerThread handlerThread = this.canHandlerThread;
        if (handlerThread != null) {
            handlerThread.quit();
        }
    }

    private void initSubscribe() {
        initSubscribe(0);
        this.mSubscribedTypeList = new ArrayList();
    }

    private void initSubscribe(int i) {
        if (this.mSubscribeBases == null) {
            this.mSubscribeBases = new ArrayList();
        }
        this.mSubscribeBases.add(new MBCanSubscribeBase(MBCanDataType.eMBCAN_HARDKEY.getValue(), 0));
        this.mSubscribeBases.add(new MBCanSubscribeBase(MBCanDataType.eMBCAN_DVR_STATUS.getValue(), 0));
        this.mSubscribeBases.add(new MBCanSubscribeBase(MBCanDataType.eMBCAN_RCTA_ALARM.getValue(), 0));
        this.mSubscribeBases.add(new MBCanSubscribeBase(MBCanDataType.eMBCAN_DOW_ALARM.getValue(), 0));
        this.mSubscribeBases.add(new MBCanSubscribeBase(MBCanDataType.eMBCAN_BSD_ALARM.getValue(), 0));
        this.mSubscribeBases.add(new MBCanSubscribeBase(MBCanDataType.eMBCAN_SEAT_STATUS.getValue(), 0));
        this.mSubscribeBases.add(new MBCanSubscribeBase(MBCanDataType.eMBCAN_VEHICLE_ACCSTATUS.getValue(), 0));
        this.mSubscribeBases.add(new MBCanSubscribeBase(MBCanDataType.eMBCAN_VEHICLE_DOOR.getValue(), 0));
        this.mSubscribeBases.add(new MBCanSubscribeBase(MBCanDataType.eMBCAN_VEHICLE_FUELLEVEL.getValue(), 0));
        this.mSubscribeBases.add(new MBCanSubscribeBase(MBCanDataType.eMBCAN_VEHICLE_SPEED.getValue(), i));
        this.mSubscribeBases.add(new MBCanSubscribeBase(MBCanDataType.eMBCAN_VEHICLE_STEERING_ANGLE.getValue(), i));
        this.mSubscribeBases.add(new MBCanSubscribeBase(MBCanDataType.eMBCAN_VEHICLE_TURNLIGHT.getValue(), 0));
        this.mSubscribeBases.add(new MBCanSubscribeBase(MBCanDataType.eMBCAN_VEHICLE_WHEEL.getValue(), i));
        this.mSubscribeBases.add(new MBCanSubscribeBase(MBCanDataType.eMBCAN_WPC_STATUS.getValue(), 0));
        this.mSubscribeBases.add(new MBCanSubscribeBase(MBCanDataType.eMBCAN_SEAT_BELT_STATUS.getValue(), 0));
        this.mSubscribeBases.add(new MBCanSubscribeBase(MBCanDataType.eMBCAN_VEHICLE_TOTALODOMETER.getValue(), 0));
        this.mSubscribeBases.add(new MBCanSubscribeBase(MBCanDataType.eMBCAN_AVM_STATUS.getValue(), 0));
        this.mSubscribeBases.add(new MBCanSubscribeBase(MBCanDataType.eMBCAN_CHIME_STATUS.getValue(), 0));
        this.mSubscribeBases.add(new MBCanSubscribeBase(MBCanDataType.eMBCAN_RADIO_FREQUENCYINFO.getValue(), 0));
        this.mSubscribeBases.add(new MBCanSubscribeBase(MBCanDataType.eMBCAN_SYSTEMMODE.getValue(), 0));
        this.mSubscribeBases.add(new MBCanSubscribeBase(MBCanDataType.eMBCAN_RADARSENSOR.getValue(), 0));
        this.mSubscribeBases.add(new MBCanSubscribeBase(MBCanDataType.eMBCAN_VEHICLE_GEAR.getValue(), 0));
        this.mSubscribeBases.add(new MBCanSubscribeBase(MBCanDataType.eMBCAN_VEHICLE_BCM_STATUS.getValue(), 0));
        this.mSubscribeBases.add(new MBCanSubscribeBase(MBCanDataType.eMBCAN_VEHICLE_ENGINE.getValue(), i));
        this.mSubscribeBases.add(new MBCanSubscribeBase(MBCanDataType.eMBCAN_CFG_AUDIO.getValue(), 0));
        this.mSubscribeBases.add(new MBCanSubscribeBase(MBCanDataType.eMBCAN_CFG_VEHICLE.getValue(), 0));
        this.mSubscribeBases.add(new MBCanSubscribeBase(MBCanDataType.eMBCAN_CFG_DMS.getValue(), 0));
        this.mSubscribeBases.add(new MBCanSubscribeBase(MBCanDataType.eMBCAN_UPGRADE_PROGRESS.getValue(), 0));
        this.mSubscribeBases.add(new MBCanSubscribeBase(MBCanDataType.eMBCAN_DTC.getValue(), 0));
        this.mSubscribeBases.add(new MBCanSubscribeBase(MBCanDataType.eMBCAN_PM25INFO.getValue(), 0));
        this.mSubscribeBases.add(new MBCanSubscribeBase(MBCanDataType.eMBCAN_VEHICLE_ENGINE_GEAR.getValue(), 0));
        this.mSubscribeBases.add(new MBCanSubscribeBase(MBCanDataType.eMBCAN_RADIO_PROGRAMSTATE.getValue(), 0));
        this.mSubscribeBases.add(new MBCanSubscribeBase(MBCanDataType.eMBCAN_INSTRUMENT_CMDREPLY.getValue(), 0));
        this.mSubscribeBases.add(new MBCanSubscribeBase(MBCanDataType.eMBCAN_USB_DEVICE.getValue(), 0));
        this.mSubscribeBases.add(new MBCanSubscribeBase(MBCanDataType.eMBCAN_UART_TEST_RESULT.getValue(), 0));
        this.mSubscribeBases.add(new MBCanSubscribeBase(MBCanDataType.eMBCAN_VEHICLE_TIRE.getValue(), 0));
        this.mSubscribeBases.add(new MBCanSubscribeBase(MBCanDataType.eMBCAN_VEHICLE_FUELTANK.getValue(), 0));
        this.mSubscribeBases.add(new MBCanSubscribeBase(MBCanDataType.eMBCAN_VEHICLE_GASPED_STATUS.getValue(), 0));
        this.mSubscribeBases.add(new MBCanSubscribeBase(MBCanDataType.eMBCAN_VEHICLE_AQS_STATUS.getValue(), 0));
        this.mSubscribeBases.add(new MBCanSubscribeBase(MBCanDataType.eMBCAN_VEHICLE_EXTERNAL_TEMP_RAW.getValue(), 0));
        this.mSubscribeBases.add(new MBCanSubscribeBase(MBCanDataType.eMBCAN_VEHICLE_EBS_SOC.getValue(), 0));
        this.mSubscribeBases.add(new MBCanSubscribeBase(MBCanDataType.eMBCAN_VEHICLE_LKA_STATUS.getValue(), 0));
        this.mSubscribeBases.add(new MBCanSubscribeBase(MBCanDataType.eMBCAN_VEHICLE_FRM_INFO.getValue(), 0));
        this.mSubscribeBases.add(new MBCanSubscribeBase(MBCanDataType.eMBCAN_VEHICLE_ICM_INFO.getValue(), 0));
        this.mSubscribeBases.add(new MBCanSubscribeBase(MBCanDataType.eMBCAN_VEHICLE_ICM_FAULT_INFO.getValue(), 0));
        this.mSubscribeBases.add(new MBCanSubscribeBase(MBCanDataType.eMBCAN_VEHICLE_ICM_DRIVE_INFO.getValue(), 0));
        this.mSubscribeBases.add(new MBCanSubscribeBase(MBCanDataType.eMBCAN_ICM_TRIP_INFO.getValue(), 0));
        this.mSubscribeBases.add(new MBCanSubscribeBase(MBCanDataType.eMBCAN_ICM_ALARM_INFO.getValue(), 0));
        this.mSubscribeBases.add(new MBCanSubscribeBase(MBCanDataType.eMBCAN_VEHICLE_CEM_FRAG.getValue(), 0));
        this.mSubscribeBases.add(new MBCanSubscribeBase(MBCanDataType.eMBCAN_VEHICLE_INVERTER_STATUS.getValue(), 0));
        this.mSubscribeBases.add(new MBCanSubscribeBase(MBCanDataType.eMBCAN_VEHICLE_DVR_PARAM.getValue(), 0));
        this.mSubscribeBases.add(new MBCanSubscribeBase(MBCanDataType.eMBCAN_VEHICLE_EPB_STATUS.getValue(), 0));
        this.mSubscribeBases.add(new MBCanSubscribeBase(MBCanDataType.eMBCAN_VEHICLE_CONSUMPTION.getValue(), 0));
        this.mSubscribeBases.add(new MBCanSubscribeBase(MBCanDataType.eMBCAN_CHARGING_RESERVE.getValue(), 0));
    }

    public static MBCanEngine getInstance() {
        if (instance == null) {
            synchronized (MBCanEngine.class) {
                if (instance == null) {
                    instance = new MBCanEngine();
                }
            }
        }
        return instance;
    }

    public boolean isEngineInited() {
        return isCanClientInited;
    }

    public List<MBCanDataType> getSubscribeDataList() {
        return this.mSubscribedTypeList;
    }

    public List<MBCanDataType> getAllCanDataType() {
        return Arrays.asList(MBCanDataType.eMBCAN_VEHICLE_SPEED, MBCanDataType.eMBCAN_VEHICLE_TURNLIGHT, MBCanDataType.eMBCAN_VEHICLE_STEERING_ANGLE, MBCanDataType.eMBCAN_VEHICLE_WHEEL, MBCanDataType.eMBCAN_VEHICLE_DOOR, MBCanDataType.eMBCAN_VEHICLE_ACCSTATUS, MBCanDataType.eMBCAN_RADARSENSOR, MBCanDataType.eMBCAN_SYSTEMMODE, MBCanDataType.eMBCAN_HARDKEY, MBCanDataType.eMBCAN_SEAT_STATUS, MBCanDataType.eMBCAN_RCTA_ALARM, MBCanDataType.eMBCAN_VEHICLE_FUELLEVEL, MBCanDataType.eMBCAN_DVR_STATUS, MBCanDataType.eMBCAN_WPC_STATUS, MBCanDataType.eMBCAN_SEAT_BELT_STATUS, MBCanDataType.eMBCAN_VEHICLE_TOTALODOMETER, MBCanDataType.eMBCAN_AVM_STATUS, MBCanDataType.eMBCAN_CHIME_STATUS, MBCanDataType.eMBCAN_RADIO_FREQUENCYINFO, MBCanDataType.eMBCAN_VEHICLE_GEAR, MBCanDataType.eMBCAN_VEHICLE_BCM_STATUS, MBCanDataType.eMBCAN_VEHICLE_ENGINE, MBCanDataType.eMBCAN_CFG_AUDIO, MBCanDataType.eMBCAN_CFG_VEHICLE, MBCanDataType.eMBCAN_CFG_DMS, MBCanDataType.eMBCAN_UPGRADE_PROGRESS, MBCanDataType.eMBCAN_DTC, MBCanDataType.eMBCAN_PM25INFO, MBCanDataType.eMBCAN_VEHICLE_ENGINE_GEAR, MBCanDataType.eMBCAN_RADIO_PROGRAMSTATE, MBCanDataType.eMBCAN_INSTRUMENT_CMDREPLY, MBCanDataType.eMBCAN_USB_DEVICE, MBCanDataType.eMBCAN_UART_TEST_RESULT, MBCanDataType.eMBCAN_VEHICLE_TIRE, MBCanDataType.eMBCAN_VEHICLE_FUELTANK, MBCanDataType.eMBCAN_VEHICLE_GASPED_STATUS, MBCanDataType.eMBCAN_VEHICLE_AQS_STATUS, MBCanDataType.eMBCAN_VEHICLE_EXTERNAL_TEMP_RAW, MBCanDataType.eMBCAN_VEHICLE_EBS_SOC, MBCanDataType.eMBCAN_VEHICLE_LKA_STATUS, MBCanDataType.eMBCAN_VEHICLE_FRM_INFO, MBCanDataType.eMBCAN_VEHICLE_ICM_INFO, MBCanDataType.eMBCAN_VEHICLE_ICM_FAULT_INFO, MBCanDataType.eMBCAN_VEHICLE_ICM_DRIVE_INFO, MBCanDataType.eMBCAN_VEHICLE_CEM_FRAG, MBCanDataType.eMBCAN_DOW_ALARM, MBCanDataType.eMBCAN_BSD_ALARM, MBCanDataType.eMBCAN_ICM_TRIP_INFO, MBCanDataType.eMBCAN_ICM_ALARM_INFO, MBCanDataType.eMBCAN_VEHICLE_INVERTER_STATUS, MBCanDataType.eMBCAN_VEHICLE_DVR_PARAM, MBCanDataType.eMBCAN_VEHICLE_EPB_STATUS, MBCanDataType.eMBCAN_VEHICLE_CONSUMPTION, MBCanDataType.eMBCAN_CHARGING_RESERVE);
    }

    public int subscribeCanDataWithList(ArrayList<MBCanDataType> arrayList) {
        int i;
        boolean z;
        ArrayList<MBCanSubscribeBase> arrayList2 = new ArrayList<>();
        if (arrayList != null && arrayList.size() > 0) {
            i = 0;
            for (int size = arrayList.size() - 1; size >= 0; size--) {
                int value = arrayList.get(size).getValue();
                Iterator<MBCanDataType> it = this.mSubscribedTypeList.iterator();
                while (true) {
                    if (it.hasNext()) {
                        if (it.next().getValue() == value) {
                            arrayList.remove(size);
                            z = true;
                            break;
                        }
                    } else {
                        z = false;
                        break;
                    }
                }
                if (!z) {
                    for (MBCanSubscribeBase mBCanSubscribeBase : this.mSubscribeBases) {
                        if (value == mBCanSubscribeBase.getSubscribeType()) {
                            arrayList2.add(mBCanSubscribeBase);
                            i++;
                        }
                    }
                }
            }
        } else {
            arrayList2.addAll(this.mSubscribeBases);
            i = 0;
        }
        if (canClientSubscribe(arrayList2) != 0) {
            return 0;
        }
        if (arrayList != null) {
            this.mSubscribedTypeList.addAll(arrayList);
        } else if (arrayList == null) {
            this.mSubscribedTypeList.addAll(getAllCanDataType());
        }
        return i;
    }

    public int unSubscribeCanDataWithList(ArrayList<MBCanDataType> arrayList) {
        int i;
        ArrayList<MBCanSubscribeBase> arrayList2 = new ArrayList<>();
        if (arrayList == null || arrayList.size() <= 0) {
            if (arrayList == null) {
                arrayList2.addAll(this.mSubscribeBases);
            }
            i = 0;
        } else {
            i = 0;
            for (MBCanDataType mBCanDataType : this.mSubscribedTypeList) {
                for (int size = arrayList.size() - 1; size >= 0; size--) {
                    int value = arrayList.get(size).getValue();
                    if (mBCanDataType.getValue() == value) {
                        for (MBCanSubscribeBase mBCanSubscribeBase : this.mSubscribeBases) {
                            if (value == mBCanSubscribeBase.getSubscribeType()) {
                                arrayList2.add(mBCanSubscribeBase);
                                i++;
                            }
                        }
                    }
                }
            }
        }
        if (canClientUnSubscribe(arrayList2) != 0) {
            return 0;
        }
        if (arrayList != null) {
            this.mSubscribedTypeList.removeAll(arrayList);
        } else if (arrayList == null) {
            this.mSubscribedTypeList.clear();
        }
        return i;
    }

    public void registIMBLogListener(IMBLogListener iMBLogListener) {
        this.mIMBLogListener = iMBLogListener;
        String str = TAG;
        Log.d(str, iMBLogListener + "注册了");
    }

    public void unRegistIMBLogListener() {
        Log.d(TAG, "取消注册了");
        this.mIMBLogListener = null;
    }

    public void registCMDListener(MBCanDataType mBCanDataType, IMBCmdListener iMBCmdListener) {
        Map<MBCanDataType, List<IMBCmdListener>> map;
        if (mBCanDataType == null || iMBCmdListener == null || (map = this.mCachedIMBCmdListeners) == null || map.get(mBCanDataType) == null) {
            return;
        }
        if (this.mCachedIMBCmdListeners.get(mBCanDataType).size() <= 0) {
            ArrayList<MBCanDataType> arrayList = new ArrayList<>();
            arrayList.add(mBCanDataType);
            subscribeCanDataWithList(arrayList);
        }
        this.mCachedIMBCmdListeners.get(mBCanDataType).add(iMBCmdListener);
    }

    public void unRegistCMDListener(MBCanDataType mBCanDataType) {
        if (mBCanDataType == null) {
            return;
        }
        Map<MBCanDataType, List<IMBCmdListener>> map = this.mCachedIMBCmdListeners;
        if (map != null && map.get(mBCanDataType) != null) {
            this.mCachedIMBCmdListeners.get(mBCanDataType).clear();
        }
        ArrayList<MBCanDataType> arrayList = new ArrayList<>();
        arrayList.add(mBCanDataType);
        unSubscribeCanDataWithList(arrayList);
    }

    public void registVehicleListener(IMBVehicleListener iMBVehicleListener) {
        this.mVehicletener = iMBVehicleListener;
        ArrayList<MBCanDataType> arrayList = new ArrayList<>();
        arrayList.add(MBCanDataType.eMBCAN_VEHICLE_SPEED);
        arrayList.add(MBCanDataType.eMBCAN_VEHICLE_STEERING_ANGLE);
        arrayList.add(MBCanDataType.eMBCAN_VEHICLE_TURNLIGHT);
        arrayList.add(MBCanDataType.eMBCAN_VEHICLE_WHEEL);
        subscribeCanDataWithList(arrayList);
    }

    public void unRegistVehicleListener() {
        this.mVehicletener = null;
        ArrayList<MBCanDataType> arrayList = new ArrayList<>();
        arrayList.add(MBCanDataType.eMBCAN_VEHICLE_SPEED);
        arrayList.add(MBCanDataType.eMBCAN_VEHICLE_STEERING_ANGLE);
        arrayList.add(MBCanDataType.eMBCAN_VEHICLE_TURNLIGHT);
        arrayList.add(MBCanDataType.eMBCAN_VEHICLE_WHEEL);
        unSubscribeCanDataWithList(arrayList);
    }

    public void registACCListener(IMbCanVehicleAccStatusCallback iMbCanVehicleAccStatusCallback) {
        this.mbCanVehicleAccStatusCallback = iMbCanVehicleAccStatusCallback;
        ArrayList<MBCanDataType> arrayList = new ArrayList<>();
        arrayList.add(MBCanDataType.eMBCAN_VEHICLE_ACCSTATUS);
        arrayList.add(MBCanDataType.eMBCAN_VEHICLE_ENGINE_GEAR);
        arrayList.add(MBCanDataType.eMBCAN_SYSTEMMODE);
        arrayList.add(MBCanDataType.eMBCAN_VEHICLE_GEAR);
        arrayList.add(MBCanDataType.eMBCAN_DVR_STATUS);
        arrayList.add(MBCanDataType.eMBCAN_VEHICLE_BCM_STATUS);
        arrayList.add(MBCanDataType.eMBCAN_SEAT_STATUS);
        arrayList.add(MBCanDataType.eMBCAN_VEHICLE_DOOR);
        arrayList.add(MBCanDataType.eMBCAN_UPGRADE_PROGRESS);
        arrayList.add(MBCanDataType.eMBCAN_WPC_STATUS);
        arrayList.add(MBCanDataType.eMBCAN_VEHICLE_TOTALODOMETER);
        subscribeCanDataWithList(arrayList);
    }

    public void unRegistACCListener() {
        this.mbCanVehicleAccStatusCallback = null;
        ArrayList<MBCanDataType> arrayList = new ArrayList<>();
        arrayList.add(MBCanDataType.eMBCAN_VEHICLE_ACCSTATUS);
        arrayList.add(MBCanDataType.eMBCAN_VEHICLE_ENGINE_GEAR);
        arrayList.add(MBCanDataType.eMBCAN_SYSTEMMODE);
        arrayList.add(MBCanDataType.eMBCAN_VEHICLE_GEAR);
        arrayList.add(MBCanDataType.eMBCAN_DVR_STATUS);
        arrayList.add(MBCanDataType.eMBCAN_VEHICLE_BCM_STATUS);
        arrayList.add(MBCanDataType.eMBCAN_SEAT_STATUS);
        arrayList.add(MBCanDataType.eMBCAN_VEHICLE_DOOR);
        arrayList.add(MBCanDataType.eMBCAN_UPGRADE_PROGRESS);
        arrayList.add(MBCanDataType.eMBCAN_WPC_STATUS);
        arrayList.add(MBCanDataType.eMBCAN_VEHICLE_TOTALODOMETER);
        unSubscribeCanDataWithList(arrayList);
    }

    public void registHardKeyListener(IMBHardKeyListener iMBHardKeyListener) {
        this.mHardKeyListener = iMBHardKeyListener;
        ArrayList<MBCanDataType> arrayList = new ArrayList<>();
        arrayList.add(MBCanDataType.eMBCAN_HARDKEY);
        subscribeCanDataWithList(arrayList);
    }

    public void unRegistHardKeyListener() {
        this.mHardKeyListener = null;
        ArrayList<MBCanDataType> arrayList = new ArrayList<>();
        arrayList.add(MBCanDataType.eMBCAN_HARDKEY);
        unSubscribeCanDataWithList(arrayList);
    }

    public void registMBCanAvmStatusCallback(IMbCanAvmStatusCallback iMbCanAvmStatusCallback) {
        this.mbCanAvmStatusCallback = iMbCanAvmStatusCallback;
        ArrayList<MBCanDataType> arrayList = new ArrayList<>();
        arrayList.add(MBCanDataType.eMBCAN_AVM_STATUS);
        subscribeCanDataWithList(arrayList);
    }

    public void unRegistMBCanAvmStatusCallback() {
        this.mbCanAvmStatusCallback = null;
        ArrayList<MBCanDataType> arrayList = new ArrayList<>();
        arrayList.add(MBCanDataType.eMBCAN_AVM_STATUS);
        unSubscribeCanDataWithList(arrayList);
    }

    public void registIMBAirPurgeListener(IMBAirPurgeListener iMBAirPurgeListener) {
        this.mbAirPurgeListener = iMBAirPurgeListener;
        ArrayList<MBCanDataType> arrayList = new ArrayList<>();
        arrayList.add(MBCanDataType.eMBCAN_VEHICLE_AQS_STATUS);
        arrayList.add(MBCanDataType.eMBCAN_PM25INFO);
        arrayList.add(MBCanDataType.eMBCAN_VEHICLE_BCM_STATUS);
        arrayList.add(MBCanDataType.eMBCAN_VEHICLE_CEM_FRAG);
        subscribeCanDataWithList(arrayList);
    }

    public void unRegistIMBAirPurgeListener() {
        this.mbAirPurgeListener = null;
        ArrayList<MBCanDataType> arrayList = new ArrayList<>();
        arrayList.add(MBCanDataType.eMBCAN_VEHICLE_AQS_STATUS);
        arrayList.add(MBCanDataType.eMBCAN_PM25INFO);
        arrayList.add(MBCanDataType.eMBCAN_VEHICLE_BCM_STATUS);
        arrayList.add(MBCanDataType.eMBCAN_VEHICLE_CEM_FRAG);
        unSubscribeCanDataWithList(arrayList);
    }

    public void registIMBCanVehicleTiresListener(IMBCanVehicleTiresCallback iMBCanVehicleTiresCallback) {
        this.mVehicleTiresCallback = iMBCanVehicleTiresCallback;
        ArrayList<MBCanDataType> arrayList = new ArrayList<>();
        arrayList.add(MBCanDataType.eMBCAN_VEHICLE_TIRE);
        subscribeCanDataWithList(arrayList);
    }

    public void unRegistIMBCanVehicleTiresListener() {
        this.mVehicleTiresCallback = null;
        ArrayList<MBCanDataType> arrayList = new ArrayList<>();
        arrayList.add(MBCanDataType.eMBCAN_VEHICLE_TIRE);
        unSubscribeCanDataWithList(arrayList);
    }

    public void registIMBCanVehicleGaspedStatusListener(IMBCanVehicleGaspedStatusCallback iMBCanVehicleGaspedStatusCallback) {
        this.mGaspedStatusCallback = iMBCanVehicleGaspedStatusCallback;
        ArrayList<MBCanDataType> arrayList = new ArrayList<>();
        arrayList.add(MBCanDataType.eMBCAN_VEHICLE_GASPED_STATUS);
        subscribeCanDataWithList(arrayList);
    }

    public void unRegistIMBCanVehicleGaspedStatusListener() {
        this.mGaspedStatusCallback = null;
        ArrayList<MBCanDataType> arrayList = new ArrayList<>();
        arrayList.add(MBCanDataType.eMBCAN_VEHICLE_GASPED_STATUS);
        unSubscribeCanDataWithList(arrayList);
    }

    public void registIMBCanVehicleLkaSlaStatusListener(IMBCanVehicleLkaSlaStatusCallback iMBCanVehicleLkaSlaStatusCallback) {
        this.mLkaSlaStatusCallback = iMBCanVehicleLkaSlaStatusCallback;
        ArrayList<MBCanDataType> arrayList = new ArrayList<>();
        arrayList.add(MBCanDataType.eMBCAN_VEHICLE_LKA_STATUS);
        subscribeCanDataWithList(arrayList);
    }

    public void unRegistIMBCanVehicleLkaSlaStatusListener() {
        this.mLkaSlaStatusCallback = null;
        ArrayList<MBCanDataType> arrayList = new ArrayList<>();
        arrayList.add(MBCanDataType.eMBCAN_VEHICLE_LKA_STATUS);
        unSubscribeCanDataWithList(arrayList);
    }

    public void registIMBVehicleFrmDectInfoListener(IMBCanVehicleFrmDectInfoCallback iMBCanVehicleFrmDectInfoCallback) {
        this.mFrmDectInfoCalback = iMBCanVehicleFrmDectInfoCallback;
        ArrayList<MBCanDataType> arrayList = new ArrayList<>();
        arrayList.add(MBCanDataType.eMBCAN_VEHICLE_FRM_INFO);
        subscribeCanDataWithList(arrayList);
    }

    public void unRegistIMBVehicleFrmDectInfoListener() {
        this.mFrmDectInfoCalback = null;
        ArrayList<MBCanDataType> arrayList = new ArrayList<>();
        arrayList.add(MBCanDataType.eMBCAN_VEHICLE_FRM_INFO);
        unSubscribeCanDataWithList(arrayList);
    }

    public void registIMBChimeStatusListener(IMBCanChimeStatusCallback iMBCanChimeStatusCallback) {
        this.mChimeStatusCallback = iMBCanChimeStatusCallback;
        ArrayList<MBCanDataType> arrayList = new ArrayList<>();
        arrayList.add(MBCanDataType.eMBCAN_CHIME_STATUS);
        subscribeCanDataWithList(arrayList);
    }

    public void unRegistIMBChimeStatusListener() {
        this.mChimeStatusCallback = null;
        ArrayList<MBCanDataType> arrayList = new ArrayList<>();
        arrayList.add(MBCanDataType.eMBCAN_CHIME_STATUS);
        unSubscribeCanDataWithList(arrayList);
    }

    public void registIMBBsdAlarmListener(IMbCanBsdAlarmCallback iMbCanBsdAlarmCallback) {
        this.mBsdAlarmCallback = iMbCanBsdAlarmCallback;
        ArrayList<MBCanDataType> arrayList = new ArrayList<>();
        arrayList.add(MBCanDataType.eMBCAN_BSD_ALARM);
        subscribeCanDataWithList(arrayList);
    }

    public void unRegistIMBBsdAlarmListener() {
        this.mBsdAlarmCallback = null;
        ArrayList<MBCanDataType> arrayList = new ArrayList<>();
        arrayList.add(MBCanDataType.eMBCAN_BSD_ALARM);
        unSubscribeCanDataWithList(arrayList);
    }

    public void registIMBDowAlarmListener(IMbCanDowAlarmCallback iMbCanDowAlarmCallback) {
        this.mDowAlarmCallback = iMbCanDowAlarmCallback;
        ArrayList<MBCanDataType> arrayList = new ArrayList<>();
        arrayList.add(MBCanDataType.eMBCAN_DOW_ALARM);
        subscribeCanDataWithList(arrayList);
    }

    public void unRegistIMBDowAlarmListener() {
        this.mDowAlarmCallback = null;
        ArrayList<MBCanDataType> arrayList = new ArrayList<>();
        arrayList.add(MBCanDataType.eMBCAN_DOW_ALARM);
        unSubscribeCanDataWithList(arrayList);
    }

    public void registIMBRCTAAlarmListener(IMbCanRCTAAlarmCallback iMbCanRCTAAlarmCallback) {
        this.mRCTAAlarmCallback = iMbCanRCTAAlarmCallback;
        ArrayList<MBCanDataType> arrayList = new ArrayList<>();
        arrayList.add(MBCanDataType.eMBCAN_RCTA_ALARM);
        subscribeCanDataWithList(arrayList);
    }

    public void unRegistIMBRCTAAlarmListener() {
        this.mRCTAAlarmCallback = null;
        ArrayList<MBCanDataType> arrayList = new ArrayList<>();
        arrayList.add(MBCanDataType.eMBCAN_RCTA_ALARM);
        unSubscribeCanDataWithList(arrayList);
    }

    public void registIMBCarSettingsListener(IMBCanSettingsCallback iMBCanSettingsCallback) {
        this.mCanSettingsCallback = iMBCanSettingsCallback;
        ArrayList<MBCanDataType> arrayList = new ArrayList<>();
        arrayList.add(MBCanDataType.eMBCAN_VEHICLE_AQS_STATUS);
        arrayList.add(MBCanDataType.eMBCAN_VEHICLE_TIRE);
        arrayList.add(MBCanDataType.eMBCAN_VEHICLE_TOTALODOMETER);
        arrayList.add(MBCanDataType.eMBCAN_VEHICLE_ENGINE);
        arrayList.add(MBCanDataType.eMBCAN_VEHICLE_ENGINE_GEAR);
        arrayList.add(MBCanDataType.eMBCAN_VEHICLE_GEAR);
        arrayList.add(MBCanDataType.eMBCAN_VEHICLE_ICM_INFO);
        arrayList.add(MBCanDataType.eMBCAN_VEHICLE_ICM_FAULT_INFO);
        arrayList.add(MBCanDataType.eMBCAN_ICM_TRIP_INFO);
        arrayList.add(MBCanDataType.eMBCAN_VEHICLE_BCM_STATUS);
        arrayList.add(MBCanDataType.eMBCAN_VEHICLE_EBS_SOC);
        arrayList.add(MBCanDataType.eMBCAN_VEHICLE_FUELLEVEL);
        arrayList.add(MBCanDataType.eMBCAN_VEHICLE_SPEED);
        arrayList.add(MBCanDataType.eMBCAN_VEHICLE_INVERTER_STATUS);
        arrayList.add(MBCanDataType.eMBCAN_VEHICLE_ACCSTATUS);
        arrayList.add(MBCanDataType.eMBCAN_WPC_STATUS);
        arrayList.add(MBCanDataType.eMBCAN_VEHICLE_LKA_STATUS);
        arrayList.add(MBCanDataType.eMBCAN_VEHICLE_CEM_FRAG);
        arrayList.add(MBCanDataType.eMBCAN_VEHICLE_FUELTANK);
        arrayList.add(MBCanDataType.eMBCAN_CHARGING_RESERVE);
        arrayList.add(MBCanDataType.eMBCAN_VEHICLE_CONSUMPTION);
        subscribeCanDataWithList(arrayList);
    }

    public void unregistIMBCarSettingsListener() {
        this.mCanSettingsCallback = null;
        ArrayList<MBCanDataType> arrayList = new ArrayList<>();
        arrayList.add(MBCanDataType.eMBCAN_VEHICLE_AQS_STATUS);
        arrayList.add(MBCanDataType.eMBCAN_VEHICLE_TIRE);
        arrayList.add(MBCanDataType.eMBCAN_VEHICLE_TOTALODOMETER);
        arrayList.add(MBCanDataType.eMBCAN_VEHICLE_ENGINE);
        arrayList.add(MBCanDataType.eMBCAN_VEHICLE_ENGINE_GEAR);
        arrayList.add(MBCanDataType.eMBCAN_VEHICLE_GEAR);
        arrayList.add(MBCanDataType.eMBCAN_VEHICLE_ICM_INFO);
        arrayList.add(MBCanDataType.eMBCAN_VEHICLE_ICM_FAULT_INFO);
        arrayList.add(MBCanDataType.eMBCAN_ICM_TRIP_INFO);
        arrayList.add(MBCanDataType.eMBCAN_VEHICLE_EBS_SOC);
        arrayList.add(MBCanDataType.eMBCAN_VEHICLE_BCM_STATUS);
        arrayList.add(MBCanDataType.eMBCAN_VEHICLE_FUELLEVEL);
        arrayList.add(MBCanDataType.eMBCAN_VEHICLE_SPEED);
        arrayList.add(MBCanDataType.eMBCAN_VEHICLE_INVERTER_STATUS);
        arrayList.add(MBCanDataType.eMBCAN_VEHICLE_ACCSTATUS);
        arrayList.add(MBCanDataType.eMBCAN_WPC_STATUS);
        arrayList.add(MBCanDataType.eMBCAN_VEHICLE_LKA_STATUS);
        arrayList.add(MBCanDataType.eMBCAN_VEHICLE_CEM_FRAG);
        arrayList.add(MBCanDataType.eMBCAN_VEHICLE_FUELTANK);
        arrayList.add(MBCanDataType.eMBCAN_CHARGING_RESERVE);
        arrayList.add(MBCanDataType.eMBCAN_VEHICLE_CONSUMPTION);
        unSubscribeCanDataWithList(arrayList);
    }

    public void registCarDorListener(IMbCanVehicleDoorCallback iMbCanVehicleDoorCallback) {
        this.mbCanVehicleDoorCallback = iMbCanVehicleDoorCallback;
        ArrayList<MBCanDataType> arrayList = new ArrayList<>();
        arrayList.add(MBCanDataType.eMBCAN_VEHICLE_BCM_STATUS);
        subscribeCanDataWithList(arrayList);
    }

    public void unregistCarDorListener() {
        this.mbCanVehicleDoorCallback = null;
        ArrayList<MBCanDataType> arrayList = new ArrayList<>();
        arrayList.add(MBCanDataType.eMBCAN_VEHICLE_BCM_STATUS);
        unSubscribeCanDataWithList(arrayList);
    }

    public void registRadarSensorListener(IMbCanRadarSensorCallback iMbCanRadarSensorCallback) {
        this.mbCanRadarSensorCallback = iMbCanRadarSensorCallback;
        ArrayList<MBCanDataType> arrayList = new ArrayList<>();
        arrayList.add(MBCanDataType.eMBCAN_RADARSENSOR);
        subscribeCanDataWithList(arrayList);
    }

    public void unregistRadarSensorListener() {
        this.mbCanRadarSensorCallback = null;
        ArrayList<MBCanDataType> arrayList = new ArrayList<>();
        arrayList.add(MBCanDataType.eMBCAN_RADARSENSOR);
        unSubscribeCanDataWithList(arrayList);
    }

    public void registerICMAlarmInfoListener(IMbCanICMAlarmInfoCallback iMbCanICMAlarmInfoCallback) {
        this.mbCanICMAlarmInfoCallback = iMbCanICMAlarmInfoCallback;
        ArrayList<MBCanDataType> arrayList = new ArrayList<>();
        arrayList.add(MBCanDataType.eMBCAN_ICM_TRIP_INFO);
        arrayList.add(MBCanDataType.eMBCAN_ICM_ALARM_INFO);
        subscribeCanDataWithList(arrayList);
    }

    public void unregisterICMAlarmInfoListener() {
        this.mbCanICMAlarmInfoCallback = null;
        ArrayList<MBCanDataType> arrayList = new ArrayList<>();
        arrayList.add(MBCanDataType.eMBCAN_ICM_TRIP_INFO);
        arrayList.add(MBCanDataType.eMBCAN_ICM_ALARM_INFO);
        unSubscribeCanDataWithList(arrayList);
    }

    public void registerCanDVRParamInfoCallback(IMbCanDVRParamCallback iMbCanDVRParamCallback) {
        this.mbCanDVRParamInfoCallback = iMbCanDVRParamCallback;
        ArrayList<MBCanDataType> arrayList = new ArrayList<>();
        arrayList.add(MBCanDataType.eMBCAN_VEHICLE_DVR_PARAM);
        subscribeCanDataWithList(arrayList);
    }

    public void unregisterCanDVRParamInfoCallback() {
        this.mbCanDVRParamInfoCallback = null;
        ArrayList<MBCanDataType> arrayList = new ArrayList<>();
        arrayList.add(MBCanDataType.eMBCAN_VEHICLE_DVR_PARAM);
        unSubscribeCanDataWithList(arrayList);
    }

    public void registerImbCanTrackListener(IMBCanTrackListener iMBCanTrackListener) {
        this.mbCanTrackListener = iMBCanTrackListener;
        ArrayList<MBCanDataType> arrayList = new ArrayList<>();
        arrayList.add(MBCanDataType.eMBCAN_VEHICLE_GASPED_STATUS);
        arrayList.add(MBCanDataType.eMBCAN_VEHICLE_GEAR);
        arrayList.add(MBCanDataType.eMBCAN_VEHICLE_ENGINE);
        arrayList.add(MBCanDataType.eMBCAN_VEHICLE_ENGINE_GEAR);
        arrayList.add(MBCanDataType.eMBCAN_VEHICLE_SPEED);
        subscribeCanDataWithList(arrayList);
    }

    public void unregisterImbCanTrackListener() {
        this.mbCanTrackListener = null;
        ArrayList<MBCanDataType> arrayList = new ArrayList<>();
        arrayList.add(MBCanDataType.eMBCAN_VEHICLE_GASPED_STATUS);
        arrayList.add(MBCanDataType.eMBCAN_VEHICLE_GEAR);
        arrayList.add(MBCanDataType.eMBCAN_VEHICLE_ENGINE);
        arrayList.add(MBCanDataType.eMBCAN_VEHICLE_ENGINE_GEAR);
        arrayList.add(MBCanDataType.eMBCAN_VEHICLE_SPEED);
        unSubscribeCanDataWithList(arrayList);
    }

    public void registIMBCarVehicleCarControlCallback(IMbCanVehicleCarControlCallback iMbCanVehicleCarControlCallback) {
        this.mbCanVehicleCarControlCallback = iMbCanVehicleCarControlCallback;
        ArrayList<MBCanDataType> arrayList = new ArrayList<>();
        arrayList.add(MBCanDataType.eMBCAN_VEHICLE_EBS_SOC);
        arrayList.add(MBCanDataType.eMBCAN_VEHICLE_FUELLEVEL);
        subscribeCanDataWithList(arrayList);
    }

    public void unregistIMBCarVehicleCarControlCallback() {
        this.mbCanVehicleCarControlCallback = null;
        ArrayList<MBCanDataType> arrayList = new ArrayList<>();
        arrayList.add(MBCanDataType.eMBCAN_VEHICLE_EBS_SOC);
        arrayList.add(MBCanDataType.eMBCAN_VEHICLE_FUELLEVEL);
        unSubscribeCanDataWithList(arrayList);
    }

    public void registerImbS51AutoWashCallBack(IMbCanS51AutoWashCallBack iMbCanS51AutoWashCallBack) {
        this.mbCanS51AutoWashCallBack = iMbCanS51AutoWashCallBack;
        ArrayList<MBCanDataType> arrayList = new ArrayList<>();
        arrayList.add(MBCanDataType.eMBCAN_VEHICLE_EPB_STATUS);
        subscribeCanDataWithList(arrayList);
    }

    public void unregisterImbAutoWashCallBack() {
        this.mbCanS51AutoWashCallBack = null;
        ArrayList<MBCanDataType> arrayList = new ArrayList<>();
        arrayList.add(MBCanDataType.eMBCAN_VEHICLE_EPB_STATUS);
        unSubscribeCanDataWithList(arrayList);
    }

    public static void releaseInstance() {
        if (instance != null) {
            instance.destroy();
            canClientUnInit();
        }
        isCanClientInited = false;
        instance = null;
    }

    private int canClientInit() {
        Log.d(TAG, "canClientInit: ");
        nativeCanInit(this.mCanCallback);
        return 0;
    }

    private static void canClientUnInit() {
        nativeCanUnInit();
    }

    public String getVersion(int i) {
        String nativeCanGetVersion = nativeCanGetVersion(i);
        String str = TAG;
        Log.d(str, "getVersion: " + nativeCanGetVersion);
        return nativeCanGetVersion;
    }

    private int canClientSubscribe(ArrayList<MBCanSubscribeBase> arrayList) {
        int nativeCanSubscribe = (arrayList == null || arrayList.size() <= 0) ? -1 : nativeCanSubscribe(arrayList);
        String str = TAG;
        Log.d(str, "canClientSubscribe: " + nativeCanSubscribe);
        return nativeCanSubscribe;
    }

    private int canClientUnSubscribe(ArrayList<MBCanSubscribeBase> arrayList) {
        int nativeCanUnSubscribe = (arrayList == null || arrayList.size() <= 0) ? -1 : nativeCanUnSubscribe(arrayList);
        String str = TAG;
        Log.d(str, "canClientUnSubscribe: " + nativeCanUnSubscribe);
        return nativeCanUnSubscribe;
    }

    public int canSetVehicleParam(MBVehicleProperty mBVehicleProperty, int i) {
        int canSetVehicleParam = canSetVehicleParam(mBVehicleProperty.getValue(), i);
        String str = TAG;
        Log.d(str, "MBCAN_CLIENT_LOG: " + i);
        return canSetVehicleParam;
    }

    public int canSetVehicleParam(int i, int i2) {
        return nativeCanVehicleSet(i, i2);
    }

    public int canGetVehicleParam(MBVehicleProperty mBVehicleProperty) {
        return canGetVehicleParam(mBVehicleProperty.getValue());
    }

    public int canGetVehicleParam(int i) {
        return nativeCanVehicleGet(i);
    }

    public int canSetVehicleParamString(MBVehicleProperty mBVehicleProperty, String str) {
        return canSetVehicleParamString(mBVehicleProperty.getValue(), str);
    }

    public int canSetVehicleParamString(int i, String str) {
        return nativeCanVehicleSetString(i, str);
    }

    public String canGetVehicleParamString(MBVehicleProperty mBVehicleProperty) {
        return canGetVehicleParamString(mBVehicleProperty.getValue());
    }

    public String canGetVehicleParamString(int i) {
        return nativeCanVehicleGetString(i);
    }

    public int canSetDmsParam(MBCanDmsCommand mBCanDmsCommand, int i) {
        return canSetDmsParam(mBCanDmsCommand.getValue(), i);
    }

    public int canSetDmsParam(int i, int i2) {
        return nativeCanDmsSet(i, i2);
    }

    public int canGetDmsParam(MBCanDmsCommand mBCanDmsCommand) {
        return canGetDmsParam(mBCanDmsCommand.getValue());
    }

    public int canGetDmsParam(int i) {
        return nativeCanDmsGet(i);
    }

    public int canSetAudioParam(MBAudioProperty mBAudioProperty, int i) {
        return canSetAudioParam(mBAudioProperty.getValue(), i);
    }

    public int canSetAudioParam(int i, int i2) {
        return nativeCanAudioSet(i, i2);
    }

    public int canGetAudioParam(MBAudioProperty mBAudioProperty) {
        return canGetAudioParam(mBAudioProperty.getValue());
    }

    public int canGetAudioParam(int i) {
        return nativeCanAudioGet(i);
    }

    public int canAudioMix(byte b, byte b2, byte b3) {
        return nativeCanAudioMix(b, b2, b3);
    }

    public int canAudioSetMusicCloundness(MBMusicCloundness mBMusicCloundness) {
        if (mBMusicCloundness != null) {
            return nativeCanSetMusicLoudness(mBMusicCloundness);
        }
        return -1;
    }

    public int canSetDateTime(int i, byte b, byte b2, byte b3, byte b4, byte b5, short s) {
        return nativeCanSetDateTime(i, b, b2, b3, b4, b5, s);
    }

    public int canUpgrade(int i, String str) {
        if (str == null || str.length() < 1) {
            return -10000;
        }
        return nativeCanUpgrade(i, str);
    }

    public int canSimulationStart(String str, int i) {
        if (str == null || str.length() < 1) {
            return -1;
        }
        return nativeCanSimulationStart(str, i);
    }

    public int canSimulationStop() {
        return nativeCanSimulationStop();
    }

    public ArrayList<String> getCanSimultaionFileList() {
        Log.d(TAG, "getCanSimultaionFileList: fileName = str");
        ArrayList<String> arrayList = (ArrayList) nativeGetSimulationList();
        if (arrayList != null && arrayList.size() > 0) {
            Iterator<String> it = arrayList.iterator();
            while (it.hasNext()) {
                String str = TAG;
                Log.d(str, "getCanSimultaionFileList: fileName = " + it.next());
            }
        }
        String str2 = TAG;
        StringBuilder sb = new StringBuilder();
        sb.append("getCanSimultaionFileList: size=");
        sb.append(arrayList != null ? arrayList.size() : 0);
        Log.d(str2, sb.toString());
        return arrayList;
    }

    private Class getClsWithDataType(int i) {
        switch (i) {
            case 1:
            case 20:
                return MBCanVehicleSpeed.class;
            case 2:
                return MBCanVehicleTurnLight.class;
            case 3:
                return MBCanVehicleSteeringAngle.class;
            case 4:
                return MBCanVehicleWheel.class;
            case 5:
                return MBCanVehicleDoor.class;
            case 6:
            case 8:
                return MBCanVehicleAccStatus.class;
            case 7:
                return MBCanRadarSensor.class;
            case 9:
                return MBHardKey.class;
            case 10:
                return MBCanSeatStatus.class;
            case 11:
                return MBCanRctaAlarm.class;
            case 12:
                return MBCanVehicleFuelLevel.class;
            case 13:
                return MBCanDvrStatus.class;
            case 14:
                return MBCanWpcStatus.class;
            case 15:
                return MBCanSeatBeltWarning.class;
            case 16:
                return MBCanTotalOdometer.class;
            case 17:
                return MBCanAvmStatus.class;
            case 18:
                return MBCanChime.class;
            case 19:
                return MBCanRadioFrequencyInfo.class;
            case 21:
                return MBCanVehicleBcmStatus.class;
            case 22:
                return MBCanVehicleEngine.class;
            case 23:
            case 24:
            case 25:
                return MBCanCfgItem.class;
            case 26:
            case 27:
                return Integer.class;
            case 28:
                return MBCanPM25.class;
            case 29:
                return MBCanVehicleEngine.class;
            case 30:
                return MBRadioProgramState.class;
            default:
                String str = TAG;
                Log.d(str, "getClsWithDataType: " + i);
                return null;
        }
    }

    public <T> T getMbCanData(int i, Class<T> cls) {
        Object nativeGetCanDataWithType = nativeGetCanDataWithType(i);
        if (nativeGetCanDataWithType != null) {
            if (nativeGetCanDataWithType.getClass() != cls) {
                String str = TAG;
                Log.d(str, "getMbCanData: Exception!!!, Class not matched.expected cls is " + cls.getSimpleName() + ",dataType = " + i);
            }
            try {
                return cls.cast(nativeGetCanDataWithType);
            } catch (Exception unused) {
                String str2 = TAG;
                Log.d(str2, "Exception!!! getMbCanData error, type = " + i);
            }
        }
        return null;
    }

    public byte[] canGetVehicleValue(MBVehicleProperty mBVehicleProperty) {
        return canGetVehicleValue(mBVehicleProperty.getValue());
    }

    public byte[] canGetVehicleValue(int i) {
        return nativeCanVehicleGetValue(i);
    }

    public int canSetApaCoordinate(short s, short s2) {
        return nativeSetAPACoordinate(s, s2);
    }

    public int canRadioSet(int i, int i2) {
        int nativeCanRadioSet = nativeCanRadioSet(i, i2);
        String str = TAG;
        Log.d(str, "canRadioSet: cmdType = " + i + ", value = " + i2);
        return nativeCanRadioSet;
    }

    public int canSetInstrument(int i, byte[] bArr) {
        if (bArr != null) {
            return nativeCanInstumentSet(i, bArr, bArr.length);
        }
        return -1;
    }

    public int canSetAirCondition(MBAirCondition mBAirCondition) {
        if (mBAirCondition == null) {
            return -1;
        }
        return nativeCanSetAirConditioner(mBAirCondition);
    }

    public int canSetWindowStatus(MBCanVehicleWindow mBCanVehicleWindow) {
        if (mBCanVehicleWindow == null) {
            return -1;
        }
        return nativeCanVehicleSetWindowSts(mBCanVehicleWindow);
    }

    public int canUartTestCmd(int i) {
        return nativeCanUartTest(i);
    }

    public int canUartRequestResult() {
        return nativeCanUartRequestResult();
    }

    public int canSetKeyMode(int i) {
        return nativeCanSetKeyMode(i);
    }

    public int canSetVehicleDvrParam(byte[] bArr) {
        if (bArr != null) {
            return nativeCanVehicleSetDvrParam(bArr);
        }
        return -1;
    }

    public int canSetVehicleBTParam(String str) {
        byte[] ascllStr = getAscllStr(str);
        if (ascllStr != null) {
            return nativeCanVehicleSetBTParam(ascllStr);
        }
        return -1;
    }

    public int canSetVehicleBookChg(MBCanBookChargeTime mBCanBookChargeTime) {
        if (mBCanBookChargeTime != null) {
            return nativeCanVehicleSetBookChg(mBCanBookChargeTime);
        }
        return -1;
    }

    private byte[] getAscllStr(String str) {
        if (str == null) {
            Log.e(TAG, "input null error");
            return null;
        } else if (!str.matches("^[A-Fa-f0-9]{12}")) {
            String str2 = TAG;
            Log.e(str2, "input string format error " + str);
            return null;
        } else {
            byte[] bArr = new byte[6];
            strToHex(str, bArr);
            String str3 = TAG;
            Log.d(str3, "byte array = " + Arrays.toString(bArr));
            return bArr;
        }
    }

    private void strToHex(String str, byte[] bArr) {
        int length = str.length();
        int i = 0;
        int i2 = 0;
        while (i < length) {
            int i3 = i + 1;
            bArr[i2] = (byte) ((getAsciiKey((byte) str.charAt(i)) << 4) | getAsciiKey((byte) str.charAt(i3)));
            i = i3 + 1;
            i2++;
        }
    }
}