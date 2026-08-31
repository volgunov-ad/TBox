package vad.dashing.tbox

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetCatalogInstrumentedTest {
    @Test
    fun gasBrakeWidgetIsInTelemetryPickerSection() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("vad.dashing.tbox", appContext.packageName)
        val keys = WidgetsRepository.getAvailableDataKeysWidgets(noTboxConnect = false)
        assertTrue(keys.contains(GAS_BRAKE_WIDGET_DATA_KEY))
        assertEquals(
            WidgetTypeSectionId.Telemetry,
            WidgetTypeSections.sectionFor(GAS_BRAKE_WIDGET_DATA_KEY),
        )
        val title = WidgetsRepository.getTitleForDataKey(appContext, GAS_BRAKE_WIDGET_DATA_KEY)
        assertTrue(title.isNotBlank())
    }
}
