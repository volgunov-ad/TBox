package vad.dashing.dataresize

import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vad.dashing.dataresize.device.RootShell
import vad.dashing.dataresize.gpt.DiskMap
import vad.dashing.dataresize.gpt.DiskMapView
import vad.dashing.dataresize.gpt.Gpt
import vad.dashing.dataresize.gpt.GptEntry
import vad.dashing.dataresize.gpt.GptException
import vad.dashing.dataresize.gpt.GptImages
import vad.dashing.dataresize.gpt.GptPlanner
import vad.dashing.dataresize.gpt.MovePlan
import vad.dashing.dataresize.gpt.PartitionState
import vad.dashing.dataresize.gpt.SECTOR

enum class Action { Refresh, Move, Stage2, Rollback, FixSecondary }

enum class BannerKind { Info, Warning, Success, Error }

data class Banner(val text: String, val kind: BannerKind)

data class UiState(
    val preflight: List<StepUi> = ResizeViewModel.PREFLIGHT.map { StepUi(it.id, it.title) },
    val operations: List<Operation> = emptyList(),
    val facts: List<Pair<String, String>> = emptyList(),
    val diskMaps: List<DiskMapView> = emptyList(),
    val actions: Set<Action> = emptySet(),
    val busy: Boolean = false,
    val banner: Banner? = null,
    val events: List<String> = emptyList(),
    val journal: List<String> = emptyList(),
)

class ResizeViewModel(app: Application) : AndroidViewModel(app) {
    private val pending = PendingStore(app)
    private val keysDir = File(app.filesDir, "adb")
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()
    private var plan: MovePlan? = null
    private var layout: Pair<Long, List<GptEntry>>? = null
    private var job: Job? = null

    init {
        refresh()
    }

    fun refresh() = launch { inspect() }

    fun move() = launch { runMove() }

    fun stage2() = launch { openShell().use { runStage2(it) } }

    fun rollback() = launch { runRollback() }

    fun fixSecondary() = launch { runFixSecondary() }

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
                event("Ошибка: $text")
                _state.update {
                    it.copy(banner = Banner(text, BannerKind.Error), actions = setOf(Action.Refresh))
                }
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    private fun openShell(): RootShell = RootShell.open(keysDir, "dataresize@${Build.MODEL}")

    private suspend fun inspect() {
        plan = null
        layout = null
        _state.update { UiState(busy = true, events = it.events) }
        event("Проверка устройства")
        val shell = pre("adb") { openShell() to "${RootShell.HOST}:${RootShell.PORT}" }
        shell.use { sh ->
            pre("root") {
                if (sh.rootText("id -u").trim() != "0") throw IOException("su не дал root")
                Unit to "su 0"
            }
            runningScript(sh)?.let { run -> if (!attach(sh, run)) return }
            pre("device") { Unit to checkDevice(sh) }
            val gpt = pre("gpt") { readGpt(sh) }
            val data = pre("data") { readDf(sh).let { it to "${bytes(it.first)}, свободно ${bytes(it.second)}" } }
            pending.set(null)
            showDiskMaps()
            when (gpt) {
                is PartitionState.Movable -> onMovable(sh, gpt.plan, data)
                is PartitionState.AlreadyMoved -> onAlreadyMoved(sh, gpt.entry.first)
                is PartitionState.MixedCopies -> onMixedCopies(gpt.primaryNew)
                is PartitionState.NotPossible -> _state.update {
                    it.copy(banner = Banner(gpt.reason, BannerKind.Error), actions = setOf(Action.Refresh))
                }
            }
        }
    }

    /** Run of the script that holds its lock right now, if any. */
    private fun runningScript(sh: RootShell): ScriptRun? {
        val out = sh.rootText("[ -f ${MoveFiles.SCRIPT} ] && sh ${MoveFiles.SCRIPT} running 2>/dev/null; true")
        val fields = MoveFiles.fields(out)
        if (fields["RUNNING"] != "1") return null
        val mode = fields["MODE"]
        return Journal.RUNS.firstOrNull { it.mode == mode } ?: throw IOException("скрипт выполняет неизвестный режим: $mode")
    }

    /**
     * Follows a run started earlier (e.g. before the app was reopened) instead of starting a second one.
     * True when it finished successfully and inspection can go on.
     */
    private suspend fun attach(sh: RootShell, run: ScriptRun): Boolean {
        event("${run.title}: скрипт уже работает, программа следит за журналом")
        _state.update {
            it.copy(banner = Banner("${run.title}: скрипт уже работает, дождитесь завершения", BannerKind.Warning))
        }
        val (_, failure) = try {
            poll(sh, run, emptyList())
        } catch (e: IOException) {
            event("Связь с ГУ потеряна: ${e.message}")
            _state.update { it.copy(actions = setOf(Action.Refresh)) }
            return false
        }
        if (failure != null) {
            _state.update { it.copy(banner = Banner(failure, BannerKind.Error), actions = setOf(Action.Refresh)) }
            return false
        }
        return true
    }

    private fun onMixedCopies(primaryNew: Boolean) {
        val table = if (primaryNew) "новая" else "старая"
        _state.update {
            it.copy(
                facts = listOf(
                    "Основная GPT" to "$table (по ней загружается ГУ)",
                    "Резервная GPT" to if (primaryNew) "старая" else "новая",
                ),
                banner = Banner(
                    "Копии таблицы разделов различаются: ${if (primaryNew) "откат" else "перенос"} прервался между " +
                        "записями. Резервную копию можно дописать по основной, перезагрузка не нужна.",
                    BannerKind.Warning,
                ),
                actions = setOf(Action.FixSecondary, Action.Refresh),
            )
        }
    }

    private fun checkDevice(sh: RootShell): String {
        val probes = TOOLS.joinToString("; ") { "command -v $it >/dev/null || echo MISSING $it" } +
            "; [ -w /proc/mmc_debug ] || echo MISSING /proc/mmc_debug" +
            "; [ -r /sys/block/mmcblk0/userdata/start ] || echo MISSING sysfs userdata" +
            "; [ -b /dev/block/userdata ] || echo MISSING /dev/block/userdata" +
            "; echo CRYPTO=`getprop ro.crypto.state`; echo HW=`getprop ro.hardware`"
        val lines = sh.rootText(probes).lines().map { it.trim() }
        val missing = lines.filter { it.startsWith("MISSING ") }.map { it.removePrefix("MISSING ") }
        if (missing.isNotEmpty()) throw IOException("нет: ${missing.joinToString()}")
        if (lines.any { it == "CRYPTO=encrypted" }) throw IOException("/data зашифрован")
        val hw = lines.firstOrNull { it.startsWith("HW=") }?.removePrefix("HW=").orEmpty()
        return "платформа $hw, инструменты на месте"
    }

    private fun readGpt(sh: RootShell): Pair<PartitionState, String> {
        val disk = sh.rootText("cat /sys/block/mmcblk0/size").trim().toLong()
        val head = sh.readSectors(DEVICE, 0, 2)
        val header = Gpt.parseHeader(head, SECTOR)
        val tailSectors = disk - header.lastUsable - 1
        if (header.firstUsable !in 34..2048 || tailSectors !in 33..2048) {
            throw IOException("необычная разметка: first ${header.firstUsable}, last ${header.lastUsable}")
        }
        val images = GptImages(
            primary = sh.readSectors(DEVICE, 0, header.firstUsable),
            secondary = sh.readSectors(DEVICE, header.lastUsable + 1, tailSectors),
            diskSectors = disk,
        )
        val state = try {
            GptPlanner.evaluate(images)
        } catch (e: GptException) {
            mixedCopies(sh, images) ?: throw e
        }
        layout = runCatching { disk to GptPlanner.parse(images).entries }.getOrNull()
        val detail = when (state) {
            is PartitionState.Movable ->
                "обе копии в порядке, userdata ${gib(state.plan.entry.sectors)} → ${gib(state.plan.newSectors)}"
            is PartitionState.AlreadyMoved -> "обе копии в порядке, userdata уже в конце диска (${gib(state.entry.sectors)})"
            is PartitionState.MixedCopies ->
                "основная копия — ${if (state.primaryNew) "новая" else "старая"} таблица, резервная — другая"
            is PartitionState.NotPossible -> state.reason
        }
        return state to "eMMC ${gib(disk)}; $detail"
    }

    /** «Сейчас» bar, plus [after] (title to the range «userdata» would take) when an operation is possible. */
    private fun showDiskMaps(after: Pair<String, LongRange>? = null) {
        val (disk, entries) = layout ?: run {
            _state.update { it.copy(diskMaps = emptyList()) }
            return
        }
        val maps = listOfNotNull(
            DiskMap.view("Сейчас", disk, entries),
            after?.let { (title, range) -> DiskMap.view(title, disk, entries, moveUserdataTo = range) },
        )
        _state.update { it.copy(diskMaps = maps) }
    }

    /** Where a rollback puts «userdata» back, from the params of the move this program made. */
    private fun rollbackRange(sh: RootShell): LongRange? {
        val params = MoveFiles.fields(sh.rootText("cat ${MoveFiles.PARAMS} 2>/dev/null; true"))
        val first = params["OLD_FIRST"]?.toLongOrNull() ?: return null
        val sectors = params["OLD_SECT"]?.toLongOrNull()?.takeIf { it > 0 } ?: return null
        return first until first + sectors
    }

    /** Only when both copies match the tables this program wrote, byte for byte (MD5 from its params). */
    private fun mixedCopies(sh: RootShell, images: GptImages): PartitionState.MixedCopies? {
        val params = MoveFiles.fields(sh.rootText("cat ${MoveFiles.PARAMS} 2>/dev/null; true"))
        if (params["DISK_SECT"] != images.diskSectors.toString()) return null
        val primaryNew = MoveFiles.mixedPrimaryNew(
            params,
            GptPlanner.md5(images.primary),
            GptPlanner.md5(images.secondary),
        ) ?: return null
        return PartitionState.MixedCopies(primaryNew)
    }

    /** /data size and free space in bytes. */
    private fun readDf(sh: RootShell): Pair<Long, Long> {
        val cols = sh.rootText("df -k /data | tail -1").trim().split(Regex("\\s+"))
        return cols[1].toLong() * 1024 to cols[3].toLong() * 1024
    }

    private suspend fun onMovable(sh: RootShell, movePlan: MovePlan, data: Pair<Long, Long>) {
        plan = movePlan
        showDiskMaps("После переноса" to movePlan.newFirst..movePlan.newLast)
        val rolledBack = lastResult(sh) == "rolledback"
        val op = if (rolledBack) {
            journalOp(sh, Journal.RUN_ROLLBACK, completed = true)
        } else {
            journalOp(sh, Journal.RUN_MOVE, completed = false)
        }
        _state.update {
            it.copy(
                operations = listOfNotNull(op),
                facts = listOf(
                    "/data сейчас" to "${bytes(data.first)}, свободно ${bytes(data.second)}",
                    "userdata сейчас" to "LBA ${movePlan.entry.first}…${movePlan.entry.last} (${gib(movePlan.entry.sectors)})",
                    "userdata после" to "LBA ${movePlan.newFirst}…${movePlan.newLast} (${gib(movePlan.newSectors)})",
                    "Копируется" to "${movePlan.copyMiB} МиБ",
                ),
                banner = when {
                    rolledBack -> Banner("Откат выполнен, /data на прежнем месте", BannerKind.Success)
                    op != null -> Banner("Прошлый перенос не завершён, разметка не изменена", BannerKind.Warning)
                    else -> Banner("Перенос возможен", BannerKind.Info)
                },
                actions = setOf(Action.Move, Action.Refresh),
            )
        }
    }

    private suspend fun onAlreadyMoved(sh: RootShell, expectedStart: Long) {
        val installed = sh.root("[ -f ${MoveFiles.PARAMS} ]").exitCode == 0
        if (!installed) {
            _state.update {
                it.copy(
                    banner = Banner("userdata уже в конце диска, но перенос делала не эта программа", BannerKind.Warning),
                    actions = setOf(Action.Refresh),
                )
            }
            return
        }
        rollbackRange(sh)?.let { showDiskMaps("После отката" to it) }
        val status = scriptStatus(sh)
        val ops = listOfNotNull(journalOp(sh, Journal.RUN_MOVE, completed = status["GPT"] == "new"))
            .toMutableList()
        _state.update { it.copy(operations = ops.toList()) }
        if (status["START"]?.toLongOrNull() != expectedStart) {
            _state.update {
                it.copy(
                    banner = Banner("Таблица уже новая, но ядро видит старый раздел — перезагрузите ГУ", BannerKind.Warning),
                    actions = setOf(Action.Refresh),
                )
            }
            return
        }
        val partBytes = (status["SIZE"]?.toLongOrNull() ?: 0L) * SECTOR
        val fsBytes = (status["FS_KB"]?.toLongOrNull() ?: 0L) * 1024
        if (fsBytes < partBytes * 9 / 10) {
            if (lastResult(sh) == "moved") {
                runStage2(sh)
            } else {
                _state.update {
                    it.copy(
                        banner = Banner("Раздел перенесён, файловую систему нужно расширить", BannerKind.Info),
                        actions = setOf(Action.Stage2, Action.Refresh),
                    )
                }
            }
            return
        }
        journalOp(sh, Journal.RUN_STAGE2, completed = true)?.let { ops += it }
        showDone(sh, ops)
    }

    private suspend fun runStage2(sh: RootShell) {
        event("Расширение файловой системы")
        val run = Journal.RUN_STAGE2
        val before = _state.value.operations.filter { it.title != run.title }
        installScript(sh)
        launchDetached(sh, run)
        val (op, failure) = poll(sh, run, before)
        if (failure != null) {
            _state.update { it.copy(banner = Banner(failure, BannerKind.Error), actions = setOf(Action.Refresh)) }
            return
        }
        showDone(sh, before + op)
    }

    private fun showDone(sh: RootShell, ops: List<Operation>) {
        val (size, free) = readDf(sh)
        setPre("data", StepStatus.Ok, "${bytes(size)}, свободно ${bytes(free)}")
        _state.update {
            it.copy(
                operations = ops,
                facts = listOf("/data" to "${bytes(size)}, свободно ${bytes(free)}"),
                banner = Banner("Готово: /data ${bytes(size)}, свободно ${bytes(free)}", BannerKind.Success),
                actions = setOf(Action.Rollback, Action.Refresh),
            )
        }
    }

    private suspend fun runMove() {
        val movePlan = plan ?: throw IOException("сначала проверка")
        val prepare = StepUi("prepare", "Подготовка файлов в ${MoveFiles.DIR}", StepStatus.Running)
        fun show(prep: StepUi, steps: List<StepUi>, error: String? = null) =
            _state.update { it.copy(operations = listOf(Operation("Перенос", listOf(prep) + steps, error))) }
        val idle = Journal.steps(Journal.MOVE, emptyList()).first
        show(prepare, idle)
        openShell().use { sh ->
            try {
                installFiles(sh, movePlan)
                val gpt = scriptStatus(sh)["GPT"]
                if (gpt != "old") throw IOException("скрипт видит GPT: $gpt")
            } catch (e: Exception) {
                show(prepare.copy(status = StepStatus.Failed, detail = e.message.orEmpty()), idle)
                throw e
            }
            val prepared = prepare.copy(status = StepStatus.Ok, detail = "скрипт, параметры и 4 копии GPT сверены по MD5")
            show(prepared, idle)
            pending.set(PendingOp.Move)
            event("Перенос запущен, дальше скрипт работает сам")
            _state.update {
                it.copy(
                    banner = Banner(
                        "Идёт перенос. Экран замрёт примерно на 2 минуты, ГУ перезагрузится само. Не выключайте зажигание.",
                        BannerKind.Warning,
                    ),
                )
            }
            launchDetached(sh, Journal.RUN_MOVE)
            val (op, failure) = try {
                poll(sh, Journal.RUN_MOVE, emptyList(), prefix = prepared)
            } catch (e: IOException) {
                event("Связь с ГУ потеряна: ${e.message}. Перенос продолжается без программы.")
                _state.update { it.copy(actions = setOf(Action.Refresh)) }
                return
            }
            if (failure != null) {
                pending.set(null)
                show(prepared, op.steps.drop(1), failure)
                _state.update { it.copy(banner = Banner(failure, BannerKind.Error), actions = setOf(Action.Refresh)) }
            }
        }
    }

    private suspend fun runRollback() {
        openShell().use { sh ->
            val gpt = scriptStatus(sh)["GPT"]
            if (gpt != "new") throw IOException("откат невозможен: GPT сейчас $gpt")
            installScript(sh)
            pending.set(PendingOp.Rollback)
            event("Откат запущен")
            _state.update { it.copy(banner = Banner("Откат: ГУ перезагрузится само", BannerKind.Warning)) }
            launchDetached(sh, Journal.RUN_ROLLBACK)
            val before = _state.value.operations
            val (_, failure) = try {
                poll(sh, Journal.RUN_ROLLBACK, before)
            } catch (e: IOException) {
                event("Связь с ГУ потеряна: ${e.message}")
                _state.update { it.copy(actions = setOf(Action.Refresh)) }
                return
            }
            if (failure != null) {
                pending.set(null)
                _state.update { it.copy(banner = Banner(failure, BannerKind.Error), actions = setOf(Action.Refresh)) }
            }
        }
    }

    private suspend fun runFixSecondary() {
        val run = Journal.RUN_FIXSGPT
        openShell().use { sh ->
            // The installed script may predate the fixsgpt mode; params and GPT images stay as they are.
            installScript(sh)
            val gpt = scriptStatus(sh)["GPT"]
            if (gpt != "mixed_old" && gpt != "mixed_new") throw IOException("скрипт видит GPT: $gpt")
            event("Запись резервной GPT по основной")
            launchDetached(sh, run)
            val (_, failure) = try {
                poll(sh, run, emptyList())
            } catch (e: IOException) {
                event("Связь с ГУ потеряна: ${e.message}")
                _state.update { it.copy(actions = setOf(Action.Refresh)) }
                return
            }
            if (failure != null) {
                _state.update { it.copy(banner = Banner(failure, BannerKind.Error), actions = setOf(Action.Refresh)) }
                return
            }
        }
        inspect()
    }

    private fun installFiles(sh: RootShell, movePlan: MovePlan) {
        val dir = MoveFiles.DIR
        sh.rootText("mkdir -p $dir && chmod 700 $dir && rm -f $dir/*.log $dir/state")
        val files = MoveFiles.binaries(movePlan) +
            ("params" to MoveFiles.params(movePlan).toByteArray()) +
            ("dataresize.sh" to scriptBytes())
        writeVerified(sh, files)
    }

    private fun installScript(sh: RootShell) = writeVerified(sh, mapOf("dataresize.sh" to scriptBytes()))

    private fun scriptBytes(): ByteArray = getApplication<Application>().assets.open(MoveFiles.ASSET_SCRIPT)
        .use { it.readBytes() }
        .toString(Charsets.UTF_8)
        .replace("\r\n", "\n")
        .toByteArray()

    private fun writeVerified(sh: RootShell, files: Map<String, ByteArray>) {
        val dir = MoveFiles.DIR
        for ((name, data) in files) {
            event("Запись $name (${data.size} Б)")
            sh.writeFile("$dir/$name", data)
        }
        sh.rootText("cd $dir && chmod 600 ${files.keys.joinToString(" ")}")
        val sums = sh.rootText("cd $dir && md5sum ${files.keys.joinToString(" ")}").lines()
            .mapNotNull { line -> line.trim().split(Regex("\\s+")).takeIf { it.size == 2 }?.let { it[1] to it[0] } }
            .toMap()
        for ((name, data) in files) {
            if (sums[name] != GptPlanner.md5(data)) throw IOException("$name записан с ошибкой")
        }
    }

    /** Clears [run]'s old journal so a stale `!FAIL` or `@done` is not read as this run's outcome. */
    private fun launchDetached(sh: RootShell, run: ScriptRun) {
        runningScript(sh)?.let { throw IOException("скрипт уже выполняется: ${it.title}") }
        sh.rootText(
            "rm -f ${MoveFiles.DIR}/${run.file}; setsid sh ${MoveFiles.SCRIPT} ${run.mode} </dev/null >/dev/null 2>&1 &",
        )
    }

    private fun scriptStatus(sh: RootShell): Map<String, String> =
        MoveFiles.fields(sh.rootText("sh ${MoveFiles.SCRIPT} status"))

    private fun readJournal(sh: RootShell, file: String): List<String> =
        sh.rootText("cat ${MoveFiles.DIR}/$file 2>/dev/null; true").lines().filter { it.isNotBlank() }

    /** Null when the script never wrote [file]. */
    private fun journalOp(sh: RootShell, run: ScriptRun, completed: Boolean): Operation? {
        val lines = readJournal(sh, run.file)
        if (lines.isEmpty()) return null
        val (steps, failure) = Journal.steps(run.defs, lines, completed)
        _state.update { it.copy(journal = lines) }
        return Operation(run.title, steps, failure)
    }

    /**
     * Outcome the script recorded in /cache (`moved`, `done`, `rolledback`). App prefs live on
     * /data, which a rollback returns to the moment of the move, so they cannot tell this.
     */
    private fun lastResult(sh: RootShell): String =
        sh.rootText("cat ${MoveFiles.DIR}/state 2>/dev/null; true").trim()

    /**
     * Follows [run]'s journal until it finishes, `!FAIL` or stays empty too long after launch;
     * returns the operation and the failure text.
     */
    private suspend fun poll(
        sh: RootShell,
        run: ScriptRun,
        before: List<Operation>,
        prefix: StepUi? = null,
    ): Pair<Operation, String?> {
        val started = System.currentTimeMillis()
        while (true) {
            val lines = readJournal(sh, run.file)
            val elapsed = System.currentTimeMillis() - started
            val (steps, logged) = Journal.steps(run.defs, lines)
            val failure = logged ?: Journal.startFailure(lines, elapsed)
            val op = Operation(run.title, listOfNotNull(prefix) + steps, failure)
            _state.update { it.copy(operations = before + op, journal = lines) }
            if (failure != null || run.finished(lines)) return op to failure
            if (elapsed > POLL_LIMIT_MS) throw IOException("скрипт не завершился за ${POLL_LIMIT_MS / 60_000} мин")
            delay(POLL_INTERVAL_MS)
        }
    }

    private suspend fun <T> pre(id: String, block: suspend () -> Pair<T, String>): T {
        setPre(id, StepStatus.Running, "")
        return try {
            val (value, detail) = block()
            setPre(id, StepStatus.Ok, detail)
            value
        } catch (e: Exception) {
            setPre(id, StepStatus.Failed, e.message ?: e.javaClass.simpleName)
            throw e
        }
    }

    private fun setPre(id: String, status: StepStatus, detail: String) = _state.update { s ->
        s.copy(preflight = s.preflight.map { if (it.id == id) it.copy(status = status, detail = detail) else it })
    }

    private fun event(text: String) {
        val line = "${SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())}  $text"
        _state.update { it.copy(events = (it.events + line).takeLast(200)) }
    }

    companion object {
        const val DEVICE = "/dev/block/mmcblk0"
        private const val POLL_INTERVAL_MS = 700L
        private const val POLL_LIMIT_MS = 15 * 60_000L
        private val TOOLS = listOf("dd", "md5sum", "blockdev", "resize2fs", "setsid", "base64", "awk", "toybox")

        val PREFLIGHT = listOf(
            StepDef("adb", "Подключение к ADB"),
            StepDef("root", "Права root"),
            StepDef("device", "Устройство и инструменты"),
            StepDef("gpt", "Таблица разделов (GPT)"),
            StepDef("data", "Раздел /data"),
        )

        private val RU = Locale.forLanguageTag("ru")

        fun gib(sectors: Long): String = String.format(RU, "%.1f ГиБ", sectors * SECTOR / 1073741824.0)

        fun bytes(value: Long): String = String.format(RU, "%.1f ГиБ", value / 1073741824.0)
    }
}
