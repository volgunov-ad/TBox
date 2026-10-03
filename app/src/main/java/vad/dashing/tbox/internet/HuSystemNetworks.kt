package vad.dashing.tbox.internet

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketException

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
    HOTSPOT,
    OTHER,
}

/** IPv4 interface that is up on the head unit but is not a ConnectivityService network. */
data class HuLocalInterface(
    val name: String,
    val addresses: List<InetAddress>,
)

object HuSystemNetworks {
    fun read(cm: ConnectivityManager): List<HuSystemNetwork> =
        merge(readConnectivity(cm), readLocalInterfaces())

    fun readConnectivity(cm: ConnectivityManager): List<HuSystemNetwork> {
        val active = cm.activeNetwork
        return cm.allNetworks.mapNotNull { network ->
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
    }

    /**
     * SoftAP (wlan1 and the other tether names) is not a [ConnectivityManager] network.
     * Any other up IPv4 interface that ConnectivityService does not track is included too.
     */
    fun readLocalInterfaces(): List<HuLocalInterface> {
        val interfaces = try {
            NetworkInterface.getNetworkInterfaces() ?: return emptyList()
        } catch (_: SocketException) {
            return emptyList()
        }
        val rows = ArrayList<HuLocalInterface>()
        while (interfaces.hasMoreElements()) {
            val network = interfaces.nextElement()
            if (!network.isUp) continue
            val name = network.name?.trim().orEmpty()
            if (name.isEmpty() || name.equals("lo", ignoreCase = true) || name.equals("dummy0", ignoreCase = true)) {
                continue
            }
            val addresses = ArrayList<InetAddress>()
            val raw = network.inetAddresses
            while (raw.hasMoreElements()) {
                val address = raw.nextElement()
                if (!address.isLoopbackAddress) addresses.add(address)
            }
            if (addresses.none { it is Inet4Address && it.hostAddress != "0.0.0.0" }) continue
            rows.add(HuLocalInterface(name, addresses))
        }
        return rows
    }

    fun merge(
        connectivity: List<HuSystemNetwork>,
        local: List<HuLocalInterface>,
    ): List<HuSystemNetwork> {
        val known = connectivity.map { it.interfaceName.lowercase() }.toSet()
        val extras = local.mapNotNull { iface ->
            if (iface.name.lowercase() in known) return@mapNotNull null
            fromLink(
                interfaceName = iface.name,
                addresses = iface.addresses,
                wifi = false,
                ethernet = false,
                cellular = false,
                vpn = false,
                hotspot = looksLikeSoftAp(iface.name, iface.addresses),
                validated = false,
                isDefault = false,
            ).takeIf { it.ipv4.isNotEmpty() }
        }
        return sort(connectivity + extras)
    }

    fun looksLikeSoftAp(name: String, addresses: List<InetAddress>): Boolean {
        if (name.lowercase() in SOFT_AP_NAMES) return true
        return addresses.filterIsInstance<Inet4Address>().any { ipv4InTetherPool(it.hostAddress) }
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
        hotspot: Boolean = false,
    ): HuSystemNetwork {
        val transports = if (hotspot) {
            listOf(HuNetTransport.HOTSPOT)
        } else {
            buildList {
                if (wifi) add(HuNetTransport.WIFI)
                if (ethernet) add(HuNetTransport.ETHERNET)
                if (cellular) add(HuNetTransport.CELLULAR)
                if (vpn) add(HuNetTransport.VPN)
                if (isEmpty()) add(HuNetTransport.OTHER)
            }
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

    /** Same DHCP pools the head unit uses for its own SoftAP (192.168.42.0/24 … 192.168.51.0/24). */
    fun ipv4InTetherPool(ip: String?): Boolean {
        val parts = ip?.split('.') ?: return false
        if (parts.size != 4 || parts[0] != "192" || parts[1] != "168") return false
        val third = parts[2].toIntOrNull() ?: return false
        return third in 42..51
    }

    private val SOFT_AP_NAMES = setOf("wlan1", "ap0", "softap0", "swlan0", "wlan2")
}
