package vad.dashing.tbox.ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import vad.dashing.tbox.R
import vad.dashing.tbox.internet.HuNetTransport
import vad.dashing.tbox.internet.HuSystemNetwork
import vad.dashing.tbox.internet.HuSystemNetworks

@Composable
fun HuSystemNetworksSection() {
    val context = LocalContext.current
    var rows by remember { mutableStateOf<List<HuSystemNetwork>>(emptyList()) }

    DisposableEffect(context) {
        val appContext = context.applicationContext
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (cm == null) {
            return@DisposableEffect onDispose {}
        }
        fun publish() {
            rows = runCatching { HuSystemNetworks.read(cm) }.getOrDefault(emptyList())
        }
        publish()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = publish()
            override fun onLost(network: Network) = publish()
            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities,
            ) = publish()
            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) =
                publish()
        }
        val request = NetworkRequest.Builder()
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        val registered = runCatching {
            cm.registerNetworkCallback(request, callback, Handler(Looper.getMainLooper()))
        }.isSuccess
        onDispose {
            if (registered) {
                runCatching { cm.unregisterNetworkCallback(callback) }
            }
        }
    }

    LaunchedEffect(context) {
        val cm = context.applicationContext
            .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return@LaunchedEffect
        while (isActive) {
            delay(2_000)
            rows = runCatching { HuSystemNetworks.read(cm) }.getOrDefault(emptyList())
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        SettingsTitle(stringResource(R.string.info_hu_networks))
        if (rows.isEmpty()) {
            StatusRow(
                label = stringResource(R.string.info_hu_networks),
                value = stringResource(R.string.info_hu_networks_none),
            )
        } else {
            val internetYes = stringResource(R.string.info_hu_network_internet_yes)
            val internetNo = stringResource(R.string.info_hu_network_internet_no)
            val defaultRoute = stringResource(R.string.info_hu_network_default)
            val noIp = stringResource(R.string.info_hu_network_no_ip)
            val transportNames = mapOf(
                HuNetTransport.WIFI to stringResource(R.string.info_hu_transport_wifi),
                HuNetTransport.ETHERNET to stringResource(R.string.info_hu_transport_ethernet),
                HuNetTransport.CELLULAR to stringResource(R.string.info_hu_transport_cellular),
                HuNetTransport.VPN to stringResource(R.string.info_hu_transport_vpn),
                HuNetTransport.HOTSPOT to stringResource(R.string.info_hu_transport_hotspot),
                HuNetTransport.OTHER to stringResource(R.string.info_hu_transport_other),
            )
            rows.forEach { row ->
                StatusRow(
                    label = networkLabel(row, transportNames),
                    value = networkValue(row, internetYes, internetNo, defaultRoute, noIp),
                    valueMaxLines = 3,
                )
            }
        }
    }
}

private fun networkLabel(
    row: HuSystemNetwork,
    transportNames: Map<HuNetTransport, String>,
): String {
    val transport = row.transports.joinToString(separator = " · ") { transportNames.getValue(it) }
    return "${row.interfaceName} · $transport"
}

private fun networkValue(
    row: HuSystemNetwork,
    internetYes: String,
    internetNo: String,
    defaultRoute: String,
    noIp: String,
): String {
    val ip = row.ipv4.joinToString(separator = ", ").ifEmpty { noIp }
    if (HuNetTransport.HOTSPOT in row.transports) return ip
    val internet = if (row.validated) internetYes else internetNo
    return if (row.isDefault) "$ip · $internet · $defaultRoute" else "$ip · $internet"
}
