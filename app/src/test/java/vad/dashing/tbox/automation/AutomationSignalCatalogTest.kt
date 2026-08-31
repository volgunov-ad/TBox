package vad.dashing.tbox.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationSignalCatalogTest {
    @Test
    fun catalog_coversEverySignalId() {
        assertEquals(
            AutomationSignalId.entries.toSet(),
            AutomationSignalCatalog.entries.map { it.id }.toSet(),
        )
    }

    @Test
    fun every_entry_hasValueHint() {
        assertTrue(AutomationSignalCatalog.entries.isNotEmpty())
        AutomationSignalCatalog.entries.forEach { entry ->
            assertTrue(entry.label, entry.valueHint().isNotBlank())
        }
    }

    @Test
    fun gearMode_listsPrnd() {
        val hint = AutomationSignalCatalog.get(AutomationSignalId.GEAR_MODE).valueHint()
        listOf("P", "R", "N", "D").forEach { value ->
            assertTrue(hint, hint.contains(value))
        }
    }

    @Test
    fun accStatus_isHeadUnitOnlyWithAccAndIgn() {
        val descriptor = AutomationSignalCatalog.get(AutomationSignalId.ACC_STATUS)
        assertTrue(AutomationSignalSource.HEAD_UNIT in descriptor.sources)
        assertFalse(AutomationSignalSource.TBOX in descriptor.sources)
        val hint = descriptor.valueHint()
        assertTrue(hint, hint.contains("ACC ON"))
        assertTrue(hint, hint.contains("ON"))
        assertTrue(hint, hint.contains("off"))
        assertTrue(hint, hint.contains("acc"))
        assertTrue(hint, hint.contains("ign"))
        assertTrue(hint, hint.contains("Android 9"))
        assertTrue(hint, hint.contains("Android 10"))
    }

    @Test
    fun gasPedal_isHeadUnitOnlyPercent() {
        val descriptor = AutomationSignalCatalog.get(AutomationSignalId.GAS_PEDAL)
        assertTrue(AutomationSignalSource.HEAD_UNIT in descriptor.sources)
        assertFalse(AutomationSignalSource.TBOX in descriptor.sources)
        assertEquals("%", descriptor.unit)
        val hint = descriptor.valueHint()
        assertTrue(hint, hint.contains("0…100"))
    }

    @Test
    fun wiperSts_isHeadUnitOnlyWithIntLowHigh() {
        val descriptor = AutomationSignalCatalog.get(AutomationSignalId.WIPER_STS)
        assertTrue(AutomationSignalSource.HEAD_UNIT in descriptor.sources)
        assertFalse(AutomationSignalSource.TBOX in descriptor.sources)
        val hint = descriptor.valueHint()
        assertTrue(hint, hint.contains("Выключено"))
        assertTrue(hint, hint.contains("INT"))
        assertTrue(hint, hint.contains("Low"))
        assertTrue(hint, hint.contains("High"))
        assertTrue(hint, hint.contains("off"))
        assertTrue(hint, hint.contains("int"))
        assertTrue(hint, hint.contains("low"))
        assertTrue(hint, hint.contains("high"))
        assertTrue(hint, hint.contains("TTG"))
        assertTrue(hint, hint.contains("Не сервисное положение"))
    }

    @Test
    fun shadeRoofWindows_areHeadUnitOnlyStates() {
        val shade = AutomationSignalCatalog.get(AutomationSignalId.SUNSHADE)
        val roof = AutomationSignalCatalog.get(AutomationSignalId.SUNROOF)
        val window = AutomationSignalCatalog.get(AutomationSignalId.WINDOW_FRONT_LEFT)
        assertTrue(AutomationSignalSource.HEAD_UNIT in shade.sources)
        assertFalse(AutomationSignalSource.TBOX in shade.sources)
        assertTrue(shade.valueHint().contains("Закрыто"))
        assertTrue(shade.valueHint().contains("Открыто"))
        assertTrue(shade.valueHint().contains("closed"))
        assertTrue(shade.valueHint().contains("open"))
        assertTrue(roof.valueHint().contains("Откинут"))
        assertTrue(roof.valueHint().contains("tilt"))
        assertTrue(window.valueHint().contains("Щель"))
        assertTrue(window.valueHint().contains("vent"))
        assertEquals("Закрыто", AutomationSignalCatalog.stateOptionLabel("closed"))
        assertEquals("Открыто", AutomationSignalCatalog.stateOptionLabel("open"))
        assertEquals("Откинут", AutomationSignalCatalog.stateOptionLabel("tilt"))
        assertEquals("Щель", AutomationSignalCatalog.stateOptionLabel("vent"))
    }

    @Test
    fun signalPickers_areSortedByRussianLabel() {
        val collator = java.text.Collator.getInstance(java.util.Locale.forLanguageTag("ru-RU")).apply {
            strength = java.text.Collator.PRIMARY
        }
        listOf(
            AutomationSignalValueType.NUMBER,
            AutomationSignalValueType.STATE,
        ).forEach { type ->
            val labels = AutomationSignalCatalog.signalsOfType(type)
                .map { AutomationSignalCatalog.get(it).label }
            assertEquals(type.name, labels.sortedWith(collator), labels)
        }
    }

    @Test
    fun rainDetected_isHeadUnitOnlyBinary() {
        val descriptor = AutomationSignalCatalog.get(AutomationSignalId.RAIN_DETECTED)
        assertTrue(AutomationSignalSource.HEAD_UNIT in descriptor.sources)
        assertFalse(AutomationSignalSource.TBOX in descriptor.sources)
        val hint = descriptor.valueHint()
        assertTrue(hint, hint.contains("Выключено"))
        assertTrue(hint, hint.contains("Включено"))
        assertTrue(hint, hint.contains("off"))
        assertTrue(hint, hint.contains("on"))
        assertTrue(hint, hint.contains("дождь"))
        assertTrue(hint, hint.contains("RainDetected"))
        assertTrue(hint, hint.contains("Не отказ датчика"))
    }

    @Test
    fun brakePedal_isHeadUnitOnlyBinary() {
        val descriptor = AutomationSignalCatalog.get(AutomationSignalId.BRAKE_PEDAL)
        assertTrue(AutomationSignalSource.HEAD_UNIT in descriptor.sources)
        assertFalse(AutomationSignalSource.TBOX in descriptor.sources)
        val hint = descriptor.valueHint()
        assertTrue(hint, hint.contains("Выключено"))
        assertTrue(hint, hint.contains("Включено"))
        assertTrue(hint, hint.contains("off"))
        assertTrue(hint, hint.contains("on"))
    }

    @Test
    fun binaryState_listsOnOffLabels() {
        val hint = AutomationSignalCatalog.get(AutomationSignalId.AVH).valueHint()
        assertTrue(hint, hint.contains("Выключено"))
        assertTrue(hint, hint.contains("Включено"))
        assertTrue(hint, hint.contains("off"))
        assertTrue(hint, hint.contains("on"))
    }

    @Test
    fun headlightMode_listsStaffRawValues() {
        val hint = AutomationSignalCatalog.get(AutomationSignalId.HEADLIGHT_MODE).valueHint()
        assertTrue(hint, hint.contains("AUTO"))
        assertTrue(hint, hint.contains("PARK"))
        assertTrue(hint, hint.contains("LOW"))
        assertTrue(hint, hint.contains("OFF"))
        assertTrue(hint, hint.contains("(1)"))
        assertTrue(hint, hint.contains("(4)"))
    }

    @Test
    fun driveMode_listsCanRawNotWidget6dct() {
        val hint = AutomationSignalCatalog.get(AutomationSignalId.DRIVE_MODE).valueHint()
        assertTrue(hint, hint.contains("NOR"))
        assertTrue(hint, hint.contains("ECO"))
        assertTrue(hint, hint.contains("(0)"))
        assertTrue(hint, hint.contains("(2)"))
        assertFalse(hint, hint.contains("(100)"))
        assertFalse(hint, hint.contains("(101)"))
        assertFalse(hint, hint.contains("(102)"))
    }

    @Test
    fun seatMode_listsHeatAndVent() {
        val hint = AutomationSignalCatalog.get(AutomationSignalId.FRONT_LEFT_SEAT_MODE).valueHint()
        assertTrue(hint, hint.contains("Подогрев 1"))
        assertTrue(hint, hint.contains("Вентиляция 3"))
    }

    @Test
    fun foregroundApp_isAppOnlyWithoutFixedStates() {
        val descriptor = AutomationSignalCatalog.get(AutomationSignalId.FOREGROUND_APP)
        assertTrue(AutomationSignalSource.APP in descriptor.sources)
        assertFalse(AutomationSignalSource.TBOX in descriptor.sources)
        assertFalse(AutomationSignalSource.HEAD_UNIT in descriptor.sources)
        assertTrue(descriptor.stateOptions.isEmpty())
        val hint = descriptor.valueHint()
        assertTrue(hint, hint.contains("статистик"))
        assertTrue(hint, hint.contains("1 с"))
        assertTrue(hint, hint.contains("10 с"))
        assertTrue(hint, hint.contains("com.mengbo.avm"))
    }

    @Test
    fun espCompanionSignals_areAppBinary() {
        listOf(
            AutomationSignalId.ESP_GPIO_IN_0,
            AutomationSignalId.ESP_GPIO_IN_3,
            AutomationSignalId.ESP_RELAY_0,
            AutomationSignalId.ESP_RELAY_1,
        ).forEach { id ->
            val descriptor = AutomationSignalCatalog.get(id)
            assertTrue(id.name, AutomationSignalSource.APP in descriptor.sources)
            assertFalse(id.name, AutomationSignalSource.TBOX in descriptor.sources)
            val hint = descriptor.valueHint()
            assertTrue(hint, hint.contains("Выключено"))
            assertTrue(hint, hint.contains("Включено"))
        }
    }
}
