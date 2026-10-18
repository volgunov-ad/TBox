package vad.dashing.usbaudio

import android.app.Application
import android.content.Context
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import vad.dashing.usbaudio.device.RootShell

enum class UsbAudioAction { Refresh, Apply, Restore }

enum class UsbAudioBannerKind { Info, Warning, Success, Error }

data class UsbAudioBanner(val text: String, val kind: UsbAudioBannerKind)

data class UsbVolumePanel(
    val card: Int? = null,
    val controlIndex: Int? = null,
    val value: Int? = null,
    val busy: Boolean = false,
    val error: String? = null,
)

data class UsbAudioState(
    val facts: List<Pair<String, String>> = emptyList(),
    val actions: Set<UsbAudioAction> = emptySet(),
    val busy: Boolean = false,
    val banner: UsbAudioBanner? = null,
    val devices: List<UsbDeviceInfo> = emptyList(),
    val volume: UsbVolumePanel = UsbVolumePanel(),
)

class UsbAudioViewModel(app: Application) : AndroidViewModel(app) {
    private val keysDir = File(app.filesDir, "adb")
    private val _state = MutableStateFlow(UsbAudioState())
    val state: StateFlow<UsbAudioState> = _state.asStateFlow()
    private var job: Job? = null
    private var pollJob: Job? = null
    private var volumeJob: Job? = null

    private companion object {
        const val CARD_FACT = "USB-карта"
        const val USB_POLL_MS = 2000L
    }

    init {
        refresh()
    }

    fun refresh() = launch { inspect() }

    fun apply() = launch { runScript(permanent = true) }

    fun restore() = launch { runScript(permanent = false) }

    fun startDeviceWatch() {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            snapshotUsbDevices()
            while (isActive) {
                delay(USB_POLL_MS)
                snapshotUsbDevices()
            }
        }
    }

    fun stopDeviceWatch() {
        pollJob?.cancel()
        pollJob = null
    }

    fun onUsbDeviceEvent() {
        snapshotUsbDevices()
    }

    fun refreshVolume() {
        launchVolume {
            openShell().use { shell -> readVolume(shell) }
        }
    }

    fun applyVolume(value: Int) {
        launchVolume {
            val v = value.coerceIn(0, UsbVolume.MAX_VALUE)
            openShell().use { shell ->
                val card = usbCardOrThrow()
                val list = shell.rootText("tinymix -D $card")
                val (index, _) = UsbVolume.parseControl(list)
                    ?: throw IOException("контрол громкости USB-карты не найден")
                shell.rootText("tinymix -D $card $index $v $v")
                readVolume(shell)
            }
        }
    }

    private fun launchVolume(block: suspend () -> Unit) {
        volumeJob?.cancel()
        volumeJob = viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(volume = it.volume.copy(busy = true, error = null)) }
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(volume = it.volume.copy(busy = false, error = e.message ?: e.javaClass.simpleName))
                }
            }
        }
    }

    private fun usbCardOrThrow(): Int =
        UsbVolume.usbCardNumber(runCatching { File("/proc/asound/cards").readText() }.getOrDefault(""))
            ?: throw IOException("USB-карта не найдена (проверьте хаб/кабель)")

    private fun readVolume(shell: RootShell) {
        val card = usbCardOrThrow()
        val list = shell.rootText("tinymix -D $card")
        val (index, value) = UsbVolume.parseControl(list)
            ?: throw IOException("контрол громкости USB-карты не найден")
        _state.update { it.copy(volume = UsbVolumePanel(card = card, controlIndex = index, value = value)) }
    }

    private fun snapshotUsbDevices() {
        val devices = runCatching { readUsbDevices() }
        val card = usbCardVisible()
        _state.update { state ->
            state.copy(
                devices = devices.getOrDefault(state.devices),
                facts = state.facts.map { if (it.first == CARD_FACT) CARD_FACT to usbCardFactText(card) else it },
            )
        }
    }

    private fun readUsbDevices(): List<UsbDeviceInfo> {
        val manager = getApplication<Application>().getSystemService(Context.USB_SERVICE) as? UsbManager
        return manager?.deviceList?.values.orEmpty().map { device ->
            val interfaces = (0 until device.interfaceCount).map { device.getInterface(it).interfaceClass }
            UsbDeviceInfo(
                name = device.productName.orEmpty(),
                manufacturer = device.manufacturerName,
                vendorId = device.vendorId,
                productId = device.productId,
                classDescription = UsbDeviceCatalog.describe(device.deviceClass, interfaces),
                isAudio = UsbDeviceCatalog.isAudio(device.deviceClass, interfaces),
            )
        }.sortedWith(
            compareByDescending<UsbDeviceInfo> { it.isAudio }.thenBy { it.vendorId }.thenBy { it.productId },
        )
    }

    private fun usbCardVisible(): Boolean = runCatching {
        File("/proc/asound/cards").readText().lineSequence().any { "USB-Audio" in it }
    }.getOrDefault(false)

    private fun usbCardFactText(card: Boolean): String =
        if (card) "видна ядром (ALSA)" else "не видна ядром — проверьте хаб/кабель"

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
        val card = usbCardVisible()
        val facts = buildList {
            add(CARD_FACT to usbCardFactText(card))
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
