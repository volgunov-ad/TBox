package vad.dashing.tbox.ui

import android.content.ComponentName
import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun AppLauncherWidgetItem(
    appPackageName: String?,
    appClassName: String?,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    elevation: Dp = 0.dp,
    shape: Dp = 0.dp,
    backgroundTransparent: Boolean = false
) {
    val context = LocalContext.current
    val packageManager = context.packageManager

    data class AppDisplay(
        val label: String,
        val icon: Drawable?,
        val isResolved: Boolean
    )

    val display = remember(appPackageName, appClassName) {
        if (appPackageName.isNullOrBlank()) {
            AppDisplay(label = "", icon = null, isResolved = false)
        } else {
            val component = if (!appClassName.isNullOrBlank()) {
                ComponentName(appPackageName, appClassName)
            } else {
                null
            }
            try {
                if (component != null) {
                    val info = packageManager.getActivityInfo(component, 0)
                    AppDisplay(
                        label = info.loadLabel(packageManager)?.toString().orEmpty(),
                        icon = info.loadIcon(packageManager),
                        isResolved = true
                    )
                } else {
                    throw IllegalStateException("Missing component")
                }
            } catch (_: Exception) {
                try {
                    val appInfo = packageManager.getApplicationInfo(appPackageName, 0)
                    AppDisplay(
                        label = appInfo.loadLabel(packageManager)?.toString().orEmpty(),
                        icon = appInfo.loadIcon(packageManager),
                        isResolved = true
                    )
                } catch (_: Exception) {
                    AppDisplay(label = "", icon = null, isResolved = false)
                }
            }
        }
    }

    val placeholder = if (appPackageName.isNullOrBlank()) {
        "Приложение не выбрано"
    } else if (!display.isResolved) {
        "Приложение недоступно"
    } else {
        display.label.ifBlank { "Приложение" }
    }

    Card(
        modifier = Modifier
            .fillMaxSize()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        elevation = CardDefaults.cardElevation(elevation),
        colors = CardDefaults.cardColors(
            containerColor = if (backgroundTransparent) Color.Transparent else MaterialTheme.colorScheme.surface
        ),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(shape)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Transparent)
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (display.icon != null) {
                    AndroidView(
                        factory = { viewContext ->
                            ImageView(viewContext).apply {
                                scaleType = ImageView.ScaleType.FIT_CENTER
                                setImageDrawable(display.icon)
                            }
                        },
                        update = { it.setImageDrawable(display.icon) },
                        modifier = Modifier.size(48.dp)
                    )
                }
                Text(
                    text = placeholder,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
