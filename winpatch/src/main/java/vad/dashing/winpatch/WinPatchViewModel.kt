package vad.dashing.winpatch

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vad.dashing.winpatch.WinPatchCodec.FileState
import vad.dashing.winpatch.WinPatchCodec.Target
import vad.dashing.winpatch.device.RootShell

enum class WinPatchAction { Refresh, Reboot }

enum class WinPatchBannerKind { Info, Warning, Success, Error }

data class WinPatchBanner(val text: String, val kind: WinPatchBannerKind)

/** Per-target (Android Auto / CarPlay) status card; install and restore are independent. */
data class WinPatchTargetPanel(
    val id: String,
    val title: String,
    val patchLabel: String,
    val status: String,
    val backup: String,
    val kind: WinPatchBannerKind,
    val canApply: Boolean,
    val canRestore: Boolean,
)

data class WinPatchState(
    val facts: List<Pair<String, String>> = emptyList(),
    val targets: List<WinPatchTargetPanel> = emptyList(),
    val actions: Set<WinPatchAction> = emptySet(),
    val busy: Boolean = false,
    val banner: WinPatchBanner? = null,
)

class WinPatchViewModel(app: Application) : AndroidViewModel(app) {
    private val keysDir = File(app.filesDir, "adb")
    private val _state = MutableStateFlow(WinPatchState())
    val state: StateFlow<WinPatchState> = _state.asStateFlow()
    private var job: Job? = null

    init {
        refresh()
    }

    fun refresh() = launch { inspect() }

    fun apply(targetId: String) = launch { runInstall(targetOrThrow(targetId)) }

    fun restore(targetId: String) = launch { runRestore(targetOrThrow(targetId)) }

    private fun targetOrThrow(id: String): Target =
        WinPatchCodec.targetById(id) ?: throw IOException("неизвестная цель $id")

    fun reboot() = launch { runReboot() }

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
                _state.update { s ->
                    s.copy(
                        banner = WinPatchBanner(text, WinPatchBannerKind.Error),
                        actions = setOf(WinPatchAction.Refresh),
                        targets = s.targets.map { it.copy(canApply = false, canRestore = false) },
                    )
                }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    private fun openShell(): RootShell = RootShell.open(keysDir, "winpatch@${Build.MODEL}")

    private fun md5Of(file: File): String {
        val digest = MessageDigest.getInstance("MD5")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun stageDir(): File = getApplication<Application>().getExternalFilesDir(null)
        ?: throw IOException("внешняя память недоступна — некуда распаковать APK")

    /** Copies the bundled APKs to the app external files dir (root-readable via sdcardfs) and verifies MD5. */
    private fun stageAssets(targets: List<Target>): Map<Target, String> {
        val app = getApplication<Application>()
        val dir = stageDir()
        val stages = mutableMapOf<Target, String>()
        for (t in targets) {
            val out = File(dir, t.assetName)
            val staged = out.isFile && runCatching { md5Of(out) }.getOrNull()?.equals(t.patchedMd5, ignoreCase = true) == true
            if (!staged) {
                out.outputStream().use { os -> app.assets.open(t.assetName).use { it.copyTo(os) } }
            }
            val actual = md5Of(out)
            if (!actual.equals(t.patchedMd5, ignoreCase = true)) throw IOException("APK ${t.assetName}: MD5 не совпал")
            stages[t] = out.absolutePath
        }
        return stages
    }

    private data class TargetInfo(
        val target: Target,
        val system: FileState,
        val systemMd5: String?,
        val backupOk: Boolean,
        val legacyOk: Boolean,
    )

    private fun inspect() {
        val paths = WinPatchCodec.TARGETS.flatMap { listOf(it.systemPath, it.backupPath, it.legacyBackupPath) } +
            WinPatchCodec.CARPLAY_CONFIG
        val output = openShell().use { shell ->
            shell.rootText(paths.joinToString(" ") { "md5sum $it" } + " 2>/dev/null; true")
        }
        val md5s = WinPatchCodec.parseMd5Map(output)
        val infos = WinPatchCodec.TARGETS.map { t ->
            TargetInfo(
                target = t,
                system = WinPatchCodec.fileState(md5s[t.systemPath], t),
                systemMd5 = md5s[t.systemPath],
                backupOk = md5s[t.backupPath]?.equals(t.stockMd5, ignoreCase = true) == true,
                legacyOk = md5s[t.legacyBackupPath]?.equals(t.stockMd5, ignoreCase = true) == true,
            )
        }
        val configMd5 = md5s[WinPatchCodec.CARPLAY_CONFIG]
        val facts = listOf(
            "CarPlayManagerConfig.json" to
                (configMd5?.let { "не изменяется установщиком (${it.take(8)}…)" } ?: "не найден"),
            "Подключение" to "проводное и беспроводное — как в стоке",
        )
        val panels = infos.map(::panelOf)
        val broken = infos.filter { it.system == FileState.Unknown || it.system == FileState.Missing }
        val anyPatched = infos.any { it.system == FileState.Patched }
        val banner = if (broken.isNotEmpty()) {
            WinPatchBanner(
                "На ГУ не найдены штатные ${broken.joinToString { it.target.title }} " +
                    "(неизвестный или отсутствующий APK) — для них установка и откат недоступны. " +
                    "Это должен быть ГУ A10 (Adayo/VHAL) со штатными AndroidAuto и CarPlayView.",
                WinPatchBannerKind.Error,
            )
        } else {
            WinPatchBanner(
                "Android Auto и CarPlay ставятся и откатываются отдельно: запись в /system со снятием защиты eMMC, " +
                    "штатный APK сохраняется в резерв, процесс перезапускается. Проводное и беспроводное подключение " +
                    "не затрагиваются." +
                    if (anyPatched) " Если окно не подхватилось после установки — перезагрузите ГУ." else "",
                WinPatchBannerKind.Info,
            )
        }
        val actions = buildSet {
            if (anyPatched) add(WinPatchAction.Reboot)
            add(WinPatchAction.Refresh)
        }
        _state.update { it.copy(facts = facts, targets = panels, banner = banner, actions = actions) }
    }

    private fun panelOf(info: TargetInfo): WinPatchTargetPanel {
        val t = info.target
        val backup = when {
            info.backupOk -> "есть (штатный APK)"
            info.legacyOk -> "в /data/local/tmp (переймётся при установке)"
            else -> "нет"
        }
        val base = WinPatchTargetPanel(
            id = t.id,
            title = t.title,
            patchLabel = t.patchLabel,
            status = "",
            backup = backup,
            kind = WinPatchBannerKind.Info,
            canApply = false,
            canRestore = false,
        )
        return when (info.system) {
            FileState.Stock -> base.copy(status = "штатный APK", canApply = true)
            FileState.Patched -> when {
                info.backupOk -> base.copy(
                    status = "патч ${t.patchLabel} установлен",
                    kind = WinPatchBannerKind.Success,
                    canRestore = true,
                )
                info.legacyOk -> base.copy(
                    status = "патч ${t.patchLabel} установлен; «Установить» перенесёт резерв для отката",
                    kind = WinPatchBannerKind.Warning,
                    canApply = true,
                )
                else -> base.copy(
                    status = "патч ${t.patchLabel} установлен, штатного резерва нет — откат недоступен",
                    kind = WinPatchBannerKind.Warning,
                )
            }
            FileState.Unknown -> base.copy(
                status = "неизвестный APK (${info.systemMd5?.take(8)}…)",
                kind = WinPatchBannerKind.Error,
            )
            FileState.Missing -> base.copy(status = "нет файла ${t.systemPath}", kind = WinPatchBannerKind.Error)
        }
    }

    private suspend fun runInstall(target: Target) {
        val stages = stageAssets(listOf(target))
        openShell().use { shell ->
            if (shell.rootText("id -u").trim() != "0") throw IOException("su не дал root")
            shell.rootText("mkdir -p ${WinPatchCodec.WORK_DIR} && chmod 700 ${WinPatchCodec.WORK_DIR}")
            val script = WinPatchCodec.applyScript(stages)
            shell.writeFile(WinPatchCodec.APPLY_SCRIPT, script.toByteArray(Charsets.UTF_8))
            val output = shell.root("sh ${WinPatchCodec.APPLY_SCRIPT}")
            when (val outcome = WinPatchCodec.parseOutcome("${output.stdout}\n${output.stderr}")) {
                is WinPatchCodec.Outcome.Failed -> throw IOException("скрипт: ${outcome.code}")
                else -> Unit
            }
        }
        inspect()
    }

    private suspend fun runRestore(target: Target) {
        openShell().use { shell ->
            if (shell.rootText("id -u").trim() != "0") throw IOException("su не дал root")
            shell.rootText("mkdir -p ${WinPatchCodec.WORK_DIR} && chmod 700 ${WinPatchCodec.WORK_DIR}")
            val script = WinPatchCodec.restoreScript(listOf(target))
            shell.writeFile(WinPatchCodec.RESTORE_SCRIPT, script.toByteArray(Charsets.UTF_8))
            val output = shell.root("sh ${WinPatchCodec.RESTORE_SCRIPT}")
            when (val outcome = WinPatchCodec.parseOutcome("${output.stdout}\n${output.stderr}")) {
                is WinPatchCodec.Outcome.Failed -> throw IOException("скрипт: ${outcome.code}")
                else -> Unit
            }
        }
        inspect()
    }

    private fun runReboot() {
        openShell().use { shell -> runCatching { shell.root("sync; reboot") } }
        _state.update {
            it.copy(banner = WinPatchBanner("Перезагрузка ГУ отправлена — поднимется примерно через минуту.", WinPatchBannerKind.Info))
        }
    }
}
