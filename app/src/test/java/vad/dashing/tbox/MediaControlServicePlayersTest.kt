package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaControlServicePlayersTest {

    @Test
    fun fromPackage_resolvesWtLocalMultimediaPlayer() {
        val resolved = SupportedMediaPlayer.fromPackage("com.wt.multimedia.local")

        assertEquals(SupportedMediaPlayer.WT_LOCAL_MULTIMEDIA, resolved)
    }

    @Test
    fun normalizeMediaPlayerPackages_includesWtLocalMultimediaAndAlias() {
        val normalized = normalizeMediaPlayerPackages(
            listOf(
                " COM.WT.MULTIMEDIA.LOCAL ",
                "ru.yandex.radio"
            )
        )

        assertTrue("com.wt.multimedia.local" in normalized)
        assertTrue("ru.yandex.mobile.fmradio" in normalized)
    }
}
