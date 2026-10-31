package vad.dashing.tbox.hutimezone

import android.content.Context
import vad.dashing.tbox.TboxRepository
import vad.dashing.tbox.adb.AdbShellResult
import vad.dashing.tbox.adb.LocalhostAdbSession

internal sealed interface HuTimeZoneResult {
    data class Zone(val zoneId: String) : HuTimeZoneResult
    data class AdbFailed(val reason: String) : HuTimeZoneResult
    data object ServiceFailed : HuTimeZoneResult
}

internal object HuTimeZoneClient {
    private const val TAG = "HU_TIME_ZONE"

    suspend fun read(context: Context): HuTimeZoneResult = session(context) { execute ->
        readZone(execute)
    }

    suspend fun write(context: Context, zoneId: String): HuTimeZoneResult {
        val command = HuTimeZoneCodec.writeCommand(zoneId)
        val result = session(context) { execute ->
            val status = HuTimeZoneCodec.parseInt(execute(command).stdout)
            if (status != 0) HuTimeZoneResult.ServiceFailed else readZone(execute)
        }
        TboxRepository.addLog(
            level = if (result is HuTimeZoneResult.Zone && result.zoneId == zoneId) "INFO" else "ERROR",
            tag = TAG,
            message = "Set HU time zone $zoneId: $result",
        )
        return result
    }

    private fun readZone(execute: (String) -> AdbShellResult): HuTimeZoneResult =
        HuTimeZoneCodec.parseString(execute(HuTimeZoneCodec.readCommand()).stdout)
            ?.let { HuTimeZoneResult.Zone(it) }
            ?: HuTimeZoneResult.ServiceFailed

    private suspend fun session(
        context: Context,
        block: (execute: (String) -> AdbShellResult) -> HuTimeZoneResult,
    ): HuTimeZoneResult {
        val app = context.applicationContext
        return when (
            val session = LocalhostAdbSession.run(
                gateway = LocalhostAdbSession.AndroidGateway(),
                keysDir = app.filesDir.resolve("adb"),
                clientName = LocalhostAdbSession.defaultClientName(),
                block = block,
            )
        ) {
            is LocalhostAdbSession.Result.Failed -> HuTimeZoneResult.AdbFailed(session.reason.name)
            is LocalhostAdbSession.Result.Ok -> session.value
        }
    }
}
