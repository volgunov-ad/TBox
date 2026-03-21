package vad.dashing.tbox.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import vad.dashing.tbox.DashboardWidget
import vad.dashing.tbox.R

@Composable
internal fun DashboardAppLauncherWidgetItem(
    widget: DashboardWidget,
    packageName: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    elevation: Dp,
    shape: Dp,
    backgroundColor: Color,
) {
    val context = LocalContext.current
    val imageBitmap = remember(packageName) {
        if (packageName.isBlank()) return@remember null
        runCatching {
            val pm = context.packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            info.loadIcon(pm).toBitmap().asImageBitmap()
        }.getOrNull()
    }
    DashboardWidgetScaffold(
        modifier = Modifier.fillMaxSize(),
        onClick = onClick,
        onLongClick = onLongClick,
        elevation = elevation,
        shape = shape,
        backgroundColor = backgroundColor,
    ) { _, _ ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
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
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
