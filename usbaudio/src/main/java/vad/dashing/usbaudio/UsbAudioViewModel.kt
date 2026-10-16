package vad.dashing.usbaudio

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vad.dashing.usbaudio.device.RootShell

enum class UsbAudioAction { Refresh, Apply, Restore }

enum class UsbAudioBannerKind { Info, Warning, Success, Error }

data class UsbAudioBanner(val text: String, val kind: UsbAudioBannerKind)

data class UsbAudioState(
    val facts: List<Pair<String, String>> = emptyList(),
    val actions: Set<UsbAudioAction> = emptySet(),
    val busy: Boolean = false,
    val banner: UsbAudioBanner? = null,
)

class UsbAudioViewModel(app: Application) : AndroidViewModel(app) {
    private val keysDir = File(app.filesDir, "adb")
    private val _state = MutableStateFlow(UsbAudioState())
    val state: StateFlow<UsbAudioState> = _state.asStateFlow()
    private var job: Job? = null

    init {
        refresh()
    }

    fun refresh() = launch { inspect() }

    fun apply() = launch { runScript(permanent = true) }

    fun restore() = launch { runScript(permanent = false) }

    private fun launch(block: suspend () -> Unit) {
        if (job?.isActive == true) return
        job = viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(busy = true, actions = emptySet()) }
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val text = e.message ?: e.javaClass.simpleName
                _state.update {
                    it.copy(banner = UsbAudioBanner(text, UsbAudioBannerKind.Error), actions = setOf(UsbAudioAction.Refresh))
                }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    private fun openShell(): RootShell = RootShell.open(keysDir, "usbaudio@${Build.MODEL}")

    private fun inspect() {
        val vendorMain = File(UsbAudioCodec.VENDOR_MAIN)
        val systemMain = File(UsbAudioCodec.SYSTEM_MAIN)
        val vendorContent = vendorMain.takeIf { it.isFile }?.readText()
        val systemContent = systemMain.takeIf { it.isFile }?.readText()
        if (vendorContent == null && systemContent == null) {
            throw IOException("нет аудиоконфига ни в /vendor, ни в /system")
        }
        val permanent = UsbAudioCodec.effectiveApplied(vendorMain.isFile, vendorContent, systemContent)
        val bind = runCatching { File("/proc/mounts").readText() }.getOrDefault("")
            .let { UsbAudioCodec.isBindMounted(it) }
        val card = File("/proc/asound/cards").readText().lineSequence().any { "USB-Audio" in it }
        val facts = buildList {
            add("USB-карта" to if (card) "видна (за хабом)" else "не видна — проверьте хаб")
            add(
                "Конфиг /vendor" to if (vendorMain.isFile) "есть" else "имя удалено (fallback на /system)",
            )
            add("Конфиг /system" to if (systemMain.isFile) "есть" else "нет")
            add("Модуль usb" to if (permanent) "активен" else "выключен (штатно)")
            add("Наложение TBox Monitor" to if (bind) "активно (до перезагрузки)" else "нет")
        }
        val (banner, actions) = when {
            permanent -> UsbAudioBanner("USB-аудио включён постоянно", UsbAudioBannerKind.Success) to
                setOf(UsbAudioAction.Restore, UsbAudioAction.Refresh)

            bind -> UsbAudioBanner(
                "USB-аудио включено наложением TBox Monitor — до перезагрузки ГУ. Чтобы включить навсегда, нажмите «Включить постоянно».",
                UsbAudioBannerKind.Warning,
            ) to setOf(UsbAudioAction.Apply, UsbAudioAction.Refresh)

            else -> UsbAudioBanner(
                "USB-аудио выключен. «Включить» перепишет конфиг в /system, снимет на время защиту eMMC и перезапустит звук (пару секунд тишины).",
                UsbAudioBannerKind.Info,
            ) to setOf(UsbAudioAction.Apply, UsbAudioAction.Refresh)
        }
        _state.update { it.copy(facts = facts, banner = banner, actions = actions) }
    }

    private suspend fun runScript(permanent: Boolean) {
        val source = File(UsbAudioCodec.SYSTEM_MAIN).takeIf { it.isFile }?.readText()
            ?: File(UsbAudioCodec.VENDOR_MAIN).takeIf { it.isFile }?.readText()
            ?: throw IOException("нет аудиоконфига для трансформации")
        val config = if (permanent) {
            UsbAudioCodec.buildApplyConfig(source) ?: throw IOException("неожиданный формат аудиоконфига (нет секции usb)")
        } else {
            UsbAudioCodec.buildRestoreConfig(source)
        }
        openShell().use { shell ->
            if (shell.rootText("id -u").trim() != "0") throw IOException("su не дал root")
            shell.rootText("mkdir -p ${UsbAudioCodec.WORK_DIR} && chmod 700 ${UsbAudioCodec.WORK_DIR}")
            shell.writeFile(UsbAudioCodec.STAGED_CONFIG, config.toByteArray(Charsets.UTF_8))
            val script = if (permanent) UsbAudioCodec.applyScript() else UsbAudioCodec.restoreScript()
            val scriptPath = if (permanent) UsbAudioCodec.APPLY_SCRIPT else UsbAudioCodec.RESTORE_SCRIPT
            shell.writeFile(scriptPath, script.toByteArray(Charsets.UTF_8))
            val output = shell.root("sh $scriptPath")
            when (val outcome = UsbAudioCodec.parseOutcome("${output.stdout}\n${output.stderr}")) {
                is UsbAudioCodec.Outcome.Failed -> throw IOException("скрипт: ${outcome.code}")
                else -> Unit
            }
        }
        inspect()
    }
}
