package vad.dashing.tbox.adb

import android.content.Context
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import vad.dashing.tbox.TboxRepository

/**
 * ADB package ops for App List advanced mode: hide/unhide, disable/enable, force-stop,
 * plus batch status from `pm list` / `ps`.
 *
 * While advanced mode is active, TCP :5555 is kept available ([AfterSession.LeaveTcpEnabled])
 * so each tap does not enable/restore. Leaving advanced mode or closing the dialog restores
 * TCP only if this session turned it on (port-open truth via [LocalhostAdbSession]).
 */
internal object PackageAdbActions {
    private const val TAG = "PKG_ADB"

    enum class Action {
        Hide,
        Unhide,
        Disable,
        Enable,
        ForceStop,
    }

    data class PackageStatus(
        val packageName: String,
        val systemHidden: Boolean = false,
        val disabled: Boolean = false,
        val running: Boolean = false,
    )

    enum class Reason {
        TcpEnableFailed,
        TcpNotReady,
        AdbConnectFailed,
        ShellCommandFailed,
        SessionNotActive,
        InvalidPackage,
    }

    sealed class Outcome {
        data class Success(val detail: String = "") : Outcome()
        data class Failed(val reason: Reason, val detail: String = "") : Outcome()
    }

    sealed class StatusLoadOutcome {
        data class Success(val statuses: Map<String, PackageStatus>) : StatusLoadOutcome()
        data class Failed(val reason: Reason, val detail: String = "") : StatusLoadOutcome()
    }

    private val sessionMutex = Mutex()
    private val sessionActive = AtomicBoolean(false)
    private val tcpEnabledByUs = AtomicBoolean(false)

    fun isAdvancedSessionActive(): Boolean = sessionActive.get()

    /** Test seam: clear advanced-session flags between unit tests. */
    internal fun resetSessionForTests() {
        sessionActive.set(false)
        tcpEnabledByUs.set(false)
    }

    fun buildHideCommand(packageName: String): String =
        "pm hide ${packageName.trim()}"

    fun buildUnhideCommand(packageName: String): String =
        "pm unhide ${packageName.trim()}"

    fun buildDisableCommand(packageName: String): String =
        "pm disable-user --user 0 ${packageName.trim()}"

    fun buildEnableCommand(packageName: String): String =
        "pm enable ${packageName.trim()}"

    fun buildForceStopCommand(packageName: String): String =
        "am force-stop ${packageName.trim()}"

    fun commandFor(action: Action, packageName: String): String = when (action) {
        Action.Hide -> buildHideCommand(packageName)
        Action.Unhide -> buildUnhideCommand(packageName)
        Action.Disable -> buildDisableCommand(packageName)
        Action.Enable -> buildEnableCommand(packageName)
        Action.ForceStop -> buildForceStopCommand(packageName)
    }

    /**
     * Parses `pm list packages` / `-d` / `-u` lines (`package:com.example`).
     */
    fun parsePmListPackages(output: String): Set<String> {
        val result = linkedSetOf<String>()
        output.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (!line.startsWith("package:")) return@forEach
            val pkg = line.removePrefix("package:").trim()
            if (pkg.isNotEmpty()) result += pkg
        }
        return result
    }

    /**
     * Process names from `ps -A` (NAME column). Package is treated as running when its
     * name equals a process name or a process name ends with the package (common for
     * `:service` suffixes — those still imply the app uid is alive).
     */
    fun parseRunningPackages(psOutput: String, candidates: Set<String>): Set<String> {
        if (candidates.isEmpty()) return emptySet()
        val names = linkedSetOf<String>()
        psOutput.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("USER") || line.startsWith("PID")) return@forEach
            val parts = line.split(Regex("\\s+"))
            if (parts.isEmpty()) return@forEach
            val name = parts.last().trim()
            if (name.isNotEmpty() && name != "NAME") names += name
        }
        return candidates.filterTo(linkedSetOf()) { pkg ->
            names.any { proc -> proc == pkg || proc.startsWith("$pkg:") }
        }
    }

    /**
     * Builds per-package status from batch shell dumps.
     * Hidden ≈ in `-u` but missing from the normal installed list.
     */
    fun buildStatuses(
        listed: Set<String>,
        listedIncludingUninstalled: Set<String>,
        disabled: Set<String>,
        running: Set<String>,
    ): Map<String, PackageStatus> {
        val all = linkedSetOf<String>().apply {
            addAll(listed)
            addAll(listedIncludingUninstalled)
            addAll(disabled)
            addAll(running)
        }
        return all.associateWith { pkg ->
            PackageStatus(
                packageName = pkg,
                systemHidden = pkg in listedIncludingUninstalled && pkg !in listed,
                disabled = pkg in disabled,
                running = pkg in running,
            )
        }
    }

    suspend fun enterAdvancedSession(context: Context): StatusLoadOutcome {
        val appContext = context.applicationContext
        return enterAdvancedSessionWith(
            gateway = LocalhostAdbSession.AndroidGateway(),
            keysDir = appContext.filesDir.resolve("adb"),
            clientName = LocalhostAdbSession.defaultClientName(),
        )
    }

    internal suspend fun enterAdvancedSessionWith(
        gateway: LocalhostAdbSession.Gateway,
        keysDir: File,
        clientName: String,
        host: String = LocalhostAdbSession.LOCAL_HOST,
        port: Int = HuAdbControl.TCP_ENABLED_PORT,
        readyTimeoutMs: Long = LocalhostAdbSession.TCP_READY_TIMEOUT_MS,
        nowMs: () -> Long = { System.currentTimeMillis() },
        delayMs: (suspend (Long) -> Unit)? = null,
    ): StatusLoadOutcome = sessionMutex.withLock {
        if (sessionActive.get()) {
            return@withLock loadStatusesWithUnlocked(
                gateway = gateway,
                keysDir = keysDir,
                clientName = clientName,
                host = host,
                port = port,
                readyTimeoutMs = readyTimeoutMs,
                nowMs = nowMs,
                delayMs = delayMs,
            )
        }
        gateway.refreshHuAdb()
        val availableBefore =
            gateway.isTcpEnabled() ||
                gateway.isTcpPortOpen(host, port, LocalhostAdbSession.TCP_PROBE_TIMEOUT_MS)
        val session = LocalhostAdbSession.run(
            gateway = gateway,
            keysDir = keysDir,
            clientName = clientName,
            host = host,
            port = port,
            readyTimeoutMs = readyTimeoutMs,
            nowMs = nowMs,
            delayMs = delayMs,
            afterSession = LocalhostAdbSession.AfterSession.LeaveTcpEnabled,
        ) { execute ->
            collectStatuses(execute)
        }
        return@withLock when (session) {
            is LocalhostAdbSession.Result.Failed ->
                StatusLoadOutcome.Failed(mapSessionReason(session.reason), session.detail)
            is LocalhostAdbSession.Result.Ok -> {
                sessionActive.set(true)
                tcpEnabledByUs.set(!availableBefore)
                TboxRepository.addLog(
                    level = "INFO",
                    tag = TAG,
                    message = "App list advanced ADB session started" +
                        if (!availableBefore) " (TCP enabled by us)" else "",
                )
                StatusLoadOutcome.Success(session.value)
            }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    suspend fun exitAdvancedSession(context: Context) {
        exitAdvancedSessionWith(LocalhostAdbSession.AndroidGateway())
    }

    internal suspend fun exitAdvancedSessionWith(gateway: LocalhostAdbSession.Gateway) {
        sessionMutex.withLock {
            if (!sessionActive.getAndSet(false)) {
                tcpEnabledByUs.set(false)
                return@withLock
            }
            val shouldRestore = tcpEnabledByUs.getAndSet(false)
            if (shouldRestore) {
                runCatching {
                    gateway.setTcpEnabled(false)
                    gateway.refreshHuAdb()
                }.onFailure { e ->
                    TboxRepository.addLog(
                        level = "WARN",
                        tag = TAG,
                        message = "Failed to restore ADB TCP after advanced mode: " +
                            (e.message ?: e.javaClass.simpleName),
                    )
                }
                TboxRepository.addLog(
                    level = "INFO",
                    tag = TAG,
                    message = "App list advanced ADB session ended; TCP restored off",
                )
            } else {
                TboxRepository.addLog(
                    level = "INFO",
                    tag = TAG,
                    message = "App list advanced ADB session ended; TCP left as-found",
                )
            }
        }
    }

    suspend fun refreshStatuses(context: Context): StatusLoadOutcome {
        val appContext = context.applicationContext
        return refreshStatusesWith(
            gateway = LocalhostAdbSession.AndroidGateway(),
            keysDir = appContext.filesDir.resolve("adb"),
            clientName = LocalhostAdbSession.defaultClientName(),
        )
    }

    internal suspend fun refreshStatusesWith(
        gateway: LocalhostAdbSession.Gateway,
        keysDir: File,
        clientName: String,
        host: String = LocalhostAdbSession.LOCAL_HOST,
        port: Int = HuAdbControl.TCP_ENABLED_PORT,
        readyTimeoutMs: Long = LocalhostAdbSession.TCP_READY_TIMEOUT_MS,
        nowMs: () -> Long = { System.currentTimeMillis() },
        delayMs: (suspend (Long) -> Unit)? = null,
    ): StatusLoadOutcome = sessionMutex.withLock {
        if (!sessionActive.get()) {
            return@withLock StatusLoadOutcome.Failed(Reason.SessionNotActive)
        }
        loadStatusesWithUnlocked(
            gateway = gateway,
            keysDir = keysDir,
            clientName = clientName,
            host = host,
            port = port,
            readyTimeoutMs = readyTimeoutMs,
            nowMs = nowMs,
            delayMs = delayMs,
        )
    }

    suspend fun runAction(context: Context, action: Action, packageName: String): Outcome {
        val appContext = context.applicationContext
        return runActionWith(
            gateway = LocalhostAdbSession.AndroidGateway(),
            keysDir = appContext.filesDir.resolve("adb"),
            clientName = LocalhostAdbSession.defaultClientName(),
            action = action,
            packageName = packageName,
        )
    }

    internal suspend fun runActionWith(
        gateway: LocalhostAdbSession.Gateway,
        keysDir: File,
        clientName: String,
        action: Action,
        packageName: String,
        host: String = LocalhostAdbSession.LOCAL_HOST,
        port: Int = HuAdbControl.TCP_ENABLED_PORT,
        readyTimeoutMs: Long = LocalhostAdbSession.TCP_READY_TIMEOUT_MS,
        nowMs: () -> Long = { System.currentTimeMillis() },
        delayMs: (suspend (Long) -> Unit)? = null,
    ): Outcome {
        val pkg = packageName.trim()
        if (pkg.isEmpty()) {
            return Outcome.Failed(Reason.InvalidPackage, "empty package")
        }
        return sessionMutex.withLock {
            if (!sessionActive.get()) {
                return@withLock Outcome.Failed(Reason.SessionNotActive)
            }
            val command = commandFor(action, pkg)
            val session = LocalhostAdbSession.run(
                gateway = gateway,
                keysDir = keysDir,
                clientName = clientName,
                host = host,
                port = port,
                readyTimeoutMs = readyTimeoutMs,
                nowMs = nowMs,
                delayMs = delayMs,
                afterSession = LocalhostAdbSession.AfterSession.LeaveTcpEnabled,
            ) { execute ->
                execute(command)
            }
            when (session) {
                is LocalhostAdbSession.Result.Failed ->
                    Outcome.Failed(mapSessionReason(session.reason), session.detail)
                is LocalhostAdbSession.Result.Ok -> {
                    val shell = session.value
                    val failure = AdbShellResults.failureDetail(shell)
                    if (failure != null) {
                        TboxRepository.addLog(
                            level = "WARN",
                            tag = TAG,
                            message = "$command failed: $failure",
                        )
                        return@withLock Outcome.Failed(Reason.ShellCommandFailed, failure)
                    }
                    val output = listOf(shell.stdout, shell.stderr)
                        .firstOrNull { it.isNotBlank() }
                        .orEmpty()
                        .trim()
                        .lines()
                        .firstOrNull { it.isNotBlank() }
                        .orEmpty()
                        .take(160)
                    Outcome.Success(output)
                }
            }
        }
    }

    private suspend fun loadStatusesWithUnlocked(
        gateway: LocalhostAdbSession.Gateway,
        keysDir: File,
        clientName: String,
        host: String,
        port: Int,
        readyTimeoutMs: Long,
        nowMs: () -> Long,
        delayMs: (suspend (Long) -> Unit)?,
    ): StatusLoadOutcome {
        val session = LocalhostAdbSession.run(
            gateway = gateway,
            keysDir = keysDir,
            clientName = clientName,
            host = host,
            port = port,
            readyTimeoutMs = readyTimeoutMs,
            nowMs = nowMs,
            delayMs = delayMs,
            afterSession = LocalhostAdbSession.AfterSession.LeaveTcpEnabled,
        ) { execute ->
            collectStatuses(execute)
        }
        return when (session) {
            is LocalhostAdbSession.Result.Failed ->
                StatusLoadOutcome.Failed(mapSessionReason(session.reason), session.detail)
            is LocalhostAdbSession.Result.Ok -> StatusLoadOutcome.Success(session.value)
        }
    }

    private fun collectStatuses(
        execute: (String) -> AdbShellResult,
    ): Map<String, PackageStatus> {
        val listedOut = shellText(execute("pm list packages"))
        val uninstalledOut = shellText(execute("pm list packages -u"))
        val disabledOut = shellText(execute("pm list packages -d"))
        val listed = parsePmListPackages(listedOut)
        val includingUninstalled = parsePmListPackages(uninstalledOut)
        val disabled = parsePmListPackages(disabledOut)
        val candidates = linkedSetOf<String>().apply {
            addAll(listed)
            addAll(includingUninstalled)
            addAll(disabled)
        }
        val psOut = shellText(execute("ps -A"))
        val running = parseRunningPackages(psOut, candidates)
        return buildStatuses(listed, includingUninstalled, disabled, running)
    }

    private fun shellText(result: AdbShellResult): String =
        listOf(result.stdout, result.stderr)
            .firstOrNull { it.isNotBlank() }
            .orEmpty()

    private fun mapSessionReason(reason: LocalhostAdbSession.Reason): Reason = when (reason) {
        LocalhostAdbSession.Reason.TcpEnableFailed -> Reason.TcpEnableFailed
        LocalhostAdbSession.Reason.TcpNotReady -> Reason.TcpNotReady
        LocalhostAdbSession.Reason.AdbConnectFailed -> Reason.AdbConnectFailed
    }
}
