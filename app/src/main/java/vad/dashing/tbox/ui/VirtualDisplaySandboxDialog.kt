package vad.dashing.tbox.ui

import android.app.ActivityOptions
import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.os.Build
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.MainActivityIntentHelper
import vad.dashing.tbox.R
import vad.dashing.tbox.SettingsViewModel

/**
 * Expert experiment: create a [VirtualDisplay] backed by a [SurfaceView] surface, and optionally
 * start this app or a user-picked launcher app on that display (API 26+). OEM may block launches.
 */
@Composable
fun VirtualDisplaySandboxDialog(
    settingsViewModel: SettingsViewModel,
    onDismissRequest: () -> Unit,
) {
    val context = LocalContext.current
    val appCtx = context.applicationContext
    val displayManager = remember {
        appCtx.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    }
    var virtualDisplay by remember { mutableStateOf<VirtualDisplay?>(null) }
    var lastWidth by remember { mutableIntStateOf(0) }
    var lastHeight by remember { mutableIntStateOf(0) }
    var statusLine by remember { mutableStateOf("") }
    var selectedTargetPackage by remember { mutableStateOf("") }
    var filterText by rememberSaveable { mutableStateOf("") }
    val iconRevision by settingsViewModel.launcherAppIconRevision.collectAsStateWithLifecycle()
    val apps = rememberLaunchableAppEntries(iconRevision)
    val needle = filterText.trim().lowercase()
    val filteredApps = remember(apps, needle) {
        if (needle.isEmpty()) {
            apps
        } else {
            apps.filter {
                it.label.lowercase().contains(needle) ||
                    it.packageName.lowercase().contains(needle)
            }
        }
    }

    fun releaseVirtualDisplay() {
        virtualDisplay?.release()
        virtualDisplay = null
        lastWidth = 0
        lastHeight = 0
    }

    DisposableEffect(Unit) {
        onDispose { releaseVirtualDisplay() }
    }

    fun tryCreateVirtualDisplay(surface: Surface, width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        if (virtualDisplay != null && width == lastWidth && height == lastHeight) {
            return
        }
        releaseVirtualDisplay()
        lastWidth = width
        lastHeight = height
        val dpi = appCtx.resources.configuration.densityDpi
        val flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY or
            DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION
        val vd = displayManager.createVirtualDisplay(
            "TBoxMonitor-VirtualDisplay-Test",
            width,
            height,
            dpi,
            surface,
            flags
        )
        virtualDisplay = vd
        statusLine = if (vd != null) {
            val id = vd.display?.displayId
            appCtx.getString(R.string.settings_virtual_display_created, id ?: -1, width, height)
        } else {
            appCtx.getString(R.string.settings_virtual_display_create_failed)
        }
    }

    fun tryLaunchPackageOnVirtualDisplay(packageName: String) {
        if (packageName.isBlank()) {
            Toast.makeText(
                appCtx,
                appCtx.getString(R.string.settings_virtual_display_none_selected_toast),
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        val vd = virtualDisplay ?: run {
            Toast.makeText(
                appCtx,
                appCtx.getString(R.string.settings_virtual_display_need_surface),
                Toast.LENGTH_LONG
            ).show()
            return
        }
        val displayId = vd.display?.displayId ?: run {
            Toast.makeText(
                appCtx,
                appCtx.getString(R.string.settings_virtual_display_no_display_id),
                Toast.LENGTH_LONG
            ).show()
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            Toast.makeText(
                appCtx,
                appCtx.getString(R.string.settings_virtual_display_api_low),
                Toast.LENGTH_LONG
            ).show()
            return
        }
        try {
            val launchIntent = appCtx.packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent == null) {
                Toast.makeText(
                    appCtx,
                    appCtx.getString(R.string.widget_app_launcher_no_launch_intent, packageName),
                    Toast.LENGTH_LONG
                ).show()
                return
            }
            MainActivityIntentHelper.applyExternalAppLaunchFlags(launchIntent, context)
            val opts = ActivityOptions.makeBasic().apply {
                launchDisplayId = displayId
            }.toBundle()
            context.startActivity(launchIntent, opts)
            Toast.makeText(
                appCtx,
                appCtx.getString(R.string.settings_virtual_display_launch_sent, displayId),
                Toast.LENGTH_SHORT
            ).show()
        } catch (e: Exception) {
            Toast.makeText(
                appCtx,
                userMessageForVirtualDisplayLaunchFailure(appCtx, e),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    AlertDialog(
        onDismissRequest = {
            releaseVirtualDisplay()
            onDismissRequest()
        },
        modifier = Modifier.fillMaxWidth(0.96f),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = {
            Text(
                text = stringResource(R.string.settings_virtual_display_dialog_title),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = stringResource(R.string.settings_virtual_display_dialog_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                if (statusLine.isNotBlank()) {
                    Text(
                        text = statusLine,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    factory = { ctx ->
                        FrameLayout(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            val surfaceView = SurfaceView(ctx).apply {
                                layoutParams = FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                holder.addCallback(object : SurfaceHolder.Callback {
                                    override fun surfaceCreated(holder: SurfaceHolder) {}

                                    override fun surfaceChanged(
                                        holder: SurfaceHolder,
                                        format: Int,
                                        width: Int,
                                        height: Int,
                                    ) {
                                        tryCreateVirtualDisplay(holder.surface, width, height)
                                    }

                                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                                        releaseVirtualDisplay()
                                    }
                                })
                            }
                            addView(surfaceView)
                        }
                    }
                )
                Text(
                    text = stringResource(R.string.settings_virtual_display_pick_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                )
                OutlinedTextField(
                    value = filterText,
                    onValueChange = { filterText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    label = {
                        Text(
                            text = stringResource(R.string.widget_app_launcher_search),
                            fontSize = 18.sp
                        )
                    },
                    singleLine = true
                )
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    filteredApps.forEach { app ->
                        val pkg = app.packageName
                        val selected = pkg == selectedTargetPackage
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedTargetPackage = pkg }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            RadioButton(
                                selected = selected,
                                onClick = { selectedTargetPackage = pkg }
                            )
                            if (app.icon != null) {
                                Image(
                                    bitmap = app.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(36.dp),
                                    contentScale = ContentScale.Fit
                                )
                            } else {
                                Text(
                                    text = stringResource(R.string.widget_app_launcher_no_icon),
                                    fontSize = 14.sp,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                            Text(
                                text = app.label,
                                fontSize = 18.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                OutlinedButton(
                    onClick = { tryLaunchPackageOnVirtualDisplay(appCtx.packageName) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Text(stringResource(R.string.settings_virtual_display_launch_self))
                }
                Button(
                    onClick = { tryLaunchPackageOnVirtualDisplay(selectedTargetPackage) },
                    enabled = selectedTargetPackage.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                ) {
                    Text(stringResource(R.string.settings_virtual_display_launch_selected_app))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    releaseVirtualDisplay()
                    onDismissRequest()
                }
            ) {
                Text(stringResource(R.string.settings_virtual_display_close))
            }
        },
        dismissButton = {}
    )
}

private fun throwableMessageChain(t: Throwable): String = buildString {
    var x: Throwable? = t
    while (x != null) {
        x.message?.let { msg ->
            if (msg.isNotBlank()) {
                append(msg)
                append('\n')
            }
        }
        x = x.cause
    }
}

/**
 * ActivityManager often returns "Permission Denial … with launchDisplayId=N" for third-party
 * launches onto a [VirtualDisplay] from an untrusted app — not fixable via manifest permission.
 */
private fun userMessageForVirtualDisplayLaunchFailure(appCtx: Context, e: Exception): String {
    val chain = throwableMessageChain(e)
    val launchDisplayDenial = chain.contains("Permission Denial", ignoreCase = true) &&
        chain.contains("launchDisplayId", ignoreCase = true)
    return if (launchDisplayDenial) {
        appCtx.getString(R.string.settings_virtual_display_third_party_denied)
    } else if (e is SecurityException) {
        appCtx.getString(R.string.settings_virtual_display_security_denied_short)
    } else {
        e.message?.takeIf { it.isNotBlank() }
            ?: appCtx.getString(R.string.settings_virtual_display_launch_failed)
    }
}
