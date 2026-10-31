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
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import vad.dashing.usbaudio.UsbAudioAction
import vad.dashing.usbaudio.UsbAudioBannerKind
import vad.dashing.usbaudio.UsbAudioState
import vad.dashing.usbaudio.UsbDeviceCatalog
import vad.dashing.usbaudio.UsbVolume

@Composable
fun UsbAudioScreen(
    state: UsbAudioState,
    onRefresh: () -> Unit,
    onApply: () -> Unit,
    onRestore: () -> Unit,
    onRefreshVolume: () -> Unit,
    onApplyVolume: (Int) -> Unit,
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
            Card {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "USB-громкость",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "Аппаратный регулятор громкости USB-карты (tinymix через root). " +
                            "Шкала 0–${UsbVolume.MAX_VALUE}: 0 ≈ −28.4 дБ, ${UsbVolume.MAX_VALUE} ≈ 0 дБ. " +
                            "Не зависит от ручки громкости ГУ, которая и так на максимуме.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val volume = state.volume
                    volume.error?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    if (volume.value != null) {
                        var slider by remember(volume.value) { mutableStateOf(volume.value.toFloat()) }
                        Text(
                            text = "Текущая: ${volume.value} " +
                                "(≈${"%.1f".format(UsbVolume.approxDb(volume.value))} дБ)",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Slider(
                            value = slider,
                            onValueChange = { slider = it },
                            valueRange = 0f..UsbVolume.MAX_VALUE.toFloat(),
                            steps = UsbVolume.MAX_VALUE - 1,
                            enabled = !volume.busy,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedButton(
                                onClick = onRefreshVolume,
                                enabled = !volume.busy,
                            ) {
                                Text(text = "Прочитать")
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            Button(
                                onClick = { onApplyVolume(slider.roundToInt()) },
                                enabled = !volume.busy && slider.roundToInt() != volume.value,
                            ) {
                                Text(text = "Применить ${slider.roundToInt()}")
                            }
                        }
                    } else {
                        OutlinedButton(
                            onClick = onRefreshVolume,
                            enabled = !volume.busy,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = if (volume.busy) "Чтение…" else "Прочитать громкость USB-карты",
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                    if (volume.busy && volume.value != null) {
                        Text(
                            text = "Выполняется…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Card {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "USB-устройства (${state.devices.size})",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (state.devices.isEmpty()) {
                        Text(
                            text = "Нет подключённых USB-устройств",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    state.devices.forEach { device ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = device.name.ifBlank { "Без названия" },
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (device.isAudio) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface,
                                )
                                if (device.isAudio) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "аудио",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                            Text(
                                text = listOfNotNull(
                                    "${UsbDeviceCatalog.formatId(device.vendorId)}:${UsbDeviceCatalog.formatId(device.productId)}",
                                    device.manufacturer?.takeIf { it.isNotBlank() },
                                    device.classDescription,
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
