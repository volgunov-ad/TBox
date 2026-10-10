package vad.dashing.tbox.externalapi

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClimatePanelFrontButtonTest {
    @Test
    fun climateButtonSendsTheFrontPowerProperty() {
        val html = javaClass.classLoader!!
            .getResourceAsStream("external_api/climate_panel.html")!!
            .bufferedReader()
            .readText()
        assertTrue(html.contains("var FRONT_POWER = 90;"))
        assertTrue(html.contains("toggleNamed(\"front\", FRONT_POWER)"))
        assertFalse(html.contains("var FRONT = 90;"))
    }
}
