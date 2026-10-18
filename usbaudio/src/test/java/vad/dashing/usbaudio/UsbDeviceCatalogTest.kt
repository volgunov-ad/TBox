package vad.dashing.usbaudio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsbDeviceCatalogTest {
    @Test
    fun describeKnownDeviceClasses() {
        assertEquals("Аудио", UsbDeviceCatalog.describe(0x01, emptyList()))
        assertEquals("HID (клавиатура/мышь)", UsbDeviceCatalog.describe(0x03, emptyList()))
        assertEquals("Накопитель", UsbDeviceCatalog.describe(0x08, emptyList()))
        assertEquals("USB-хаб", UsbDeviceCatalog.describe(0x09, emptyList()))
        assertEquals("Видео", UsbDeviceCatalog.describe(0x0e, emptyList()))
        assertEquals("Составное устройство", UsbDeviceCatalog.describe(0xef, emptyList()))
        assertEquals("Фирменный (vendor-specific)", UsbDeviceCatalog.describe(0xff, emptyList()))
        assertEquals("Класс 0x55", UsbDeviceCatalog.describe(0x55, emptyList()))
    }

    @Test
    fun describeCompositeFallsBackToInterfaceClasses() {
        assertEquals("Неизвестный класс", UsbDeviceCatalog.describe(0x00, emptyList()))
        assertEquals("Аудио", UsbDeviceCatalog.describe(0x00, listOf(0x01, 0x01, 0x01)))
        assertEquals("Связь (CDC) + CDC-данные", UsbDeviceCatalog.describe(0x00, listOf(0x02, 0x0a)))
    }

    @Test
    fun isAudioByDeviceOrInterfaceClass() {
        assertTrue(UsbDeviceCatalog.isAudio(0x01, emptyList()))
        assertTrue(UsbDeviceCatalog.isAudio(0x00, listOf(0x01, 0x02)))
        assertTrue(UsbDeviceCatalog.isAudio(0xef, listOf(0x0e, 0x01)))
        assertFalse(UsbDeviceCatalog.isAudio(0x00, listOf(0x09)))
        assertFalse(UsbDeviceCatalog.isAudio(0x08, listOf(0x06)))
    }

    @Test
    fun formatIdPadsHexUppercase() {
        assertEquals("1B3F", UsbDeviceCatalog.formatId(0x1b3f))
        assertEquals("0D8C", UsbDeviceCatalog.formatId(0x0d8c))
        assertEquals("0000", UsbDeviceCatalog.formatId(0))
    }
}
