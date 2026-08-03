package vad.dashing.tbox.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.DashboardWidget
import vad.dashing.tbox.R
import vad.dashing.tbox.location.GeoBearingSource
import vad.dashing.tbox.location.GeoDisplayRepository
import vad.dashing.tbox.location.GeoSpeedSource
import vad.dashing.tbox.trip.TripWidgetTileDisplay
import vad.dashing.tbox.ui.theme.tboxBody
import vad.dashing.tbox.valueToString
import java.util.Locale

@Composable
fun DashboardGeopositionDataWidgetItem(
    widget: DashboardWidget,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    elevation: Dp = 4.dp,
    shape: Dp = 12.dp,
    textColor: Color? = null,
    backgroundColor: Color? = null,
    showTitle: Boolean = false,
    titleOverride: String = "",
    showRowDividers: Boolean = TripWidgetTileDisplay.DEFAULT_SHOW_ROW_DIVIDERS,
    labelColumnWidthPercent: Int = TripWidgetTileDisplay.DEFAULT_LABEL_COLUMN_WIDTH_PERCENT,
) {
    val geo by GeoDisplayRepository.state.collectAsStateWithLifecycle()
    val yes = stringResource(R.string.value_yes)
    val no = stringResource(R.string.value_no)
    val rowStyle = MaterialTheme.typography.tboxBody
    val labelCol = labelColumnWidthPercent

    val fixText = if (geo.locateStatus) yes else no
    val truthText = if (geo.isTruthful) yes else no
    val retainText = if (geo.retaining) yes else no
    val courseText = remember(geo.bearingDeg, geo.bearingSource) {
        val deg = geo.bearingDeg
        val num = if (deg != null) {
            String.format(Locale.getDefault(), "%.1f", deg)
        } else {
            "—"
        }
        val src = when (geo.bearingSource) {
            GeoBearingSource.GNSS -> "GNSS"
            GeoBearingSource.RETENTION -> "retention"
            GeoBearingSource.HELD -> "held"
        }
        "$num ($src)"
    }
    val speedText = remember(geo.speedKmh, geo.speedSource) {
        val num = valueToString(geo.speedKmh, 1)
        val src = when (geo.speedSource) {
            GeoSpeedSource.GNSS -> "GNSS"
            GeoSpeedSource.CAN -> "CAN"
            GeoSpeedSource.RETENTION -> "retention"
        }
        "$num ($src)"
    }
    val satsText = remember(geo.visibleSatellites, geo.usingSatellites) {
        "${geo.visibleSatellites}/${geo.usingSatellites}"
    }

    val defaultTitle = stringResource(R.string.data_title_geoposition_data_widget)
    val titleText = titleOverride.trim().ifBlank { defaultTitle }

    DashboardWidgetScaffold(
        onClick = onClick,
        onLongClick = onLongClick,
        elevation = elevation,
        shape = shape,
        textColor = textColor,
        backgroundColor = backgroundColor,
    ) { availableHeight, resolvedTextColor ->
        DashboardWidgetContentWithOptionalTitle(
            showTitle = showTitle,
            titleText = titleText,
            availableHeight = availableHeight,
            resolvedTextColor = resolvedTextColor,
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp)
                .wrapContentHeight(),
        ) { contentModifier ->
            Column(modifier = contentModifier.fillMaxWidth()) {
                StatusRow(
                    label = stringResource(R.string.location_fixation),
                    value = fixText,
                    style = rowStyle,
                    color = resolvedTextColor,
                    showDivider = showRowDividers,
                    labelColumnWidthPercent = labelCol,
                )
                StatusRow(
                    label = stringResource(R.string.location_truth),
                    value = truthText,
                    style = rowStyle,
                    color = resolvedTextColor,
                    showDivider = showRowDividers,
                    labelColumnWidthPercent = labelCol,
                )
                StatusRow(
                    label = stringResource(R.string.location_retention_active),
                    value = retainText,
                    style = rowStyle,
                    color = resolvedTextColor,
                    showDivider = showRowDividers,
                    labelColumnWidthPercent = labelCol,
                )
                StatusRow(
                    label = stringResource(R.string.location_true_direction),
                    value = courseText,
                    style = rowStyle,
                    color = resolvedTextColor,
                    showDivider = showRowDividers,
                    labelColumnWidthPercent = labelCol,
                )
                StatusRow(
                    label = stringResource(R.string.location_speed),
                    value = speedText,
                    style = rowStyle,
                    color = resolvedTextColor,
                    showDivider = showRowDividers,
                    labelColumnWidthPercent = labelCol,
                )
                StatusRow(
                    label = stringResource(R.string.location_satellites),
                    value = satsText,
                    style = rowStyle,
                    color = resolvedTextColor,
                    showDivider = false,
                    labelColumnWidthPercent = labelCol,
                )
            }
        }
    }
}
