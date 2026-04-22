package com.mengbo.mbCan

/**
 * Forwards native [ICanBaseCallback.onCanDataCallback] to app code without coupling MB-CAN JNI
 * types to the TBox layer.
 */
object MbCanEngineCallbackRelay {
    @Volatile
    var listener: ((type: Int, data: Any?) -> Unit)? = null
}
