package vad.dashing.tbox.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import vad.dashing.tbox.R

enum class HuUsbClassKind {
    INTERFACE_DEFINED,
    AUDIO,
    COMM_CDC,
    HID,
    PHYSICAL,
    IMAGE,
    PRINTER,
    MASS_STORAGE,
    HUB,
    CDC_DATA,
    SMART_CARD,
    CONTENT_SECURITY,
    VIDEO,
    PERSONAL_HEALTHCARE,
    AUDIO_VIDEO,
    BILLBOARD,
    USB_TYPE_C_BRIDGE,
    DIAGNOSTIC,
    WIRELESS_CONTROLLER,
    MISC_COMPOSITE,
    APPLICATION_SPECIFIC,
    VENDOR_SPECIFIC,
    UNKNOWN,
}

sealed interface HuUsbClassSegment {
    data object UnknownNoInterfaces : HuUsbClassSegment
    data class Known(val kind: HuUsbClassKind) : HuUsbClassSegment
    data class RawCode(val code: Int) : HuUsbClassSegment
}

data class HuUsbDeviceRow(
    val deviceName: String,
    val productName: String,
    val manufacturer: String?,
    val vendorId: Int,
    val productId: Int,
    val classSegments: List<HuUsbClassSegment>,
) {
    val vidPidHex: String
        get() = "${ HuUsbDeviceCatalog.formatId(vendorId) }:${ HuUsbDeviceCatalog.formatId(productId) }"
}

object HuUsbDeviceCatalog {
    fun formatId(id: Int): String = "%04x".format(id and 0xFFFF).uppercase()

    fun classifySegments(deviceClass: Int, interfaceClasses: List<Int>): List<HuUsbClassSegment> =
        when {
            deviceClass != 0x00 -> listOf(segmentForCode(deviceClass))
            interfaceClasses.isEmpty() -> listOf(HuUsbClassSegment.UnknownNoInterfaces)
            else -> interfaceClasses.distinct().map { segmentForCode(it) }
        }

    fun segmentForCode(code: Int): HuUsbClassSegment = when (code and 0xFF) {
        0x00 -> HuUsbClassSegment.Known(HuUsbClassKind.INTERFACE_DEFINED)
        0x01 -> HuUsbClassSegment.Known(HuUsbClassKind.AUDIO)
        0x02 -> HuUsbClassSegment.Known(HuUsbClassKind.COMM_CDC)
        0x03 -> HuUsbClassSegment.Known(HuUsbClassKind.HID)
        0x05 -> HuUsbClassSegment.Known(HuUsbClassKind.PHYSICAL)
        0x06 -> HuUsbClassSegment.Known(HuUsbClassKind.IMAGE)
        0x07 -> HuUsbClassSegment.Known(HuUsbClassKind.PRINTER)
        0x08 -> HuUsbClassSegment.Known(HuUsbClassKind.MASS_STORAGE)
        0x09 -> HuUsbClassSegment.Known(HuUsbClassKind.HUB)
        0x0a -> HuUsbClassSegment.Known(HuUsbClassKind.CDC_DATA)
        0x0b -> HuUsbClassSegment.Known(HuUsbClassKind.SMART_CARD)
        0x0d -> HuUsbClassSegment.Known(HuUsbClassKind.CONTENT_SECURITY)
        0x0e -> HuUsbClassSegment.Known(HuUsbClassKind.VIDEO)
        0x0f -> HuUsbClassSegment.Known(HuUsbClassKind.PERSONAL_HEALTHCARE)
        0x10 -> HuUsbClassSegment.Known(HuUsbClassKind.AUDIO_VIDEO)
        0x11 -> HuUsbClassSegment.Known(HuUsbClassKind.BILLBOARD)
        0x12 -> HuUsbClassSegment.Known(HuUsbClassKind.USB_TYPE_C_BRIDGE)
        0xdc -> HuUsbClassSegment.Known(HuUsbClassKind.DIAGNOSTIC)
        0xe0 -> HuUsbClassSegment.Known(HuUsbClassKind.WIRELESS_CONTROLLER)
        0xef -> HuUsbClassSegment.Known(HuUsbClassKind.MISC_COMPOSITE)
        0xfe -> HuUsbClassSegment.Known(HuUsbClassKind.APPLICATION_SPECIFIC)
        0xff -> HuUsbClassSegment.Known(HuUsbClassKind.VENDOR_SPECIFIC)
        else -> HuUsbClassSegment.RawCode(code and 0xFF)
    }

    fun readDevices(usbManager: UsbManager?): List<HuUsbDeviceRow> {
        if (usbManager == null) return emptyList()
        return usbManager.deviceList.values
            .map { device -> toRow(device) }
            .sortedWith(
                compareBy<HuUsbDeviceRow> { it.vendorId }
                    .thenBy { it.productId }
                    .thenBy { it.deviceName },
            )
    }

    private fun toRow(device: UsbDevice): HuUsbDeviceRow {
        val productName = runCatching { device.productName?.trim().orEmpty() }.getOrDefault("")
        val manufacturer = runCatching { device.manufacturerName?.trim() }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
        val interfaces = runCatching {
            (0 until device.interfaceCount).map { idx ->
                device.getInterface(idx).interfaceClass
            }
        }.getOrDefault(emptyList())
        return HuUsbDeviceRow(
            deviceName = device.deviceName.orEmpty(),
            productName = productName,
            manufacturer = manufacturer,
            vendorId = device.vendorId,
            productId = device.productId,
            classSegments = classifySegments(device.deviceClass, interfaces),
        )
    }
}

@Composable
fun HuUsbDevicesSection() {
    val context = LocalContext.current
    var rows by remember { mutableStateOf<List<HuUsbDeviceRow>>(emptyList()) }

    DisposableEffect(context) {
        val appContext = context.applicationContext
        val usbManager = appContext.getSystemService(Context.USB_SERVICE) as? UsbManager
        fun publish() {
            rows = runCatching { HuUsbDeviceCatalog.readDevices(usbManager) }
                .getOrDefault(emptyList())
        }
        publish()
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                publish()
            }
        }
        val registered = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                appContext.registerReceiver(receiver, filter)
            }
        }.isSuccess
        onDispose {
            if (registered) {
                runCatching { appContext.unregisterReceiver(receiver) }
            }
        }
    }

    LaunchedEffect(context) {
        val usbManager = context.applicationContext
            .getSystemService(Context.USB_SERVICE) as? UsbManager
            ?: return@LaunchedEffect
        while (isActive) {
            delay(2_000)
            rows = withContext(Dispatchers.IO) {
                runCatching { HuUsbDeviceCatalog.readDevices(usbManager) }
                    .getOrDefault(emptyList())
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        SettingsTitle(stringResource(R.string.info_hu_usb_devices))
        if (rows.isEmpty()) {
            StatusRow(
                label = stringResource(R.string.info_hu_usb_devices),
                value = stringResource(R.string.info_hu_usb_devices_none),
            )
        } else {
            val untitled = stringResource(R.string.info_hu_usb_device_unnamed)
            rows.forEach { row ->
                val title = row.productName.ifBlank {
                    row.deviceName.ifBlank { untitled }
                }
                val classText = buildList {
                    for (segment in row.classSegments) {
                        add(renderClassSegment(segment))
                    }
                }.joinToString(" + ")
                val details = listOfNotNull(
                    row.vidPidHex,
                    row.manufacturer,
                    classText.takeIf { it.isNotBlank() },
                ).joinToString(" · ")
                StatusRow(
                    label = title,
                    value = details,
                    valueMaxLines = 3,
                )
            }
        }
    }
}

@Composable
private fun renderClassSegment(segment: HuUsbClassSegment): String = when (segment) {
    HuUsbClassSegment.UnknownNoInterfaces -> stringResource(R.string.info_hu_usb_class_unknown)
    is HuUsbClassSegment.RawCode ->
        stringResource(R.string.info_hu_usb_class_code, "0x%02x".format(segment.code))
    is HuUsbClassSegment.Known -> when (segment.kind) {
        HuUsbClassKind.INTERFACE_DEFINED -> stringResource(R.string.info_hu_usb_class_per_interface)
        HuUsbClassKind.AUDIO -> stringResource(R.string.info_hu_usb_class_audio)
        HuUsbClassKind.COMM_CDC -> stringResource(R.string.info_hu_usb_class_cdc)
        HuUsbClassKind.HID -> stringResource(R.string.info_hu_usb_class_hid)
        HuUsbClassKind.PHYSICAL -> stringResource(R.string.info_hu_usb_class_physical)
        HuUsbClassKind.IMAGE -> stringResource(R.string.info_hu_usb_class_image)
        HuUsbClassKind.PRINTER -> stringResource(R.string.info_hu_usb_class_printer)
        HuUsbClassKind.MASS_STORAGE -> stringResource(R.string.info_hu_usb_class_storage)
        HuUsbClassKind.HUB -> stringResource(R.string.info_hu_usb_class_hub)
        HuUsbClassKind.CDC_DATA -> stringResource(R.string.info_hu_usb_class_cdc_data)
        HuUsbClassKind.SMART_CARD -> stringResource(R.string.info_hu_usb_class_smart_card)
        HuUsbClassKind.CONTENT_SECURITY -> stringResource(R.string.info_hu_usb_class_security)
        HuUsbClassKind.VIDEO -> stringResource(R.string.info_hu_usb_class_video)
        HuUsbClassKind.PERSONAL_HEALTHCARE -> stringResource(R.string.info_hu_usb_class_healthcare)
        HuUsbClassKind.AUDIO_VIDEO -> stringResource(R.string.info_hu_usb_class_av)
        HuUsbClassKind.BILLBOARD -> stringResource(R.string.info_hu_usb_class_bluetooth)
        HuUsbClassKind.USB_TYPE_C_BRIDGE -> stringResource(R.string.info_hu_usb_class_bridge)
        HuUsbClassKind.DIAGNOSTIC -> stringResource(R.string.info_hu_usb_class_diagnostic)
        HuUsbClassKind.WIRELESS_CONTROLLER -> stringResource(R.string.info_hu_usb_class_wireless)
        HuUsbClassKind.MISC_COMPOSITE -> stringResource(R.string.info_hu_usb_class_composite)
        HuUsbClassKind.APPLICATION_SPECIFIC -> stringResource(R.string.info_hu_usb_class_app_specific)
        HuUsbClassKind.VENDOR_SPECIFIC -> stringResource(R.string.info_hu_usb_class_vendor)
        HuUsbClassKind.UNKNOWN -> stringResource(R.string.info_hu_usb_class_unknown)
    }
}
