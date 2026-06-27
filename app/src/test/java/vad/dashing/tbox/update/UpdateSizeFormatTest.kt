package vad.dashing.tbox.update

import org.junit.Assert.assertEquals
import org.junit.Test

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
        assertEquals("512 КБ/с", formatDownloadSpeed(512L * 1024L))
    }

    @Test
    fun formatDownloadSpeed_formatsMegabytesPerSecond() {
        assertEquals("1.5 МБ/с", formatDownloadSpeed((1.5 * 1024 * 1024).toLong()))
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
