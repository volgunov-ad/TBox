package vad.dashing.tbox.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import vad.dashing.tbox.CamdWindowClient
import vad.dashing.tbox.R
import vad.dashing.tbox.normalizeCamdWindowCamera

@Composable
internal fun DashboardCamdWindowWidgetItem(
    isEditMode: Boolean,
    onEditClick: () -> Unit,
    onLongClick: () -> Unit,
    elevation: Dp,
    shape: Dp,
    backgroundColor: Color,
    textColor: Color,
    showTitle: Boolean,
    titleOverride: String,
    cameraId: Int,
    x: Int,
    y: Int,
    w: Int,
    h: Int,
    rotation: Int,
    flipH: Boolean = false,
    flipV: Boolean = false,
) {
    val context = LocalContext.current
    val camera = normalizeCamdWindowCamera(cameraId)
    val cameraName = stringResource(camdWindowCameraTitle(camera))
    val titleText = titleOverride.trim().ifBlank { cameraName }
    DashboardWidgetScaffold(
        modifier = Modifier.fillMaxSize(),
        onClick = {
            if (isEditMode) {
                onEditClick()
            } else {
                CamdWindowClient.open(context, camera, x, y, w, h, rotation, flipH, flipV)
            }
        },
        onDoubleClick = if (isEditMode) {
            null
        } else {
            { CamdWindowClient.close(context, camera) }
        },
        onLongClick = onLongClick,
        elevation = elevation,
        shape = shape,
        textColor = textColor,
        backgroundColor = backgroundColor,
    ) { availableHeight, resolvedTextColor ->
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = if (showTitle) titleText else cameraName,
                style = calculateResponsiveTextStyle(
                    containerHeight = availableHeight,
                    textType = TextType.TITLE,
                    forWidgetTitle = true,
                ),
                color = resolvedTextColor,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
            )
        }
    }
}

internal fun camdWindowCameraTitle(cameraId: Int): Int {
    return when (normalizeCamdWindowCamera(cameraId)) {
        0 -> R.string.camd_window_camera_cabin
        1 -> R.string.camd_window_camera_oms
        3 -> R.string.camd_window_camera_dms
        4 -> R.string.camd_window_camera_left
        5 -> R.string.camd_window_camera_right
        8 -> R.string.camd_window_camera_front
        9 -> R.string.camd_window_camera_rear
        else -> R.string.camd_window_camera_front
    }
}
