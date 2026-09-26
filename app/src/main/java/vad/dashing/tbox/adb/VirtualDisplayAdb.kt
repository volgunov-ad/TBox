package vad.dashing.tbox.adb

import android.content.Context
import android.content.pm.PackageManager
import java.io.File
import vad.dashing.tbox.TboxRepository
import vad.dashing.tbox.VirtualDisplayLaunchPolicy

/**
 * Lists HU displays and launches apps onto a chosen display via localhost ADB.
 *
 * - [refreshDisplayList]: restore ADB TCP to the previous state after the dump (like permission grants).
 * - [launchOnDisplay]: leave ADB TCP enabled after `am start --display`.
 *
 * Before start, [launchOnDisplay] remaps the stored display id by size when the catalog
 * changed (launcher restart). [VirtualDisplayLaunchPolicy.RELOCATE] force-stops the package
 * first; [VirtualDisplayLaunchPolicy.NEW_INSTANCE] adds multiple-task flags.
 */
object VirtualDisplayAdb {
    private const val TAG = "VD_ADB"
    const val DUMPSYS_DISPLAY_COMMAND = "dumpsys display"

    sealed class RefreshOutcome {
        data class Success(val displays: List<HuDisplayInfo>) : RefreshOutcome()
        data class Failed(
            val reason: Reason,
            val detail: String = "",
        ) : RefreshOutcome()
    }

    sealed class LaunchOutcome {
        data class Success(
            val displayId: Int,
            val remapped: Boolean = false,
            val widthPx: Int = 0,
            val heightPx: Int = 0,
        ) : LaunchOutcome()

        data class Failed(
            val reason: Reason,
            val detail: String = "",
        ) : LaunchOutcome()
    }

    enum class Reason {
        TcpEnableFailed,
        TcpNotReady,
        AdbConnectFailed,
        DumpParseEmpty,
        ShellCommandFailed,
        NoLaunchActivity,
        InvalidDisplayId,
        DisplayNotFound,
    }

    suspend fun refreshDisplayList(context: Context): RefreshOutcome {
        val appContext = context.applicationContext
        return refreshDisplayListWith(
            gateway = LocalhostAdbSession.AndroidGateway(),
            keysDir = appContext.filesDir.resolve("adb"),
            clientName = LocalhostAdbSession.defaultClientName(),
        )
    }

    internal suspend fun refreshDisplayListWith(
        gateway: LocalhostAdbSession.Gateway,
        keysDir: File,
        clientName: String,
        dumpCommand: String = DUMPSYS_DISPLAY_COMMAND,
        host: String = LocalhostAdbSession.LOCAL_HOST,
        port: Int = HuAdbControl.TCP_ENABLED_PORT,
        readyTimeoutMs: Long = LocalhostAdbSession.TCP_READY_TIMEOUT_MS,
        nowMs: () -> Long = { System.currentTimeMillis() },
        delayMs: (suspend (Long) -> Unit)? = null,
    ): RefreshOutcome {
        val session = LocalhostAdbSession.run(
            gateway = gateway,
            keysDir = keysDir,
            clientName = clientName,
            host = host,
            port = port,
            readyTimeoutMs = readyTimeoutMs,
            nowMs = nowMs,
            delayMs = delayMs,
            afterSession = LocalhostAdbSession.AfterSession.RestorePreviousTcp,
        ) { execute ->
            execute(dumpCommand)
        }
        return when (session) {
            is LocalhostAdbSession.Result.Failed ->
                RefreshOutcome.Failed(mapSessionReason(session.reason), session.detail)
            is LocalhostAdbSession.Result.Ok -> {
                val shell = session.value
                val dump = listOf(shell.stdout, shell.stderr)
                    .firstOrNull { it.isNotBlank() }
                    .orEmpty()
                if (shell.exitCode != null &&
                    shell.exitCode != 0 &&
                    dump.isBlank()
                ) {
                    return RefreshOutcome.Failed(
                        Reason.ShellCommandFailed,
                        "exit ${shell.exitCode}",
                    )
                }
                val displays = HuDisplayDumpParser.parse(dump)
                if (displays.isEmpty()) {
                    TboxRepository.addLog(
                        level = "WARN",
                        tag = TAG,
                        message = "dumpsys display parsed 0 displays (stdout ${shell.stdout.length} chars)",
                    )
                    return RefreshOutcome.Failed(Reason.DumpParseEmpty)
                }
                TboxRepository.addLog(
                    level = "INFO",
                    tag = TAG,
                    message = "HU displays refreshed: ${displays.joinToString { it.label() }}",
                )
                RefreshOutcome.Success(displays)
            }
        }
    }

    /**
     * Launch [packageName] on the display identified by [displayId] (+ optional size for remap).
     *
     * Uses [cachedDisplays] when they already resolve; otherwise refreshes via dumpsys in the
     * same ADB session as the launch (TCP left enabled).
     */
    suspend fun launchOnDisplay(
        context: Context,
        packageName: String,
        displayId: Int,
        displayWidthPx: Int? = null,
        displayHeightPx: Int? = null,
        policy: VirtualDisplayLaunchPolicy = VirtualDisplayLaunchPolicy.DEFAULT,
        cachedDisplays: List<HuDisplayInfo> = emptyList(),
        onDisplaysRefreshed: (List<HuDisplayInfo>) -> Unit = {},
    ): LaunchOutcome {
        val appContext = context.applicationContext
        val component = resolveLaunchComponent(appContext.packageManager, packageName)
            ?: return LaunchOutcome.Failed(Reason.NoLaunchActivity)
        if (displayId < 0) {
            return LaunchOutcome.Failed(Reason.InvalidDisplayId)
        }
        return launchOnDisplayWith(
            gateway = LocalhostAdbSession.AndroidGateway(),
            keysDir = appContext.filesDir.resolve("adb"),
            clientName = LocalhostAdbSession.defaultClientName(),
            packageName = packageName.trim(),
            preferredDisplayId = displayId,
            preferredWidthPx = displayWidthPx,
            preferredHeightPx = displayHeightPx,
            policy = policy,
            component = component,
            cachedDisplays = cachedDisplays,
            onDisplaysRefreshed = onDisplaysRefreshed,
        )
    }

    internal suspend fun launchOnDisplayWith(
        gateway: LocalhostAdbSession.Gateway,
        keysDir: File,
        clientName: String,
        packageName: String,
        preferredDisplayId: Int,
        preferredWidthPx: Int?,
        preferredHeightPx: Int?,
        policy: VirtualDisplayLaunchPolicy,
        component: String,
        cachedDisplays: List<HuDisplayInfo> = emptyList(),
        onDisplaysRefreshed: (List<HuDisplayInfo>) -> Unit = {},
        host: String = LocalhostAdbSession.LOCAL_HOST,
        port: Int = HuAdbControl.TCP_ENABLED_PORT,
        readyTimeoutMs: Long = LocalhostAdbSession.TCP_READY_TIMEOUT_MS,
        nowMs: () -> Long = { System.currentTimeMillis() },
        delayMs: (suspend (Long) -> Unit)? = null,
    ): LaunchOutcome {
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
            // Always re-discover before start so remapped ids survive launcher restarts.
            val dumpShell = execute(DUMPSYS_DISPLAY_COMMAND)
            val dump = listOf(dumpShell.stdout, dumpShell.stderr)
                .firstOrNull { it.isNotBlank() }
                .orEmpty()
            val parsed = HuDisplayDumpParser.parse(dump)
            if (parsed.isNotEmpty()) {
                onDisplaysRefreshed(parsed)
            }
            val catalog = parsed.ifEmpty { cachedDisplays }

            val resolved = VirtualDisplayTargetResolver.resolve(
                preferredId = preferredDisplayId,
                preferredWidth = preferredWidthPx,
                preferredHeight = preferredHeightPx,
                catalog = catalog,
            )
            when (resolved) {
                is VirtualDisplayTargetResolver.ResolveResult.Failed ->
                    return@run LaunchStep.ResolveFailed(resolved)
                is VirtualDisplayTargetResolver.ResolveResult.Matched -> {
                    val targetId = resolved.display.displayId
                    if (policy == VirtualDisplayLaunchPolicy.RELOCATE) {
                        val stop = execute(buildForceStopCommand(packageName))
                        val stopFail = AdbShellResults.failureDetail(stop)
                        if (stopFail != null) {
                            TboxRepository.addLog(
                                level = "WARN",
                                tag = TAG,
                                message = "am force-stop $packageName: $stopFail (continuing)",
                            )
                        }
                    }
                    val startCmd = buildAmStartOnDisplayCommand(
                        displayId = targetId,
                        component = component,
                        multipleTask = policy == VirtualDisplayLaunchPolicy.NEW_INSTANCE,
                    )
                    val start = execute(startCmd)
                    LaunchStep.Started(
                        matched = resolved,
                        startResult = start,
                    )
                }
            }
        }

        return when (session) {
            is LocalhostAdbSession.Result.Failed ->
                LaunchOutcome.Failed(mapSessionReason(session.reason), session.detail)
            is LocalhostAdbSession.Result.Ok -> when (val step = session.value) {
                is LaunchStep.ResolveFailed -> {
                    val detail = step.failed.detail.ifBlank { step.failed.reason.name }
                    TboxRepository.addLog(
                        level = "ERROR",
                        tag = TAG,
                        message = "VD remap failed: $detail",
                    )
                    LaunchOutcome.Failed(Reason.DisplayNotFound, detail)
                }
                is LaunchStep.Started -> {
                    val shellFailure = AdbShellResults.failureDetail(step.startResult)
                    if (shellFailure != null) {
                        TboxRepository.addLog(
                            level = "ERROR",
                            tag = TAG,
                            message = "am start --display failed: $shellFailure",
                        )
                        return LaunchOutcome.Failed(Reason.ShellCommandFailed, shellFailure)
                    }
                    val d = step.matched.display
                    val remapNote = if (step.matched.remapped) {
                        ", remapped from $preferredDisplayId"
                    } else {
                        ""
                    }
                    TboxRepository.addLog(
                        level = "INFO",
                        tag = TAG,
                        message = "Launched $component on display ${d.displayId}" +
                            " (${d.widthPx}×${d.heightPx}$remapNote)" +
                            " policy=${policy.storageKey} via localhost ADB",
                    )
                    LaunchOutcome.Success(
                        displayId = d.displayId,
                        remapped = step.matched.remapped,
                        widthPx = d.widthPx,
                        heightPx = d.heightPx,
                    )
                }
            }
        }
    }

    fun buildAmStartOnDisplayCommand(
        displayId: Int,
        component: String,
        multipleTask: Boolean = false,
    ): String {
        val flags = if (multipleTask) {
            " --activity-multiple-task --activity-new-task"
        } else {
            ""
        }
        return "am start --display $displayId$flags -n $component"
    }

    fun buildForceStopCommand(packageName: String): String =
        "am force-stop ${packageName.trim()}"

    fun resolveLaunchComponent(pm: PackageManager, packageName: String): String? {
        val pkg = packageName.trim()
        if (pkg.isEmpty()) return null
        val intent = runCatching { pm.getLaunchIntentForPackage(pkg) }.getOrNull() ?: return null
        val component = intent.component ?: return null
        return "${component.packageName}/${component.className}"
    }

    private sealed class LaunchStep {
        data class ResolveFailed(
            val failed: VirtualDisplayTargetResolver.ResolveResult.Failed,
        ) : LaunchStep()

        data class Started(
            val matched: VirtualDisplayTargetResolver.ResolveResult.Matched,
            val startResult: AdbShellResult,
        ) : LaunchStep()
    }

    private fun mapSessionReason(reason: LocalhostAdbSession.Reason): Reason = when (reason) {
        LocalhostAdbSession.Reason.TcpEnableFailed -> Reason.TcpEnableFailed
        LocalhostAdbSession.Reason.TcpNotReady -> Reason.TcpNotReady
        LocalhostAdbSession.Reason.AdbConnectFailed -> Reason.AdbConnectFailed
    }
}
