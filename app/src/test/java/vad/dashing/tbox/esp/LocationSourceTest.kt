package vad.dashing.tbox.esp

import org.junit.Assert.assertEquals
import org.junit.Test

class LocationSourceTest {

    @Test
    fun fromStorage() {
        assertEquals(LocationSource.TBOX, LocationSource.fromStorage("TBOX"))
        assertEquals(LocationSource.ESP32, LocationSource.fromStorage("esp32"))
        assertEquals(LocationSource.ANDROID, LocationSource.fromStorage("Android"))
        assertEquals(LocationSource.USB, LocationSource.fromStorage("usb"))
        assertEquals(LocationSource.TBOX, LocationSource.fromStorage(null))
        assertEquals(LocationSource.TBOX, LocationSource.fromStorage(""))
    }

    @Test
    fun fromLegacyGetLocData() {
        assertEquals(LocationSource.TBOX, LocationSource.fromLegacyGetLocData(true))
        assertEquals(LocationSource.ANDROID, LocationSource.fromLegacyGetLocData(false))
        assertEquals(LocationSource.TBOX, LocationSource.fromLegacyGetLocData(null))
    }
}
