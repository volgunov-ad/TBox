package vad.dashing.tbox.hotspot

import android.content.Context
import android.net.wifi.WifiManager
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.SocketException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import vad.dashing.tbox.adb.LocalhostAdbSession

internal data class HuSoftApSnapshot(
    val radioEnabled: Boolean?,
    val read: HuSoftApRead?,
    val ipv4: String?,
    val errorCode: String?,
    val adbFailed: Boolean,
)

internal object HuSoftApClient {
    private val gate = Mutex()

    fun radioEnabled(context: Context): Boolean? {
        val wifi = context.applicationContext.getSystemService(WifiManager::class.java) ?: return null
        return try {
            val state = wifi.javaClass.getMethod("getWifiApState").invoke(wifi) as? Int ?: return null
            state == 13 || state == 12
        } catch (_: ReflectiveOperationException) {
            null
        }
    }

    fun hotspotIpv4(): String? {
        val ifaces = ArrayList<SoftApIface>()
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            while (interfaces.hasMoreElements()) {
                val network = interfaces.nextElement()
                if (!network.isUp) continue
                val addresses = network.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (address is Inet4Address && !address.isLoopbackAddress) {
                        val host = address.hostAddress ?: continue
                        ifaces.add(SoftApIface(network.name, host))
                    }
                }
            }
        } catch (_: SocketException) {
            return null
        }
        return HuSoftApCodec.selectHotspotIpv4(ifaces)
    }

    suspend fun refresh(context: Context): HuSoftApSnapshot = run(context, "get")

    /** A9: write 2.4 GHz and restart the AP, then read the new password. */
    suspend fun retune24(context: Context): HuSoftApSnapshot {
        val action = run(context, "band24")
        if (action.errorCode != null || action.adbFailed) return action
        var last = run(context, "get")
        repeat(3) {
            val read = last.read
            if (read != null && HuSoftApCodec.on24Ghz(read.liveMhz)) return last
            delay(2_000)
            last = run(context, "get")
        }
        return last
    }

    suspend fun setEnabled(context: Context, enabled: Boolean): HuSoftApSnapshot {
        val action = run(context, if (enabled) "up" else "down")
        if (action.errorCode != null || action.adbFailed) return action
        return run(context, "get")
    }

    private suspend fun run(context: Context, action: String): HuSoftApSnapshot = gate.withLock {
        val radio = radioEnabled(context)
        val ipv4 = hotspotIpv4()
        val apk = context.applicationInfo.sourceDir
        if (apk.isNullOrBlank()) {
            return HuSoftApSnapshot(radio, null, ipv4, "no-apk", adbFailed = true)
        }
        val command = HuSoftApCodec.shellCommand(apk, action)
        val session = LocalhostAdbSession.run(
            gateway = LocalhostAdbSession.AndroidGateway(),
            keysDir = context.applicationContext.filesDir.resolve("adb"),
            clientName = LocalhostAdbSession.defaultClientName(),
        ) { execute ->
            execute(command)
        }
        when (session) {
            is LocalhostAdbSession.Result.Failed -> HuSoftApSnapshot(
                radioEnabled = radio,
                read = null,
                ipv4 = ipv4,
                errorCode = session.reason.name,
                adbFailed = true,
            )
            is LocalhostAdbSession.Result.Ok -> {
                val stdout = session.value.stdout
                val stderr = session.value.stderr
                val combined = "$stdout\n$stderr"
                val read = if (action == "get") HuSoftApCodec.parse(combined) else null
                val actionOk = if (action == "get") null else HuSoftApCodec.parseActionOk(combined)
                val error = when {
                    action == "get" && read == null -> HuSoftApCodec.errorCode(combined) ?: "failed"
                    action != "get" && actionOk != true -> HuSoftApCodec.errorCode(combined) ?: "failed"
                    else -> null
                }
                HuSoftApSnapshot(
                    radioEnabled = radio,
                    read = read,
                    ipv4 = ipv4,
                    errorCode = error,
                    adbFailed = false,
                )
            }
        }
    }
}
