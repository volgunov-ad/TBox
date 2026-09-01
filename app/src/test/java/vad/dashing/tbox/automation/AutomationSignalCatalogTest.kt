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
    fun headlightMode_listsNamedStaffValues() {
        val descriptor = AutomationSignalCatalog.get(AutomationSignalId.HEADLIGHT_MODE)
        assertEquals(AutomationSignalValueType.STATE, descriptor.id.valueType)
        val hint = descriptor.valueHint()
        assertTrue(hint, hint.contains("AUTO"))
        assertTrue(hint, hint.contains("PARK"))
        assertTrue(hint, hint.contains("LOW"))
        assertTrue(hint, hint.contains("OFF"))
        assertFalse(hint, hint.contains("(raw)"))
        assertFalse(hint, hint.contains("(1)"))
    }

    @Test
    fun driveMode_listsNamedCanModes() {
        val descriptor = AutomationSignalCatalog.get(AutomationSignalId.DRIVE_MODE)
        assertEquals(AutomationSignalValueType.STATE, descriptor.id.valueType)
        val hint = descriptor.valueHint()
        assertTrue(hint, hint.contains("NOR"))
        assertTrue(hint, hint.contains("ECO"))
        assertFalse(hint, hint.contains("(raw)"))
        assertFalse(hint, hint.contains("(0)"))
    }

    @Test
    fun canCatalogAndSignalCatalog_shareVehicleLabels() {
        val driveModeProperty = vad.dashing.tbox.mbcan.MbCanKnownVehiclePropertyId.VEHICLE_DRIVEMODE
        assertEquals(
            AutomationCanCatalog.get(AutomationCanBus.VEHICLE, driveModeProperty)!!.label,
            AutomationParameterLabels.signalLabel(AutomationSignalId.DRIVE_MODE),
        )
    }

    @Test
    fun newCanSignals_countMatchesPhaseB1() {
        val newIds = setOf(
            AutomationSignalId.DOOR_AUTO_LOCK,
            AutomationSignalId.DOOR_IGNOFF_UNLOCK,
            AutomationSignalId.HEADLIGHTS_FOLLOW_ME_HOME,
            AutomationSignalId.DRIVER_UNLOCK_MODE,
            AutomationSignalId.REMOTE_LOCK_FEEDBACK,
            AutomationSignalId.WIPER_SENSITIVITY,
            AutomationSignalId.REAR_WIPER,
            AutomationSignalId.MIRROR_AUTO_FOLD,
            AutomationSignalId.LOW_BEAM_HEIGHT,
            AutomationSignalId.TURN_FLASH_COUNT,
            AutomationSignalId.LAS_MODE,
            AutomationSignalId.BLIND_SPOT_DETECTION,
            AutomationSignalId.DOOR_OPEN_WARNING,
            AutomationSignalId.FCW,
            AutomationSignalId.FCW_SENSITIVITY,
            AutomationSignalId.LDW_SENSITIVITY,
            AutomationSignalId.HVAC_CUSTOM_MODE,
            AutomationSignalId.FRONT_WINDSCREEN_HEAT,
            AutomationSignalId.HVAC_REAR_DEFROSTER,
            AutomationSignalId.HVAC_AC_CLEAN_WHEN_LOCKED,
            AutomationSignalId.HVAC_ANION_PURIFY,
            AutomationSignalId.FRAGRANCE,
            AutomationSignalId.FRAGRANCE_SMELL,
            AutomationSignalId.FRAGRANCE_CONCENTRATION,
            AutomationSignalId.HVAC_FIRST_BLOWING,
            AutomationSignalId.BT_REDUCE_FAN,
            AutomationSignalId.HVAC_AUTO_VENTILATION,
            AutomationSignalId.HVAC_FAN_DIRECTION,
            AutomationSignalId.HVAC_TEMPERATURE_LEFT,
            AutomationSignalId.HVAC_TEMPERATURE_RIGHT,
            AutomationSignalId.HVAC_FAN_SPEED,
            AutomationSignalId.HVAC_FRONT_OFF,
            AutomationSignalId.HUD,
            AutomationSignalId.HUD_HEIGHT,
            AutomationSignalId.HUD_BRIGHTNESS,
            AutomationSignalId.HUD_DISPLAY_MODE,
            AutomationSignalId.HUD_AUTO_BRIGHTNESS,
            AutomationSignalId.ICM_BRIGHTNESS_MODE,
            AutomationSignalId.ICM_BRIGHTNESS,
            AutomationSignalId.OVERSPEED_ALARM,
            AutomationSignalId.STEERING_MODE,
            AutomationSignalId.EPS_MODE,
            AutomationSignalId.DRIVE_MODE_6DCT,
            AutomationSignalId.TSR_SWITCH,
            AutomationSignalId.TRUNK_DOOR,
            AutomationSignalId.AUDIO_VOLUME_SPEED_MODE,
            AutomationSignalId.AUDIO_KEY_TONE_VOLUME,
            AutomationSignalId.AUDIO_RADAR_ALARM_VOLUME,
            AutomationSignalId.AUDIO_EQ_MODE,
            AutomationSignalId.AUDIO_EQ_BASS,
            AutomationSignalId.AUDIO_EQ_MIDDLE,
            AutomationSignalId.AUDIO_EQ_TREBLE,
            AutomationSignalId.AUDIO_BALANCE,
            AutomationSignalId.AUDIO_FADER,
        )
        assertEquals(54, newIds.size)
        newIds.forEach { id ->
            assertTrue(id.name, AutomationSignalCatalog.get(id).valueHint().isNotBlank())
        }
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
