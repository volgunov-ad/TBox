package vad.dashing.tbox

import org.json.JSONObject
import vad.dashing.tbox.automation.AUTOMATION_ESP_BLE_BTN_MAX
import vad.dashing.tbox.automation.AUTOMATION_ESP_BLE_BTN_MIN
import vad.dashing.tbox.esp.normalizeEspBleMac

/**
 * One physical button bound to a dashboard tile (main-screen or floating panel).
 * Duplicates single/double taps while the tile is on screen.
 */
sealed class WidgetButtonBinding {
    data class HardKey(val keyCode: Int) : WidgetButtonBinding()

    data class EspBle(
        val mac: String,
        val btn: Int = 1,
    ) : WidgetButtonBinding()

    data class EspGpio(val channel: Int) : WidgetButtonBinding()
}

const val WIDGET_BUTTON_BINDING_TYPE_HARD_KEY = "hard_key"
const val WIDGET_BUTTON_BINDING_TYPE_ESP_BLE = "esp_ble"
const val WIDGET_BUTTON_BINDING_TYPE_ESP_GPIO = "esp_gpio"

const val WIDGET_BUTTON_BINDING_ESP_GPIO_MIN = 0
const val WIDGET_BUTTON_BINDING_ESP_GPIO_MAX = 3

fun normalizeWidgetButtonBinding(raw: WidgetButtonBinding?): WidgetButtonBinding? =
    when (raw) {
        null -> null
        is WidgetButtonBinding.HardKey ->
            raw.takeIf { it.keyCode != 0 }
        is WidgetButtonBinding.EspBle -> {
            val mac = normalizeEspBleMac(raw.mac)
            val btn = raw.btn
            if (mac.isBlank() || btn !in AUTOMATION_ESP_BLE_BTN_MIN..AUTOMATION_ESP_BLE_BTN_MAX) {
                null
            } else {
                WidgetButtonBinding.EspBle(mac = mac, btn = btn)
            }
        }
        is WidgetButtonBinding.EspGpio ->
            raw.takeIf {
                it.channel in WIDGET_BUTTON_BINDING_ESP_GPIO_MIN..WIDGET_BUTTON_BINDING_ESP_GPIO_MAX
            }
    }

fun encodeWidgetButtonBinding(binding: WidgetButtonBinding): JSONObject =
    when (binding) {
        is WidgetButtonBinding.HardKey -> JSONObject()
            .put("type", WIDGET_BUTTON_BINDING_TYPE_HARD_KEY)
            .put("keyCode", binding.keyCode)
        is WidgetButtonBinding.EspBle -> JSONObject()
            .put("type", WIDGET_BUTTON_BINDING_TYPE_ESP_BLE)
            .put("mac", binding.mac)
            .put("btn", binding.btn)
        is WidgetButtonBinding.EspGpio -> JSONObject()
            .put("type", WIDGET_BUTTON_BINDING_TYPE_ESP_GPIO)
            .put("ch", binding.channel)
    }

fun decodeWidgetButtonBinding(raw: JSONObject?): WidgetButtonBinding? {
    if (raw == null) return null
    val type = raw.optString("type", "").trim().lowercase()
    return normalizeWidgetButtonBinding(
        when (type) {
            WIDGET_BUTTON_BINDING_TYPE_HARD_KEY ->
                WidgetButtonBinding.HardKey(keyCode = raw.optInt("keyCode", 0))
            WIDGET_BUTTON_BINDING_TYPE_ESP_BLE ->
                WidgetButtonBinding.EspBle(
                    mac = raw.optString("mac", ""),
                    btn = raw.optInt("btn", 1),
                )
            WIDGET_BUTTON_BINDING_TYPE_ESP_GPIO ->
                WidgetButtonBinding.EspGpio(channel = raw.optInt("ch", -1))
            else -> null
        },
    )
}

/**
 * Tiles with multiple independent hit targets cannot bind one physical button
 * to a single unambiguous action.
 */
fun supportsWidgetButtonBinding(dataKey: String): Boolean {
    if (dataKey.isBlank()) return false
    if (isMusicWidgetDataKey(dataKey)) return false
    return when (dataKey) {
        "frontLeftSeatHeatVentWidget",
        "frontRightSeatHeatVentWidget",
        HVAC_BLOW_MODE_PANEL_WIDGET_HORIZONTAL_DATA_KEY,
        HVAC_BLOW_MODE_PANEL_WIDGET_VERTICAL_DATA_KEY,
        MAIN_SCREEN_PAGE_SELECTOR_WIDGET_HORIZONTAL_DATA_KEY,
        MAIN_SCREEN_PAGE_SELECTOR_WIDGET_VERTICAL_DATA_KEY,
        -> false
        else -> true
    }
}
