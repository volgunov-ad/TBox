package vad.dashing.tbox.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import vad.dashing.tbox.SettingsViewModel
import vad.dashing.tbox.TileBackgroundImageStorage

/**
 * Solid tile color with an optional image on top ([ContentScale.Fit], not cropped), for use behind
 * a transparent [vad.dashing.tbox.ui.DashboardWidgetScaffold] card.
 */
@Composable
internal fun DashboardTileBackgroundImageUnderlay(
    relPath: String?,
    backgroundColor: Color,
    shapeDp: Dp,
    settingsViewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val rev by settingsViewModel.tileBackgroundImageRevision.collectAsStateWithLifecycle()
    var bitmap by remember(relPath, rev) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(relPath, rev) {
        bitmap = withContext(Dispatchers.IO) {
            val f = TileBackgroundImageStorage.resolveFile(context, relPath)
            if (f == null || !f.isFile) return@withContext null
            runCatching {
                BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap()
            }.getOrNull()
        }
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(shapeDp))
            .background(backgroundColor)
    ) {
        val b = bitmap
        if (b != null) {
            Image(
                bitmap = b,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                alignment = Alignment.Center
            )
        }
    }
}
