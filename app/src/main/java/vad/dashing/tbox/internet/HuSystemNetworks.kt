package vad.dashing.tbox.internet

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.Inet4Address
import java.net.InetAddress

/**
 * One network tracked by ConnectivityService on the head unit.
 * [validated] is NetworkMonitor's result (`NET_CAPABILITY_VALIDATED`), not the
 * agent's own INTERNET claim.
 */
data class HuSystemNetwork(
    val interfaceName: String,
    val transports: List<HuNetTransport>,
    val ipv4: List<String>,
    val validated: Boolean,
    val isDefault: Boolean,
)

enum class HuNetTransport {
    WIFI,
    ETHERNET,
    CELLULAR,
    VPN,
    OTHER,
}

object HuSystemNetworks {
    fun read(cm: ConnectivityManager): List<HuSystemNetwork> {
        val active = cm.activeNetwork
        val rows = cm.allNetworks.mapNotNull { network ->
            val link = cm.getLinkProperties(network) ?: return@mapNotNull null
            val caps = cm.getNetworkCapabilities(network)
            fromLink(
                interfaceName = link.interfaceName,
                addresses = link.linkAddresses.map { it.address },
                wifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true,
                ethernet = caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true,
                cellular = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true,
                vpn = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true,
                validated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true,
                isDefault = network == active,
            )
        }
        return sort(rows)
    }

    fun fromLink(
        interfaceName: String?,
        addresses: List<InetAddress>,
        wifi: Boolean,
        ethernet: Boolean,
        cellular: Boolean,
        vpn: Boolean,
        validated: Boolean,
        isDefault: Boolean,
    ): HuSystemNetwork {
        val transports = buildList {
            if (wifi) add(HuNetTransport.WIFI)
            if (ethernet) add(HuNetTransport.ETHERNET)
            if (cellular) add(HuNetTransport.CELLULAR)
            if (vpn) add(HuNetTransport.VPN)
            if (isEmpty()) add(HuNetTransport.OTHER)
        }
        val ipv4 = addresses
            .filterIsInstance<Inet4Address>()
            .mapNotNull { it.hostAddress }
            .filter { it.isNotEmpty() && it != "0.0.0.0" }
            .distinct()
            .sorted()
        return HuSystemNetwork(
            interfaceName = interfaceName?.trim().orEmpty().ifEmpty { "—" },
            transports = transports,
            ipv4 = ipv4,
            validated = validated,
            isDefault = isDefault,
        )
    }

    fun sort(rows: List<HuSystemNetwork>): List<HuSystemNetwork> =
        rows.sortedWith(
            compareByDescending<HuSystemNetwork> { it.isDefault }
                .thenByDescending { it.validated }
                .thenBy { it.interfaceName },
        )
}
