package vad.dashing.tbox.phone

import android.bluetooth.BluetoothDevice

/**
 * Reflective HFP Client commands on a `BluetoothHeadsetClient` proxy. Each returns `true` only
 * when the stack reported the command as issued: a `false` from the proxy is a refusal.
 */
internal object HeadsetClientCommands {
    const val HEADSET_CLIENT_CALL_CLASS = "android.bluetooth.BluetoothHeadsetClientCall"

    /** `BluetoothHeadsetClient.CALL_ACCEPT_NONE`. */
    private const val CALL_ACCEPT_NONE = 0

    private val deviceType = BluetoothDevice::class.java
    private val intType = Int::class.javaPrimitiveType!!

    fun accept(proxy: Any, device: BluetoothDevice?): Boolean {
        val withFlag = invokeStackCommand(
            proxy,
            "acceptCall",
            arrayOf(deviceType, intType),
            device,
            CALL_ACCEPT_NONE,
        )
        return (withFlag ?: invokeStackCommand(proxy, "acceptCall", arrayOf(deviceType), device)) == true
    }

    /**
     * A [ringing] call can only be rejected: AOSP `HeadsetClientStateMachine.terminateCall()`
     * ends dialing / alerting / active / held calls and skips a ringing one without an error.
     */
    fun hangup(proxy: Any, device: BluetoothDevice?, call: Any?, ringing: Boolean): Boolean {
        if (ringing) {
            return invokeStackCommand(proxy, "rejectCall", arrayOf(deviceType), device) == true
        }
        val callType = runCatching { Class.forName(HEADSET_CLIENT_CALL_CLASS) }.getOrNull()
        if (call != null && callType != null) {
            val byCall = invokeStackCommand(
                proxy,
                "terminateCall",
                arrayOf(deviceType, callType),
                device,
                call,
            )
            if (byCall == true) return true
        }
        return invokeStackCommand(proxy, "terminateCall", arrayOf(deviceType, intType), device, 0) == true
    }
}

/**
 * `null` when [name] does not resolve or throws, so the caller may try another path; otherwise
 * whether [target] accepted the command (a `boolean` result as returned, `void` as accepted).
 */
internal fun invokeStackCommand(
    target: Any,
    name: String,
    parameterTypes: Array<Class<*>>,
    vararg args: Any?,
): Boolean? = runCatching {
    val method = target.javaClass.getMethod(name, *parameterTypes)
    val result = method.invoke(target, *args)
    if (method.returnType == Void.TYPE) true else result == true
}.getOrNull()
