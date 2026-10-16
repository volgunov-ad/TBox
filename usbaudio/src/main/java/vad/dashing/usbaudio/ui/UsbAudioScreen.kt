package vad.dashing.usbaudio.ui

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
import vad.dashing.usbaudio.UsbAudioAction
import vad.dashing.usbaudio.UsbAudioBannerKind
import vad.dashing.usbaudio.UsbAudioState

@Composable
fun UsbAudioScreen(
    state: UsbAudioState,
    onRefresh: () -> Unit,
    onApply: () -> Unit,
    onRestore: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(text = "USB-аудио ГУ", style = MaterialTheme.typography.headlineSmall)
            Text(
                text = "Постоянное включение звука на USB-карту для ГУ A10 (Adayo/VHAL). " +
                    "Записывает конфигурацию аудио в /system и удаляет имя файла в /vendor, " +
                    "на время записи снимая защиту eMMC (как «Расширение /data»). Действует после " +
                    "перезагрузок. Для временного включения до перезагрузки используйте TBox Monitor.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.banner?.let { banner ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = when (banner.kind) {
                            UsbAudioBannerKind.Info -> MaterialTheme.colorScheme.secondaryContainer
                            UsbAudioBannerKind.Warning -> MaterialTheme.colorScheme.tertiaryContainer
                            UsbAudioBannerKind.Success -> MaterialTheme.colorScheme.primaryContainer
                            UsbAudioBannerKind.Error -> MaterialTheme.colorScheme.errorContainer
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
                        text = "Выполняется… (перезапуск звука, 15–20 секунд)",
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
            if (UsbAudioAction.Apply in state.actions) {
                Button(
                    onClick = onApply,
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Включить постоянно",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            if (UsbAudioAction.Restore in state.actions) {
                OutlinedButton(
                    onClick = onRestore,
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Вернуть штатную политику",
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
