package com.mengbo.mbCan;

import com.mengbo.mbCan.entity.MBAirCondition;
import com.mengbo.mbCan.entity.MBCanBookChargeTime;
import com.mengbo.mbCan.entity.MBCanVehicleWindow;
import com.mengbo.mbCan.entity.MBMusicCloundness;
import com.mengbo.mbCan.interfaces.ICanBaseCallback;

import java.util.List;

/**
 * JNI surface for {@code libmbCan.so} (same method names/signatures as Mengbo reference APK).
 */
public class MBCanClient {

    public MBCanClient() {
    }

    public static native void nativeCanUnInit();

    public native int nativeCanAudioGet(int property);

    public native int nativeCanAudioMix(byte a, byte b, byte c);

    public native int nativeCanAudioSet(int property, int value);

    public native int nativeCanDmsGet(int cmd);

    public native int nativeCanDmsSet(int cmd, int value);

    public native String nativeCanGetVersion(int type);

    public native int nativeCanInit(ICanBaseCallback callback);

    public native int nativeCanInstumentSet(int type, byte[] data, int len);

    public native int nativeCanRadioSet(int cmd, int value);

    public native int nativeCanSetAirConditioner(MBAirCondition condition);

    public native int nativeCanSetDateTime(
            byte year,
            byte month,
            byte day,
            byte hour,
            byte minute,
            short second
    );

    public native int nativeCanSetKeyMode(int mode);

    public native int nativeCanSetMusicLoudness(MBMusicCloundness loudness);

    public native int nativeCanSimulationStart(String name, int param);

    public native int nativeCanSimulationStop();

    public native int nativeCanSubscribe(List<?> subscriptions);

    public native int nativeCanUartRequestResult();

    public native int nativeCanUartTest(int cmd);

    public native int nativeCanUnSubscribe(List<?> subscriptions);

    public native int nativeCanUpgrade(int type, String path);

    public native int nativeCanVehicleGet(int property);

    public native String nativeCanVehicleGetString(int property);

    public native byte[] nativeCanVehicleGetValue(int property);

    public native int nativeCanVehicleSet(int property, int value);

    public native int nativeCanVehicleSetBTParam(byte[] param);

    public native int nativeCanVehicleSetBookChg(MBCanBookChargeTime time);

    public native int nativeCanVehicleSetDvrParam(byte[] param);

    public native int nativeCanVehicleSetString(int property, String value);

    public native int nativeCanVehicleSetWindowSts(MBCanVehicleWindow window);

    public native Object nativeGetCanDataWithType(int dataType);

    public native List<?> nativeGetSimulationList();

    public native int nativeSetAPACoordinate(short x, short y);
}
