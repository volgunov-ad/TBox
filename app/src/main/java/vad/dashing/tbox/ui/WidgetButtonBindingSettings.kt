package vad.dashing.tbox.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import vad.dashing.tbox.R
import vad.dashing.tbox.WIDGET_BUTTON_BINDING_ESP_GPIO_MAX
import vad.dashing.tbox.WIDGET_BUTTON_BINDING_ESP_GPIO_MIN
import vad.dashing.tbox.WidgetButtonBinding
import vad.dashing.tbox.automation.AUTOMATION_ESP_BLE_BTN_MAX
import vad.dashing.tbox.automation.AUTOMATION_ESP_BLE_BTN_MIN
import vad.dashing.tbox.supportsWidgetButtonBinding
import vad.dashing.tbox.ui.theme.tboxButton
import vad.dashing.tbox.ui.theme.tboxCaption

internal enum class WidgetButtonBindingSource {
    NONE,
    HARD_KEY,
    ESP_BLE,
    ESP_GPIO,
}

/**
 * UI source from the draft binding. Must not use [vad.dashing.tbox.normalizeWidgetButtonBinding]:
 * that drops incomplete Shelly drafts (blank MAC) and makes the source appear stuck on «Нет».
 */
internal fun widgetButtonBindingUiSource(raw: WidgetButtonBinding?): WidgetButtonBindingSource =
    when (raw) {
        null -> WidgetButtonBindingSource.NONE
        is WidgetButtonBinding.HardKey -> WidgetButtonBindingSource.HARD_KEY
        is WidgetButtonBinding.EspBle -> WidgetButtonBindingSource.ESP_BLE
        is WidgetButtonBinding.EspGpio -> WidgetButtonBindingSource.ESP_GPIO
    }

@Composable
internal fun WidgetButtonBindingSettingsSection(
    state: WidgetSelectionDialogState,
    modifier: Modifier = Modifier,
) {
    if (!supportsWidgetButtonBinding(state.selectedDataKey)) return
    // Draft as stored in dialog state — incomplete EspBle (empty MAC) must stay visible.
    val draft = state.buttonBinding
    val source = widgetButtonBindingUiSource(draft)
    val sourceNone = stringResource(R.string.widget_button_binding_source_none)
    val sourceHardKey = stringResource(R.string.widget_button_binding_source_hard_key)
    val sourceEspBle = stringResource(R.string.widget_button_binding_source_esp_ble)
    val sourceEspGpio = stringResource(R.string.widget_button_binding_source_esp_gpio)
    val bleBtnLabels = (AUTOMATION_ESP_BLE_BTN_MIN..AUTOMATION_ESP_BLE_BTN_MAX).associateWith {
        stringResource(R.string.widget_button_binding_ble_btn_option, it)
    }
    val gpioLabels = (
        WIDGET_BUTTON_BINDING_ESP_GPIO_MIN..WIDGET_BUTTON_BINDING_ESP_GPIO_MAX
        ).associateWith {
        stringResource(R.string.widget_button_binding_gpio_option, it)
    }
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.widget_button_binding_title),
            style = MaterialTheme.typography.tboxButton,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        AutomationDropdown(
            label = stringResource(R.string.widget_button_binding_source_label),
            value = source,
            options = WidgetButtonBindingSource.entries,
            optionLabel = { src ->
                when (src) {
                    WidgetButtonBindingSource.NONE -> sourceNone
                    WidgetButtonBindingSource.HARD_KEY -> sourceHardKey
                    WidgetButtonBindingSource.ESP_BLE -> sourceEspBle
                    WidgetButtonBindingSource.ESP_GPIO -> sourceEspGpio
                }
            },
            onValueChange = { next ->
                state.buttonBinding = when (next) {
                    WidgetButtonBindingSource.NONE -> null
                    WidgetButtonBindingSource.HARD_KEY ->
                        WidgetButtonBinding.HardKey(
                            keyCode = (draft as? WidgetButtonBinding.HardKey)?.keyCode ?: 115,
                        )
                    WidgetButtonBindingSource.ESP_BLE ->
                        WidgetButtonBinding.EspBle(
                            mac = (draft as? WidgetButtonBinding.EspBle)?.mac.orEmpty(),
                            btn = (draft as? WidgetButtonBinding.EspBle)?.btn ?: 1,
                        )
                    WidgetButtonBindingSource.ESP_GPIO ->
                        WidgetButtonBinding.EspGpio(
                            channel = (draft as? WidgetButtonBinding.EspGpio)?.channel ?: 0,
                        )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        when (val current = draft) {
            is WidgetButtonBinding.HardKey -> {
                val keyOptions = remember(current.keyCode) {
                    if (current.keyCode in HARD_KEY_PICKER_CODES) {
                        HARD_KEY_PICKER_CODES
                    } else {
                        HARD_KEY_PICKER_CODES + current.keyCode
                    }
                }
                AutomationDropdown(
                    label = stringResource(R.string.widget_button_binding_key_picker_label),
                    value = current.keyCode,
                    options = keyOptions,
                    optionLabel = ::hardKeyCodeLabel,
                    onValueChange = {
                        state.buttonBinding = WidgetButtonBinding.HardKey(keyCode = it)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
                AutomationIntField(
                    label = stringResource(R.string.widget_button_binding_key_code_label),
                    value = current.keyCode,
                    onValueChange = { raw ->
                        if (raw == Int.MIN_VALUE) return@AutomationIntField
                        state.buttonBinding = WidgetButtonBinding.HardKey(keyCode = raw)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
            }
            is WidgetButtonBinding.EspBle -> {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    AutomationEspBleDevicePicker(
                        label = stringResource(R.string.widget_button_binding_ble_device_label),
                        mac = current.mac,
                        onValueChange = {
                            state.buttonBinding = current.copy(mac = it)
                        },
                    )
                }
                AutomationDropdown(
                    label = stringResource(R.string.widget_button_binding_ble_btn_label),
                    value = current.btn.coerceIn(
                        AUTOMATION_ESP_BLE_BTN_MIN,
                        AUTOMATION_ESP_BLE_BTN_MAX,
                    ),
                    options = (AUTOMATION_ESP_BLE_BTN_MIN..AUTOMATION_ESP_BLE_BTN_MAX).toList(),
                    optionLabel = { btn -> bleBtnLabels.getValue(btn) },
                    onValueChange = { state.buttonBinding = current.copy(btn = it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
            }
            is WidgetButtonBinding.EspGpio -> {
                AutomationDropdown(
                    label = stringResource(R.string.widget_button_binding_gpio_label),
                    value = current.channel.coerceIn(
                        WIDGET_BUTTON_BINDING_ESP_GPIO_MIN,
                        WIDGET_BUTTON_BINDING_ESP_GPIO_MAX,
                    ),
                    options = (
                        WIDGET_BUTTON_BINDING_ESP_GPIO_MIN..WIDGET_BUTTON_BINDING_ESP_GPIO_MAX
                        ).toList(),
                    optionLabel = { ch -> gpioLabels.getValue(ch) },
                    onValueChange = { state.buttonBinding = current.copy(channel = it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
            }
            null -> Unit
        }
        Text(
            text = stringResource(R.string.widget_button_binding_hint),
            style = MaterialTheme.typography.tboxCaption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, bottom = 8.dp),
        )
    }
}
