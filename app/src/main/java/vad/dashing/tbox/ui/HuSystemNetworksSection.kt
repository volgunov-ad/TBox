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
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import vad.dashing.tbox.R
import vad.dashing.tbox.internet.HuNetTransport
import vad.dashing.tbox.internet.HuSystemNetwork
import vad.dashing.tbox.internet.HuSystemNetworks
import vad.dashing.tbox.ui.theme.tboxBody
import vad.dashing.tbox.ui.theme.tboxHeadline

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

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.info_hu_networks),
            style = MaterialTheme.typography.tboxHeadline,
            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
        )
        if (rows.isEmpty()) {
            Text(
                text = stringResource(R.string.info_hu_networks_none),
                style = MaterialTheme.typography.tboxBody,
                modifier = Modifier.padding(bottom = 8.dp),
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
                HuNetTransport.OTHER to stringResource(R.string.info_hu_transport_other),
            )
            rows.forEach { row ->
                StatusRow(
                    label = networkLabel(row, transportNames),
                    value = networkValue(row, internetYes, internetNo, defaultRoute, noIp),
                    valueMaxLines = 3,
                    labelColumnWidthPercent = 42,
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
    val internet = if (row.validated) internetYes else internetNo
    return if (row.isDefault) "$ip · $internet · $defaultRoute" else "$ip · $internet"
}
