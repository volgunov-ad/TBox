package vad.dashing.tbox.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vad.dashing.tbox.R
import vad.dashing.tbox.SupportedMediaPlayer
import vad.dashing.tbox.defaultMediaPlayerPackages
import vad.dashing.tbox.normalizeMediaPlayerPackages
import vad.dashing.tbox.orderedMediaPlayerPackages

fun normalizeMediaPlayersSelection(rawPackages: Collection<String>): Set<String> {
    val normalized = normalizeMediaPlayerPackages(rawPackages)
    return if (normalized.isEmpty()) {
        defaultMediaPlayerPackages()
    } else {
        normalized
    }
}

@Composable
fun MediaPlayersInlineSelection(
    selectedPlayers: Set<String>,
    onSelectionChange: (Set<String>) -> Unit,
    enabled: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = stringResource(R.string.widget_music_players),
            fontSize = 24.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = stringResource(R.string.widget_music_players_hint),
            fontSize = 20.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        SupportedMediaPlayer.entries.forEach { player ->
            val isChecked = player.packageName in selectedPlayers
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = enabled) {
                        val updated = selectedPlayers.toMutableSet()
                        if (isChecked) {
                            updated.remove(player.packageName)
                        } else {
                            updated.add(player.packageName)
                        }
                        onSelectionChange(normalizeMediaPlayerPackages(updated))
                    }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = isChecked,
                    onCheckedChange = { checked ->
                        val updated = selectedPlayers.toMutableSet()
                        if (checked) {
                            updated.add(player.packageName)
                        } else {
                            updated.remove(player.packageName)
                        }
                        onSelectionChange(normalizeMediaPlayerPackages(updated))
                    },
                    enabled = enabled
                )
                Icon(
                    painter = painterResource(id = player.iconRes),
                    contentDescription = stringResource(player.titleRes),
                    tint = Color.Unspecified,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(24.dp)
                )
                Text(
                    text = stringResource(player.titleRes),
                    fontSize = 22.sp,
                    modifier = Modifier.padding(start = 10.dp),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

fun orderedMediaPlayersForStorage(selectedPlayers: Set<String>): List<String> {
    return orderedMediaPlayerPackages(selectedPlayers)
}
