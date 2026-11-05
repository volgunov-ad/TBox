package vad.dashing.tbox.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PhoneDialCallIndicatorSizeTest {

    @Test
    fun defaultScaleIsDoubleOriginalTenDp() {
        assertEquals(20f, phoneDialCallIndicatorSizeDp(iconScale = 1f), 0.01f)
    }

    @Test
    fun iconScaleMultipliesBaseDiameter() {
        assertEquals(30f, phoneDialCallIndicatorSizeDp(iconScale = 1.5f), 0.01f)
        assertEquals(10f, phoneDialCallIndicatorSizeDp(iconScale = 0.5f), 0.01f)
        assertEquals(40f, phoneDialCallIndicatorSizeDp(iconScale = 2f), 0.01f)
    }

    @Test
    fun nonFiniteOrNonPositiveScaleFallsBackToBase() {
        assertEquals(20f, phoneDialCallIndicatorSizeDp(iconScale = Float.NaN), 0.01f)
        assertEquals(20f, phoneDialCallIndicatorSizeDp(iconScale = 0f), 0.01f)
        assertEquals(20f, phoneDialCallIndicatorSizeDp(iconScale = -1f), 0.01f)
    }
}
