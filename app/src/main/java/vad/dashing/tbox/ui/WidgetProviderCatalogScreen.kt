package vad.dashing.tbox.ui

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.pm.PackageManager
import android.os.Process
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import vad.dashing.tbox.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetProviderCatalogScreen(
    appWidgetManager: AppWidgetManager,
    packageManager: PackageManager,
    onProviderClick: (AppWidgetProviderInfo) -> Unit,
    onCancel: () -> Unit,
    onOpenSystemPicker: () -> Unit,
) {
    val providers = remember(appWidgetManager) {
        appWidgetManager.getInstalledProvidersForProfile(Process.myUserHandle())
            .sortedBy { it.loadLabel(packageManager)?.toString().orEmpty().lowercase() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.widget_picker_catalog_title)) },
                navigationIcon = {
                    TextButton(onClick = onCancel) {
                        Text(stringResource(R.string.widget_picker_back))
                    }
                },
                actions = {
                    TextButton(onClick = onOpenSystemPicker) {
                        Text(stringResource(R.string.widget_picker_system_list))
                    }
                }
            )
        }
    ) { padding ->
        if (providers.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.widget_picker_empty_list),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = providers,
                    key = { "${it.provider.packageName}/${it.provider.className}" }
                ) { info ->
                    WidgetProviderCatalogRow(
                        info = info,
                        packageManager = packageManager,
                        onClick = { onProviderClick(info) }
                    )
                }
            }
        }
    }
}

@Composable
private fun WidgetProviderCatalogRow(
    info: AppWidgetProviderInfo,
    packageManager: PackageManager,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val title = remember(info) { info.loadLabel(packageManager)?.toString().orEmpty() }
    val subtitle = remember(info) { info.provider.packageName }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AndroidView(
                modifier = Modifier
                    .height(88.dp)
                    .weight(0.35f),
                factory = { ctx ->
                    AppWidgetPreviewInflater.inflatePreview(
                        ctx,
                        info,
                        R.string.widget_picker_preview_placeholder
                    )
                }
            )
            Column(
                modifier = Modifier.weight(0.65f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title.ifBlank { subtitle },
                    style = MaterialTheme.typography.titleMedium
                )
                if (title.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
