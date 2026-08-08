package vad.dashing.tbox.ui

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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import vad.dashing.tbox.PanelBackgroundImageStorage
import vad.dashing.tbox.SettingsViewModel
import vad.dashing.tbox.decodeFileToOwnedImageBitmap

/**
 * Solid panel color with an optional image on top ([ContentScale.Fit]), drawn under the tile grid.
 * Parent should also clip children to [shapeDp] so tiles follow the panel corner radius.
 */
@Composable
internal fun DashboardPanelBackgroundUnderlay(
    relPath: String?,
    backgroundColor: Color,
    shapeDp: Dp,
    settingsViewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val rev by settingsViewModel.panelBackgroundImageRevision.collectAsStateWithLifecycle()
    val themeActivating by settingsViewModel.themeActivationInProgress.collectAsStateWithLifecycle()
    val themeLookup = rememberLauncherAppIconLookup(settingsViewModel)
    var bitmap by remember(relPath, rev, themeLookup) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(relPath, rev, themeLookup, themeActivating) {
        if (themeActivating) {
            bitmap = null
            return@LaunchedEffect
        }
        bitmap = withContext(Dispatchers.IO) {
            val f = PanelBackgroundImageStorage.resolveFile(context.filesDir, relPath, themeLookup)
            if (f == null || !f.isFile) return@withContext null
            runCatching {
                decodeFileToOwnedImageBitmap(f)
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
        if (b != null && !themeActivating) {
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
