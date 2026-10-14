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
import vad.dashing.dataresize.gpt.Gpt
import vad.dashing.dataresize.gpt.GptImages
import vad.dashing.dataresize.gpt.GptPlanner
import vad.dashing.dataresize.gpt.MovePlan
import vad.dashing.dataresize.gpt.PartitionState
import vad.dashing.dataresize.gpt.SECTOR

enum class Action { Refresh, Move, Stage2, Rollback }

enum class BannerKind { Info, Warning, Success, Error }

data class Banner(val text: String, val kind: BannerKind)

data class UiState(
    val preflight: List<StepUi> = ResizeViewModel.PREFLIGHT.map { StepUi(it.id, it.title) },
    val operations: List<Operation> = emptyList(),
    val facts: List<Pair<String, String>> = emptyList(),
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
    private var job: Job? = null

    init {
        refresh()
    }

    fun refresh() = launch { inspect() }

    fun move() = launch { runMove() }

    fun stage2() = launch { openShell().use { runStage2(it) } }

    fun rollback() = launch { runRollback() }

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
        _state.update { UiState(busy = true, events = it.events) }
        event("Проверка устройства")
        val shell = pre("adb") { openShell() to "${RootShell.HOST}:${RootShell.PORT}" }
        shell.use { sh ->
            pre("root") {
                if (sh.rootText("id -u").trim() != "0") throw IOException("su не дал root")
                Unit to "su 0"
            }
            pre("device") { Unit to checkDevice(sh) }
            val gpt = pre("gpt") { readGpt(sh) }
            val data = pre("data") { readDf(sh).let { it to "${bytes(it.first)}, свободно ${bytes(it.second)}" } }
            pending.set(null)
            when (gpt) {
                is PartitionState.Movable -> onMovable(sh, gpt.plan, data)
                is PartitionState.AlreadyMoved -> onAlreadyMoved(sh, gpt.entry.first)
                is PartitionState.NotPossible -> _state.update {
                    it.copy(banner = Banner(gpt.reason, BannerKind.Error), actions = setOf(Action.Refresh))
                }
            }
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
        val state = GptPlanner.evaluate(images)
        val detail = when (state) {
            is PartitionState.Movable ->
                "обе копии в порядке, userdata ${gib(state.plan.entry.sectors)} → ${gib(state.plan.newSectors)}"
            is PartitionState.AlreadyMoved -> "обе копии в порядке, userdata уже в конце диска (${gib(state.entry.sectors)})"
            is PartitionState.NotPossible -> state.reason
        }
        return state to "eMMC ${gib(disk)}; $detail"
    }

    /** /data size and free space in bytes. */
    private fun readDf(sh: RootShell): Pair<Long, Long> {
        val cols = sh.rootText("df -k /data | tail -1").trim().split(Regex("\\s+"))
        return cols[1].toLong() * 1024 to cols[3].toLong() * 1024
    }

    private suspend fun onMovable(sh: RootShell, movePlan: MovePlan, data: Pair<Long, Long>) {
        plan = movePlan
        val rolledBack = lastResult(sh) == "rolledback"
        val op = if (rolledBack) {
            journalOp(sh, "Откат", "rollback.log", Journal.ROLLBACK, completed = true)
        } else {
            journalOp(sh, "Перенос", "move.log", Journal.MOVE, completed = false)
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
        val status = scriptStatus(sh)
        val ops = listOfNotNull(journalOp(sh, "Перенос", "move.log", Journal.MOVE, completed = status["GPT"] == "new"))
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
        journalOp(sh, "Расширение", "stage2.log", Journal.STAGE2, completed = true)?.let { ops += it }
        showDone(sh, ops)
    }

    private suspend fun runStage2(sh: RootShell) {
        event("Расширение файловой системы")
        val before = _state.value.operations.filter { it.title != "Расширение" }
        launchDetached(sh, "stage2")
        val (op, failure) = poll(sh, "Расширение", "stage2.log", Journal.STAGE2, before) { lines ->
            lines.any { Journal.payload(it).startsWith("@done") }
        }
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
            launchDetached(sh, "move")
            val (op, failure) = try {
                poll(sh, "Перенос", "move.log", Journal.MOVE, emptyList(), prefix = prepared) { false }
            } catch (e: IOException) {
                event("Связь с ГУ потеряна: ${e.message}. Перенос продолжается без программы.")
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
            pending.set(PendingOp.Rollback)
            event("Откат запущен")
            _state.update { it.copy(banner = Banner("Откат: ГУ перезагрузится само", BannerKind.Warning)) }
            launchDetached(sh, "rollback")
            val before = _state.value.operations
            val (_, failure) = try {
                poll(sh, "Откат", "rollback.log", Journal.ROLLBACK, before) { false }
            } catch (e: IOException) {
                event("Связь с ГУ потеряна: ${e.message}")
                return
            }
            if (failure != null) {
                pending.set(null)
                _state.update { it.copy(banner = Banner(failure, BannerKind.Error), actions = setOf(Action.Refresh)) }
            }
        }
    }

    private fun installFiles(sh: RootShell, movePlan: MovePlan) {
        val dir = MoveFiles.DIR
        sh.rootText("mkdir -p $dir && chmod 700 $dir && rm -f $dir/*.log $dir/state")
        val script = getApplication<Application>().assets.open(MoveFiles.ASSET_SCRIPT)
            .use { it.readBytes() }
            .toString(Charsets.UTF_8)
            .replace("\r\n", "\n")
            .toByteArray()
        val files = MoveFiles.binaries(movePlan) +
            ("params" to MoveFiles.params(movePlan).toByteArray()) +
            ("dataresize.sh" to script)
        for ((name, data) in files) {
            event("Запись $name (${data.size} Б)")
            sh.writeFile("$dir/$name", data)
        }
        sh.rootText("chmod 600 $dir/*")
        val sums = sh.rootText("cd $dir && md5sum ${files.keys.joinToString(" ")}").lines()
            .mapNotNull { line -> line.trim().split(Regex("\\s+")).takeIf { it.size == 2 }?.let { it[1] to it[0] } }
            .toMap()
        for ((name, data) in files) {
            if (sums[name] != GptPlanner.md5(data)) throw IOException("$name записан с ошибкой")
        }
    }

    private fun launchDetached(sh: RootShell, mode: String) {
        sh.rootText("setsid sh ${MoveFiles.SCRIPT} $mode </dev/null >/dev/null 2>&1 &")
    }

    private fun scriptStatus(sh: RootShell): Map<String, String> =
        sh.rootText("sh ${MoveFiles.SCRIPT} status").trim().split(' ')
            .mapNotNull { kv -> kv.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }
            .toMap()

    private fun readJournal(sh: RootShell, file: String): List<String> =
        sh.rootText("cat ${MoveFiles.DIR}/$file 2>/dev/null; true").lines().filter { it.isNotBlank() }

    /** Null when the script never wrote [file]. */
    private fun journalOp(sh: RootShell, title: String, file: String, defs: List<StepDef>, completed: Boolean): Operation? {
        val lines = readJournal(sh, file)
        if (lines.isEmpty()) return null
        val (steps, failure) = Journal.steps(defs, lines, completed)
        _state.update { it.copy(journal = lines) }
        return Operation(title, steps, failure)
    }

    /**
     * Outcome the script recorded in /cache (`moved`, `done`, `rolledback`). App prefs live on
     * /data, which a rollback returns to the moment of the move, so they cannot tell this.
     */
    private fun lastResult(sh: RootShell): String =
        sh.rootText("cat ${MoveFiles.DIR}/state 2>/dev/null; true").trim()

    /** Follows [file] until [finished] or `!FAIL`; returns the operation and the failure text. */
    private suspend fun poll(
        sh: RootShell,
        title: String,
        file: String,
        defs: List<StepDef>,
        before: List<Operation>,
        prefix: StepUi? = null,
        finished: (List<String>) -> Boolean,
    ): Pair<Operation, String?> {
        val deadline = System.currentTimeMillis() + POLL_LIMIT_MS
        while (true) {
            val lines = readJournal(sh, file)
            val (steps, failure) = Journal.steps(defs, lines)
            val op = Operation(title, listOfNotNull(prefix) + steps, failure)
            _state.update { it.copy(operations = before + op, journal = lines) }
            if (failure != null || finished(lines)) return op to failure
            if (System.currentTimeMillis() > deadline) throw IOException("скрипт не завершился за ${POLL_LIMIT_MS / 60_000} мин")
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
