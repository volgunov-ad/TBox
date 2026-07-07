package vad.dashing.tbox.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import vad.dashing.tbox.DashboardWidget
import vad.dashing.tbox.HTTP_REQUEST_POST_ACTION_BLOCK_MS
import vad.dashing.tbox.HttpRequestIconPaths
import vad.dashing.tbox.HttpRequestWidgetResult
import vad.dashing.tbox.LauncherAppIconPaths
import vad.dashing.tbox.R
import vad.dashing.tbox.browserUrlFromHttpRequestYaml
import vad.dashing.tbox.decodeFileToOwnedImageBitmap
import vad.dashing.tbox.executeHttpRequestWidget
import vad.dashing.tbox.httpRequestWidgetErrorMessage
import vad.dashing.tbox.openHttpRequestWidgetUrlInBrowser
import vad.dashing.tbox.parseHttpRequestWidgetYaml
import vad.dashing.tbox.ui.theme.tboxCaption

@Composable
internal fun DashboardHttpRequestWidgetItem(
    widget: DashboardWidget,
    iconKey: String,
    requestYaml: String,
    openBrowser: Boolean,
    customIconRevision: Int,
    iconLookup: LauncherAppIconPaths.Lookup,
    suppressCustomIcon: Boolean = false,
    showTitle: Boolean,
    titleOverride: String = "",
    isEditMode: Boolean,
    onEditClick: () -> Unit,
    onLongClick: () -> Unit,
    elevation: Dp,
    shape: Dp,
    textColor: Color,
    backgroundColor: Color,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var blockedUntilMs by remember { mutableLongStateOf(0L) }
    var flashColor by remember { mutableStateOf<Color?>(null) }
    var flashDurationMs by remember { mutableIntStateOf(0) }
    val animatedBackground by animateColorAsState(
        targetValue = flashColor ?: backgroundColor,
        animationSpec = tween(durationMillis = if (flashColor == null) flashDurationMs else 250),
        label = "httpRequestFlash"
    )
    val imageBitmap = remember(iconKey, customIconRevision, iconLookup, suppressCustomIcon) {
        if (iconKey.isBlank() || suppressCustomIcon) {
            null
        } else {
            runCatching {
                val file = HttpRequestIconPaths.resolveIconFile(context.filesDir, iconKey, iconLookup)
                    ?: return@runCatching null
                decodeFileToOwnedImageBitmap(file)
            }.getOrNull()
        }
    }

    LaunchedEffect(flashColor, flashDurationMs) {
        val activeColor = flashColor ?: return@LaunchedEffect
        delay(flashDurationMs.toLong())
        if (flashColor == activeColor) {
            flashColor = null
        }
    }

    DashboardWidgetScaffold(
        modifier = Modifier.fillMaxSize(),
        onClick = {
            if (isEditMode) {
                onEditClick()
                return@DashboardWidgetScaffold
            }
            val now = System.currentTimeMillis()
            if (now < blockedUntilMs) return@DashboardWidgetScaffold
            if (openBrowser) {
                blockedUntilMs = now + HTTP_REQUEST_POST_ACTION_BLOCK_MS
                browserUrlFromHttpRequestYaml(requestYaml).onSuccess { url ->
                    openHttpRequestWidgetUrlInBrowser(context, url)
                }.onFailure { e ->
                    android.widget.Toast.makeText(
                        context,
                        context.getString(R.string.widget_http_request_invalid_yaml, e.message.orEmpty()),
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
                return@DashboardWidgetScaffold
            }
            val parsed = parseHttpRequestWidgetYaml(requestYaml)
            val config = parsed.getOrElse { e ->
                blockedUntilMs = now + HTTP_REQUEST_POST_ACTION_BLOCK_MS
                android.widget.Toast.makeText(
                    context,
                    context.getString(R.string.widget_http_request_invalid_yaml, e.message.orEmpty()),
                    android.widget.Toast.LENGTH_LONG
                ).show()
                flashColor = Color(0xFFB3261E)
                flashDurationMs = 2000
                return@DashboardWidgetScaffold
            }
            blockedUntilMs = now + config.timeoutMillis + HTTP_REQUEST_POST_ACTION_BLOCK_MS
            scope.launch {
                val result = executeHttpRequestWidget(config)
                blockedUntilMs = System.currentTimeMillis() + HTTP_REQUEST_POST_ACTION_BLOCK_MS
                when (result) {
                    is HttpRequestWidgetResult.Success -> {
                        flashColor = Color(0xFF2E7D32)
                        flashDurationMs = 1000
                    }
                    is HttpRequestWidgetResult.Failure -> {
                        android.widget.Toast.makeText(
                            context,
                            context.getString(
                                R.string.widget_http_request_failed,
                                httpRequestWidgetErrorMessage(result)
                            ),
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                        flashColor = Color(0xFFB3261E)
                        flashDurationMs = 2000
                    }
                }
            }
        },
        onLongClick = onLongClick,
        elevation = elevation,
        shape = shape,
        textColor = textColor,
        backgroundColor = animatedBackground,
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
            val titleLine = titleOverride.trim().ifBlank { widget.title }
            if (showTitle && titleLine.isNotEmpty()) {
                val titleStyle = calculateResponsiveTextStyle(
                    containerHeight = availableHeight,
                    textType = TextType.TITLE
                )
                Text(
                    text = titleLine,
                    style = titleStyle,
                    color = resolvedTextColor,
                    textAlign = LocalWidgetTextAlign.current,
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
