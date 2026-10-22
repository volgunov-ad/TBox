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
import vad.dashing.winpatch.WinPatchCodec.StockVariant
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

    private fun stageSingleAsset(assetPath: String, expectedMd5: String): String {
        val app = getApplication<Application>()
        val out = File(stageDir(), assetPath.replace('/', '_'))
        val staged = out.isFile && runCatching { md5Of(out) }.getOrNull()?.equals(expectedMd5, ignoreCase = true) == true
        if (!staged) {
            out.outputStream().use { os -> app.assets.open(assetPath).use { it.copyTo(os) } }
        }
        val actual = md5Of(out)
        if (!actual.equals(expectedMd5, ignoreCase = true)) {
            throw IOException("APK $assetPath: MD5 не совпал ($actual != $expectedMd5)")
        }
        return out.absolutePath
    }

    /** Copies the bundled patched APKs to the app external files dir (root-readable via sdcardfs) and verifies MD5. */
    private fun stageAssets(targets: List<Target>): Map<Target, String> =
        targets.associateWith { stageSingleAsset(it.assetName, it.patchedMd5) }

    private fun stageStockAsset(variant: StockVariant): String =
        stageSingleAsset(variant.assetPath, variant.md5)

    private data class TargetInfo(
        val target: Target,
        val system: FileState,
        val systemMd5: String?,
        val stockVariant: StockVariant,
        val backupOk: Boolean,
        val legacyOk: Boolean,
    )

    private fun resolveStockVariant(target: Target, systemMd5: String?, backupMd5: String?, incremental: String): StockVariant {
        target.stockVariantByMd5(systemMd5)?.let { return it }
        target.stockVariantByMd5(backupMd5)?.let { return it }
        return target.stockVariantForIncremental(incremental)
    }

    private fun inspect() {
        val paths = WinPatchCodec.TARGETS.flatMap { listOf(it.systemPath, it.backupPath, it.legacyBackupPath) } +
            WinPatchCodec.CARPLAY_CONFIG
        val (md5Output, incremental) = openShell().use { shell ->
            val md5s = shell.rootText(paths.joinToString(" ") { "md5sum $it" } + " 2>/dev/null; true")
            val inc = shell.rootText("getprop ro.build.version.incremental").trim()
            md5s to inc
        }
        val md5s = WinPatchCodec.parseMd5Map(md5Output)
        val infos = WinPatchCodec.TARGETS.map { t ->
            val sysMd5 = md5s[t.systemPath]
            val bakMd5 = md5s[t.backupPath]
            val legMd5 = md5s[t.legacyBackupPath]
            val stockVar = resolveStockVariant(t, sysMd5, bakMd5 ?: legMd5, incremental)
            TargetInfo(
                target = t,
                system = WinPatchCodec.fileState(sysMd5, t),
                systemMd5 = sysMd5,
                stockVariant = stockVar,
                backupOk = t.stockVariantByMd5(bakMd5) != null,
                legacyOk = t.stockVariantByMd5(legMd5) != null,
            )
        }
        val configMd5 = md5s[WinPatchCodec.CARPLAY_CONFIG]
        val facts = listOf(
            "Прошивка ГУ" to WinPatchCodec.firmwareNameForIncremental(incremental),
            "CarPlayManagerConfig.json" to
                (configMd5?.let { "не изменяется установщиком (${it.take(8)}…)" } ?: "не найден"),
            "Резервные копии стока" to "вшиты в приложение для V00.03.07, 03.16, 05.09 и 07.12",
        )
        val panels = infos.map(::panelOf)
        val missing = infos.filter { it.system == FileState.Missing }
        val anyPatched = infos.any { it.system == FileState.Patched || it.system == FileState.LegacyPatched }
        val banner = if (missing.isNotEmpty()) {
            WinPatchBanner(
                "На ГУ не найдены файлы ${missing.joinToString { it.target.systemPath }} — " +
                    "это должен быть ГУ A10 (Adayo/VHAL) со штатными AndroidAuto и CarPlayView.",
                WinPatchBannerKind.Error,
            )
        } else {
            WinPatchBanner(
                "Android Auto (${WinPatchCodec.ANDROID_AUTO.patchLabel}, база V00.07.12 + режим парковки P) и CarPlay " +
                    "(${WinPatchCodec.CAR_PLAY.patchLabel}, автоперевод в окно лаунчера) ставятся и откатываются отдельно: " +
                    "запись в /system со снятием защиты eMMC, очистка dalvik-cache и перезапуск процесса. " +
                    "Эталонные стоковые APK для всех версий прошивки вшиты в приложение." +
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
        val vLabel = info.stockVariant.firmwareLabel
        val backup = when {
            info.backupOk -> "в ${WinPatchCodec.WORK_DIR} + встроенный сток ($vLabel)"
            info.legacyOk -> "в /data/local/tmp + встроенный сток ($vLabel)"
            else -> "встроенный в приложение сток ($vLabel)"
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
            FileState.Stock -> {
                val matched = t.stockVariantByMd5(info.systemMd5)
                base.copy(
                    status = "штатный APK (${matched?.firmwareLabel ?: vLabel})",
                    canApply = true,
                )
            }
            FileState.Patched -> base.copy(
                status = "патч ${t.patchLabel} установлен (актуальный)",
                kind = WinPatchBannerKind.Success,
                canRestore = true,
            )
            FileState.LegacyPatched -> {
                val oldRev = t.legacyPatchedMd5s[info.systemMd5?.lowercase()] ?: "предыдущая версия"
                base.copy(
                    status = "установлен старый патч ($oldRev) — рекомендуется обновить до ${t.patchLabel}",
                    kind = WinPatchBannerKind.Warning,
                    canApply = true,
                    canRestore = true,
                )
            }
            FileState.Unknown -> base.copy(
                status = "нестандартный APK (${info.systemMd5?.take(8)}…) — можно заменить на ${t.patchLabel} или вернуть сток",
                kind = WinPatchBannerKind.Warning,
                canApply = true,
                canRestore = true,
            )
            FileState.Missing -> base.copy(status = "нет файла ${t.systemPath}", kind = WinPatchBannerKind.Error)
        }
    }

    private suspend fun runInstall(target: Target) {
        val stages = stageAssets(listOf(target))
        openShell().use { shell ->
            if (shell.rootText("id -u").trim() != "0") throw IOException("su не дал root")
            val inc = shell.rootText("getprop ro.build.version.incremental").trim()
            val sysMd5Out = shell.rootText("md5sum ${target.systemPath} ${target.backupPath} ${target.legacyBackupPath} 2>/dev/null; true")
            val md5s = WinPatchCodec.parseMd5Map(sysMd5Out)
            val stockVar = resolveStockVariant(target, md5s[target.systemPath], md5s[target.backupPath] ?: md5s[target.legacyBackupPath], inc)
            val stockStage = stageStockAsset(stockVar)
            shell.rootText("mkdir -p ${WinPatchCodec.WORK_DIR} && chmod 700 ${WinPatchCodec.WORK_DIR}")
            val script = WinPatchCodec.applyScript(
                stages = stages,
                stockStages = mapOf(target to stockStage),
                stockMd5ByTarget = mapOf(target to stockVar.md5),
            )
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
            val inc = shell.rootText("getprop ro.build.version.incremental").trim()
            val sysMd5Out = shell.rootText("md5sum ${target.systemPath} ${target.backupPath} ${target.legacyBackupPath} 2>/dev/null; true")
            val md5s = WinPatchCodec.parseMd5Map(sysMd5Out)
            val stockVar = resolveStockVariant(target, md5s[target.systemPath], md5s[target.backupPath] ?: md5s[target.legacyBackupPath], inc)
            val stockStage = stageStockAsset(stockVar)
            shell.rootText("mkdir -p ${WinPatchCodec.WORK_DIR} && chmod 700 ${WinPatchCodec.WORK_DIR}")
            val script = WinPatchCodec.restoreScript(
                targets = listOf(target),
                stockStages = mapOf(target to stockStage),
                stockMd5ByTarget = mapOf(target to stockVar.md5),
            )
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
