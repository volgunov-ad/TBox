package vad.dashing.tbox.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import vad.dashing.tbox.R
import vad.dashing.tbox.mqtt.MqttApkStatus
import vad.dashing.tbox.mqtt.MqttBridgePollResult
import vad.dashing.tbox.mqtt.MqttBridgeStatusSnapshot
import vad.dashing.tbox.mqtt.MqttWireguardUi
import vad.dashing.tbox.mqtt.mqttRowDetail
import vad.dashing.tbox.mqtt.mqttWireguardUi
import vad.dashing.tbox.ui.theme.tboxBody

/**
 * «TBox MQTT» block on the Info tab. Shown only when `vad.dashing.mqtt` is installed.
 * Polls localhost status while this composable is in composition (Info tab selected).
 */
@Composable
fun MqttStatusInfoSection() {
    val context = LocalContext.current
    val installed = remember(context) {
        MqttApkStatus.isInstalled(context.packageManager)
    }
    if (!installed) return

    val client = remember { MqttApkStatus.defaultClient() }
    var poll by remember {
        mutableStateOf<MqttBridgePollResult>(MqttBridgePollResult.ServiceDown())
    }

    LaunchedEffect(client) {
        while (isActive) {
            poll = withContext(Dispatchers.IO) { MqttApkStatus.fetch(client) }
            delay(MqttApkStatus.POLL_MS)
        }
    }

    val yes = stringResource(R.string.value_yes)
    val no = stringResource(R.string.value_no)
    val wgOff = stringResource(R.string.info_mqtt_wg_off)
    val wgUp = stringResource(R.string.info_mqtt_wg_up)
    val wgError = stringResource(R.string.info_mqtt_wg_error)
    val serviceDownHint = stringResource(R.string.info_mqtt_service_down)
    val okColor = MaterialTheme.colorScheme.primary
    val failColor = MaterialTheme.colorScheme.error
    val neutralColor = MaterialTheme.colorScheme.onSurface

    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        SettingsTitle(stringResource(R.string.info_mqtt_section))

        when (val result = poll) {
            is MqttBridgePollResult.ServiceDown -> {
                StatusRow(
                    label = stringResource(R.string.info_mqtt_service),
                    value = no,
                    color = failColor,
                )
                DetailLine(result.detail.ifBlank { serviceDownHint })
                StatusRow(
                    label = stringResource(R.string.info_mqtt_broker),
                    value = no,
                    color = failColor,
                )
                StatusRow(
                    label = stringResource(R.string.info_mqtt_monitor_link),
                    value = no,
                    color = failColor,
                )
                StatusRow(
                    label = stringResource(R.string.info_mqtt_wireguard),
                    value = wgOff,
                    color = neutralColor,
                )
            }
            is MqttBridgePollResult.Ok -> {
                val status = result.status
                StatusRow(
                    label = stringResource(R.string.info_mqtt_service),
                    value = yes,
                    color = okColor,
                )
                BoolStatusRow(
                    label = stringResource(R.string.info_mqtt_broker),
                    up = status.brokerUp,
                    yes = yes,
                    no = no,
                    okColor = okColor,
                    failColor = failColor,
                )
                DetailLine(mqttRowDetail(status.brokerUp, status.brokerError))
                BoolStatusRow(
                    label = stringResource(R.string.info_mqtt_monitor_link),
                    up = status.monitorUp,
                    yes = yes,
                    no = no,
                    okColor = okColor,
                    failColor = failColor,
                )
                DetailLine(mqttRowDetail(status.monitorUp, status.monitorError))
                WireguardRow(
                    status = status,
                    wgOff = wgOff,
                    wgUp = wgUp,
                    wgError = wgError,
                    okColor = okColor,
                    failColor = failColor,
                    neutralColor = neutralColor,
                )
                if (status.lastError.isNotBlank()) {
                    DetailLine(status.lastError)
                }
            }
        }
    }
}

@Composable
private fun BoolStatusRow(
    label: String,
    up: Boolean,
    yes: String,
    no: String,
    okColor: Color,
    failColor: Color,
) {
    StatusRow(
        label = label,
        value = if (up) yes else no,
        color = if (up) okColor else failColor,
    )
}

@Composable
private fun WireguardRow(
    status: MqttBridgeStatusSnapshot,
    wgOff: String,
    wgUp: String,
    wgError: String,
    okColor: Color,
    failColor: Color,
    neutralColor: Color,
) {
    val ui = mqttWireguardUi(status.wireguardEnabled, status.tunnelError)
    val (value, color) = when (ui) {
        MqttWireguardUi.OFF -> wgOff to neutralColor
        MqttWireguardUi.UP -> wgUp to okColor
        MqttWireguardUi.ERROR -> wgError to failColor
    }
    StatusRow(
        label = stringResource(R.string.info_mqtt_wireguard),
        value = value,
        color = color,
    )
    if (ui == MqttWireguardUi.ERROR) {
        DetailLine(status.tunnelError)
    }
}

@Composable
private fun DetailLine(text: String) {
    if (text.isBlank()) return
    Text(
        text = text,
        style = MaterialTheme.typography.tboxBody,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        maxLines = 3,
    )
}
