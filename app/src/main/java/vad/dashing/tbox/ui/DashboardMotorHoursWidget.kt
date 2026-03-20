package vad.dashing.tbox.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.DashboardWidget
import vad.dashing.tbox.R

@Composable
fun DashboardMotorHoursWidgetItem(
    widget: DashboardWidget,
    dataProvider: DataProvider,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    onDoubleClick: () -> Unit = {},
    elevation: Dp = 0.dp,
    shape: Dp = 12.dp,
    units: Boolean = true,
    textColor: Color? = null,
    backgroundColor: Color? = null
) {
    val motorHoursFlow = dataProvider.getValueFlow("motorHours")
    val motorHoursString by motorHoursFlow.collectAsStateWithLifecycle()

    val motorHoursTripFlow = dataProvider.getValueFlow("motorHoursTrip")
    val motorHoursTripString by motorHoursTripFlow.collectAsStateWithLifecycle()
    val hourUnit = stringResource(R.string.unit_hours)

    DashboardWidgetScaffold(
        onClick = onClick,
        onLongClick = onLongClick,
        onDoubleClick = onDoubleClick,
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
            Text(
                text = "$motorHoursString ${if (units) "\u2009$hourUnit" else ""}",
                fontSize = calculateResponsiveFontSize(
                    containerHeight = availableHeight,
                    textType = TextType.VALUE
                ),
                fontWeight = FontWeight.Medium,
                color = resolvedTextColor,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .wrapContentHeight(Alignment.CenterVertically)
            )
            Text(
                text = "$motorHoursTripString ${if (units) "\u2009$hourUnit" else ""}",
                fontSize = calculateResponsiveFontSize(
                    containerHeight = availableHeight,
                    textType = TextType.VALUE
                ),
                fontWeight = FontWeight.Medium,
                color = resolvedTextColor,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .wrapContentHeight(Alignment.CenterVertically)
            )
        }
    }
}