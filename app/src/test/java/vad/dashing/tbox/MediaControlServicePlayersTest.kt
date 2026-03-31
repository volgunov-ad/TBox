package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaControlServicePlayersTest {

    @Test
    fun fromPackage_resolvesAndroidBluetoothPackage() {
        assertEquals(
            SupportedMediaPlayer.BLUETOOTH_PHONE,
            SupportedMediaPlayer.fromPackage("com.android.bluetooth")
        )
    }

    @Test
    fun fromPackage_yandexRadioAlias_mapsToYandexMobileFmradio() {
        assertEquals(
            SupportedMediaPlayer.YANDEX_RADIO,
            SupportedMediaPlayer.fromPackage("ru.yandex.radio")
        )
    }

    @Test
    fun normalizeMediaPlayerPackages_keepsRecognizedPackagesOnly() {
        val normalized = normalizeMediaPlayerPackages(
            listOf(
                "ru.yandex.music",
                "com.unknown.oem.player",
                "ru.yandex.radio"
            )
        )

        assertEquals(
            setOf("ru.yandex.music", "ru.yandex.mobile.fmradio"),
            normalized
        )
    }
}
