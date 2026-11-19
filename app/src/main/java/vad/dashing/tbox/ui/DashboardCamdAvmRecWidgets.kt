package vad.dashing.tbox.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vad.dashing.tbox.CAMD_KEEP_AVM_WIDGET_CENTER_LABEL
import vad.dashing.tbox.CAMD_RECORDER_WIDGET_CENTER_LABEL
import vad.dashing.tbox.CamdRecorderWidgetPaint
import vad.dashing.tbox.CamdStatusRepository
import vad.dashing.tbox.CamdWindowClient
import vad.dashing.tbox.R
import vad.dashing.tbox.camdFailureToast
import vad.dashing.tbox.camdKeepAvmCommandLine
import vad.dashing.tbox.camdKeepAvmWidgetLooksActive
import vad.dashing.tbox.camdRecorderCommandLine
import vad.dashing.tbox.camdRecorderWidgetPaint

/** Hard-coded while camd reports `rec_active=1` — not taken from widget control colors. */
private val CamdRecordingContentRed = Color(0xFFFF0000)

@Composable
internal fun DashboardCamdKeepAvmWidgetItem(
    isEditMode: Boolean,
    onEditClick: () -> Unit,
    onLongClick: () -> Unit,
    elevation: Dp,
    shape: Dp,
    backgroundColor: Color,
    textColor: Color,
    showTitle: Boolean = false,
    titleOverride: String = "",
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val status by CamdStatusRepository.status.collectAsStateWithLifecycle()
    var busy by remember { mutableStateOf(false) }
    val controls = LocalWidgetControlAppearance.current
    val defaultTitle = stringResource(R.string.data_title_camd_keep_avm_widget)
    val titleText = titleOverride.trim().ifBlank { defaultTitle }
    val keepAvm = status?.keepAvm
    val labelColor = when {
        keepAvm == null -> controls.inactiveContent.copy(alpha = 0.25f)
        camdKeepAvmWidgetLooksActive(keepAvm) -> controls.activeContent
        else -> controls.inactiveContent
    }

    DashboardWidgetScaffold(
        onClick = {
            if (isEditMode) {
                onEditClick()
                return@DashboardWidgetScaffold
            }
            if (busy) return@DashboardWidgetScaffold
            val app = context.applicationContext
            if (keepAvm == null) {
                Toast.makeText(
                    app,
                    app.getString(R.string.settings_camd_keep_avm_down),
                    Toast.LENGTH_SHORT,
                ).show()
                scope.launch { CamdStatusRepository.refreshNow() }
                return@DashboardWidgetScaffold
            }
            busy = true
            val nextKeep = !keepAvm
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    CamdWindowClient.command(
                        camdKeepAvmCommandLine(nextKeep),
                        context = app,
                    )
                }
                CamdStatusRepository.refreshNow()
                busy = false
                result.exceptionOrNull()?.let { error ->
                    Toast.makeText(app, camdFailureToast(app, error), Toast.LENGTH_SHORT).show()
                }
            }
        },
        onLongClick = onLongClick,
        elevation = elevation,
        shape = shape,
        textColor = textColor,
        backgroundColor = backgroundColor,
    ) { availableHeight, resolvedTextColor ->
        CamdCenterLabelTileContent(
            showTitle = showTitle,
            titleText = titleText,
            availableHeight = availableHeight,
            titleColor = resolvedTextColor,
            centerLabel = CAMD_KEEP_AVM_WIDGET_CENTER_LABEL,
            labelColor = labelColor,
        )
    }
}

@Composable
internal fun DashboardCamdRecorderWidgetItem(
    isEditMode: Boolean,
    onEditClick: () -> Unit,
    onLongClick: () -> Unit,
    elevation: Dp,
    shape: Dp,
    backgroundColor: Color,
    textColor: Color,
    showTitle: Boolean = false,
    titleOverride: String = "",
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val status by CamdStatusRepository.status.collectAsStateWithLifecycle()
    var busy by remember { mutableStateOf(false) }
    val controls = LocalWidgetControlAppearance.current
    val defaultTitle = stringResource(R.string.data_title_camd_recorder_widget)
    val titleText = titleOverride.trim().ifBlank { defaultTitle }
    val paint = camdRecorderWidgetPaint(status?.recorderEnabled, status?.recordingActive)
    val labelColor = when (paint) {
        CamdRecorderWidgetPaint.Unknown -> controls.inactiveContent.copy(alpha = 0.25f)
        CamdRecorderWidgetPaint.Disabled -> controls.inactiveContent
        CamdRecorderWidgetPaint.EnabledIdle -> controls.activeContent
        CamdRecorderWidgetPaint.Recording -> CamdRecordingContentRed
    }
    val canToggle = status != null &&
        (status?.recorderEnabled != null || status?.recordingActive != null)

    DashboardWidgetScaffold(
        onClick = {
            if (isEditMode) {
                onEditClick()
                return@DashboardWidgetScaffold
            }
            if (busy) return@DashboardWidgetScaffold
            val app = context.applicationContext
            if (!canToggle) {
                Toast.makeText(
                    app,
                    app.getString(R.string.camd_window_unreachable),
                    Toast.LENGTH_SHORT,
                ).show()
                scope.launch { CamdStatusRepository.refreshNow() }
                return@DashboardWidgetScaffold
            }
            busy = true
            val nextEnabled = !(status?.recorderEnabled ?: false)
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    CamdWindowClient.command(
                        camdRecorderCommandLine(nextEnabled),
                        context = app,
                    )
                }
                CamdStatusRepository.refreshNow()
                busy = false
                result.exceptionOrNull()?.let { error ->
                    Toast.makeText(app, camdFailureToast(app, error), Toast.LENGTH_SHORT).show()
                }
            }
        },
        onLongClick = onLongClick,
        elevation = elevation,
        shape = shape,
        textColor = textColor,
        backgroundColor = backgroundColor,
    ) { availableHeight, resolvedTextColor ->
        CamdCenterLabelTileContent(
            showTitle = showTitle,
            titleText = titleText,
            availableHeight = availableHeight,
            titleColor = resolvedTextColor,
            centerLabel = CAMD_RECORDER_WIDGET_CENTER_LABEL,
            labelColor = labelColor,
        )
    }
}

@Composable
private fun CamdCenterLabelTileContent(
    showTitle: Boolean,
    titleText: String,
    availableHeight: Dp,
    titleColor: Color,
    centerLabel: String,
    labelColor: Color,
) {
    DashboardWidgetContentWithOptionalTitle(
        showTitle = showTitle,
        titleText = titleText,
        availableHeight = availableHeight,
        resolvedTextColor = titleColor,
        modifier = Modifier
            .fillMaxSize()
            .wrapContentHeight(Alignment.CenterVertically),
    ) { contentModifier ->
        Box(
            modifier = contentModifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            val valueStyle = calculateResponsiveTextStyle(
                containerHeight = availableHeight,
                textType = TextType.VALUE,
            )
            Text(
                text = centerLabel,
                style = valueStyle,
                color = labelColor,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
