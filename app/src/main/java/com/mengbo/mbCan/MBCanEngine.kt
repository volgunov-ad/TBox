package com.mengbo.mbCan

import android.util.Log
import com.mengbo.mbCan.defines.MBCanDataType
import com.mengbo.mbCan.entity.MBCanSubscribeBase
import com.mengbo.mbCan.interfaces.ICanBaseCallback

/**
 * Singleton for Mengbo MB-CAN (same JNI entry points as MB_FactoryMode / tboxservice).
 *
 * After [nativeCanInit], registers the same default subscription set as decompiled
 * [com.mengbo.mbCan.MBCanEngine] so [nativeGetCanDataWithType] and push callbacks receive
 * vehicle data on a real head unit.
 */
class MBCanEngine private constructor() : MBCanClient() {

    private val canCallback = object : ICanBaseCallback {
        override fun onCanDataCallback(type: Int, data: Any?) {
            MbCanEngineCallbackRelay.listener?.invoke(type, data)
        }
    }

    init {
        val initRc = try {
            nativeCanInit(canCallback)
        } catch (e: Throwable) {
            Log.e(TAG, "nativeCanInit failed", e)
            -1
        }
        if (initRc != 0) {
            Log.w(TAG, "nativeCanInit returned $initRc")
        }
        val bases = ArrayList<MBCanSubscribeBase>(64)
        for (t in MBCanDataType.entries) {
            if (t == MBCanDataType.eMBCAN_COUNT) continue
            bases.add(MBCanSubscribeBase(t.value, 0))
        }
        val subRc = try {
            nativeCanSubscribe(bases)
        } catch (e: Throwable) {
            Log.e(TAG, "nativeCanSubscribe failed", e)
            -1
        }
        if (subRc != 0) {
            Log.w(TAG, "nativeCanSubscribe returned $subRc (continuing; polling may still work)")
        } else {
            Log.i(TAG, "nativeCanSubscribe OK (${bases.size} types)")
        }
    }

    /**
     * Pull snapshot for a [MBCanDataType] ordinal (same contract as Mengbo
     * `MBCanEngine.getMbCanData(int, Class)`).
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> getMbCanData(dataType: Int, cls: Class<T>): T? {
        val o = try {
            nativeGetCanDataWithType(dataType)
        } catch (e: Throwable) {
            Log.d(TAG, "nativeGetCanDataWithType($dataType) failed: ${e.message}")
            null
        } ?: return null
        return if (cls.isInstance(o)) cls.cast(o) else {
            Log.d(
                TAG,
                "getMbCanData type=$dataType expected=${cls.simpleName} actual=${o.javaClass.simpleName}",
            )
            null
        }
    }

    fun canGetVehicleParam(propertyId: Int): Int = nativeCanVehicleGet(propertyId)

    fun canSetVehicleParam(propertyId: Int, value: Int): Int =
        nativeCanVehicleSet(propertyId, value)

    fun canGetVehicleValue(propertyId: Int): ByteArray? = nativeCanVehicleGetValue(propertyId)

    fun canGetVehicleParamString(propertyId: Int): String? =
        nativeCanVehicleGetString(propertyId)

    fun canSetVehicleParamString(propertyId: Int, value: String): Int =
        nativeCanVehicleSetString(propertyId, value)

    fun release() {
        synchronized(MBCanEngine::class.java) {
            try {
                nativeCanUnInit()
            } catch (_: Throwable) {
            }
            instance = null
        }
    }

    companion object {
        private const val TAG = "MBCanEngine"
        @Volatile
        private var nativeLoadTried: Boolean = false

        @Volatile
        private var nativeReady: Boolean = false

        @Volatile
        private var nativeLoadError: String? = null

        @Volatile
        private var instance: MBCanEngine? = null

        @JvmStatic
        fun availabilityError(): String? {
            ensureNativeLoaded()
            return nativeLoadError
        }

        @JvmStatic
        fun isAvailable(): Boolean {
            ensureNativeLoaded()
            return nativeReady
        }

        private fun ensureNativeLoaded() {
            if (nativeLoadTried) return
            synchronized(MBCanEngine::class.java) {
                if (nativeLoadTried) return
                nativeLoadTried = true
                try {
                    System.loadLibrary("mbcanclient")
                    System.loadLibrary("mbCan")
                    nativeReady = true
                } catch (t: Throwable) {
                    nativeLoadError = "${t::class.java.simpleName}: ${t.message ?: "unknown native load error"}"
                    Log.e(TAG, "Failed to load MB-CAN native libraries", t)
                }
            }
        }

        @JvmStatic
        fun getInstance(): MBCanEngine {
            ensureNativeLoaded()
            if (!nativeReady) {
                throw IllegalStateException(nativeLoadError ?: "MB-CAN native libraries are unavailable")
            }
            instance?.let { return it }
            return synchronized(MBCanEngine::class.java) {
                instance ?: MBCanEngine().also { instance = it }
            }
        }
    }
}
