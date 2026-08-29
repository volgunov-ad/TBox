package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import vad.dashing.tbox.mbcan.MbCanKnownAudioPropertyId

class PlatformAudioDomainTest {
    @Test fun a10Streams_matchStockVolumeData() {
        assertEquals(3, PlatformAudioDomain.a10StreamType(PlatformAudioDomain.VolumeChannel.Media))
        assertEquals(6, PlatformAudioDomain.a10StreamType(PlatformAudioDomain.VolumeChannel.Phone))
        assertEquals(7, PlatformAudioDomain.a10StreamType(PlatformAudioDomain.VolumeChannel.Navi))
        assertEquals(9, PlatformAudioDomain.a10StreamType(PlatformAudioDomain.VolumeChannel.Voice))
    }

    @Test fun a9Usages_matchStockMbAudioManager() {
        assertEquals(1, PlatformAudioDomain.a9Usage(PlatformAudioDomain.VolumeChannel.Media))
        assertEquals(2, PlatformAudioDomain.a9Usage(PlatformAudioDomain.VolumeChannel.Phone))
        assertEquals(12, PlatformAudioDomain.a9Usage(PlatformAudioDomain.VolumeChannel.Navi))
        assertEquals(16, PlatformAudioDomain.a9Usage(PlatformAudioDomain.VolumeChannel.Voice))
    }

    @Test fun sanitizeVolume_clampsToStockRanges() {
        assertEquals(0, PlatformAudioDomain.sanitizeVolume(PlatformAudioDomain.VolumeChannel.Media, 0))
        assertEquals(31, PlatformAudioDomain.sanitizeVolume(PlatformAudioDomain.VolumeChannel.Media, 40))
        assertEquals(1, PlatformAudioDomain.sanitizeVolume(PlatformAudioDomain.VolumeChannel.Phone, 0))
        assertEquals(2, PlatformAudioDomain.sanitizeVolume(PlatformAudioDomain.VolumeChannel.Voice, 0))
        assertEquals(10, PlatformAudioDomain.sanitizeVolume(PlatformAudioDomain.VolumeChannel.Navi, 12))
        assertNull(PlatformAudioDomain.sanitizeVolume(PlatformAudioDomain.VolumeChannel.Media, -1))
    }

    @Test fun nextVolume_stepsFromLiveReading() {
        val media = PlatformAudioDomain.VolumeChannel.Media
        assertEquals(11, PlatformAudioDomain.nextVolume(media, 10, increase = true))
        assertEquals(9, PlatformAudioDomain.nextVolume(media, 10, increase = false))
        var live = 10
        repeat(3) {
            live = PlatformAudioDomain.nextVolume(media, live, increase = true)!!
        }
        assertEquals(13, live)
        assertEquals(31, PlatformAudioDomain.nextVolume(media, 31, increase = true))
        assertNull(PlatformAudioDomain.nextVolume(media, 0, increase = false))
        assertEquals(1, PlatformAudioDomain.nextVolume(media, null, increase = true))
    }

    @Test fun headrest_mapsA9ZeroBasedToSharedUi() {
        assertEquals(PlatformAudioDomain.HEADREST_OFF, PlatformAudioDomain.decodeHeadrestMbCan(0))
        assertEquals(PlatformAudioDomain.HEADREST_ONLY, PlatformAudioDomain.decodeHeadrestMbCan(1))
        assertEquals(PlatformAudioDomain.HEADREST_ASSIST, PlatformAudioDomain.decodeHeadrestMbCan(2))
        assertEquals(0, PlatformAudioDomain.encodeHeadrestMbCan(PlatformAudioDomain.HEADREST_OFF))
        assertEquals(1, PlatformAudioDomain.encodeHeadrestMbCan(PlatformAudioDomain.HEADREST_ONLY))
        assertEquals(2, PlatformAudioDomain.encodeHeadrestMbCan(PlatformAudioDomain.HEADREST_ASSIST))
        assertNull(PlatformAudioDomain.decodeHeadrestMbCan(3))
        assertEquals(PlatformAudioDomain.HEADREST_ONLY, PlatformAudioDomain.decodeHeadrestVhal(1))
        assertNull(PlatformAudioDomain.decodeHeadrestVhal(0))
        assertEquals(37, MbCanKnownAudioPropertyId.HEADREST_SPEAKER)
    }
}
