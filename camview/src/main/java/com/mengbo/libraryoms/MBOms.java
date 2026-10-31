package com.mengbo.libraryoms;

import com.mengbo.libraryoms.entity.MbOmsErrorStatus;
import com.mengbo.libraryoms.entity.MbOmsFlowParam;
import com.mengbo.libraryoms.interfaces.IFeatureItemCallback;
import com.mengbo.libraryoms.interfaces.IOmsCameraOperationCallback;
import com.mengbo.libraryoms.interfaces.IOmsImageCallback;
import com.mengbo.libraryoms.interfaces.IOmsLeftCallback;

public class MBOms {
    static {
        System.loadLibrary("mbutils");
        System.loadLibrary("mboms_sdk_android");
        System.loadLibrary("libraryoms");
    }

    public native MbOmsErrorStatus nativeGetErrorState();

    public native MbOmsFlowParam nativeGetFlowState();

    public native String nativeGetVersion();

    public native int nativeOmsInit(
            IFeatureItemCallback featureItemCallback,
            IOmsImageCallback imageCallback,
            IOmsLeftCallback leftCallback,
            IOmsCameraOperationCallback cameraOperationCallback
    );

    public native void nativeOmsUnInit();

    public native int nativeSetSwitch(int flowSwitchType, int enabled);
}
