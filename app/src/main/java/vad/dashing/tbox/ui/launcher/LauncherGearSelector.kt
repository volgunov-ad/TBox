package vad.dashing.tbox.ui.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val GEAR_SLOTS = listOf('P', 'R', 'N', 'D')

fun resolveActiveGearSlot(gearBoxMode: String, gearBoxCurrentGear: Int?): Char? {
    val mode = gearBoxMode.uppercase()
    return when {
        mode.contains('P') || gearBoxCurrentGear == 0 -> 'P'
        mode.contains('R') -> 'R'
        mode.contains('N') -> 'N'
        mode.contains('D') || (gearBoxCurrentGear != null && gearBoxCurrentGear > 0) -> 'D'
        else -> null
    }
}

@Composable
fun LauncherGearSelector(
    activeSlot: Char?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GEAR_SLOTS.forEach { slot ->
            val active = activeSlot == slot
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (active) LauncherColors.GearActive else LauncherColors.LeftPanelCard
                    )
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = slot.toString(),
                    fontSize = 18.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                    color = if (active) LauncherColors.LeftPanelCard else LauncherColors.GearInactive,
                )
            }
        }
    }
}
