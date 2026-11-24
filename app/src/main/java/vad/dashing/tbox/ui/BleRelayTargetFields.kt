package vad.dashing.tbox.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.automation.AutomationAction
import vad.dashing.tbox.automation.AutomationBuiltinActionType
import vad.dashing.tbox.automation.EspBleRelayActionEncoding
import vad.dashing.tbox.esp.EspBleRelayChannel
import vad.dashing.tbox.esp.EspBleRelayIds
import vad.dashing.tbox.esp.EspCompanionRepository
import vad.dashing.tbox.esp.EspRelayDevice
import vad.dashing.tbox.ruEn

internal data class BleRelayDeviceChoice(val id: String, val label: String) {
    override fun toString(): String = label
}

internal data class BleRelayChannelChoice(val channel: Int, val label: String) {
    override fun toString(): String = label
}

internal fun bleRelayDeviceChoices(
    relays: List<EspRelayDevice>,
    selectedId: String,
    missingLabel: String,
    chooseLabel: String,
): List<BleRelayDeviceChoice> {
    val selected = EspBleRelayIds.normalize(selectedId)
    val paired = relays.mapNotNull { relay ->
        val id = EspBleRelayIds.normalize(relay.id)
        if (id.isEmpty()) return@mapNotNull null
        val name = relay.name.trim().ifEmpty { id }
        BleRelayDeviceChoice(id, name)
    }
    val known = if (selected.isNotEmpty() && paired.none { it.id == selected }) {
        paired + BleRelayDeviceChoice(selected, missingLabel.ifBlank { selected })
    } else {
        paired
    }
    return if (selected.isEmpty()) listOf(BleRelayDeviceChoice("", chooseLabel)) + known else known
}

@Composable
internal fun BleRelayActionTargetFields(
    action: AutomationAction.Builtin,
    onChange: (AutomationAction) -> Unit,
    showPulse: Boolean,
) {
    val relays by EspCompanionRepository.bleRelays.collectAsStateWithLifecycle()
    val selectedId = EspBleRelayActionEncoding.idOf(action)
    val channel = EspBleRelayActionEncoding.channelOf(action)
        .takeIf(EspBleRelayChannel::inRange)
        ?: EspBleRelayChannel.DEFAULT
    LaunchedEffect(relays, selectedId, action.intValue) {
        if (selectedId.isNotEmpty() || action.intValue !in 0..1) return@LaunchedEffect
        val id = EspBleRelayIds.normalize(relays.getOrNull(action.intValue)?.id)
        if (id.isEmpty()) return@LaunchedEffect
        onChange(
            action.copy(
                stringValue = EspBleRelayActionEncoding.pack(
                    action.type,
                    id,
                    channel,
                    EspBleRelayActionEncoding.pulseText(action),
                ),
            ),
        )
    }
    val chooseLabel = ruEn("Выберите плату", "Choose a board")
    val missingLabel = ruEn("$selectedId (нет в списке)", "$selectedId (not in the list)")
    val devices = bleRelayDeviceChoices(relays, selectedId, missingLabel, chooseLabel)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        AutomationDropdown(
            label = ruEn("Плата", "Board"),
            value = selectedId,
            options = devices.map { it.id },
            optionLabel = { id -> devices.find { it.id == id }?.label ?: id },
            onValueChange = { id ->
                onChange(
                    action.copy(
                        stringValue = EspBleRelayActionEncoding.pack(
                            action.type,
                            id,
                            channel,
                            EspBleRelayActionEncoding.pulseText(action),
                        ),
                    ),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )
        AutomationDropdown(
            label = ruEn("Канал", "Channel"),
            value = channel,
            options = (0 until EspBleRelayChannel.COUNT).toList(),
            optionLabel = { EspBleRelayChannel.displayNumber(it).toString() },
            onValueChange = { next ->
                onChange(
                    action.copy(
                        stringValue = EspBleRelayActionEncoding.pack(
                            action.type,
                            selectedId,
                            next,
                            EspBleRelayActionEncoding.pulseText(action),
                        ),
                    ),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )
        if (showPulse) {
            AutomationTextField(
                value = EspBleRelayActionEncoding.pulseText(action),
                onValueChange = { ms ->
                    onChange(
                        action.copy(
                            stringValue = EspBleRelayActionEncoding.pack(
                                action.type,
                                selectedId,
                                channel,
                                ms,
                            ),
                        ),
                    )
                },
                label = ruEn("Длительность, мс (пусто = стандарт)", "Duration, ms (empty = default)"),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (action.type == AutomationBuiltinActionType.ESP_BLE_RELAY_SET) {
            AutomationDropdown(
                label = ruEn("Состояние", "State"),
                value = action.boolValue,
                options = listOf(true, false),
                optionLabel = { if (it) ruEn("Замкнуть", "Energize") else ruEn("Разомкнуть", "De-energize") },
                onValueChange = { onChange(action.copy(boolValue = it)) },
            )
        }
    }
}

@Composable
internal fun BleRelaySignalTargetFields(
    relayId: String,
    relayChannel: Int,
    legacyIndex: Int,
    onChange: (id: String, channel: Int) -> Unit,
) {
    val relays by EspCompanionRepository.bleRelays.collectAsStateWithLifecycle()
    val selected = EspBleRelayIds.normalize(relayId).ifEmpty {
        EspBleRelayIds.normalize(relays.getOrNull(legacyIndex)?.id.orEmpty())
    }
    val chooseLabel = ruEn("Выберите плату", "Choose a board")
    val missingLabel = ruEn("$selected (нет в списке)", "$selected (not in the list)")
    val devices = bleRelayDeviceChoices(relays, selected, missingLabel, chooseLabel)
    val channel = relayChannel.takeIf(EspBleRelayChannel::inRange) ?: EspBleRelayChannel.DEFAULT
    LaunchedEffect(selected, channel) {
        if (EspBleRelayIds.normalize(relayId).isEmpty() && selected.isNotEmpty()) {
            onChange(selected, channel)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        AutomationDropdown(
            label = ruEn("Плата", "Board"),
            value = if (EspBleRelayIds.normalize(relayId).isEmpty() && selected.isNotEmpty()) selected else EspBleRelayIds.normalize(relayId),
            options = devices.map { it.id },
            optionLabel = { id -> devices.find { it.id == id }?.label ?: id },
            onValueChange = { onChange(it, channel) },
            modifier = Modifier.fillMaxWidth(),
        )
        AutomationDropdown(
            label = ruEn("Канал", "Channel"),
            value = channel,
            options = (0 until EspBleRelayChannel.COUNT).toList(),
            optionLabel = { EspBleRelayChannel.displayNumber(it).toString() },
            onValueChange = { onChange(EspBleRelayIds.normalize(relayId).ifEmpty { selected }, it) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
