package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaControlServicePlayersTest {

    @Test
    fun fromPackage_doesNotResolveWtLocalMultimediaWhenEntryDisabled() {
        assertEquals(null, SupportedMediaPlayer.fromPackage("com.wt.multimedia.local"))
    }

    @Test
    fun fromPackage_resolvesBluetoothPhonePackage() {
        assertEquals(
            SupportedMediaPlayer.BLUETOOTH_PHONE,
            SupportedMediaPlayer.fromPackage("com.android.bluetooth")
        )
    }

    @Test
    fun fromPackage_returnsNullForVendorBluetoothStackPackages() {
        listOf("com.wt.wtbtservice", "com.nforetek.bt", "com.wt.openbt.server").forEach { pkg ->
            assertEquals(null, SupportedMediaPlayer.fromPackage(pkg))
        }
    }

    @Test
    fun fromPackage_doesNotResolvePlatform3WhenWtLocalEntryDisabled() {
        assertEquals(null, SupportedMediaPlayer.fromPackage("com.wt.multimedia.platform3"))
    }

    @Test
    fun normalizeMediaPlayerPackages_includesBluetoothAndYandexRadioAlias() {
        val normalized = normalizeMediaPlayerPackages(
            listOf("ru.yandex.radio", "com.android.bluetooth")
        )

        assertTrue("com.android.bluetooth" in normalized)
        assertTrue("ru.yandex.mobile.fmradio" in normalized)
    }
}
