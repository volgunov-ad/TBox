package vad.dashing.tbox.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.DashboardWidget
import vad.dashing.tbox.R
import vad.dashing.tbox.TboxViewModel

@Composable
fun DashboardNetWidgetItem(
    widget: DashboardWidget,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    viewModel: TboxViewModel,
    elevation: Dp = 4.dp,
    shape: Dp = 12.dp,
    backgroundTransparent: Boolean = false
) {
    val netState by viewModel.netState.collectAsStateWithLifecycle()
    val apnStatus by viewModel.apnStatus.collectAsStateWithLifecycle()

    val currentTheme by viewModel.currentTheme.collectAsStateWithLifecycle()

    // Определяем ресурс изображения на основе параметров
    val imageRes = remember(netState.signalLevel, netState.netStatus, apnStatus, currentTheme) {
        if (apnStatus) {
            when (netState.netStatus) {
                "2G" -> {
                    when (netState.signalLevel) {
                        1 -> {
                            if (currentTheme == 2) R.drawable.ic_signal_e_cellular_1_sharp_outlined
                            else R.drawable.ic_signal_e_cellular_1_sharp_outlined_dark
                        }
                        2 -> {
                            if (currentTheme == 2) R.drawable.ic_signal_e_cellular_2_sharp_outlined
                            else R.drawable.ic_signal_e_cellular_2_sharp_outlined_dark
                        }
                        3 -> {
                            if (currentTheme == 2) R.drawable.ic_signal_e_cellular_3_sharp_outlined
                            else R.drawable.ic_signal_e_cellular_3_sharp_outlined_dark
                        }
                        4 -> {
                            if (currentTheme == 2) R.drawable.ic_signal_e_cellular_4_sharp_outlined
                            else R.drawable.ic_signal_e_cellular_4_sharp_outlined_dark
                        }
                        else -> {
                            if (currentTheme == 2) R.drawable.ic_signal_e_cellular_0_sharp_outlined
                            else R.drawable.ic_signal_e_cellular_0_sharp_outlined_dark
                        }
                    }
                }
                "3G" -> {
                    when (netState.signalLevel) {
                        1 -> {
                            if (currentTheme == 2) R.drawable.ic_signal_3g_cellular_1_sharp_outlined
                            else R.drawable.ic_signal_3g_cellular_1_sharp_outlined_dark
                        }
                        2 -> {
                            if (currentTheme == 2) R.drawable.ic_signal_3g_cellular_2_sharp_outlined
                            else R.drawable.ic_signal_3g_cellular_2_sharp_outlined_dark
                        }
                        3 -> {
                            if (currentTheme == 2) R.drawable.ic_signal_3g_cellular_3_sharp_outlined
                            else R.drawable.ic_signal_3g_cellular_3_sharp_outlined_dark
                        }
                        4 -> {
                            if (currentTheme == 2) R.drawable.ic_signal_3g_cellular_4_sharp_outlined
                            else R.drawable.ic_signal_3g_cellular_4_sharp_outlined_dark
                        }
                        else -> {
                            if (currentTheme == 2) R.drawable.ic_signal_3g_cellular_0_sharp_outlined
                            else R.drawable.ic_signal_3g_cellular_0_sharp_outlined_dark
                        }
                    }
                }
                "4G" -> {
                    when (netState.signalLevel) {
                        1 -> {
                            if (currentTheme == 2) R.drawable.ic_signal_4g_cellular_1_sharp_outlined
                            else R.drawable.ic_signal_4g_cellular_1_sharp_outlined_dark
                        }
                        2 -> {
                            if (currentTheme == 2) R.drawable.ic_signal_4g_cellular_2_sharp_outlined
                            else R.drawable.ic_signal_4g_cellular_2_sharp_outlined_dark
                        }
                        3 -> {
                            if (currentTheme == 2) R.drawable.ic_signal_4g_cellular_3_sharp_outlined
                            else R.drawable.ic_signal_4g_cellular_3_sharp_outlined_dark
                        }
                        4 -> {
                            if (currentTheme == 2) R.drawable.ic_signal_4g_cellular_4_sharp_outlined
                            else R.drawable.ic_signal_4g_cellular_4_sharp_outlined_dark
                        }
                        else -> {
                            // Обратите внимание: здесь theme == 1, а не 2
                            if (currentTheme == 1) R.drawable.ic_signal_4g_cellular_0_sharp_outlined
                            else R.drawable.ic_signal_4g_cellular_0_sharp_outlined_dark
                        }
                    }
                }
                else -> {
                    when (netState.signalLevel) {
                        1 -> {
                            if (currentTheme == 2) R.drawable.ic_signal_cellular_1_sharp_outlined
                            else R.drawable.ic_signal_cellular_1_sharp_outlined_dark
                        }
                        2 -> {
                            if (currentTheme == 2) R.drawable.ic_signal_cellular_2_sharp_outlined
                            else R.drawable.ic_signal_cellular_2_sharp_outlined_dark
                        }
                        3 -> {
                            if (currentTheme == 2) R.drawable.ic_signal_cellular_3_sharp_outlined
                            else R.drawable.ic_signal_cellular_3_sharp_outlined_dark
                        }
                        4 -> {
                            if (currentTheme == 2) R.drawable.ic_signal_cellular_4_sharp_outlined
                            else R.drawable.ic_signal_cellular_4_sharp_outlined_dark
                        }
                        else -> {
                            if (currentTheme == 2) R.drawable.ic_signal_cellular_0_sharp_outlined
                            else R.drawable.ic_signal_cellular_0_sharp_outlined_dark
                        }
                    }
                }
            }
        } else {
            when (netState.signalLevel) {
                1 -> {
                    if (currentTheme == 2) R.drawable.ic_signal_cellular_1_sharp_outlined
                    else R.drawable.ic_signal_cellular_1_sharp_outlined_dark
                }
                2 -> {
                    if (currentTheme == 2) R.drawable.ic_signal_cellular_2_sharp_outlined
                    else R.drawable.ic_signal_cellular_2_sharp_outlined_dark
                }
                3 -> {
                    if (currentTheme == 2) R.drawable.ic_signal_cellular_3_sharp_outlined
                    else R.drawable.ic_signal_cellular_3_sharp_outlined_dark
                }
                4 -> {
                    if (currentTheme == 2) R.drawable.ic_signal_cellular_4_sharp_outlined
                    else R.drawable.ic_signal_cellular_4_sharp_outlined_dark
                }
                else -> {
                    if (currentTheme == 2) R.drawable.ic_signal_cellular_0_sharp_outlined
                    else R.drawable.ic_signal_cellular_0_sharp_outlined_dark
                }
            }
        }
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
        shape = RoundedCornerShape(shape)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    color = Color.Transparent,
                    shape = RoundedCornerShape(shape)
                )
        ) {
            // Основной контент
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp)
                    .wrapContentHeight(Alignment.CenterVertically),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Отображаем иконку сигнала
                Image(
                    painter = painterResource(id = imageRes),
                    contentDescription = "Signal level: $netState.signalLevel, Network: $netState.netStatus",
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}