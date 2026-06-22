package vad.dashing.tbox.ui

import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.R
import vad.dashing.tbox.update.UpdateChannel
import vad.dashing.tbox.update.UpdateUiState
import vad.dashing.tbox.update.UpdateViewModel
import vad.dashing.tbox.ui.theme.tboxBody
import vad.dashing.tbox.ui.theme.tboxHeadline
import vad.dashing.tbox.ui.theme.tboxTitle

@Composable
fun UpdateTab(
    updateViewModel: UpdateViewModel,
    onOpenInstallPermissionSettings: () -> Unit,
) {
    val uiState by updateViewModel.uiState.collectAsStateWithLifecycle()
    val updateChannel by updateViewModel.updateChannel.collectAsStateWithLifecycle()
    val updateCheckEnabled by updateViewModel.updateCheckEnabled.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = LocalActivity.current
    var canInstall by remember { mutableStateOf(updateViewModel.canInstallPackages()) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                canInstall = updateViewModel.canInstallPackages()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val packageInfo = remember {
        context.packageManager.getPackageInfo(context.packageName, 0)
    }
    val currentVersionName = remember { packageInfo.versionName.orEmpty() }
    val currentVersionCode = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.update_tab_title),
            style = MaterialTheme.typography.tboxHeadline,
            color = MaterialTheme.colorScheme.onSurface
        )

        Text(
            text = stringResource(
                R.string.update_current_version,
                currentVersionName,
                currentVersionCode,
            ),
            style = MaterialTheme.typography.tboxBody,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = stringResource(
                R.string.update_channel_current,
                updateChannelLabel(updateChannel),
            ),
            style = MaterialTheme.typography.tboxBody,
            color = MaterialTheme.colorScheme.onSurface
        )

        HorizontalDivider()

        when (val state = uiState) {
            UpdateUiState.Idle -> {
                Text(
                    text = stringResource(
                        if (updateCheckEnabled) {
                            R.string.update_idle_hint
                        } else {
                            R.string.update_idle_hint_manual_only
                        },
                    ),
                    style = MaterialTheme.typography.tboxBody,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            UpdateUiState.Checking -> {
                CircularProgressIndicator()
                Text(
                    text = stringResource(R.string.update_checking),
                    style = MaterialTheme.typography.tboxBody,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            UpdateUiState.UpToDate -> {
                Text(
                    text = stringResource(R.string.update_up_to_date),
                    style = MaterialTheme.typography.tboxBody,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            is UpdateUiState.Available -> {
                UpdateReleaseDetails(state.info)
                Button(
                    onClick = rememberWrappedOnClick { updateViewModel.downloadAndVerify() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.update_download),
                        style = MaterialTheme.typography.tboxTitle,
                    )
                }
            }
            is UpdateUiState.Downloading -> {
                if (state.percent != null) {
                    LinearProgressIndicator(
                        progress = { state.percent / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = stringResource(R.string.update_downloading, state.percent),
                        style = MaterialTheme.typography.tboxBody,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                } else {
                    CircularProgressIndicator()
                    Text(
                        text = stringResource(R.string.update_downloading_unknown),
                        style = MaterialTheme.typography.tboxBody,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            UpdateUiState.Verifying -> {
                CircularProgressIndicator()
                Text(
                    text = stringResource(R.string.update_verifying),
                    style = MaterialTheme.typography.tboxBody,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            is UpdateUiState.ReadyToInstall -> {
                UpdateReleaseDetails(state.info)
                if (!canInstall) {
                    Text(
                        text = stringResource(R.string.update_install_permission_hint),
                        style = MaterialTheme.typography.tboxBody,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Button(
                        onClick = rememberWrappedOnClick(onOpenInstallPermissionSettings),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = stringResource(R.string.update_grant_install_permission),
                            style = MaterialTheme.typography.tboxTitle,
                        )
                    }
                }
                Button(
                    onClick = rememberWrappedOnClick { updateViewModel.installPreparedApk() },
                    enabled = canInstall,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.update_install),
                        style = MaterialTheme.typography.tboxTitle,
                    )
                }
            }
            is UpdateUiState.Error -> {
                Text(
                    text = resolveUpdateErrorMessage(state.message),
                    style = MaterialTheme.typography.tboxBody,
                    color = MaterialTheme.colorScheme.error,
                )
                state.cachedInfo?.let { UpdateReleaseDetails(it) }
                Button(
                    onClick = rememberWrappedOnClick { updateViewModel.checkForUpdate(force = true) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.update_retry_check),
                        style = MaterialTheme.typography.tboxTitle,
                    )
                }
                if (state.cachedInfo != null) {
                    Button(
                        onClick = rememberWrappedOnClick { updateViewModel.downloadAndVerify() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = stringResource(R.string.update_download),
                            style = MaterialTheme.typography.tboxTitle,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdateReleaseDetails(info: vad.dashing.tbox.update.UpdateReleaseInfo) {
    Text(
        text = stringResource(
            R.string.update_new_version,
            info.versionName,
            info.versionCode,
        ),
        style = MaterialTheme.typography.tboxTitle,
        color = MaterialTheme.colorScheme.onSurface
    )
    if (info.publishedAt.isNotBlank()) {
        Text(
            text = stringResource(R.string.update_published_at, info.publishedAt),
            style = MaterialTheme.typography.tboxBody,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
    if (info.changelog.isNotBlank()) {
        Text(
            text = stringResource(R.string.update_changelog),
            style = MaterialTheme.typography.tboxTitle,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = info.changelog,
            style = MaterialTheme.typography.tboxBody,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun updateChannelLabel(channel: UpdateChannel): String = when (channel) {
    UpdateChannel.RELEASE -> stringResource(R.string.update_channel_release)
    UpdateChannel.DEVELOPMENT -> stringResource(R.string.update_channel_development)
}

@Composable
private fun resolveUpdateErrorMessage(raw: String): String = when (raw) {
    "network_unavailable" -> stringResource(R.string.update_error_network)
    "Update source URL is not configured" -> stringResource(R.string.update_error_not_configured)
    "APK checksum mismatch" -> stringResource(R.string.update_error_verify_checksum)
    "APK package name mismatch" -> stringResource(R.string.update_error_verify_package)
    "APK signing certificate mismatch" -> stringResource(R.string.update_error_verify_signature)
    else -> stringResource(R.string.update_error_generic, raw)
}
