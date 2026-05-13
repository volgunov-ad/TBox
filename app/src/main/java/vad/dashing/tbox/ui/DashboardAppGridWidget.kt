package vad.dashing.tbox.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import java.io.File
import vad.dashing.tbox.DashboardWidget
import vad.dashing.tbox.R
import vad.dashing.tbox.SettingsManager
import vad.dashing.tbox.normalizeAppGridPackages

@Composable
internal fun DashboardAppGridWidgetItem(
    @Suppress("UNUSED_PARAMETER") widget: DashboardWidget,
    packages: List<String>,
    customIconRevision: Int,
    showTitle: Boolean,
    titleOverride: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    isEditMode: Boolean,
    elevation: Dp,
    shape: Dp,
    textColor: Color,
    backgroundColor: Color,
) {
    val context = LocalContext.current
    val density = context.resources.displayMetrics.density
    val iconSizePx = (32f * density).toInt().coerceIn(24, 56)
    val pkgs = remember(packages, customIconRevision) {
        normalizeAppGridPackages(packages)
    }
    val titleLine = remember(titleOverride, pkgs, context) {
        val trimmed = titleOverride.trim()
        if (trimmed.isNotEmpty()) trimmed
        else if (pkgs.isEmpty()) ""
        else context.getString(R.string.widget_app_grid_default_title, pkgs.size)
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
                .padding(4.dp)
        ) {
            if (pkgs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.widget_app_grid_empty),
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            } else {
                val cols = when {
                    pkgs.size <= 1 -> 1
                    pkgs.size <= 4 -> 2
                    pkgs.size <= 9 -> 3
                    else -> 4
                }
                val rows = remember(pkgs, cols) { pkgs.chunked(cols) }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    rows.forEach { row ->
                        Row(
                            modifier = Modifier
                                .weight(1f, fill = true)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            row.forEach { pkg ->
                                AppGridCell(
                                    packageName = pkg,
                                    iconSizePx = iconSizePx,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .then(
                                            if (!isEditMode) {
                                                Modifier.clickable {
                                                    launchAppFromWidget(context, pkg)
                                                }
                                            } else {
                                                Modifier
                                            }
                                        )
                                )
                            }
                            repeat(cols - row.size) {
                                Spacer(Modifier.weight(1f).fillMaxHeight())
                            }
                        }
                    }
                }
            }
            if (showTitle && titleLine.isNotEmpty()) {
                val titleFontSize = calculateResponsiveFontSize(
                    containerHeight = availableHeight,
                    textType = TextType.TITLE
                )
                Text(
                    text = titleLine,
                    fontSize = titleFontSize,
                    fontWeight = FontWeight.Medium,
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

@Composable
private fun AppGridCell(
    packageName: String,
    iconSizePx: Int,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val imageBitmap = remember(packageName, iconSizePx) {
        if (packageName.isBlank()) return@remember null
        val custom: ImageBitmap? = runCatching {
            val f = File(
                context.filesDir,
                "${SettingsManager.LAUNCHER_APP_ICONS_DIR}/$packageName"
            )
            if (!f.isFile || f.length() <= 0L) return@runCatching null
            BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap()
        }.getOrNull()
        custom ?: runCatching {
            val pm = context.packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            info.loadIcon(pm).toBitmap(iconSizePx, iconSizePx).asImageBitmap()
        }.getOrNull()
    }
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap,
                contentDescription = packageName,
                modifier = Modifier.fillMaxSize(0.92f),
                contentScale = ContentScale.Fit
            )
        } else {
            Text(
                text = stringResource(R.string.widget_app_launcher_no_icon),
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
