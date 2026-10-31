package com.mengbo.mbdms;

import com.mengbo.mbdms.entity.DmsParamSet;
import com.mengbo.mbdms.entity.DmsUserInfo;
import com.mengbo.mbdms.interfaces.IDmsErrorStatusCallback;
import com.mengbo.mbdms.interfaces.IDmsImageCallback;
import com.mengbo.mbdms.interfaces.IDmsSetParamAllResultCallback;
import com.mengbo.mbdms.interfaces.IDriverDangerActionCallback;
import com.mengbo.mbdms.interfaces.IDriverDistractionCallback;
import com.mengbo.mbdms.interfaces.IDriverDrowsinessCallback;
import com.mengbo.mbdms.interfaces.IDriverGazeCallback;
import com.mengbo.mbdms.interfaces.IEmotionCallback;
import com.mengbo.mbdms.interfaces.IFaceFeatureCallback;
import com.mengbo.mbdms.interfaces.IFaceRecognizeCallback;
import com.mengbo.mbdms.interfaces.IFaceRegistCallback;
import com.mengbo.mbdms.interfaces.IFaceStatusCallback;
import com.mengbo.mbdms.interfaces.IFaceVerifyCallback;
import java.util.List;

@SuppressWarnings("rawtypes")
public class MBDms {
    static {
        System.loadLibrary("mbutils");
        System.loadLibrary("mbdms_sdk_android");
        System.loadLibrary("mbdms");
    }

    public native int nativeAccountOperate(DmsUserInfo userInfo, int op);

    public native int nativeFaceOperate(String accountId, int op);

    public native int nativeGetDmsParam(String accountId, int key);

    public native DmsParamSet nativeGetDmsParamAll(String accountId);

    public native List nativeGetUserInfo(String accountId);

    public native String nativeGetVersion();

    public native int nativeInitialize(
            IDriverGazeCallback driverGazeCallback,
            IFaceStatusCallback faceStatusCallback,
            IEmotionCallback emotionCallback,
            IFaceFeatureCallback faceFeatureCallback,
            IFaceRegistCallback faceRegistCallback,
            IFaceVerifyCallback faceVerifyCallback,
            IFaceRecognizeCallback faceRecognizeCallback,
            IDriverDrowsinessCallback driverDrowsinessCallback,
            IDriverDistractionCallback driverDistractionCallback,
            IDriverDangerActionCallback driverDangerActionCallback,
            IDmsImageCallback dmsImageCallback,
            IDmsErrorStatusCallback dmsErrorStatusCallback,
            IDmsSetParamAllResultCallback dmsSetParamAllResultCallback
    );

    public native int nativeSetCurrentUser(String accountId);

    public native int nativeSetDmsParam(String accountId, int key, int value);

    public native int nativeSetDmsParamAll(String accountId, DmsParamSet paramSet);

    public native int nativeShowDmsImage(int show);

    public native void nativeUninitialize();
}
