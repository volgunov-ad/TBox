package vad.dashing.tbox

import android.os.IBinder
import android.util.Log

/**
 * Reflection boundary for Adayo's unbundled SettingsSvcIfManager / Binder interface.
 * Obtains ServiceConstants' `adayo.setting.v2.0` Binder, then adapts it with the stock
 * generated Stub class when present; failures leave platform controls unavailable.
 */
internal object AdayoSettingsService {
    private const val TAG = "AdayoSettings"
    private const val ADAYO_SERVICE = "adayo.setting.v2.0"
    private const val REMOTE_ERROR = -6

    /** Stock SystemSettings uses [ISettingsServiceInterfaceAIDL]; manager facade is a fallback. */
    private val stubClassNames = listOf(
        "com.adayo.proxy.setting.system.aidl.ISettingsServiceInterfaceAIDL\$Stub",
        "com.adayo.proxy.setting.system.SettingsSvcIfManager",
    )

    fun isAvailable(): Boolean = service() != null

    fun getInt(methodName: String): Int? = invokeNumber(methodName, emptyArray(), emptyArray())

    fun setInt(methodName: String, value: Int): Boolean =
        invokeNumber(methodName, intParams(1), arrayOf(value)) != null

    fun getInt(methodName: String, arg: Int): Int? =
        invokeNumber(methodName, intParams(1), arrayOf(arg))

    fun setInt(methodName: String, arg1: Int, arg2: Int): Boolean =
        invokeNumber(methodName, intParams(2), arrayOf(arg1, arg2)) != null

    private fun intParams(count: Int): Array<Class<*>?> =
        Array(count) { Int::class.javaPrimitiveType }

    private fun invokeNumber(
        methodName: String,
        parameterTypes: Array<Class<*>?>,
        args: Array<Any>,
    ): Int? = runCatching {
        val target = service() ?: return null
        val method = target.javaClass.methods.firstOrNull { candidate ->
            candidate.name == methodName &&
                candidate.parameterTypes.size == parameterTypes.size &&
                candidate.parameterTypes.indices.all { i ->
                    candidate.parameterTypes[i] == parameterTypes[i]
                }
        } ?: return null
        val raw = method.invoke(target, *args)
        val value = when (raw) {
            null, Unit -> 0
            is Void -> 0
            is Boolean -> if (raw) 0 else return@runCatching null
            is Number -> raw.toInt()
            else -> return@runCatching null
        }
        value.takeIf { it != REMOTE_ERROR }
    }.onFailure { Log.w(TAG, "Adayo $methodName failed", it) }.getOrNull()

    private fun service(): Any? {
        runCatching {
            Class.forName("com.adayo.proxy.setting.system.SettingsSvcIfManager")
                .getMethod("getSettingsManager")
                .invoke(null)
        }.getOrNull()?.let { return it }

        val binder = runCatching {
            Class.forName("android.os.ServiceManager")
                .getMethod("getService", String::class.java)
                .invoke(null, ADAYO_SERVICE) as? IBinder
        }.getOrNull() ?: return null
        stubClassNames.forEach { name ->
            if (name.endsWith("SettingsSvcIfManager")) return@forEach
            val adapted = runCatching {
                Class.forName(name).getMethod("asInterface", IBinder::class.java).invoke(null, binder)
            }.getOrNull()
            if (adapted != null) return adapted
        }
        return null
    }
}
