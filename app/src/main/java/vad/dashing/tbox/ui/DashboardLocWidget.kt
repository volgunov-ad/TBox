package vad.dashing.tbox.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.DashboardWidget
import vad.dashing.tbox.R
import vad.dashing.tbox.TboxViewModel

@Composable
fun DashboardLocWidgetItem(
    widget: DashboardWidget,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    viewModel: TboxViewModel,
    elevation: Dp = 4.dp,
    shape: Dp = 12.dp,
    backgroundTransparent: Boolean = false
) {
    val locValues by viewModel.locValues.collectAsStateWithLifecycle()
    val isLocValuesTrue by viewModel.isLocValuesTrue.collectAsStateWithLifecycle()

    // Определяем ресурс изображения на основе параметров
    val locIndicatorDrawable = remember(locValues.locateStatus, isLocValuesTrue) {
        when {
            !locValues.locateStatus -> R.drawable.loc_0_err
            !isLocValuesTrue -> R.drawable.loc_0_warn
            else -> R.drawable.loc_0_ok
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
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    color = Color.Transparent,
                    shape = RoundedCornerShape(shape)
                )
        ) {
            val availableHeight = maxHeight
            // Основной контент
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp)
                    .wrapContentHeight(Alignment.CenterVertically),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "${locValues.visibleSatellites}",
                    fontSize = calculateResponsiveFontSize(
                        containerHeight = availableHeight,
                        textType = TextType.TITLE
                    ),
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .wrapContentHeight(Alignment.CenterVertically)
                )
                // Отображаем иконку навигации с вращением по направлению
                Image(
                    painter = painterResource(id = locIndicatorDrawable),
                    contentDescription = stringResource(
                        R.string.dashboard_loc_content_desc,
                        locValues.locateStatus,
                        isLocValuesTrue
                    ),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .rotate(degrees = -locValues.trueDirection)
                        .weight(2f)
                        .padding(4.dp)
                        .wrapContentHeight(Alignment.CenterVertically)
                )
                Text(
                    text = "${locValues.speed}\u2009${stringResource(R.string.unit_kmh)}",
                    fontSize = calculateResponsiveFontSize(
                        containerHeight = availableHeight,
                        textType = TextType.TITLE
                    ),
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    softWrap = true,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .wrapContentHeight(Alignment.CenterVertically)
                )
            }
        }
    }
}