package com.mengbo.mbCan.interfaces

/**
 * CAN data callback from native MB stack (Mengbo). Unused for vehicle property polling;
 * an empty implementation is registered at init to satisfy [MBCanClient.nativeCanInit].
 */
interface ICanBaseCallback {
    fun onCanDataCallback(type: Int, data: Any?)
}
