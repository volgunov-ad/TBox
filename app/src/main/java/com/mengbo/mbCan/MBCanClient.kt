package com.mengbo.mbCan

import com.mengbo.mbCan.entity.MBCanBookChargeTime
import com.mengbo.mbCan.entity.MBAirCondition
import com.mengbo.mbCan.entity.MBMusicCloundness
import com.mengbo.mbCan.entity.MBCanVehicleWindow
import com.mengbo.mbCan.interfaces.ICanBaseCallback

/**
 * JNI surface matching Mengbo Factory / tboxservice APK ([MB_FactoryMode.apk]).
 * Native library loads [libmbcanclient.so] + [libmbCan.so] from jniLibs.
 */
open class MBCanClient {
    companion object {
        @JvmStatic
        external fun nativeCanUnInit()
    }

    protected external fun nativeCanInit(callback: ICanBaseCallback): Int
    protected external fun nativeCanAudioGet(type: Int): Int
    protected external fun nativeCanAudioMix(a: Byte, b: Byte, c: Byte): Int
    protected external fun nativeCanAudioSet(type: Int, value: Int): Int
    protected external fun nativeCanDmsGet(type: Int): Int
    protected external fun nativeCanDmsSet(type: Int, value: Int): Int
    protected external fun nativeCanGetVersion(type: Int): String?
    protected external fun nativeCanInstumentSet(type: Int, data: ByteArray?, len: Int): Int
    protected external fun nativeCanRadioSet(cmd: Int, value: Int): Int
    protected external fun nativeCanSetAirConditioner(ac: MBAirCondition): Int
    protected external fun nativeCanSetDateTime(
        a: Int,
        b: Byte,
        c: Byte,
        d: Byte,
        e: Byte,
        f: Byte,
        s: Short,
    ): Int
    protected external fun nativeCanSetKeyMode(mode: Int): Int
    protected external fun nativeCanSetMusicLoudness(loudness: MBMusicCloundness): Int
    protected external fun nativeCanSimulationStart(name: String?, flags: Int): Int
    protected external fun nativeCanSimulationStop(): Int
    external fun nativeCanSubscribe(types: List<*>?): Int
    external fun nativeCanUnSubscribe(types: List<*>?): Int
    protected external fun nativeCanUartRequestResult(): Int
    protected external fun nativeCanUartTest(type: Int): Int
    protected external fun nativeCanUpgrade(type: Int, path: String?): Int
    protected external fun nativeCanVehicleGet(propertyId: Int): Int
    protected external fun nativeCanVehicleGetString(propertyId: Int): String?
    protected external fun nativeCanVehicleGetValue(propertyId: Int): ByteArray?
    protected external fun nativeCanVehicleSet(propertyId: Int, value: Int): Int
    protected external fun nativeCanVehicleSetBTParam(data: ByteArray?): Int
    protected external fun nativeCanVehicleSetBookChg(time: MBCanBookChargeTime): Int
    protected external fun nativeCanVehicleSetDvrParam(data: ByteArray?): Int
    protected external fun nativeCanVehicleSetString(propertyId: Int, value: String?): Int
    protected external fun nativeCanVehicleSetWindowSts(window: MBCanVehicleWindow): Int
    protected external fun nativeGetCanDataWithType(type: Int): Any?
    protected external fun nativeGetSimulationList(): List<*>?
    protected external fun nativeSetAPACoordinate(x: Short, y: Short): Int
}
