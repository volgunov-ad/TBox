package com.mengbo.mbavm;

import com.mengbo.mbavm.entity.AvmActivation;
import com.mengbo.mbavm.entity.AvmErrorStatus;
import com.mengbo.mbavm.entity.AvmParamSet;
import com.mengbo.mbavm.interfaces.IActivationState;
import com.mengbo.mbavm.interfaces.ICalibrationState;
import com.mengbo.mbavm.interfaces.ICamera;
import com.mengbo.mbavm.interfaces.IMachineOperateCallback;
import com.mengbo.mbavm.interfaces.IShowEvent;

public class MBAvm {
    static {
        System.loadLibrary("mbutils");
        System.loadLibrary("mbcanclient");
        System.loadLibrary("mbAVM_sdk_Android");
        System.loadLibrary("mbavm");
    }

    public native int nativeActivationState(AvmActivation activation);

    public native int nativeCalibrationBackup(int mode);

    public native AvmErrorStatus nativeGetErrorStatus();

    public native int nativeGetParam(int key);

    public native AvmParamSet nativeGetParamAll();

    public native int nativeInitialize(
            IShowEvent showEvent,
            ICamera camera,
            ICalibrationState calibrationState,
            IActivationState activationState,
            IMachineOperateCallback machineOperateCallback
    );

    public native int nativeSendViewCtrol(int view);

    public native int nativeSetCarLicence(int key, String licence);

    public native int nativeSetParam(int key, int value);

    public native int nativeSetParamAll(AvmParamSet paramSet);

    public native int nativeTestSetVehicle(int a, int b);

    public native int nativeTouchEvent(int type, short x, short y);

    public native void nativeUninitialize();
}
