package vad.dashing.tbox.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.util.TimeZone
import kotlin.math.abs
import kotlinx.coroutines.launch
import vad.dashing.tbox.R
import vad.dashing.tbox.hutimezone.HuTimeZoneClient
import vad.dashing.tbox.hutimezone.HuTimeZoneCodec
import vad.dashing.tbox.hutimezone.HuTimeZoneResult
import vad.dashing.tbox.ui.theme.tboxBody
import vad.dashing.tbox.ui.theme.tboxButton

@Composable
fun HuTimeZoneSettingsCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var current by remember { mutableStateOf<HuTimeZoneResult?>(null) }
    var selected by remember {
        mutableStateOf(
            TimeZone.getDefault().id.takeIf { it in HuTimeZoneCodec.ZONES } ?: "Europe/Moscow",
        )
    }
    var expanded by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        current = HuTimeZoneClient.read(context)
        busy = false
    }

    SettingsTitle(stringResource(R.string.settings_hu_time_zone_title))
    Text(
        text = stringResource(R.string.settings_hu_time_zone_desc),
        style = MaterialTheme.typography.tboxBody,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 4.dp),
    )
    current?.let { result ->
        Text(
            text = huTimeZoneResultText(result),
            style = MaterialTheme.typography.tboxBody,
            color = if (result is HuTimeZoneResult.Zone) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.error
            },
            modifier = Modifier.padding(bottom = 4.dp),
        )
    }
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = rememberWrappedOnClick { expanded = true },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = zoneLabel(selected),
                style = MaterialTheme.typography.tboxButton,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = stringResource(
                    if (expanded) R.string.dropdown_collapse else R.string.dropdown_expand,
                ),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            HuTimeZoneCodec.ZONES.forEach { zone ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = zoneLabel(zone),
                            style = MaterialTheme.typography.tboxBody,
                            color = if (zone == selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    },
                    onClick = rememberWrappedOnClick {
                        selected = zone
                        expanded = false
                    },
                )
            }
        }
    }
    Button(
        onClick = rememberWrappedOnClick {
            if (busy) return@rememberWrappedOnClick
            scope.launch {
                busy = true
                current = HuTimeZoneClient.write(context, selected)
                busy = false
            }
        },
        enabled = !busy,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 8.dp),
    ) {
        Text(
            text = stringResource(
                if (busy) R.string.settings_hu_time_zone_busy else R.string.settings_hu_time_zone_apply,
            ),
            style = MaterialTheme.typography.tboxButton,
            maxLines = 2,
            textAlign = TextAlign.Center,
        )
    }
}

private fun zoneLabel(zoneId: String): String {
    val offsetMinutes = TimeZone.getTimeZone(zoneId).rawOffset / 60_000
    val sign = if (offsetMinutes < 0) "-" else "+"
    val minutes = abs(offsetMinutes)
    val offset = if (minutes % 60 == 0) {
        "${minutes / 60}"
    } else {
        "${minutes / 60}:${(minutes % 60).toString().padStart(2, '0')}"
    }
    return "$zoneId (UTC$sign$offset)"
}

@Composable
private fun huTimeZoneResultText(result: HuTimeZoneResult): String = when (result) {
    is HuTimeZoneResult.Zone -> stringResource(R.string.settings_hu_time_zone_current, result.zoneId)
    is HuTimeZoneResult.AdbFailed -> stringResource(R.string.settings_ca_certs_error_adb, result.reason)
    HuTimeZoneResult.ServiceFailed -> stringResource(R.string.settings_hu_time_zone_error_service)
}
