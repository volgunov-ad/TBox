package vad.dashing.tbox.ui

import android.app.ActivityManager
import android.content.Context
import android.os.Environment
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import vad.dashing.tbox.R
import vad.dashing.tbox.ui.theme.tboxBody
import vad.dashing.tbox.utils.SystemMetricsReader
import kotlin.math.roundToInt

/**
 * Live head-unit load on the Info tab: each CPU core, occupied RAM, and how full `/data` is.
 * Samples only while this composable is on screen.
 */
@Composable
fun HuSystemLoadSection() {
    val context = LocalContext.current
    var cores by remember { mutableStateOf<List<SystemMetricsReader.CoreUsage>>(emptyList()) }
    var memory by remember { mutableStateOf<SystemMetricsReader.ByteUsage?>(null) }
    var disk by remember { mutableStateOf<SystemMetricsReader.ByteUsage?>(null) }

    LaunchedEffect(context) {
        val activityManager = context.applicationContext
            .getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val dataPath = Environment.getDataDirectory().absolutePath
        var previousCores = emptyList<SystemMetricsReader.CoreCpuSnapshot>()
        while (isActive) {
            val sample = withContext(Dispatchers.IO) {
                val stat = SystemMetricsReader.readProcStat()
                val ram = activityManager?.let { SystemMetricsReader.readMemoryUsage(it) }
                val volume = SystemMetricsReader.readDiskUsage(dataPath)
                Triple(stat, ram, volume)
            }
            val stat = sample.first
            if (stat != null) {
                cores = SystemMetricsReader.coreUsagePercents(previousCores, stat.cores)
                previousCores = stat.cores
            }
            memory = sample.second
            disk = sample.third
            delay(SAMPLE_PERIOD_MS)
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        SettingsTitle(stringResource(R.string.info_system_load_section))
        if (cores.isEmpty()) {
            Text(
                text = stringResource(R.string.info_system_load_unknown),
                style = MaterialTheme.typography.tboxBody,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        } else {
            cores.forEach { core ->
                LoadBarRow(
                    label = stringResource(R.string.info_system_load_core, core.index),
                    percent = core.percent,
                    detail = null,
                )
            }
        }
        UsageRow(
            label = stringResource(R.string.info_system_load_memory),
            usage = memory,
        )
        UsageRow(
            label = stringResource(R.string.info_system_load_disk),
            usage = disk,
        )
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun UsageRow(
    label: String,
    usage: SystemMetricsReader.ByteUsage?,
) {
    val detail = if (usage == null) {
        null
    } else {
        stringResource(
            R.string.info_system_load_used,
            quantityText(usage.usedBytes),
            quantityText(usage.totalBytes),
        )
    }
    LoadBarRow(
        label = label,
        percent = usage?.usedPercent,
        detail = detail,
    )
}

@Composable
private fun LoadBarRow(
    label: String,
    percent: Float?,
    detail: String?,
) {
    val unknown = stringResource(R.string.info_system_load_unknown)
    val percentText = if (percent == null) {
        unknown
    } else {
        stringResource(R.string.info_system_load_percent, percent.roundToInt().coerceIn(0, 100))
    }
    val barColor = loadColor(percent)
    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(top = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                modifier = Modifier.width(88.dp),
                style = MaterialTheme.typography.tboxBody,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Text(
                text = detail ?: percentText,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.tboxBody,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.End,
                maxLines = 1,
            )
            if (detail != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = percentText,
                    style = MaterialTheme.typography.tboxBody,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                )
            }
        }
        LinearProgressIndicator(
            progress = { ((percent ?: 0f) / 100f).coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp)
                .height(8.dp),
            color = barColor,
            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
        )
    }
}

@Composable
private fun loadColor(percent: Float?): Color {
    val value = percent ?: return MaterialTheme.colorScheme.primary
    return if (value >= HIGH_LOAD_PERCENT) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }
}

@Composable
private fun quantityText(bytes: Long): String {
    val gib = 1024.0 * 1024.0 * 1024.0
    val mib = 1024.0 * 1024.0
    return if (bytes >= gib) {
        stringResource(R.string.info_system_load_gb, bytes / gib)
    } else {
        stringResource(R.string.info_system_load_mb, bytes / mib)
    }
}

private const val SAMPLE_PERIOD_MS = 1000L
private const val HIGH_LOAD_PERCENT = 85f
