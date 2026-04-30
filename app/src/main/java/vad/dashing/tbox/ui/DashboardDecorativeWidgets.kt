package vad.dashing.tbox.ui

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import vad.dashing.tbox.normalizeWidgetScale
import java.util.Date

const val DECORATIVE_CLOCK_WIDGET_DATA_KEY = "decorativeClock"
const val DECORATIVE_TEXT_WIDGET_DATA_KEY = "decorativeText"
const val DECORATIVE_DIVIDER_WIDGET_DATA_KEY = "decorativeDivider"

@Composable
fun DashboardClockWidgetItem(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    textColor: Color,
    backgroundColor: Color,
    elevation: Dp = 4.dp,
    shape: Dp = 12.dp
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000L)
        }
    }

    val context = LocalContext.current
    val is24h = DateFormat.is24HourFormat(context)
    val pattern = if (is24h) "HH:mm" else "hh:mm a"
    val timeText = remember(now) {
        java.text.SimpleDateFormat(pattern, java.util.Locale.getDefault()).format(Date(now))
    }

    DashboardWidgetScaffold(
        onClick = onClick,
        onLongClick = onLongClick,
        elevation = elevation,
        shape = shape,
        textColor = textColor,
        backgroundColor = backgroundColor
    ) { _, resolvedTextColor ->
        val textScale = normalizeWidgetScale(LocalWidgetTextScale.current)
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = timeText,
                color = resolvedTextColor,
                fontSize = (32 * textScale).sp,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun DashboardTextWidgetItem(
    displayText: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    textColor: Color,
    backgroundColor: Color,
    elevation: Dp = 4.dp,
    shape: Dp = 12.dp
) {
    DashboardWidgetScaffold(
        onClick = onClick,
        onLongClick = onLongClick,
        elevation = elevation,
        shape = shape,
        textColor = textColor,
        backgroundColor = backgroundColor
    ) { _, resolvedTextColor ->
        val textScale = normalizeWidgetScale(LocalWidgetTextScale.current)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = displayText,
                color = resolvedTextColor,
                fontSize = (20 * textScale).sp,
                textAlign = TextAlign.Center,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun DashboardDividerWidgetItem(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    backgroundColor: Color,
    elevation: Dp = 4.dp,
    shape: Dp = 12.dp
) {
    DashboardWidgetScaffold(
        onClick = onClick,
        onLongClick = onLongClick,
        elevation = elevation,
        shape = shape,
        backgroundColor = backgroundColor
    ) { _, _ ->
        Box(modifier = Modifier.fillMaxSize())
    }
}
