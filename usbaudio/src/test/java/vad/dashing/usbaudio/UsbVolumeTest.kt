package vad.dashing.usbaudio

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UsbVolumeTest {

    private val cards = """
         0 [atcsndcard     ]: atc-snd-card - atc-snd-card
                           atc-snd-card
         1 [Device         ]: USB-Audio - USB PnP Sound Device
                           C-Media Electronics Inc. USB PnP Sound Device at usb-13030000.usb-1.2, full spe
    """.trimIndent()

    private val tinymixList = """
        Mixer name: 'USB PnP Sound Device'
        Number of controls: 9
        ctl	type	num	name                                     value

        0	INT	2	Playback Channel Map                     0 0
        1	INT	1	Capture Channel Map                      0
        2	BOOL	1	Mic Playback Switch                     Off
        3	INT	1	Mic Playback Volume                      0
        4	BOOL	1	Speaker Playback Switch                 On
        5	INT	2	Speaker Playback Volume                  150 150
        6	BOOL	1	Mic Capture Switch                      On
        7	INT	1	Mic Capture Volume                       0
        8	BOOL	1	Auto Gain Control                       On
    """.trimIndent()

    @Test
    fun `usb card number from proc cards`() {
        assertEquals(1, UsbVolume.usbCardNumber(cards))
    }

    @Test
    fun `no usb audio card returns null`() {
        assertNull(UsbVolume.usbCardNumber(" 0 [atcsndcard]: atc-snd-card - atc-snd-card\n"))
        assertNull(UsbVolume.usbCardNumber(""))
    }

    @Test
    fun `parse control picks speaker not mic`() {
        assertEquals(5 to 150, UsbVolume.parseControl(tinymixList))
    }

    @Test
    fun `parse control clamped max value`() {
        assertEquals(5 to 151, UsbVolume.parseControl(tinymixList.replace("150 150", "151 151")))
    }

    @Test
    fun `parse control missing returns null`() {
        assertNull(UsbVolume.parseControl("Mixer name: 'USB PnP Sound Device'\n"))
    }

    @Test
    fun `approx db endpoints and known points`() {
        assertEquals(-28.375, UsbVolume.approxDb(0), 0.01)
        assertEquals(-0.0625, UsbVolume.approxDb(151), 0.01)
        assertEquals(-0.25, UsbVolume.approxDb(150), 0.01)
        assertEquals(-19.0, UsbVolume.approxDb(50), 0.01)
    }

    @Test
    fun `approx db clamps out of range`() {
        assertEquals(UsbVolume.approxDb(0), UsbVolume.approxDb(-10), abs(0.0))
        assertEquals(UsbVolume.approxDb(151), UsbVolume.approxDb(999), abs(0.0))
    }
}
