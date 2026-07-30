package vad.dashing.tbox.usbgnss

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager

/**
 * Candidate USB device for direct NMEA GNSS (user-selected).
 *
 * [stableId] is persisted as `vid:pid` or `vid:pid:serial` (hex, lowercase).
 */
data class UsbGnssDevice(
    val stableId: String,
    val label: String,
    val vendorId: Int,
    val productId: Int,
    val deviceName: String,
    val serial: String?,
) {
    override fun toString(): String = label
}

object UsbGnssDeviceIds {
    const val ESPRESSIF_VID = 0x303A

    /** Common USB-UART bridge vendors. */
    val UART_BRIDGE_VIDS: Set<Int> = setOf(
        0x1A86, // QinHeng CH340/CH341
        0x10C4, // Silicon Labs CP210x
        0x0403, // FTDI
        0x067B, // Prolific
    )

    val DEFAULT_BAUD = 115_200
    val BAUD_OPTIONS: List<Int> = listOf(
        9_600, 19_200, 38_400, 57_600, 115_200, 230_400, 460_800,
    )

    fun formatStableId(vendorId: Int, productId: Int, serial: String?): String {
        val base = "%04x:%04x".format(vendorId and 0xFFFF, productId and 0xFFFF)
        val ser = serial?.trim().orEmpty()
        return if (ser.isEmpty()) base else "$base:$ser"
    }

    data class ParsedId(
        val vendorId: Int,
        val productId: Int,
        val serial: String?,
    )

    fun parseStableId(raw: String?): ParsedId? {
        val s = raw?.trim().orEmpty()
        if (s.isEmpty()) return null
        val parts = s.split(':')
        if (parts.size < 2) return null
        val vid = parts[0].toIntOrNull(16) ?: return null
        val pid = parts[1].toIntOrNull(16) ?: return null
        val serial = parts.getOrNull(2)?.takeIf { it.isNotBlank() }
        return ParsedId(vid, pid, serial)
    }

    /**
     * Pure eligibility check (unit-testable without [UsbDevice]).
     *
     * @param looksLikeNetworkRndis true when device exposes CDC networking / RNDIS-like interfaces
     * @param hasCdcData CDC DATA (class 10) present
     * @param hasBulkIn at least one bulk IN endpoint
     * @param hasBulkInOut bulk IN and OUT on some interface (typical UART bridge)
     */
    fun isCandidate(
        vendorId: Int,
        looksLikeNetworkRndis: Boolean,
        hasCdcData: Boolean,
        hasBulkIn: Boolean,
        hasBulkInOut: Boolean,
    ): Boolean {
        if (vendorId == ESPRESSIF_VID) return false
        if (looksLikeNetworkRndis) return false
        if (!hasBulkIn) return false
        if (hasCdcData) return true
        if (vendorId in UART_BRIDGE_VIDS && hasBulkInOut) return true
        return false
    }

    fun matchesStableId(device: UsbDevice, stableId: String): Boolean {
        val actualSerial = runCatching { device.serialNumber }.getOrNull()
        return matchesStableIdParts(
            vendorId = device.vendorId,
            productId = device.productId,
            actualSerial = actualSerial,
            stableId = stableId,
        )
    }

    /**
     * Match persisted id to a USB device.
     * If the saved id includes a serial but the host cannot read [actualSerial] yet
     * (typical before USB permission), fall back to vid:pid so reconnect/boot can proceed.
     * If serial is readable and differs, reject (two adapters with same vid:pid).
     */
    fun matchesStableIdParts(
        vendorId: Int,
        productId: Int,
        actualSerial: String?,
        stableId: String,
    ): Boolean {
        val parsed = parseStableId(stableId) ?: return false
        if (vendorId != parsed.vendorId || productId != parsed.productId) {
            return false
        }
        val wantSerial = parsed.serial
        if (wantSerial.isNullOrBlank()) return true
        val actual = actualSerial?.trim().orEmpty()
        if (actual.isEmpty()) return true // soft match until permission unlocks serial
        return actual == wantSerial
    }

    fun labelFor(device: UsbDevice): String {
        val name = device.productName?.trim().orEmpty()
        val id = "%04X:%04X".format(device.vendorId and 0xFFFF, device.productId and 0xFFFF)
        val bridge = when (device.vendorId) {
            0x1A86 -> "CH340"
            0x10C4 -> "CP210x"
            0x0403 -> "FTDI"
            0x067B -> "PL2303"
            else -> null
        }
        return when {
            name.isNotEmpty() && bridge != null -> "$name ($bridge $id)"
            name.isNotEmpty() -> "$name ($id)"
            bridge != null -> "$bridge $id"
            else -> "USB $id"
        }
    }
}

object UsbGnssDeviceScanner {
    fun isEspressifPresent(usbManager: UsbManager): Boolean =
        usbManager.deviceList.values.any { it.vendorId == UsbGnssDeviceIds.ESPRESSIF_VID }

    fun listCandidates(usbManager: UsbManager): List<UsbGnssDevice> {
        return usbManager.deviceList.values
            .filter { isEligible(it) }
            .map { toUsbGnssDevice(it) }
            .sortedBy { it.label.lowercase() }
    }

    fun findByStableId(usbManager: UsbManager, stableId: String): UsbDevice? {
        if (stableId.isBlank()) return null
        return usbManager.deviceList.values.firstOrNull {
            UsbGnssDeviceIds.matchesStableId(it, stableId) && isEligible(it)
        }
    }

    fun isEligible(device: UsbDevice): Boolean {
        val caps = inspect(device)
        return UsbGnssDeviceIds.isCandidate(
            vendorId = device.vendorId,
            looksLikeNetworkRndis = caps.looksLikeNetworkRndis,
            hasCdcData = caps.hasCdcData,
            hasBulkIn = caps.hasBulkIn,
            hasBulkInOut = caps.hasBulkInOut,
        )
    }

    fun toUsbGnssDevice(device: UsbDevice): UsbGnssDevice {
        val serial = runCatching { device.serialNumber }.getOrNull()
        return UsbGnssDevice(
            stableId = UsbGnssDeviceIds.formatStableId(device.vendorId, device.productId, serial),
            label = UsbGnssDeviceIds.labelFor(device),
            vendorId = device.vendorId,
            productId = device.productId,
            deviceName = device.deviceName,
            serial = serial,
        )
    }

    data class Caps(
        val looksLikeNetworkRndis: Boolean,
        val hasCdcData: Boolean,
        val hasBulkIn: Boolean,
        val hasBulkInOut: Boolean,
    )

    fun inspect(device: UsbDevice): Caps {
        var hasCdcData = false
        var hasBulkIn = false
        var hasBulkInOut = false
        var looksLikeNetworkRndis = false
        for (i in 0 until device.interfaceCount) {
            val intf = device.getInterface(i)
            if (intf.interfaceClass == UsbConstants.USB_CLASS_CDC_DATA) {
                hasCdcData = true
            }
            // CDC COMM subclasses used by Ethernet / RNDIS-style networking.
            if (intf.interfaceClass == UsbConstants.USB_CLASS_COMM) {
                when (intf.interfaceSubclass) {
                    0x06, // ECM
                    0x0D, // NCM
                    0x0E, // MBIM
                    0x02, // Abstract Control — keep; not network by itself
                    -> {
                        if (intf.interfaceSubclass == 0x06 ||
                            intf.interfaceSubclass == 0x0D ||
                            intf.interfaceSubclass == 0x0E
                        ) {
                            looksLikeNetworkRndis = true
                        }
                    }
                }
            }
            if (intf.interfaceClass == UsbConstants.USB_CLASS_WIRELESS_CONTROLLER) {
                looksLikeNetworkRndis = true
            }
            var inEp = false
            var outEp = false
            for (e in 0 until intf.endpointCount) {
                val ep = intf.getEndpoint(e)
                if (ep.type != UsbConstants.USB_ENDPOINT_XFER_BULK) continue
                if (ep.direction == UsbConstants.USB_DIR_IN) {
                    inEp = true
                    hasBulkIn = true
                }
                if (ep.direction == UsbConstants.USB_DIR_OUT) outEp = true
            }
            if (inEp && outEp) hasBulkInOut = true
        }
        return Caps(
            looksLikeNetworkRndis = looksLikeNetworkRndis,
            hasCdcData = hasCdcData,
            hasBulkIn = hasBulkIn,
            hasBulkInOut = hasBulkInOut,
        )
    }
}
