package vad.dashing.tbox.adb

import android.content.Context
import java.io.File
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

    // A10: the CarPlay port is a dual-role controller kept in host mode; persist.usb.adbenable
    // has no consumer there, the adb gadget is always configured and only the data role flips.
    private const val DUAL_ROLE_DIR = "/sys/class/dual_role_usb/dual-role-usb20"
    private const val USB_GADGET_UDC = "/config/usb_gadget/g1/UDC"
    private const val USB_UDC = "13010000.cdncusb"
    internal const val PORT_MODE_DEVICE = "ufp"
    internal const val PORT_MODE_HOST = "dfp"

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
            val portMode = readPortMode()
            _state.value = State(
                tcpEnabled = isTcpPortPropEnabled(persistTcp, serviceTcp),
                usbEnabled = if (portMode != null) portMode == PORT_MODE_DEVICE else isUsbAdbPropEnabled(usb),
                readFailed = false,
            )
        }
    }

    suspend fun setTcpEnabled(enabled: Boolean) {
        AdbShutdownGate.withIntentionalTransportCloseSuspending {
            if (applyCommands(tcpCommands(enabled))) {
                AdbRepository.dropConnectionAfterAdbdRestart("adbd restarted")
            }
        }
    }

    suspend fun setUsbEnabled(context: Context, enabled: Boolean) {
        if (!File(DUAL_ROLE_DIR, "mode").exists()) {
            applyCommands(usbCommands(enabled))
            return
        }
        applyMutex.withLock {
            setUsbPortRole(context.applicationContext, enabled)
            refresh()
        }
    }

    private suspend fun setUsbPortRole(app: Context, enabled: Boolean) {
        val session = LocalhostAdbSession.run(
            gateway = LocalhostAdbSession.AndroidGateway(),
            keysDir = app.filesDir.resolve("adb"),
            clientName = LocalhostAdbSession.defaultClientName(),
        ) { execute ->
            execute(usbPortRoleCommand(enabled))
        }
        val expected = if (enabled) PORT_MODE_DEVICE else PORT_MODE_HOST
        val error = when (session) {
            is LocalhostAdbSession.Result.Failed -> "adb ${session.reason.name}"
            is LocalhostAdbSession.Result.Ok -> {
                val mode = session.value.stdout.lineSequence().map { it.trim() }.lastOrNull { it.isNotEmpty() }
                if (mode == expected) {
                    null
                } else {
                    "USB port mode ${mode ?: "?"} != $expected ${session.value.stderr.trim()}".trim()
                }
            }
        }
        if (error == null) {
            TboxRepository.addLog(level = "INFO", tag = TAG, message = "USB port role applied: $expected")
        } else {
            _lastError.value = error
            TboxRepository.addLog(level = "ERROR", tag = TAG, message = "USB port role apply failed: $error")
        }
    }

    internal fun usbPortRoleCommand(enabled: Boolean): String {
        val script = if (enabled) {
            "echo device > $DUAL_ROLE_DIR/data_role; sleep 1; " +
                "[ -n \"\$(cat $USB_GADGET_UDC)\" ] || echo $USB_UDC > $USB_GADGET_UDC; " +
                "cat $DUAL_ROLE_DIR/mode"
        } else {
            "echo host > $DUAL_ROLE_DIR/data_role; sleep 1; cat $DUAL_ROLE_DIR/mode"
        }
        return "su 0 sh -c '$script'"
    }

    private fun readPortMode(): String? = try {
        File(DUAL_ROLE_DIR, "mode").takeIf { it.exists() }?.readText()?.trim()
    } catch (_: Exception) {
        null
    }

    private suspend fun applyCommands(commands: List<List<String>>): Boolean {
        var applied = false
        applyMutex.withLock {
            withContext(Dispatchers.IO) {
                val errors = commands.mapNotNull { runCommand(it) }
                if (errors.isEmpty()) {
                    applied = true
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
        return applied
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
