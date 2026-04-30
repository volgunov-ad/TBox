package vad.dashing.tbox.ui

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun DashboardWidgetScaffold(
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    onDoubleClick: (() -> Unit)? = null,
    elevation: Dp = 4.dp,
    shape: Dp = 12.dp,
    textColor: Color? = null,
    backgroundColor: Color? = null,
    backgroundImageBase64: String = "",
    content: @Composable BoxWithConstraintsScope.(availableHeight: Dp, resolvedTextColor: Color) -> Unit
) {
    val resolvedInteractionPolicy = LocalDashboardWidgetInteractionPolicy.current
    val useCardClickable = resolvedInteractionPolicy.mode == DashboardWidgetInteractionMode.STANDARD
    val resolvedBgImage = backgroundImageBase64.ifBlank { LocalWidgetBackgroundImage.current }
    Card(
        modifier = modifier
            .fillMaxSize()
            .combinedClickable(
                enabled = useCardClickable,
                onClick = onClick,
                onLongClick = onLongClick,
                onDoubleClick = onDoubleClick,
            ),
        elevation = CardDefaults.cardElevation(elevation),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor ?: MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(shape)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    color = Color.Transparent,
                    shape = RoundedCornerShape(shape)
                )
        ) {
            val availableHeight = maxHeight
            val resolvedTextColor = textColor ?: MaterialTheme.colorScheme.onSurface
            Box(modifier = Modifier.fillMaxSize()) {
                if (resolvedBgImage.isNotBlank()) {
                    val bitmap = remember(resolvedBgImage) {
                        runCatching {
                            val bytes = Base64.decode(resolvedBgImage, Base64.DEFAULT)
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        }.getOrNull()
                    }
                    bitmap?.let {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(alpha = 0.45f)
                        )
                    }
                }
                this@BoxWithConstraints.content(availableHeight, resolvedTextColor)
                if (!useCardClickable) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(
                                resolvedInteractionPolicy,
                                onClick,
                                onLongClick
                            ) {
                                detectTapGestures(
                                    onTap = { offset ->
                                        if (resolvedInteractionPolicy.isActionAllowed(
                                                offset = offset,
                                                width = size.width.toFloat(),
                                                height = size.height.toFloat()
                                            )
                                        ) {
                                            onClick()
                                        }
                                    },
                                    onLongPress = { offset ->
                                        if (resolvedInteractionPolicy.isActionAllowed(
                                                offset = offset,
                                                width = size.width.toFloat(),
                                                height = size.height.toFloat()
                                            )
                                        ) {
                                            onLongClick()
                                        }
                                    }
                                )
                            }
                    )
                }
            }
        }
    }
}
