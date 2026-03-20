package vad.dashing.tbox.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.DashboardWidget
import vad.dashing.tbox.R
import vad.dashing.tbox.TboxViewModel

@Composable
fun DashboardNetNewWidgetItem(
    widget: DashboardWidget,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    viewModel: TboxViewModel,
    elevation: Dp = 4.dp,
    shape: Dp = 12.dp,
    backgroundColor: Color? = null,
    color: Color? = null,
    scale: Float = 1f
) {
    val netState by viewModel.netState.collectAsStateWithLifecycle()
    val apnStatus by viewModel.apnStatus.collectAsStateWithLifecycle()

    // Определяем ресурс изображения на основе параметров
    val imageNetRes = remember(netState.netStatus, apnStatus) {
        if (apnStatus) {
            when (netState.netStatus) {
                "2G" -> {
                    R.drawable.signal_2g
                }
                "3G" -> {
                    R.drawable.signal_3g
                }
                "4G" -> {
                    R.drawable.signal_4g
                }
                else -> {
                    null
                }
            }
        } else {
            null
        }
    }

    val imageSignalRes = remember(netState.signalLevel, apnStatus) {
        when (netState.signalLevel) {
            1 -> {
                R.drawable.signal_1
            }
            2 -> {
                R.drawable.signal_2
            }
            3 -> {
                R.drawable.signal_3
            }
            4 -> {
                R.drawable.signal_4
            }
            else -> {
                R.drawable.signal_0
            }
        }
    }

    val imageColorSignal: Color
    val imageColorNet: Color

    if (color == null) {
        when (netState.signalLevel) {
            1 -> {
                imageColorSignal = Color(colorResource(R.color.status_err).hashCode())
                imageColorNet = Color(colorResource(R.color.status_err).hashCode())
            }
            2 -> {
                imageColorSignal = Color(colorResource(R.color.status_warn).hashCode())
                imageColorNet = Color(colorResource(R.color.status_warn).hashCode())
            }
            in 3..4 -> {
                imageColorSignal = Color(colorResource(R.color.status_ok).hashCode())
                imageColorNet = Color(colorResource(R.color.status_ok).hashCode())
            }
            else -> {
                imageColorSignal = Color(colorResource(R.color.status_err).hashCode())
                imageColorNet = Color(colorResource(R.color.status_err).hashCode())
            }
        }
    } else {
        imageColorSignal = color
        imageColorNet = color
    }

    DashboardWidgetScaffold(
        onClick = onClick,
        onLongClick = onLongClick,
        elevation = elevation,
        shape = shape,
        backgroundColor = backgroundColor
    ) { _, _ ->
        // Контейнер для наложенных изображений
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp)
        ) {
            Image(
                painter = painterResource(id = imageSignalRes),
                contentDescription = netState.signalLevel.toString(),
                contentScale = ContentScale.Fit,
                colorFilter = imageColorSignal.let { ColorFilter.tint(it) },
                modifier = Modifier.matchParentSize().scale(scale)
            )
            if (imageNetRes != null) {
                Image(
                    painter = painterResource(id = imageNetRes),
                    contentDescription = netState.signalLevel.toString(),
                    contentScale = ContentScale.Fit,
                    colorFilter = imageColorNet.let { ColorFilter.tint(it) },
                    modifier = Modifier
                        .matchParentSize()
                        .scale(scale)
                )
            }
        }
    }
}