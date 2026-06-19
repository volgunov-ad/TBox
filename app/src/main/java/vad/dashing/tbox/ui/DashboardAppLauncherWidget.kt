package vad.dashing.tbox.ui

import vad.dashing.tbox.ui.theme.tboxTitle
import vad.dashing.tbox.ui.theme.tboxTabLabel
import vad.dashing.tbox.ui.theme.tboxHeadline
import vad.dashing.tbox.ui.theme.tboxCaption
import vad.dashing.tbox.ui.theme.tboxButton
import vad.dashing.tbox.ui.theme.tboxBody
import vad.dashing.tbox.ui.theme.TboxTextStyles
import vad.dashing.tbox.decodeFileToOwnedImageBitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import vad.dashing.tbox.DashboardWidget
import vad.dashing.tbox.LauncherAppIconPaths
import vad.dashing.tbox.R

@Composable
internal fun DashboardAppLauncherWidgetItem(
    widget: DashboardWidget,
    packageName: String,
    customIconRevision: Int,
    iconLookup: LauncherAppIconPaths.Lookup,
    suppressCustomIcon: Boolean = false,
    showTitle: Boolean,
    titleOverride: String = "",
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    elevation: Dp,
    shape: Dp,
    textColor: Color,
    backgroundColor: Color,
) {
    val context = LocalContext.current
    val imageBitmap = remember(packageName, customIconRevision, iconLookup, suppressCustomIcon) {
        if (packageName.isBlank()) return@remember null
        if (!suppressCustomIcon) {
            val custom: ImageBitmap? = runCatching {
                val f = LauncherAppIconPaths.resolveIconFile(context.filesDir, packageName, iconLookup)
                    ?: return@runCatching null
                decodeFileToOwnedImageBitmap(f)
            }.getOrNull()
            if (custom != null) return@remember custom
        }
        runCatching {
            val pm = context.packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            info.loadIcon(pm).toBitmap().asImageBitmap()
        }.getOrNull()
    }
    val appLabel = remember(packageName) {
        if (packageName.isBlank()) {
            ""
        } else {
            runCatching {
                val pm = context.packageManager
                val info = pm.getApplicationInfo(packageName, 0)
                info.loadLabel(pm).toString()
            }.getOrElse { packageName }
        }
    }
    DashboardWidgetScaffold(
        modifier = Modifier.fillMaxSize(),
        onClick = onClick,
        onLongClick = onLongClick,
        elevation = elevation,
        shape = shape,
        textColor = textColor,
        backgroundColor = backgroundColor,
    ) { availableHeight, resolvedTextColor ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (imageBitmap != null) {
                    Image(
                        bitmap = imageBitmap,
                        contentDescription = widget.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Text(
                        text = stringResource(R.string.widget_app_launcher_no_icon),
                        style = MaterialTheme.typography.tboxCaption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            val titleLine = titleOverride.trim().ifBlank { appLabel }
            if (showTitle && titleLine.isNotEmpty()) {
                val titleStyle = calculateResponsiveTextStyle(
                    containerHeight = availableHeight,
                    textType = TextType.TITLE
                )
                Text(
                    text = titleLine,
                    style = titleStyle,
                    color = resolvedTextColor,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp)
                )
            }
        }
    }
}
