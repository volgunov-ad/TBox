package vad.dashing.tbox.radio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class RadioPlayerTest {

    @Test
    fun `radioBand adayo ids map both ways`() {
        assertEquals(RadioBand.AM, RadioBand.fromAdayoBandId(1))
        assertEquals(RadioBand.FM, RadioBand.fromAdayoBandId(2))
        assertNull(RadioBand.fromAdayoBandId(0))
        assertNull(RadioBand.fromAdayoBandId(3))
    }

    @Test
    fun `radioBand mbCan mode values map both ways`() {
        assertEquals(RadioBand.FM, RadioBand.fromMbCanModeValue(1))
        assertEquals(RadioBand.AM, RadioBand.fromMbCanModeValue(2))
        assertNull(RadioBand.fromMbCanModeValue(0))
        assertNull(RadioBand.fromMbCanModeValue(3))
    }

    @Test
    fun `frequencyLabel fm hundredths renders one decimal mhz`() {
        val label = RadioFrequencyFormat.frequencyLabel(RadioBand.FM, 10170, Locale.ENGLISH)
        assertEquals("101.7 MHz", label)
    }

    @Test
    fun `frequencyLabel fm tenths renders one decimal mhz`() {
        val label = RadioFrequencyFormat.frequencyLabel(RadioBand.FM, 1017, Locale.ENGLISH)
        assertEquals("101.7 MHz", label)
    }

    @Test
    fun `frequencyLabel fm whole mhz renders without decimals`() {
        assertEquals("103 MHz", RadioFrequencyFormat.frequencyLabel(RadioBand.FM, 103, Locale.ENGLISH))
        assertEquals("65.9 MHz", RadioFrequencyFormat.frequencyLabel(RadioBand.FM, 65900, Locale.ENGLISH))
    }

    @Test
    fun `frequencyLabel fm out of range falls back to raw number`() {
        assertEquals("433", RadioFrequencyFormat.frequencyLabel(RadioBand.FM, 433, Locale.ENGLISH))
    }

    @Test
    fun `frequencyLabel am khz and tenths of khz`() {
        assertEquals("1053 kHz", RadioFrequencyFormat.frequencyLabel(RadioBand.AM, 1053, Locale.ENGLISH))
        assertEquals("531 kHz", RadioFrequencyFormat.frequencyLabel(RadioBand.AM, 5310, Locale.ENGLISH))
    }

    @Test
    fun `frequencyLabel zero renders dash`() {
        assertEquals("—", RadioFrequencyFormat.frequencyLabel(RadioBand.FM, 0, Locale.ENGLISH))
    }

    @Test
    fun `frequencyLabel russian locale uses cyrillic units`() {
        val label = RadioFrequencyFormat.frequencyLabel(RadioBand.FM, 1017, Locale.forLanguageTag("ru"))
        assertEquals("101,7 МГц", label)
    }

    @Test
    fun `radioPlayerState labels prefer station name`() {
        val named = RadioPlayerState(
            backendAvailable = true,
            band = RadioBand.FM,
            frequencyRaw = 1017,
            isPlaying = true,
            stationName = "Radio",
        )
        assertEquals("Radio", named.trackLabel)
        assertEquals("FM", named.bandLabel)

        val unnamed = named.copy(stationName = null)
        assertEquals(RadioFrequencyFormat.frequencyLabel(RadioBand.FM, 1017), unnamed.trackLabel)
    }

    @Test
    fun `isRadioPackage matches virtual package only`() {
        assertTrue(RadioPlayer.isRadioPackage(RADIO_PLAYER_PACKAGE))
        assertTrue(RadioPlayer.isRadioPackage(" ${RADIO_PLAYER_PACKAGE} "))
        assertFalse(RadioPlayer.isRadioPackage("ru.yandex.music"))
        assertFalse(RadioPlayer.isRadioPackage(null))
        assertFalse(RadioPlayer.isRadioPackage(""))
    }
}
