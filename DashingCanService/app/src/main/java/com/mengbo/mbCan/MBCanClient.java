package com.mengbo.mbCan;

import com.mengbo.mbCan.entity.MBAirCondition;
import com.mengbo.mbCan.entity.MBCanBookChargeTime;
import com.mengbo.mbCan.entity.MBCanSubscribeBase;
import com.mengbo.mbCan.entity.MBCanVehicleWindow;
import com.mengbo.mbCan.entity.MBMusicCloundness;
import com.mengbo.mbCan.interfaces.ICanBaseCallback;
import java.util.List;

/* loaded from: classes.dex */
public class MBCanClient {
    /* JADX INFO: Access modifiers changed from: protected */
    public static native void nativeCanUnInit();

    /* JADX INFO: Access modifiers changed from: protected */
    public native int nativeCanAudioGet(int i);

    /* JADX INFO: Access modifiers changed from: protected */
    public native int nativeCanAudioMix(byte b, byte b2, byte b3);

    /* JADX INFO: Access modifiers changed from: protected */
    public native int nativeCanAudioSet(int i, int i2);

    /* JADX INFO: Access modifiers changed from: protected */
    public native int nativeCanDmsGet(int i);

    /* JADX INFO: Access modifiers changed from: protected */
    public native int nativeCanDmsSet(int i, int i2);

    /* JADX INFO: Access modifiers changed from: protected */
    public native String nativeCanGetVersion(int i);

    /* JADX INFO: Access modifiers changed from: protected */
    public native int nativeCanInit(ICanBaseCallback iCanBaseCallback);

    /* JADX INFO: Access modifiers changed from: protected */
    public native int nativeCanInstumentSet(int i, byte[] bArr, int i2);

    /* JADX INFO: Access modifiers changed from: protected */
    public native int nativeCanRadioSet(int i, int i2);

    /* JADX INFO: Access modifiers changed from: protected */
    public native int nativeCanSetAirConditioner(MBAirCondition mBAirCondition);

    /* JADX INFO: Access modifiers changed from: protected */
    public native int nativeCanSetDateTime(int i, byte b, byte b2, byte b3, byte b4, byte b5, short s);

    /* JADX INFO: Access modifiers changed from: protected */
    public native int nativeCanSetKeyMode(int i);

    /* JADX INFO: Access modifiers changed from: protected */
    public native int nativeCanSetMusicLoudness(MBMusicCloundness mBMusicCloundness);

    /* JADX INFO: Access modifiers changed from: protected */
    public native int nativeCanSimulationStart(String str, int i);

    /* JADX INFO: Access modifiers changed from: protected */
    public native int nativeCanSimulationStop();

    public native int nativeCanSubscribe(List<MBCanSubscribeBase> list);

    /* JADX INFO: Access modifiers changed from: protected */
    public native int nativeCanUartRequestResult();

    /* JADX INFO: Access modifiers changed from: protected */
    public native int nativeCanUartTest(int i);

    public native int nativeCanUnSubscribe(List<MBCanSubscribeBase> list);

    /* JADX INFO: Access modifiers changed from: protected */
    public native int nativeCanUpgrade(int i, String str);

    /* JADX INFO: Access modifiers changed from: protected */
    public native int nativeCanVehicleGet(int i);

    /* JADX INFO: Access modifiers changed from: protected */
    public native String nativeCanVehicleGetString(int i);

    /* JADX INFO: Access modifiers changed from: protected */
    public native byte[] nativeCanVehicleGetValue(int i);

    /* JADX INFO: Access modifiers changed from: protected */
    public native int nativeCanVehicleSet(int i, int i2);

    /* JADX INFO: Access modifiers changed from: protected */
    public native int nativeCanVehicleSetBTParam(byte[] bArr);

    /* JADX INFO: Access modifiers changed from: protected */
    public native int nativeCanVehicleSetBookChg(MBCanBookChargeTime mBCanBookChargeTime);

    /* JADX INFO: Access modifiers changed from: protected */
    public native int nativeCanVehicleSetDvrParam(byte[] bArr);

    /* JADX INFO: Access modifiers changed from: protected */
    public native int nativeCanVehicleSetString(int i, String str);

    /* JADX INFO: Access modifiers changed from: protected */
    public native int nativeCanVehicleSetWindowSts(MBCanVehicleWindow mBCanVehicleWindow);

    /* JADX INFO: Access modifiers changed from: protected */
    public native Object nativeGetCanDataWithType(int i);

    /* JADX INFO: Access modifiers changed from: protected */
    public native List<String> nativeGetSimulationList();

    /* JADX INFO: Access modifiers changed from: protected */
    public native int nativeSetAPACoordinate(short s, short s2);
}
