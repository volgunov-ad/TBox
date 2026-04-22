package vad.dashing.tbox.ui

import android.content.Intent
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mengbo.mbCan.defines.MBVehicleProperty
import vad.dashing.tbox.BackgroundService
import vad.dashing.tbox.DashboardWidget
import vad.dashing.tbox.MbVehicleRepository
import vad.dashing.tbox.R

private const val TAG = "SteeringHeatWidget"

@Composable
fun DashboardSteeringWheelHeatWidgetItem(
    @Suppress("UNUSED_PARAMETER") widget: DashboardWidget,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    elevation: Dp = 4.dp,
    shape: Dp = 12.dp,
    showTitle: Boolean = true,
    textColor: Color? = null,
    backgroundColor: Color? = null,
) {
    val context = LocalContext.current
    val heatOn by MbVehicleRepository.steeringWheelHeatOn.collectAsStateWithLifecycle()
    val mbOk by MbVehicleRepository.clientAvailable.collectAsStateWithLifecycle()
    val err by MbVehicleRepository.lastError.collectAsStateWithLifecycle()

    val stateLabel = when {
        err != null -> stringResource(R.string.mb_vehicle_error_short)
        !mbOk -> stringResource(R.string.mb_vehicle_waiting)
        heatOn == true -> stringResource(R.string.steering_heat_on)
        heatOn == false -> stringResource(R.string.steering_heat_off)
        else -> stringResource(R.string.value_no_data)
    }

    DashboardWidgetScaffold(
        onClick = {
            onClick()
            val prop = MBVehicleProperty.eVEHICLE_SET_MFS_HEAT_SWITCH.nativeValue
            val next = !(heatOn ?: false)
            val value = if (next) 1 else 0
            MbVehicleRepository.applyOptimisticSteeringHeat(next)
            try {
                context.startForegroundService(
                    Intent(context, BackgroundService::class.java).apply {
                        action = BackgroundService.ACTION_MB_VEHICLE_SET_PARAM
                        putExtra(BackgroundService.EXTRA_MB_PROPERTY_ID, prop)
                        putExtra(BackgroundService.EXTRA_MB_VALUE_INT, value)
                    }
                )
                context.startForegroundService(
                    Intent(context, BackgroundService::class.java).apply {
                        action = BackgroundService.ACTION_MB_VEHICLE_POLL
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "startForegroundService MB vehicle", e)
            }
        },
        onLongClick = onLongClick,
        elevation = elevation,
        shape = shape,
        textColor = textColor,
        backgroundColor = backgroundColor
    ) { availableHeight, resolvedTextColor ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp)
                .wrapContentHeight(Alignment.CenterVertically),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (showTitle) {
                val titleFont = calculateResponsiveFontSize(
                    containerHeight = availableHeight,
                    textType = TextType.TITLE
                )
                Text(
                    text = stringResource(R.string.data_title_steering_wheel_heat_widget),
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(Alignment.CenterVertically),
                    fontSize = titleFont,
                    lineHeight = titleFont * 1.3f,
                    fontWeight = FontWeight.Medium,
                    color = resolvedTextColor,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    softWrap = true,
                    overflow = TextOverflow.Ellipsis
                )
            }
            val valueFont = calculateResponsiveFontSize(
                containerHeight = availableHeight,
                textType = TextType.VALUE
            )
            Text(
                text = stateLabel,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                fontSize = valueFont,
                fontWeight = FontWeight.SemiBold,
                color = resolvedTextColor,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
