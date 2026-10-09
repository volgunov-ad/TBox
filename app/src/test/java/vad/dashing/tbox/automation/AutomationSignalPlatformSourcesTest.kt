package vad.dashing.tbox.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.tbox.HeadUnitCanMode

class AutomationSignalPlatformSourcesTest {
    private val a9 = HeadUnitCanMode.Android9MbCan
    private val a10 = HeadUnitCanMode.Android10Vhal
    private val tboxOnly = setOf(AutomationSignalSource.TBOX)
    private val both = setOf(AutomationSignalSource.HEAD_UNIT, AutomationSignalSource.TBOX)

    @Test
    fun a9KeepsBrokenMbCanSignalsOnTbox() {
        assertEquals(tboxOnly, AutomationSignalCatalog.sources(AutomationSignalId.ENGINE_TEMPERATURE, a9))
        assertEquals(tboxOnly, AutomationSignalCatalog.sources(AutomationSignalId.TARGET_GEAR, a9))
        assertEquals(both, AutomationSignalCatalog.sources(AutomationSignalId.STEERING_SPEED, a9))
        assertEquals(both, AutomationSignalCatalog.sources(AutomationSignalId.ENGINE_RPM, a9))
    }

    @Test
    fun a10KeepsSteeringSpeedOnTbox() {
        assertEquals(tboxOnly, AutomationSignalCatalog.sources(AutomationSignalId.STEERING_SPEED, a10))
        assertEquals(both, AutomationSignalCatalog.sources(AutomationSignalId.ENGINE_TEMPERATURE, a10))
        assertEquals(both, AutomationSignalCatalog.sources(AutomationSignalId.TARGET_GEAR, a10))
    }

    @Test
    fun savedHeadUnitChoiceIsReadFromTbox() {
        assertEquals(
            AutomationSignalSource.TBOX,
            AutomationSignalCatalog.resolveSource(
                AutomationSignalId.ENGINE_TEMPERATURE,
                AutomationSignalSource.HEAD_UNIT,
                a9,
            ),
        )
        assertEquals(
            AutomationSignalSource.HEAD_UNIT,
            AutomationSignalCatalog.resolveSource(
                AutomationSignalId.ENGINE_TEMPERATURE,
                AutomationSignalSource.HEAD_UNIT,
                a10,
            ),
        )
        assertEquals(
            AutomationSignalSource.TBOX,
            AutomationSignalCatalog.resolveSource(AutomationSignalId.VOLTAGE, AutomationSignalSource.TBOX, a9),
        )
    }

    @Test
    fun headUnitOnlySignalsAreNeverEmptied() {
        AutomationSignalCatalog.entries.forEach { descriptor ->
            HeadUnitCanMode.entries.forEach { mode ->
                assertTrue(
                    "${descriptor.id} on $mode",
                    AutomationSignalCatalog.sources(descriptor.id, mode).isNotEmpty(),
                )
            }
        }
    }

    @Test
    fun validationStillAcceptsTheSavedHeadUnitSource() {
        assertTrue(
            AutomationSignalCatalog.supports(AutomationSignalId.ENGINE_TEMPERATURE, AutomationSignalSource.HEAD_UNIT),
        )
    }
}
