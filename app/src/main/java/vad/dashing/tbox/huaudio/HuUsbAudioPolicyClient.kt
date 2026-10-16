package vad.dashing.tbox.huaudio

import android.content.Context
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import vad.dashing.tbox.HuDspStartup
import vad.dashing.tbox.TboxRepository
import vad.dashing.tbox.adb.LocalhostAdbSession

internal data class HuUsbAudioPolicyStatus(
    val appliedTemporarily: Boolean,
    val appliedPermanently: Boolean,
    val known: Boolean,
)

internal sealed interface HuUsbAudioResult {
    data class Done(val outcome: HuUsbAudioOutcome) : HuUsbAudioResult
    data class AdbFailed(val reason: String) : HuUsbAudioResult
    data class BuildFailed(val code: String) : HuUsbAudioResult
    data object PrepareFailed : HuUsbAudioResult
}

internal object HuUsbAudioPolicyClient {
    private const val TAG = "HU_USB_AUDIO"

    private val gate = Mutex()

    /**
     * The bind overlay shows up as a mount over one of the config paths; the permanent state
     * lives in the config audioserver actually reads (/vendor first, /system as its fallback).
     */
    suspend fun status(): HuUsbAudioPolicyStatus = withContext(Dispatchers.IO) {
        try {
            val mounts = File("/proc/mounts").readText()
            val temp = HuUsbAudioPolicyCodec.isBindMounted(mounts)
            val vendor = File(HuUsbAudioPolicyCodec.VENDOR_MAIN)
            val effective = if (vendor.isFile) {
                vendor.readText()
            } else {
                File(HuUsbAudioPolicyCodec.SYSTEM_MAIN).readText()
            }
            HuUsbAudioPolicyStatus(
                appliedTemporarily = temp,
                appliedPermanently = HuUsbAudioPolicyCodec.isApplied(effective),
                known = true,
            )
        } catch (_: IOException) {
            HuUsbAudioPolicyStatus(appliedTemporarily = false, appliedPermanently = false, known = false)
        }
    }

    /** Boot hook behind the auto-apply switch: skipped when already covered. */
    suspend fun applyAtStartup(context: Context) {
        HuDspStartup.run(pause = { delay(it) }, apply = {
            val status = status()
            if (status.known && !status.appliedTemporarily && !status.appliedPermanently) {
                install(context)
            }
        })
    }

    suspend fun install(context: Context): HuUsbAudioResult = gate.withLock {
        val source = withContext(Dispatchers.IO) { readSourceConfig() }
            ?: return HuUsbAudioResult.BuildFailed("nosource")
        val config = HuUsbAudioPolicyCodec.buildApplyConfig(source)
            ?: return HuUsbAudioResult.BuildFailed("layout")
        runScript(context, HuUsbAudioPolicyCodec.APPLY_SCRIPT_NAME, config) {
            HuUsbAudioPolicyCodec.applyScript(it)
        }
    }

    suspend fun restore(context: Context): HuUsbAudioResult = gate.withLock {
        runScript(context, HuUsbAudioPolicyCodec.RESTORE_SCRIPT_NAME, "") {
            HuUsbAudioPolicyCodec.restoreScript()
        }
    }

    /**
     * The overlay source: the current /system config (idempotent re-apply after the permanent
     * app), otherwise the stock vendor main config.
     */
    private fun readSourceConfig(): String? = try {
        File(HuUsbAudioPolicyCodec.SYSTEM_MAIN).takeIf { it.isFile }?.readText()
            ?: File(HuUsbAudioPolicyCodec.VENDOR_MAIN).takeIf { it.isFile }?.readText()
    } catch (_: IOException) {
        null
    }

    private suspend fun runScript(
        context: Context,
        scriptName: String,
        config: String,
        script: (String) -> String,
    ): HuUsbAudioResult {
        val app = context.applicationContext
        val scriptFile = try {
            withContext(Dispatchers.IO) { prepare(app, scriptName, config, script) }
        } catch (_: IOException) {
            return HuUsbAudioResult.PrepareFailed
        }
        val command = HuUsbAudioPolicyCodec.shellCommand(scriptFile.absolutePath)
        val session = LocalhostAdbSession.run(
            gateway = LocalhostAdbSession.AndroidGateway(),
            keysDir = app.filesDir.resolve("adb"),
            clientName = LocalhostAdbSession.defaultClientName(),
        ) { execute ->
            execute(command)
        }
        val result = when (session) {
            is LocalhostAdbSession.Result.Failed -> HuUsbAudioResult.AdbFailed(session.reason.name)
            is LocalhostAdbSession.Result.Ok -> HuUsbAudioResult.Done(
                HuUsbAudioPolicyCodec.parseOutcome("${session.value.stdout}\n${session.value.stderr}"),
            )
        }
        val outcome = (result as? HuUsbAudioResult.Done)?.outcome
        TboxRepository.addLog(
            level = if (outcome is HuUsbAudioOutcome.Failed || result !is HuUsbAudioResult.Done) "WARN" else "INFO",
            tag = TAG,
            message = "USB audio policy $scriptName: $result",
        )
        return result
    }

    private fun prepare(
        app: Context,
        scriptName: String,
        config: String,
        script: (String) -> String,
    ): File {
        val dir = File(app.filesDir, HuUsbAudioPolicyCodec.WORK_DIR)
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("mkdir $dir")
        var configPath: String? = null
        if (config.isNotEmpty()) {
            val configFile = File(dir, HuUsbAudioPolicyCodec.STAGED_CONFIG)
            configFile.writeText(config)
            configPath = configFile.absolutePath
        }
        val scriptFile = File(dir, scriptName)
        scriptFile.writeText(script(configPath ?: ""))
        return scriptFile
    }
}
