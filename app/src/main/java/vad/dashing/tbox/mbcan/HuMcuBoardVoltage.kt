package vad.dashing.tbox.mbcan

import com.adayo.mcucommproxy.DevStatusMcuCallback
import com.adayo.mcucommproxy.McuCommManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import vad.dashing.tbox.HeadUnitCanMode
import vad.dashing.tbox.TboxRepository

/**
 * Board voltage from the Android 10 MCU dev-status stream (`functionId` 3, frame `0x8305`).
 * VHAL does not publish this value. Widgets and automations hold a subscription while they
 * read it; the JNI callback is registered only on Android 10.
 */
internal object HuMcuBoardVoltage {
    /** `McuCommManager.functionID.PROTOCOL_CMD_DEV_STATUS`. */
    private const val DEV_STATUS: Byte = 3

    private val lock = Any()
    private val holders = mutableSetOf<String>()
    private var registered = false
    private var failureLogged = false

    private val callback = DevStatusMcuCallback { msgbuf, length ->
        BoardVoltageCodec.decode(msgbuf, length)?.let { volts ->
            if (_state.value == null) {
                TboxRepository.addLog("INFO", "MCU", "Board voltage $volts V")
            }
            _state.value = volts
        }
    }

    private val _state = MutableStateFlow<Float?>(null)
    val state: StateFlow<Float?> = _state.asStateFlow()

    fun setHeld(holder: String, held: Boolean) {
        val id = holder.trim()
        if (id.isEmpty()) return
        synchronized(lock) {
            if (held) holders += id else holders -= id
            if (holders.isEmpty()) {
                if (registered) stop()
                registered = false
                _state.value = null
                return
            }
            if (!registered) registered = start()
        }
    }

    private fun start(): Boolean {
        if (UniversalCanRepository.mode.value != HeadUnitCanMode.Android10Vhal) return false
        if (!McuCommManager.ensureLibraryLoaded()) {
            if (!failureLogged) {
                failureLogged = true
                TboxRepository.addLog("WARN", "MCU", "Board voltage: mcucommproxy_jni not loaded")
            }
            return false
        }
        return try {
            val connected = McuCommManager.connectMcuCommServer()
            val accepted = connected && McuCommManager.registMcuCommCallback(callback, DEV_STATUS)
            if (!accepted && !failureLogged) {
                failureLogged = true
                TboxRepository.addLog(
                    "WARN",
                    "MCU",
                    if (connected) {
                        "Board voltage: MCU callback rejected"
                    } else {
                        "Board voltage: MCU server not connected"
                    },
                )
            }
            if (accepted) failureLogged = false
            accepted
        } catch (error: Throwable) {
            if (!failureLogged) {
                failureLogged = true
                TboxRepository.addLog(
                    "WARN",
                    "MCU",
                    "Board voltage: ${error.message ?: error.javaClass.simpleName}",
                )
            }
            false
        }
    }

    private fun stop() {
        try {
            if (McuCommManager.ensureLibraryLoaded()) {
                McuCommManager.unregistMcuCommCallback(callback, DEV_STATUS)
            }
        } catch (_: Throwable) {
        }
    }
}
