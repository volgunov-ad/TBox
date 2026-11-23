package vad.dashing.store.dexopt

import android.content.Context
import android.os.Build
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

sealed interface CompileState {
    data object Unknown : CompileState
    data object Checking : CompileState

    /** [filter] is `speed` / `everything`. */
    data class Compiled(val filter: String) : CompileState

    /** [filter] e.g. `speed-profile`, `verify`, `run-from-apk`; [tooLarge] blocks `speed`. */
    data class NotCompiled(val filter: String, val tooLarge: Boolean = false) : CompileState
    data object Compiling : CompileState
    data class Failed(val message: String) : CompileState
}

/** Machine-readable failure keys the UI maps to localized strings. */
object CompileErrors {
    const val ADB_CONNECT = "adb_connect"
    const val TIMEOUT = "compile_timeout"
    const val TOO_LARGE = "apk_too_large"
}

/**
 * Head units never run background dexopt (it needs a long screen-off idle), so every app
 * installed or updated from the store stays in the interpreter until compiled with `speed`.
 * One compilation at a time: installd runs one dex2oat anyway.
 */
class StoreAppCompiler(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _states = MutableStateFlow<Map<String, CompileState>>(emptyMap())
    val states: StateFlow<Map<String, CompileState>> = _states.asStateFlow()

    @Volatile private var compilingPackage: String? = null

    val isCompiling: Boolean get() = compilingPackage != null

    suspend fun check(packageName: String) {
        if (compilingPackage == packageName) return
        set(packageName, CompileState.Checking)
        val state = try {
            withContext(Dispatchers.IO) { queryState(packageName, allowAdb = canUseAdbQuietly()) }
        } catch (error: CancellationException) {
            set(packageName, CompileState.Unknown)
            throw error
        }
        if (compilingPackage != packageName) set(packageName, state)
    }

    suspend fun compile(packageName: String) {
        if (compilingPackage != null) return
        val size = withContext(Dispatchers.IO) { apkBytes(packageName) }
        if (size == null) {
            set(packageName, CompileState.Unknown)
            return
        }
        if (size > DexoptCodec.SPEED_MAX_APK_BYTES) {
            set(packageName, CompileState.Failed(CompileErrors.TOO_LARGE))
            return
        }
        compilingPackage = packageName
        set(packageName, CompileState.Compiling)
        var enabledTcp = false
        try {
            val result = withContext(Dispatchers.IO) {
                enabledTcp = StoreAdbShell.enableTcpIfNeeded()
                try {
                    openShell().use { it.execute(DexoptCodec.compileCommand(packageName)) }
                } catch (error: IOException) {
                    throw AdbConnectException(error)
                }
                prefs.edit().putBoolean(KEY_ADB_AUTHORIZED, true).apply()
                pollUntilCompiled(packageName)
            }
            set(packageName, result)
        } catch (error: CancellationException) {
            set(packageName, CompileState.Unknown)
            throw error
        } catch (error: AdbConnectException) {
            set(packageName, CompileState.Failed(CompileErrors.ADB_CONNECT))
        } catch (error: Exception) {
            set(packageName, CompileState.Failed(error.message ?: error.javaClass.simpleName))
        } finally {
            compilingPackage = null
            if (enabledTcp) {
                withContext(NonCancellable + Dispatchers.IO) {
                    runCatching { StoreAdbShell.disableTcp() }
                }
            }
        }
    }

    private suspend fun pollUntilCompiled(packageName: String): CompileState {
        delay(FIRST_POLL_DELAY_MS)
        var waited = FIRST_POLL_DELAY_MS
        while (true) {
            val state = runCatching { queryState(packageName, allowAdb = true) }.getOrNull()
            if (state is CompileState.Compiled) return state
            if (waited >= COMPILE_TIMEOUT_MS) return CompileState.Failed(CompileErrors.TIMEOUT)
            delay(POLL_INTERVAL_MS)
            waited += POLL_INTERVAL_MS
        }
    }

    private fun queryState(packageName: String, allowAdb: Boolean): CompileState {
        val size = apkBytes(packageName) ?: return CompileState.Unknown
        val tooLarge = size > DexoptCodec.SPEED_MAX_APK_BYTES
        val filter = readOdexFilter(packageName)
            ?: (if (allowAdb) adbFilter(packageName) else null)
            ?: return CompileState.Unknown
        return if (DexoptCodec.isFullyCompiled(filter)) {
            CompileState.Compiled(filter)
        } else {
            CompileState.NotCompiled(filter, tooLarge)
        }
    }

    /** Quiet status reads only: never restart adbd or pop the debugging dialog on card open. */
    private fun canUseAdbQuietly(): Boolean =
        prefs.getBoolean(KEY_ADB_AUTHORIZED, false) && StoreAdbShell.isPortOpen()

    private fun adbFilter(packageName: String): String? = runCatching {
        openShell().use { shell ->
            val result = shell.execute(DexoptCodec.statusCommand(packageName))
            DexoptCodec.parseFilters("${result.stdout}\n${result.stderr}").firstOrNull()
        }
    }.getOrNull()

    private fun openShell(): StoreAdbShell =
        StoreAdbShell.open(File(app.filesDir, "adb"), CLIENT_NAME)

    private fun readOdexFilter(packageName: String): String? {
        val info = applicationInfo(packageName) ?: return null
        val apkDir = File(info.sourceDir ?: return null).parentFile ?: return null
        for (isa in instructionSets()) {
            val odex = File(apkDir, "oat/$isa/base.odex")
            if (!odex.isFile || !odex.canRead()) continue
            val filter = runCatching {
                odex.inputStream().use { input ->
                    val buffer = ByteArray(ODEX_HEADER_READ_BYTES)
                    var total = 0
                    while (total < buffer.size) {
                        val read = input.read(buffer, total, buffer.size - total)
                        if (read <= 0) break
                        total += read
                    }
                    DexoptCodec.parseOatCompilerFilter(buffer, total)
                }
            }.getOrNull()
            if (filter != null) return filter
        }
        return null
    }

    private fun instructionSets(): List<String> =
        Build.SUPPORTED_ABIS.mapNotNull { abi ->
            when (abi) {
                "arm64-v8a" -> "arm64"
                "armeabi-v7a", "armeabi" -> "arm"
                "x86_64" -> "x86_64"
                "x86" -> "x86"
                else -> null
            }
        }.distinct()

    private fun apkBytes(packageName: String): Long? {
        val info = applicationInfo(packageName) ?: return null
        val files = listOfNotNull(info.sourceDir) + info.splitSourceDirs.orEmpty()
        return files.sumOf { File(it).length() }
    }

    private fun applicationInfo(packageName: String) = runCatching {
        app.packageManager.getApplicationInfo(packageName, 0)
    }.getOrNull()

    private fun set(packageName: String, state: CompileState) {
        _states.update { it + (packageName to state) }
    }

    private class AdbConnectException(cause: Throwable) : IOException(cause.message, cause)

    private companion object {
        const val PREFS = "store_dexopt"
        const val KEY_ADB_AUTHORIZED = "adb_authorized"
        const val CLIENT_NAME = "dashing-store"
        const val ODEX_HEADER_READ_BYTES = 512 * 1024

        /** `speed` takes ~6 s for DCL on A9 and ~130 s for TBox Monitor on A10. */
        const val FIRST_POLL_DELAY_MS = 10_000L
        const val POLL_INTERVAL_MS = 15_000L
        const val COMPILE_TIMEOUT_MS = 600_000L
    }
}
