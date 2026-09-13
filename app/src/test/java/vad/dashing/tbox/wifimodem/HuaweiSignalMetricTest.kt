package vad.dashing.tbox.wifimodem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HuaweiSignalMetricTest {
    @Test
    fun parsesPlainInt() {
        assertEquals(-70, HuaweiSignalMetric.parseInt("-70"))
        assertEquals(19, HuaweiSignalMetric.parseInt("19"))
    }

    @Test
    fun stripsDbmAndDbSuffixes() {
        assertEquals(-71, HuaweiSignalMetric.parseInt("-71dBm"))
        assertEquals(-94, HuaweiSignalMetric.parseInt("-94dBm"))
        assertEquals(-9, HuaweiSignalMetric.parseInt("-9.0dB"))
        assertEquals(19, HuaweiSignalMetric.parseInt("19dB"))
    }

    @Test
    fun blankOrJunkIsNull() {
        assertNull(HuaweiSignalMetric.parseInt(null))
        assertNull(HuaweiSignalMetric.parseInt(""))
        assertNull(HuaweiSignalMetric.parseInt("dBm"))
        assertNull(HuaweiSignalMetric.parseInt("--"))
    }
}
