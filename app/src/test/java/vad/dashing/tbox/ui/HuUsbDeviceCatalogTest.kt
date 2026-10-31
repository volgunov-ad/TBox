package vad.dashing.tbox.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class HuUsbDeviceCatalogTest {
    @Test
    fun formatId_padsHexToFourUppercaseChars() {
        assertEquals("1546", HuUsbDeviceCatalog.formatId(0x1546))
        assertEquals("01A7", HuUsbDeviceCatalog.formatId(0x01a7))
        assertEquals("0000", HuUsbDeviceCatalog.formatId(0))
    }

    @Test
    fun classifySegments_usesDeviceClassWhenNonZero() {
        assertEquals(
            listOf(HuUsbClassSegment.Known(HuUsbClassKind.HUB)),
            HuUsbDeviceCatalog.classifySegments(0x09, listOf(0x03)),
        )
        assertEquals(
            listOf(HuUsbClassSegment.RawCode(0x55)),
            HuUsbDeviceCatalog.classifySegments(0x55, emptyList()),
        )
    }

    @Test
    fun classifySegments_usesDistinctInterfacesWhenDeviceClassZero() {
        assertEquals(
            listOf(HuUsbClassSegment.UnknownNoInterfaces),
            HuUsbDeviceCatalog.classifySegments(0x00, emptyList()),
        )
        assertEquals(
            listOf(
                HuUsbClassSegment.Known(HuUsbClassKind.COMM_CDC),
                HuUsbClassSegment.Known(HuUsbClassKind.CDC_DATA),
            ),
            HuUsbDeviceCatalog.classifySegments(0x00, listOf(0x02, 0x0a, 0x0a)),
        )
    }
}
