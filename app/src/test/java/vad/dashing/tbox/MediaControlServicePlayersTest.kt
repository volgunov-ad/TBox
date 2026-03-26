package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaControlServicePlayersTest {

    @Test
    fun fromPackage_resolvesBluetoothAliasesToBluetoothPhonePlayer() {
        val packages = listOf(
            "com.android.bluetooth",
            "com.wt.wtbtservice",
            "com.nforetek.bt",
            "com.wt.openbt.server"
        )

        packages.forEach { packageName ->
            val resolved = SupportedMediaPlayer.fromPackage(packageName)
            assertEquals(SupportedMediaPlayer.BLUETOOTH_PHONE, resolved)
        }
    }

    @Test
    fun normalizeMediaPlayerPackages_includesWtLocalMultimediaAndAlias() {
        val normalized = normalizeMediaPlayerPackages(
            listOf(
                " COM.WT.MULTIMEDIA.LOCAL ",
                "com.wt.multimedia",
                "com.wt.multimedia.platform3",
                "ru.yandex.radio",
                " com.wt.wtbtservice "
            )
        )

        assertTrue("com.wt.multimedia.local" in normalized)
        assertTrue("com.android.bluetooth" in normalized)
        assertTrue("ru.yandex.mobile.fmradio" in normalized)
    }
}
