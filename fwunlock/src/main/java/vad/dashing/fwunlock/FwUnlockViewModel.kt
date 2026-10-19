package vad.dashing.fwunlock

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
import vad.dashing.fwunlock.device.RootShell

enum class FwUnlockAction { Refresh, Apply, Restore }

enum class FwUnlockBannerKind { Info, Warning, Success, Error }

data class FwUnlockBanner(val text: String, val kind: FwUnlockBannerKind)

data class FwUnlockState(
    val facts: List<Pair<String, String>> = emptyList(),
    val actions: Set<FwUnlockAction> = emptySet(),
    val busy: Boolean = false,
    val banner: FwUnlockBanner? = null,
)

class FwUnlockViewModel(app: Application) : AndroidViewModel(app) {
    private val keysDir = File(app.filesDir, "adb")
    private val _state = MutableStateFlow(FwUnlockState())
    val state: StateFlow<FwUnlockState> = _state.asStateFlow()
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
                    it.copy(banner = FwUnlockBanner(text, FwUnlockBannerKind.Error), actions = setOf(FwUnlockAction.Refresh))
                }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    private fun openShell(): RootShell = RootShell.open(keysDir, "fwunlock@${Build.MODEL}")

    private fun readConfig(): String {
        val file = File(FwUnlockCodec.CONFIG_PATH)
        if (!file.isFile) throw IOException("нет файла ${FwUnlockCodec.CONFIG_PATH} (это ГУ A10 / Adayo?)")
        return file.readText()
    }

    /** All third-party packages as `pm list packages -3` sees them (root shell, no visibility filters). */
    private fun thirdPartyPackages(shell: RootShell): List<String> =
        shell.rootText("pm list packages -3").lineSequence()
            .map { it.removePrefix("package:").trim() }
            .filter { it.isNotEmpty() }
            .sorted()
            .toList()

    private fun inspect() {
        val config = readConfig()
        val whitelisted = FwUnlockCodec.parsePackages(config)
        val installed = openShell().use { shell -> thirdPartyPackages(shell) }
        val missing = installed.filter { it !in whitelisted }
        val facts = buildList {
            add("ГУ A10 (Adayo)" to "конфиг файрвола найден")
            add("Пакетов в белом списке" to whitelisted.size.toString())
            add("Сторонних приложений" to installed.size.toString())
            add("Без сети при загрузке" to if (missing.isEmpty()) "нет" else missing.size.toString())
        }
        val (banner, actions) = when {
            whitelisted.isNotEmpty() && missing.isEmpty() ->
                FwUnlockBanner(
                    "Сеть разрешена всем ${whitelisted.size} приложениям из белого списка — постоянно, с каждой загрузки.",
                    FwUnlockBannerKind.Success,
                ) to setOf(FwUnlockAction.Restore, FwUnlockAction.Refresh)

            whitelisted.isNotEmpty() ->
                FwUnlockBanner(
                    "Белый список неполный: ${missing.size} приложений останутся без сети до старта TBox Monitor. " +
                        "Нажмите «Разрешить приложениям сеть», чтобы дописать их.",
                    FwUnlockBannerKind.Warning,
                ) to setOf(FwUnlockAction.Apply, FwUnlockAction.Refresh)

            else ->
                FwUnlockBanner(
                    "Штатный файрвол Adayo блокирует сеть всем сторонним приложениям. «Разрешить» допишет белый " +
                        "список в /system (на время записи снимается защита eMMC) и перезапустит CarService (~15 секунд).",
                    FwUnlockBannerKind.Info,
                ) to setOf(FwUnlockAction.Apply, FwUnlockAction.Refresh)
        }
        _state.update { it.copy(facts = facts, banner = banner, actions = actions) }
    }

    private suspend fun runScript(permanent: Boolean) {
        val current = readConfig()
        openShell().use { shell ->
            if (shell.rootText("id -u").trim() != "0") throw IOException("su не дал root")
            val config = if (permanent) {
                val packages = thirdPartyPackages(shell)
                FwUnlockCodec.buildApplyConfig(current, packages)
                    ?: throw IOException("не удалось собрать белый список (пустой список пакетов или недопустимое имя)")
            } else {
                FwUnlockCodec.STOCK_CONFIG
            }
            shell.rootText("mkdir -p ${FwUnlockCodec.WORK_DIR} && chmod 700 ${FwUnlockCodec.WORK_DIR}")
            shell.writeFile(FwUnlockCodec.STAGED_CONFIG, config.toByteArray(Charsets.UTF_8))
            val script = if (permanent) {
                FwUnlockCodec.applyScript(backupOriginal = !FwUnlockCodec.isGenerated(current))
            } else {
                FwUnlockCodec.restoreScript()
            }
            val scriptPath = if (permanent) FwUnlockCodec.APPLY_SCRIPT else FwUnlockCodec.RESTORE_SCRIPT
            shell.writeFile(scriptPath, script.toByteArray(Charsets.UTF_8))
            val output = shell.root("sh $scriptPath")
            when (val outcome = FwUnlockCodec.parseOutcome("${output.stdout}\n${output.stderr}")) {
                is FwUnlockCodec.Outcome.Failed -> throw IOException("скрипт: ${outcome.code}")
                else -> Unit
            }
        }
        inspect()
    }
}
