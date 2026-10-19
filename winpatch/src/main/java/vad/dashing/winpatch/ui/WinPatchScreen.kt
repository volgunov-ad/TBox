package vad.dashing.winpatch.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import vad.dashing.winpatch.WinPatchAction
import vad.dashing.winpatch.WinPatchBannerKind
import vad.dashing.winpatch.WinPatchState
import vad.dashing.winpatch.WinPatchTargetPanel

@Composable
fun WinPatchScreen(
    state: WinPatchState,
    onRefresh: () -> Unit,
    onApply: (String) -> Unit,
    onRestore: (String) -> Unit,
    onReboot: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(text = "AA/CarPlay окна ГУ", style = MaterialTheme.typography.headlineSmall)
            Text(
                text = "Постоянная установка пропатченных Android Auto (${WinPatchLabel.AA}) и CarPlayView " +
                    "(${WinPatchLabel.CP}) в /system ГУ A10 (Adayo/VHAL): Android Auto — окно лаунчера со штатными " +
                    "барами, CarPlay — без чёрных полос. Каждый ставится и откатывается отдельно. " +
                    "APK зашиты в приложение, штатные сохраняются в резерв " +
                    "в /data/local/tmp, запись идёт со снятием защиты eMMC (как в «Расширение /data»). " +
                    "CarPlayManagerConfig.json не изменяется — проводное и беспроводное подключение остаются штатными.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.banner?.let { banner ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = when (banner.kind) {
                            WinPatchBannerKind.Info -> MaterialTheme.colorScheme.secondaryContainer
                            WinPatchBannerKind.Warning -> MaterialTheme.colorScheme.tertiaryContainer
                            WinPatchBannerKind.Success -> MaterialTheme.colorScheme.primaryContainer
                            WinPatchBannerKind.Error -> MaterialTheme.colorScheme.errorContainer
                        },
                    ),
                ) {
                    Text(
                        text = banner.text,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(14.dp),
                    )
                }
            }
            if (state.busy) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Выполняется… (запись в /system и проверка MD5, ~30–60 секунд)",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            if (state.facts.isNotEmpty()) {
                Card {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        state.facts.forEach { (name, value) ->
                            Text(
                                text = "$name: $value",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            }
            state.targets.forEach { panel ->
                TargetCard(
                    panel = panel,
                    busy = state.busy,
                    onApply = { onApply(panel.id) },
                    onRestore = { onRestore(panel.id) },
                )
            }
            if (WinPatchAction.Reboot in state.actions) {
                OutlinedButton(
                    onClick = onReboot,
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Перезагрузить ГУ",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            OutlinedButton(
                onClick = onRefresh,
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Обновить",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun TargetCard(
    panel: WinPatchTargetPanel,
    busy: Boolean,
    onApply: () -> Unit,
    onRestore: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = when (panel.kind) {
                WinPatchBannerKind.Info -> MaterialTheme.colorScheme.surfaceVariant
                WinPatchBannerKind.Warning -> MaterialTheme.colorScheme.tertiaryContainer
                WinPatchBannerKind.Success -> MaterialTheme.colorScheme.primaryContainer
                WinPatchBannerKind.Error -> MaterialTheme.colorScheme.errorContainer
            },
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = "${panel.title} (${panel.patchLabel})", style = MaterialTheme.typography.titleLarge)
            Text(text = "Состояние: ${panel.status}", style = MaterialTheme.typography.bodyLarge)
            Text(text = "Резерв: ${panel.backup}", style = MaterialTheme.typography.bodyLarge)
            if (panel.canApply || panel.canRestore) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (panel.canApply) {
                        Button(onClick = onApply, enabled = !busy, modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Установить патч ${panel.title}",
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                    if (panel.canRestore) {
                        OutlinedButton(onClick = onRestore, enabled = !busy, modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Вернуть штатный ${panel.title}",
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}

private object WinPatchLabel {
    const val AA = "p5"
    const val CP = "p3"
}
