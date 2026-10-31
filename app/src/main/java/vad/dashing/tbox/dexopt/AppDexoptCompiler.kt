package vad.dashing.tbox.dexopt

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import vad.dashing.tbox.TboxRepository
import vad.dashing.tbox.adb.LocalhostAdbSession

internal enum class AppDexoptPhase {
    /** Not checked in this process; [AppDexoptState.markedCompiled] tells the last known result. */
    Unknown,
    Checking,
    NotCompiled,
    Compiling,
    Compiled,
    Failed,
}

internal data class AppDexoptState(
    val phase: AppDexoptPhase = AppDexoptPhase.Unknown,
    /** Current APK was confirmed compiled earlier (survives process restarts). */
    val markedCompiled: Boolean = false,
    val detail: String = "",
    /** Another package is being compiled from App List; installd runs one dex2oat at a time. */
    val externalCompile: Boolean = false,
)

/**
 * HUs never run background dexopt (its job needs device idle — the screen is never off while
 * powered), so after every APK update the app stays at `verify` (interpreter + JIT) and cold
 * boot overloads the main thread into an ANR. Forces `speed` compilation through localhost ADB:
 * on demand from Settings, from the post-update hint, or at startup behind the auto switch.
 */
internal object AppDexoptCompiler {
    private const val TAG = "APP_DEXOPT"
    private const val PREFS = "app_dexopt"
    private const val KEY_DONE_UPDATE_TIME = "done_update_time"
    private const val KEY_HINT_UPDATE_TIME = "hint_update_time"
    private const val KEY_AUTO_ATTEMPT_UPDATE_TIME = "attempt_update_time"
    private const val KEY_AUTO_ATTEMPTS = "attempts"
    private const val MAX_AUTO_ATTEMPTS_PER_APK = 3

    /** Boot load (load average ~20 on A10) settles within a few minutes. */
    private const val AUTO_STARTUP_DELAY_MS = 180_000L

    /** `cmd package compile -m speed` takes ~130 s on the A10 HU; each poll is an ADB session. */
    private const val FIRST_POLL_DELAY_MS = 150_000L
    private const val POLL_INTERVAL_MS = 45_000L
    private const val COMPILE_TIMEOUT_MS = 480_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val gate = Mutex()
    private val _state = MutableStateFlow(AppDexoptState())
    val state: StateFlow<AppDexoptState> = _state.asStateFlow()

    private sealed interface Check {
        data object Compiled : Check
        data class NotCompiled(val compileStarted: Boolean) : Check
        data class Failed(val detail: String) : Check
    }

    /** Syncs [AppDexoptState.markedCompiled] from prefs; no ADB. */
    suspend fun refreshMarked(context: Context) {
        val app = context.applicationContext
        val marked = withContext(Dispatchers.IO) { isMarkedCompiled(app) }
        _state.update { it.copy(markedCompiled = marked) }
    }

    /** Once per installed APK, while it was not confirmed compiled. */
    suspend fun shouldOfferHint(context: Context): Boolean = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val updateTime = currentUpdateTime(app) ?: return@withContext false
        val prefs = prefs(app)
        prefs.getLong(KEY_DONE_UPDATE_TIME, 0L) != updateTime &&
            prefs.getLong(KEY_HINT_UPDATE_TIME, 0L) != updateTime
    }

    fun markHintShown(context: Context) {
        val app = context.applicationContext
        scope.launch(Dispatchers.IO) {
            val updateTime = currentUpdateTime(app) ?: return@launch
            prefs(app).edit { putLong(KEY_HINT_UPDATE_TIME, updateTime) }
        }
    }

    fun checkStatus(context: Context) {
        val app = context.applicationContext
        scope.launch { runExclusive { runCheck(app, startCompile = false) } }
    }

    fun compile(context: Context) {
        val app = context.applicationContext
        scope.launch { runExclusive { runCompile(app) } }
    }

    fun setExternalCompileActive(active: Boolean) {
        _state.update { it.copy(externalCompile = active) }
    }

    /** Startup hook behind the auto switch. */
    suspend fun autoCompileAtStartup(context: Context) {
        val app = context.applicationContext
        val (updateTime, attempts) = withContext(Dispatchers.IO) {
            val updateTime = currentUpdateTime(app) ?: return@withContext null
            val prefs = prefs(app)
            if (prefs.getLong(KEY_DONE_UPDATE_TIME, 0L) == updateTime) return@withContext null
            val attempts = if (prefs.getLong(KEY_AUTO_ATTEMPT_UPDATE_TIME, 0L) == updateTime) {
                prefs.getInt(KEY_AUTO_ATTEMPTS, 0)
            } else {
                0
            }
            updateTime to attempts
        } ?: return
        if (attempts >= MAX_AUTO_ATTEMPTS_PER_APK) return
        delay(AUTO_STARTUP_DELAY_MS)
        // Wait for a manual check/compile instead of silently losing the attempt to tryLock.
        gate.withLock {
            if (_state.value.externalCompile) return
            prefs(app).edit {
                putLong(KEY_AUTO_ATTEMPT_UPDATE_TIME, updateTime)
                putInt(KEY_AUTO_ATTEMPTS, attempts + 1)
            }
            runCompile(app)
        }
    }

    private suspend fun runExclusive(block: suspend () -> Unit) {
        if (!gate.tryLock()) return
        try {
            block()
        } finally {
            gate.unlock()
        }
    }

    private suspend fun runCheck(
        app: Context,
        startCompile: Boolean,
        tcpEnabled: AtomicBoolean? = null,
    ): Check {
        setPhase(AppDexoptPhase.Checking)
        val check = queryStatus(app, startCompile, tcpEnabled)
        when (check) {
            Check.Compiled -> markCompiled(app)
            is Check.NotCompiled -> {
                prefs(app).edit { remove(KEY_DONE_UPDATE_TIME) }
                _state.update {
                    it.copy(phase = AppDexoptPhase.NotCompiled, markedCompiled = false, detail = "")
                }
            }
            is Check.Failed -> {
                TboxRepository.addLog("WARN", TAG, "Dexopt status check failed: ${check.detail}")
                setPhase(AppDexoptPhase.Failed, check.detail)
            }
        }
        return check
    }

    private suspend fun runCompile(app: Context) {
        if (_state.value.externalCompile) {
            TboxRepository.addLog("INFO", TAG, "Skipped: another package is compiling from App List")
            return
        }
        // One TCP enable for the start and every poll: each restore is an adbd restart.
        val tcpEnabled = AtomicBoolean(false)
        try {
            pollCompile(app, tcpEnabled)
        } finally {
            if (tcpEnabled.get()) LocalhostAdbSession.restoreTcpOff(LocalhostAdbSession.AndroidGateway())
        }
    }

    private suspend fun pollCompile(app: Context, tcpEnabled: AtomicBoolean) {
        val first = runCheck(app, startCompile = true, tcpEnabled = tcpEnabled)
        if (first !is Check.NotCompiled || !first.compileStarted) return
        TboxRepository.addLog("INFO", TAG, "Started speed compilation")
        setPhase(AppDexoptPhase.Compiling)
        var waited = FIRST_POLL_DELAY_MS
        delay(FIRST_POLL_DELAY_MS)
        while (true) {
            when (val poll = queryStatus(app, startCompile = false, tcpEnabled = tcpEnabled)) {
                Check.Compiled -> {
                    markCompiled(app)
                    TboxRepository.addLog("INFO", TAG, "App compiled with speed filter")
                    return
                }
                is Check.Failed ->
                    TboxRepository.addLog("WARN", TAG, "Dexopt poll failed: ${poll.detail}")
                is Check.NotCompiled -> Unit
            }
            if (waited >= COMPILE_TIMEOUT_MS) break
            delay(POLL_INTERVAL_MS)
            waited += POLL_INTERVAL_MS
        }
        TboxRepository.addLog("WARN", TAG, "App still not compiled after ${COMPILE_TIMEOUT_MS / 1000}s")
        setPhase(AppDexoptPhase.Failed, "timeout")
    }

    /** Tracks whether any session of a compile run turned ADB TCP on. */
    private class TcpHold {
        val enabled = AtomicBoolean(false)
    }

    private suspend fun queryStatus(app: Context, startCompile: Boolean, tcpEnabled: AtomicBoolean?): Check {
        val packageName = app.packageName
        val session = LocalhostAdbSession.run(
            gateway = LocalhostAdbSession.AndroidGateway(),
            keysDir = app.filesDir.resolve("adb"),
            clientName = LocalhostAdbSession.defaultClientName(),
            afterSession = if (tcpEnabled != null) {
                LocalhostAdbSession.AfterSession.LeaveTcpEnabled
            } else {
                LocalhostAdbSession.AfterSession.RestorePreviousTcp
            },
            onTcpEnabled = { tcpEnabled?.set(true) },
        ) { execute ->
            val status = execute(AppDexoptCodec.statusCommand(packageName))
            val output = "${status.stdout}\n${status.stderr}"
            when {
                AppDexoptCodec.parseFilters(output).isEmpty() -> Check.Failed(output.trim().take(160))
                AppDexoptCodec.isFullyCompiled(output) -> Check.Compiled
                startCompile -> {
                    execute(AppDexoptCodec.compileCommand(packageName))
                    Check.NotCompiled(compileStarted = true)
                }
                else -> Check.NotCompiled(compileStarted = false)
            }
        }
        return when (session) {
            is LocalhostAdbSession.Result.Failed -> Check.Failed("adb ${session.reason.name}")
            is LocalhostAdbSession.Result.Ok -> session.value
        }
    }

    private fun markCompiled(app: Context) {
        currentUpdateTime(app)?.let { time -> prefs(app).edit { putLong(KEY_DONE_UPDATE_TIME, time) } }
        _state.update { it.copy(phase = AppDexoptPhase.Compiled, markedCompiled = true, detail = "") }
    }

    private fun setPhase(phase: AppDexoptPhase, detail: String = "") {
        _state.update { it.copy(phase = phase, detail = detail) }
    }

    private fun isMarkedCompiled(app: Context): Boolean {
        val updateTime = currentUpdateTime(app) ?: return false
        return prefs(app).getLong(KEY_DONE_UPDATE_TIME, 0L) == updateTime
    }

    private fun currentUpdateTime(app: Context): Long? = runCatching {
        app.packageManager.getPackageInfo(app.packageName, 0).lastUpdateTime
    }.getOrNull()

    private fun prefs(app: Context): SharedPreferences =
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
