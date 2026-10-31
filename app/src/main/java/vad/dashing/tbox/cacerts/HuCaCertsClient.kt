package vad.dashing.tbox.cacerts

import android.content.Context
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import vad.dashing.tbox.TboxRepository
import vad.dashing.tbox.adb.LocalhostAdbSession

internal data class HuCaCertsStatus(
    val installed: Int,
    val total: Int,
)

internal sealed interface HuCaInstallResult {
    data class Done(val outcome: HuCaInstallOutcome) : HuCaInstallResult
    data class AdbFailed(val reason: String) : HuCaInstallResult
    data object PrepareFailed : HuCaInstallResult
}

internal object HuCaCertsClient {
    private const val TAG = "HU_CA_CERTS"
    private const val AUTO_ATTEMPTS = 3
    private const val AUTO_RETRY_DELAY_MS = 30_000L

    private val gate = Mutex()

    /** Overlay is lost on HU reboot; retries cover localhost ADB not being up yet right after boot. */
    suspend fun ensureInstalled(context: Context) {
        repeat(AUTO_ATTEMPTS) { attempt ->
            val current = status()
            if (current.installed == current.total) return
            val result = install(context)
            val outcome = (result as? HuCaInstallResult.Done)?.outcome
            if (outcome is HuCaInstallOutcome.Installed) {
                TboxRepository.addLog("INFO", TAG, "Root CA overlay installed (${outcome.totalCerts} certs)")
                return
            }
            val detail = when {
                result is HuCaInstallResult.AdbFailed -> "adb ${result.reason}"
                outcome is HuCaInstallOutcome.Failed -> outcome.code
                else -> "prepare"
            }
            TboxRepository.addLog("WARN", TAG, "Root CA auto-install attempt ${attempt + 1} failed: $detail")
            if (outcome is HuCaInstallOutcome.Failed && outcome.code == "nosu") return
            if (attempt < AUTO_ATTEMPTS - 1) delay(AUTO_RETRY_DELAY_MS)
        }
    }

    suspend fun status(): HuCaCertsStatus = withContext(Dispatchers.IO) {
        val dir = File(HuCaCertsCodec.SYSTEM_STORE_DIR)
        HuCaCertsStatus(
            installed = HuCaCertsCodec.CERT_FILES.count { File(dir, it).isFile },
            total = HuCaCertsCodec.CERT_FILES.size,
        )
    }

    suspend fun install(context: Context): HuCaInstallResult = gate.withLock {
        val app = context.applicationContext
        val workDir = app.filesDir.resolve(HuCaCertsCodec.ASSET_DIR)
        val script = try {
            withContext(Dispatchers.IO) { prepare(app, workDir) }
        } catch (_: IOException) {
            return HuCaInstallResult.PrepareFailed
        }
        val command = HuCaCertsCodec.shellCommand(script.absolutePath, workDir.absolutePath)
        val session = LocalhostAdbSession.run(
            gateway = LocalhostAdbSession.AndroidGateway(),
            keysDir = app.filesDir.resolve("adb"),
            clientName = LocalhostAdbSession.defaultClientName(),
        ) { execute ->
            execute(command)
        }
        when (session) {
            is LocalhostAdbSession.Result.Failed -> HuCaInstallResult.AdbFailed(session.reason.name)
            is LocalhostAdbSession.Result.Ok -> HuCaInstallResult.Done(
                HuCaCertsCodec.parseOutcome("${session.value.stdout}\n${session.value.stderr}"),
            )
        }
    }

    private fun prepare(context: Context, workDir: File): File {
        if (!workDir.isDirectory && !workDir.mkdirs()) throw IOException("mkdir $workDir")
        for (name in HuCaCertsCodec.CERT_FILES) {
            context.assets.open("${HuCaCertsCodec.ASSET_DIR}/$name").use { input ->
                File(workDir, name).outputStream().use { input.copyTo(it) }
            }
        }
        val script = File(workDir, HuCaCertsCodec.SCRIPT_NAME)
        script.writeText(HuCaCertsCodec.installScript())
        return script
    }
}
