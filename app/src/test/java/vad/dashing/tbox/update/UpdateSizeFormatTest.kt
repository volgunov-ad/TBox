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
}
