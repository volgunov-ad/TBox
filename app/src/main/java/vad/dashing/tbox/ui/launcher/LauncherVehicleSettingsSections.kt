package vad.dashing.tbox.ui.launcher

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vad.dashing.tbox.ui.theme.tboxCaption

enum class VehicleSettingsSection {
    Status,
    Wheels,
    Body,
    Cabin,
    Adas,
    Comfort,
}

@Composable
fun LauncherVehicleSectionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val chevron by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(280),
        label = "sectionChevron",
    )
    val bg by animateColorAsState(
        targetValue = if (expanded) LauncherColors.CardDarkElevated else LauncherColors.CardDark,
        animationSpec = tween(220),
        label = "sectionBg",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .clickable(onClick = onToggle)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, null, tint = LauncherColors.AccentCyan, modifier = Modifier.size(22.dp))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.tboxCaption,
                        color = LauncherColors.TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = subtitle,
                        color = LauncherColors.TextSecondary,
                        fontSize = 12.sp,
                    )
                }
            }
            Icon(
                Icons.Filled.KeyboardArrowDown,
                null,
                tint = LauncherColors.TextMuted,
                modifier = Modifier.rotate(chevron),
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(300)) + fadeIn(tween(280)),
            exit = shrinkVertically(tween(240)) + fadeOut(tween(200)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                content()
            }
        }
    }
}

object VehicleSettingsSectionIcons {
    val Status = Icons.Filled.Settings
    val Wheels = Icons.Filled.Build
    val Body = Icons.Filled.Star
    val Cabin = Icons.Filled.Home
    val Adas = Icons.Filled.PlayArrow
    val Comfort = Icons.Filled.Settings
}

@Composable
fun LauncherSettingsValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val rowModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(rowModifier)
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = LauncherColors.TextPrimary, fontSize = 14.sp)
        Text(value, color = LauncherColors.TextSecondary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun LauncherSettingsToggleRow(
    label: String,
    active: Boolean,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = onClick != null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .then(
                if (enabled && onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = if (enabled) LauncherColors.TextPrimary else LauncherColors.TextMuted,
            fontSize = 14.sp,
        )
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(
                    if (active) LauncherColors.AccentCyan
                    else LauncherColors.TextMuted.copy(alpha = 0.4f),
                ),
        )
    }
}
