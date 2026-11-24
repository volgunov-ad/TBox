package vad.dashing.tbox.esp

/**
 * One bit of the existing `relayDevSet` mask.
 *
 * Channel 0 is mask bit 0 (value 1). That is the only coil the X1 relay firmware
 * applies today (`body[0]` / `body[1]` bit 0). The tile and automation pickers
 * show it as channel 1. Channels 1..7 are the next mask bits, stored now so a
 * later board can use them without a new config format.
 */
object EspBleRelayChannel {
    const val COUNT = 8
    const val DEFAULT = 0

    fun inRange(channel: Int): Boolean = channel in 0 until COUNT

    fun maskBit(channel: Int): Int {
        require(inRange(channel)) { "channel $channel" }
        return 1 shl channel
    }

    fun isClosed(mask: Int, channel: Int): Boolean =
        inRange(channel) && (mask and maskBit(channel)) != 0

    /** 1-based label. Stored 0 is shown as channel 1. */
    fun displayNumber(channel: Int): Int = channel + 1

    /**
     * Next full latch mask after setting one channel.
     * A missing report is not mask 0: writing from 0 would clear every other channel.
     */
    fun nextLatchMask(current: Int?, channel: Int, on: Boolean): Int? {
        if (!inRange(channel) || current == null) return null
        val bit = maskBit(channel)
        return if (on) current or bit else current and bit.inv()
    }
}

object EspBleRelayIds {
    private val hex8 = Regex("^[0-9a-fA-F]{8}$")

    fun normalize(raw: String?): String {
        val text = raw?.trim()?.lowercase().orEmpty()
        return if (hex8.matches(text)) text else ""
    }
}

fun isEspBleRelayWidgetDataKey(dataKey: String): Boolean =
    dataKey == "espBleRelay0" || dataKey == "espBleRelay1"

/** Old tiles named the board by slot. Forget shifts that slot, so it is only a migration hint. */
fun espBleRelayLegacyIndex(dataKey: String): Int? = when (dataKey) {
    "espBleRelay0" -> 0
    "espBleRelay1" -> 1
    else -> null
}

fun espBleRelayValueKey(id: String, channel: Int): String =
    "espBleRelayId:${EspBleRelayIds.normalize(id)}:$channel"

const val ESP_BLE_RELAY_UNSET_VALUE_KEY = "espBleRelayUnset"
