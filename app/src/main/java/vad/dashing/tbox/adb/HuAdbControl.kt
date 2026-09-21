package vad.dashing.tbox.adb

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import vad.dashing.tbox.TboxRepository

object HuAdbControl {
    private const val TAG = "HU_ADB"
    private const val PROP_PERSIST_TCP_PORT = "persist.adb.tcp.port"
    private const val PROP_SERVICE_TCP_PORT = "service.adb.tcp.port"
    private const val PROP_USB_ADB_ENABLE = "persist.usb.adbenable"
    private const val PROP_CTL_RESTART = "ctl.restart"
    private const val ADBD_PROCESS = "adbd"

    const val TCP_ENABLED_PORT = 5555
    const val TCP_DISABLED_PORT = -1

    data class State(
        val tcpEnabled: Boolean = false,
        val usbEnabled: Boolean = false,
        val readFailed: Boolean = false,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val applyMutex = Mutex()

    fun consumeError() {
        _lastError.value = null
    }

    suspend fun refresh() {
        withContext(Dispatchers.IO) {
            val persistTcp = readProp(PROP_PERSIST_TCP_PORT)
            val serviceTcp = readProp(PROP_SERVICE_TCP_PORT)
            val usb = readProp(PROP_USB_ADB_ENABLE)
            if (persistTcp == null && serviceTcp == null && usb == null) {
                _state.value = State(readFailed = true)
                return@withContext
            }
            _state.value = State(
                tcpEnabled = isTcpPortPropEnabled(persistTcp, serviceTcp),
                usbEnabled = isUsbAdbPropEnabled(usb),
                readFailed = false,
            )
        }
    }

    suspend fun setTcpEnabled(enabled: Boolean) {
        applyCommands(tcpCommands(enabled))
    }

    suspend fun setUsbEnabled(enabled: Boolean) {
        applyCommands(usbCommands(enabled))
    }

    private suspend fun applyCommands(commands: List<List<String>>) {
        applyMutex.withLock {
            withContext(Dispatchers.IO) {
                val errors = commands.mapNotNull { runCommand(it) }
                if (errors.isEmpty()) {
                    TboxRepository.addLog(
                        level = "INFO",
                        tag = TAG,
                        message = "adb mode applied: ${commands.joinToString("; ") { it.joinToString(" ") }}",
                    )
                } else {
                    _lastError.value = errors.joinToString("\n")
                    TboxRepository.addLog(
                        level = "ERROR",
                        tag = TAG,
                        message = "adb mode apply failed: ${errors.joinToString("; ")}",
                    )
                }
            }
            refresh()
        }
    }

    internal fun tcpCommands(enabled: Boolean): List<List<String>> {
        val port = tcpPropValue(enabled)
        return listOf(
            listOf("setprop", PROP_PERSIST_TCP_PORT, port),
            listOf("setprop", PROP_SERVICE_TCP_PORT, port),
            listOf("setprop", PROP_CTL_RESTART, ADBD_PROCESS),
        )
    }

    internal fun usbCommands(enabled: Boolean): List<List<String>> = listOf(
        listOf("setprop", PROP_USB_ADB_ENABLE, if (enabled) "1" else "0"),
    )

    internal fun tcpPropValue(enabled: Boolean): String =
        if (enabled) TCP_ENABLED_PORT.toString() else TCP_DISABLED_PORT.toString()

    internal fun isTcpPortPropEnabled(persistValue: String?, serviceValue: String?): Boolean {
        val persistPort = persistValue?.trim()?.toIntOrNull()
        if (persistPort != null) return persistPort > 0
        val servicePort = serviceValue?.trim()?.toIntOrNull()
        if (servicePort != null) return servicePort > 0
        return false
    }

    internal fun isUsbAdbPropEnabled(value: String?): Boolean = value?.trim() == "1"

    private fun readProp(name: String): String? = try {
        val process = ProcessBuilder("getprop", name).start()
        val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
        if (process.waitFor(5, TimeUnit.SECONDS)) output else null
    } catch (e: Exception) {
        null
    }

    private fun runCommand(command: List<String>): String? = try {
        val process = ProcessBuilder(command).start()
        val errorText = process.errorStream.bufferedReader().use { it.readText() }.trim()
        if (!process.waitFor(5, TimeUnit.SECONDS)) {
            process.destroy()
            "timeout: ${command.joinToString(" ")}"
        } else if (process.exitValue() != 0) {
            errorText.ifBlank { "exit ${process.exitValue()}: ${command.joinToString(" ")}" }
        } else {
            null
        }
    } catch (e: Exception) {
        "${e.javaClass.simpleName}: ${e.message.orEmpty()}".trim() +
            " (${command.joinToString(" ")})"
    }
}
