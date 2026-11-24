package vad.dashing.tbox.update

import org.junit.Assert.assertEquals
import org.junit.Test
import vad.dashing.tbox.BuildConfig

class UpdateSizeFormatTest {

    @Test
    fun formatApkSizeMegabytes_formatsWithOneDecimalBelowTen() {
        val bytes = (4.3 * 1024 * 1024).toLong()
        assertEquals("4.3", formatApkSizeMegabytes(bytes))
    }

    @Test
    fun formatApkSizeMegabytes_roundsLargeValues() {
        val bytes = 45L * 1024 * 1024
        assertEquals("45", formatApkSizeMegabytes(bytes))
    }

    @Test
    fun formatDownloadSpeed_formatsKilobytesPerSecond() {
        val kb = if (BuildConfig.FLAVOR.equals("en", ignoreCase = true)) "KB/s" else "КБ/с"
        assertEquals("512 $kb", formatDownloadSpeed(512L * 1024L))
    }

    @Test
    fun formatDownloadSpeed_formatsMegabytesPerSecond() {
        val mb = if (BuildConfig.FLAVOR.equals("en", ignoreCase = true)) "MB/s" else "МБ/с"
        assertEquals("1.5 $mb", formatDownloadSpeed((1.5 * 1024 * 1024).toLong()))
    }

    @Test
    fun formatDownloadEta_formatsMinutesAndSeconds() {
        assertEquals("01:05", formatDownloadEta(65L))
    }

    @Test
    fun formatDownloadEta_formatsHoursMinutesAndSeconds() {
        assertEquals("1:01:05", formatDownloadEta(3665L))
    }
}
