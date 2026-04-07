package com.mengbo.mbCan

import com.mengbo.mbCan.interfaces.ICanBaseCallback

/**
 * Singleton entry point for Mengbo MB-CAN vehicle properties (same JNI layout as Factory Mode APK).
 */
class MBCanEngine private constructor() : MBCanClient() {

    private val canCallback = object : ICanBaseCallback {
        override fun onCanDataCallback(type: Int, data: Any?) {
            // Subscriptions not used; vehicle properties are polled via native getters.
        }
    }

    init {
        nativeCanInit(canCallback)
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
                // Best-effort teardown when leaving head unit stack.
            }
            instance = null
        }
    }

    companion object {
        init {
            System.loadLibrary("mbcanclient")
            System.loadLibrary("mbCan")
        }

        @Volatile
        private var instance: MBCanEngine? = null

        @JvmStatic
        fun getInstance(): MBCanEngine {
            instance?.let { return it }
            return synchronized(MBCanEngine::class.java) {
                instance ?: MBCanEngine().also { instance = it }
            }
        }
    }
}
