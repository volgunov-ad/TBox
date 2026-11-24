package vad.dashing.tbox.automation

import vad.dashing.tbox.ruEn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.tbox.HeadUnitCanMode
import vad.dashing.tbox.mbcan.AccCruiseDomain
import vad.dashing.tbox.mbcan.BodyComfortDomain
import vad.dashing.tbox.mbcan.HuSystemModeDomain
import vad.dashing.tbox.mbcan.IgnitionKeyDomain
import vad.dashing.tbox.mbcan.MbCanSignal

class AutomationSignalCatalogTest {
    @Test
    fun preferredSource_prefersHeadUnitWhenBothAvailable() {
        assertEquals(
            AutomationSignalSource.HEAD_UNIT,
            AutomationSignalCatalog.preferredSource(AutomationSignalId.ENGINE_RPM),
        )
        assertEquals(
            AutomationSignalSource.HEAD_UNIT,
            AutomationSignalCatalog.preferredSource(AutomationSignalId.GEAR_MODE),
        )
        assertEquals(
            AutomationSignalSource.HEAD_UNIT,
            AutomationSignalCatalog.preferredSource(
                setOf(AutomationSignalSource.TBOX, AutomationSignalSource.HEAD_UNIT),
            ),
        )
    }

    @Test
    fun preferredSource_keepsOnlyAvailableSource() {
        assertEquals(
            AutomationSignalSource.TBOX,
            AutomationSignalCatalog.preferredSource(AutomationSignalId.VOLTAGE),
        )
        assertEquals(
            AutomationSignalSource.HEAD_UNIT,
            AutomationSignalCatalog.preferredSource(AutomationSignalId.GAS_PEDAL),
        )
        assertEquals(
            AutomationSignalSource.APP,
            AutomationSignalCatalog.preferredSource(AutomationSignalId.FOREGROUND_APP),
        )
        assertEquals(
            AutomationSignalSource.APP,
            AutomationSignalCatalog.preferredSource(AutomationSignalId.WIFI_SSID),
        )
    }

    @Test
    fun sourcesForUi_listsHeadUnitBeforeTbox() {
        assertEquals(
            listOf(AutomationSignalSource.HEAD_UNIT, AutomationSignalSource.TBOX),
            AutomationSignalCatalog.sourcesForUi(AutomationSignalId.ENGINE_RPM),
        )
        assertEquals(
            listOf(AutomationSignalSource.HEAD_UNIT),
            AutomationSignalCatalog.sourcesForUi(AutomationSignalId.GAS_PEDAL),
        )
        assertEquals(
            listOf(AutomationSignalSource.TBOX),
            AutomationSignalCatalog.sourcesForUi(AutomationSignalId.VOLTAGE),
        )
        assertEquals(
            listOf(AutomationSignalSource.APP),
            AutomationSignalCatalog.sourcesForUi(AutomationSignalId.FOREGROUND_APP),
        )
        assertEquals(
            listOf(AutomationSignalSource.APP),
            AutomationSignalCatalog.sourcesForUi(AutomationSignalId.WIFI_ENABLED),
        )
    }

    @Test
    fun hazardAndDrl_areHeadUnitBinary() {
        listOf(AutomationSignalId.HAZARD, AutomationSignalId.DRL).forEach { id ->
            val descriptor = AutomationSignalCatalog.get(id)
            assertEquals(listOf("off", "on"), descriptor.stateOptions)
            assertTrue(AutomationSignalSource.HEAD_UNIT in descriptor.sources)
            assertFalse(AutomationSignalSource.TBOX in descriptor.sources)
            assertTrue(descriptor.valueHint().isNotBlank())
        }
        assertEquals(ruEn("Аварийка", "Hazard lights"), AutomationParameterLabels.signalLabel(AutomationSignalId.HAZARD))
        assertEquals(ruEn("ДХО", "DRL"), AutomationParameterLabels.signalLabel(AutomationSignalId.DRL))
        assertEquals(MbCanSignal.Hazard, huInterestForSignal(AutomationSignalId.HAZARD))
        assertEquals(MbCanSignal.Drl, huInterestForSignal(AutomationSignalId.DRL))
    }

    @Test
    fun turnSignalSides_areHeadUnitBinaryMappedToTurnSignals() {
        listOf(
            AutomationSignalId.TURN_SIGNAL_LEFT,
            AutomationSignalId.TURN_SIGNAL_RIGHT,
        ).forEach { id ->
            val descriptor = AutomationSignalCatalog.get(id)
            assertEquals(listOf("off", "on"), descriptor.stateOptions)
            assertTrue(AutomationSignalSource.HEAD_UNIT in descriptor.sources)
            assertFalse(AutomationSignalSource.TBOX in descriptor.sources)
            assertTrue(descriptor.valueHint().isNotBlank())
            assertEquals(MbCanSignal.TurnSignals, huInterestForSignal(id))
        }
        assertEquals(
            ruEn("Поворотник левый", "Left turn signal"),
            AutomationParameterLabels.signalLabel(AutomationSignalId.TURN_SIGNAL_LEFT),
        )
        assertEquals(
            ruEn("Поворотник правый", "Right turn signal"),
            AutomationParameterLabels.signalLabel(AutomationSignalId.TURN_SIGNAL_RIGHT),
        )
    }

    @Test
    fun turnStabilized_legacyEitherSide_hiddenFromPickerButDecodable() {
        val descriptor = AutomationSignalCatalog.get(AutomationSignalId.TURN_STABILIZED)
        assertEquals(listOf("off", "on"), descriptor.stateOptions)
        assertTrue(AutomationSignalSource.HEAD_UNIT in descriptor.sources)
        assertEquals(
            ruEn("Поворотник (стабилизированный)", "Turn signal (stabilized)"),
            AutomationParameterLabels.signalLabel(AutomationSignalId.TURN_STABILIZED),
        )
        assertEquals(
            MbCanSignal.TurnSignals,
            huInterestForSignal(AutomationSignalId.TURN_STABILIZED),
        )
        val picker = AutomationSignalCatalog.pickerSignalsOfType(AutomationSignalValueType.STATE)
        assertFalse(AutomationSignalId.TURN_STABILIZED in picker)
        assertTrue(AutomationSignalId.TURN_SIGNAL_LEFT in picker)
        assertTrue(AutomationSignalId.TURN_SIGNAL_RIGHT in picker)
    }

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
    fun ignitionKeyAndSystemMode_areHeadUnitStates() {
        val key = AutomationSignalCatalog.get(AutomationSignalId.IGNITION_KEY)
        val mode = AutomationSignalCatalog.get(AutomationSignalId.HU_SYSTEM_MODE)
        assertEquals(IgnitionKeyDomain.OPTIONS, key.stateOptions)
        assertEquals(HuSystemModeDomain.OPTIONS, mode.stateOptions)
        assertTrue(AutomationSignalSource.HEAD_UNIT in key.sources)
        assertFalse(AutomationSignalSource.TBOX in key.sources)
        assertTrue(key.valueHint().contains("crank"))
        assertTrue(mode.valueHint().contains("hold_15m"))
        assertFalse(
            AutomationSignalCatalog.headUnitUnsupported(
                AutomationSignalId.IGNITION_KEY,
                HeadUnitCanMode.Android10Vhal,
            ),
        )
        assertTrue(
            AutomationSignalCatalog.headUnitUnsupported(
                AutomationSignalId.HU_SYSTEM_MODE,
                HeadUnitCanMode.Android10Vhal,
            ),
        )
    }

    @Test
    fun accAndCcsCruiseState_areHeadUnitOnlyLogicalStates() {
        val acc = AutomationSignalCatalog.get(AutomationSignalId.ACC_CRUISE_STATE)
        val ccs = AutomationSignalCatalog.get(AutomationSignalId.CCS_CRUISE_STATE)
        assertTrue(AutomationSignalSource.HEAD_UNIT in acc.sources)
        assertFalse(AutomationSignalSource.TBOX in acc.sources)
        assertTrue(AutomationSignalSource.HEAD_UNIT in ccs.sources)
        assertFalse(AutomationSignalSource.TBOX in ccs.sources)
        assertEquals(AccCruiseDomain.ACC_AUTOMATION_STATE_OPTIONS, acc.stateOptions)
        assertEquals(AccCruiseDomain.CCS_AUTOMATION_STATE_OPTIONS, ccs.stateOptions)
        val accHint = acc.valueHint()
        assertTrue(accHint, accHint.contains("standby"))
        assertTrue(accHint, accHint.contains("active"))
        assertTrue(accHint, accHint.contains("fault"))
        assertTrue(accHint, accHint.contains("acc_status"))
        val ccsHint = ccs.valueHint()
        assertTrue(ccsHint, ccsHint.contains("standby"))
        assertTrue(ccsHint, ccsHint.contains("active"))
        assertFalse(ccsHint.contains("fault"))
        assertEquals(ruEn("Ожидание", "Standby"), AutomationSignalCatalog.stateOptionLabel("standby"))
        assertEquals(ruEn("Активен", "Active"), AutomationSignalCatalog.stateOptionLabel("active"))
        assertEquals(ruEn("Ошибка", "Error"), AutomationSignalCatalog.stateOptionLabel("fault"))
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
        assertTrue(hint, hint.contains(ruEn("Выключено", "Off")))
        assertTrue(hint, hint.contains("INT"))
        assertTrue(hint, hint.contains("Low"))
        assertTrue(hint, hint.contains("High"))
        assertTrue(hint, hint.contains("off"))
        assertTrue(hint, hint.contains("int"))
        assertTrue(hint, hint.contains("low"))
        assertTrue(hint, hint.contains("high"))
        assertTrue(hint, hint.contains("TTG"))
        assertTrue(hint, hint.contains(ruEn("Не сервисное положение", "wiper service position")))
    }

    @Test
    fun shadeRoofWindows_areHeadUnitOnlyStates() {
        val shade = AutomationSignalCatalog.get(AutomationSignalId.SUNSHADE)
        val roof = AutomationSignalCatalog.get(AutomationSignalId.SUNROOF)
        val window = AutomationSignalCatalog.get(AutomationSignalId.WINDOW_FRONT_LEFT)
        assertTrue(AutomationSignalSource.HEAD_UNIT in shade.sources)
        assertFalse(AutomationSignalSource.TBOX in shade.sources)
        assertTrue(shade.valueHint().contains("0%"))
        assertTrue(shade.valueHint().contains("100%"))
        assertEquals(BodyComfortDomain.SHADE_STATE_OPTIONS, shade.stateOptions)
        assertTrue(roof.valueHint().contains(ruEn("Откинут", "Tilted")))
        assertTrue(roof.valueHint().contains("tilt"))
        assertEquals(BodyComfortDomain.ROOF_STATE_OPTIONS, roof.stateOptions)
        assertTrue(window.valueHint().contains("0%"))
        assertTrue(window.valueHint().contains("100%"))
        assertEquals(BodyComfortDomain.WINDOW_STATE_OPTIONS, window.stateOptions)
        assertEquals("0%", AutomationSignalCatalog.stateOptionLabel("0%"))
        assertEquals("100%", AutomationSignalCatalog.stateOptionLabel("100%"))
        assertEquals(ruEn("Откинут", "Tilted"), AutomationSignalCatalog.stateOptionLabel("tilt"))
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
        assertTrue(hint, hint.contains(ruEn("Выключено", "Off")))
        assertTrue(hint, hint.contains(ruEn("Включено", "On")))
        assertTrue(hint, hint.contains("off"))
        assertTrue(hint, hint.contains("on"))
        assertTrue(hint, hint.contains(ruEn("дождь", "rain")))
        assertTrue(hint, hint.contains("RainDetected"))
        assertTrue(hint, hint.contains(ruEn("Не отказ датчика", "sensor failure")))
    }

    @Test
    fun brakePedal_isHeadUnitOnlyBinary() {
        val descriptor = AutomationSignalCatalog.get(AutomationSignalId.BRAKE_PEDAL)
        assertTrue(AutomationSignalSource.HEAD_UNIT in descriptor.sources)
        assertFalse(AutomationSignalSource.TBOX in descriptor.sources)
        val hint = descriptor.valueHint()
        assertTrue(hint, hint.contains(ruEn("Выключено", "Off")))
        assertTrue(hint, hint.contains(ruEn("Включено", "On")))
        assertTrue(hint, hint.contains("off"))
        assertTrue(hint, hint.contains("on"))
    }

    @Test
    fun binaryState_listsOnOffLabels() {
        val hint = AutomationSignalCatalog.get(AutomationSignalId.AVH).valueHint()
        assertTrue(hint, hint.contains(ruEn("Выключено", "Off")))
        assertTrue(hint, hint.contains(ruEn("Включено", "On")))
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
            AutomationSignalId.DOOR_FRONT_LEFT,
            AutomationSignalId.DOOR_FRONT_RIGHT,
            AutomationSignalId.DOOR_REAR_LEFT,
            AutomationSignalId.DOOR_REAR_RIGHT,
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
        assertEquals(58, newIds.size)
        newIds.forEach { id ->
            assertTrue(id.name, AutomationSignalCatalog.get(id).valueHint().isNotBlank())
        }
    }

    @Test
    fun seatMode_listsHeatAndVent() {
        val hint = AutomationSignalCatalog.get(AutomationSignalId.FRONT_LEFT_SEAT_MODE).valueHint()
        assertTrue(hint, hint.contains(ruEn("Подогрев 1", "Heat 1")))
        assertTrue(hint, hint.contains(ruEn("Вентиляция 3", "Ventilation 3")))
    }

    @Test
    fun foregroundApp_isAppOnlyWithoutFixedStates() {
        val descriptor = AutomationSignalCatalog.get(AutomationSignalId.FOREGROUND_APP)
        assertTrue(AutomationSignalSource.APP in descriptor.sources)
        assertFalse(AutomationSignalSource.TBOX in descriptor.sources)
        assertFalse(AutomationSignalSource.HEAD_UNIT in descriptor.sources)
        assertTrue(descriptor.stateOptions.isEmpty())
        val hint = descriptor.valueHint()
        assertTrue(hint, hint.contains(ruEn("статистик", "usage")))
        assertTrue(hint, hint.contains(ruEn("1 с", "1 s")))
        assertTrue(hint, hint.contains(ruEn("10 с", "10 s")))
        assertTrue(hint, hint.contains("com.mengbo.avm"))
    }

    @Test
    fun espCompanionSignals_areAppBinary() {
        listOf(
            AutomationSignalId.ESP_GPIO_IN_0,
            AutomationSignalId.ESP_GPIO_IN_3,
            AutomationSignalId.ESP_RELAY_0,
            AutomationSignalId.ESP_RELAY_1,
            AutomationSignalId.ESP_BLE_BOUND,
        ).forEach { id ->
            val descriptor = AutomationSignalCatalog.get(id)
            assertTrue(id.name, AutomationSignalSource.APP in descriptor.sources)
            assertFalse(id.name, AutomationSignalSource.TBOX in descriptor.sources)
            val hint = descriptor.valueHint()
            assertTrue(hint, hint.contains(ruEn("Выключено", "Off")))
            assertTrue(hint, hint.contains(ruEn("Включено", "On")))
        }
        val bat = AutomationSignalCatalog.get(AutomationSignalId.ESP_BLE_BATTERY)
        assertEquals("%", bat.unit)
        assertTrue(AutomationSignalSource.APP in bat.sources)
    }

    @Test
    fun wifiSignals_areAppOnlyWithExplicitStates() {
        val enabled = AutomationSignalCatalog.get(AutomationSignalId.WIFI_ENABLED)
        val associated = AutomationSignalCatalog.get(AutomationSignalId.WIFI_ASSOCIATED)
        val ssid = AutomationSignalCatalog.get(AutomationSignalId.WIFI_SSID)
        assertEquals(listOf("off", "on"), enabled.stateOptions)
        assertEquals(listOf("off", "on"), associated.stateOptions)
        assertTrue(ssid.stateOptions.isEmpty())
        assertTrue(enabled.valueHint().contains("wlan0"))
        assertTrue(associated.valueHint().contains(ruEn("явный off", "explicit off")))
        assertTrue(ssid.valueHint().contains("none"))
        assertEquals(AutomationSignalSource.APP, AutomationSignalCatalog.preferredSource(enabled.id))
    }

    @Test
    fun huInternetStatus_isAppOnlyWithExplicitStates() {
        val signal = AutomationSignalCatalog.get(AutomationSignalId.HU_INTERNET_STATUS)
        assertEquals(
            listOf("unknown", "checking", "online", "offline"),
            signal.stateOptions,
        )
        assertEquals(AutomationSignalSource.APP, AutomationSignalCatalog.preferredSource(signal.id))
        assertTrue(signal.valueHint().contains(ruEn("двух", "two")))
        assertEquals(
            ruEn("Есть", "Online"),
            AutomationSignalCatalog.stateOptionLabel("online"),
        )
        assertEquals(
            ruEn("Нет", "No"),
            AutomationSignalCatalog.stateOptionLabel("offline"),
        )
    }

    @Test
    fun modemSignals_areAppOnlyWithExplicitStates() {
        val link = AutomationSignalCatalog.get(AutomationSignalId.WIFI_MODEM_LINK_STATUS)
        val data = AutomationSignalCatalog.get(AutomationSignalId.MODEM_MOBILE_DATA)
        val net = AutomationSignalCatalog.get(AutomationSignalId.MODEM_NET_TYPE)
        val sim = AutomationSignalCatalog.get(AutomationSignalId.MODEM_SIM_STATUS)
        assertEquals(
            listOf("idle", "ok", "auth_failed", "unreachable", "error"),
            link.stateOptions,
        )
        assertEquals(listOf("off", "on"), data.stateOptions)
        assertEquals(listOf("none", "2g", "3g", "4g"), net.stateOptions)
        assertEquals(listOf("none", "ready", "pin", "error", "unknown"), sim.stateOptions)
        assertEquals(AutomationSignalSource.APP, AutomationSignalCatalog.preferredSource(link.id))
        assertTrue(link.valueHint().contains("Wi‑Fi HTTP"))
        assertTrue(data.valueHint().contains("apnStatus"))
        assertEquals(ruEn("Связь OK", "Link OK"), AutomationSignalCatalog.stateOptionLabel("ok"))
        assertEquals("2G", AutomationSignalCatalog.stateOptionLabel("2g"))
        assertEquals(ruEn("Готова", "Ready"), AutomationSignalCatalog.stateOptionLabel("ready"))
    }

    @Test
    fun geoModemEnrichmentSignals_areAppOnlyReadOnly() {
        val altitude = AutomationSignalCatalog.get(AutomationSignalId.GEO_ALTITUDE_M)
        val speedSrc = AutomationSignalCatalog.get(AutomationSignalId.GEO_SPEED_SOURCE)
        val fixValid = AutomationSignalCatalog.get(AutomationSignalId.GEO_FIX_VALID)
        val geoSource = AutomationSignalCatalog.get(AutomationSignalId.GEO_SOURCE)
        val rssi = AutomationSignalCatalog.get(AutomationSignalId.MODEM_RSSI_DBM)
        val registered = AutomationSignalCatalog.get(AutomationSignalId.MODEM_REGISTERED)
        val operator = AutomationSignalCatalog.get(AutomationSignalId.MODEM_OPERATOR)
        val modemSource = AutomationSignalCatalog.get(AutomationSignalId.MODEM_SOURCE)
        val down = AutomationSignalCatalog.get(AutomationSignalId.MODEM_THROUGHPUT_DOWN_KBPS)
        val up = AutomationSignalCatalog.get(AutomationSignalId.MODEM_THROUGHPUT_UP_KBPS)

        assertEquals(AutomationSignalValueType.NUMBER, altitude.id.valueType)
        assertEquals(listOf("gnss", "can", "retention", "none"), speedSrc.stateOptions)
        assertEquals(listOf("off", "on"), fixValid.stateOptions)
        assertEquals(listOf("tbox", "companion", "android", "usb", "phone"), geoSource.stateOptions)
        assertEquals(ruEn("Телефон", "Phone"), AutomationSignalCatalog.stateOptionLabel("phone"))
        assertEquals(AutomationSignalValueType.NUMBER, rssi.id.valueType)
        assertEquals(listOf("off", "on"), registered.stateOptions)
        assertTrue(operator.stateOptions.isEmpty())
        assertEquals(listOf("tbox", "wifi_http"), modemSource.stateOptions)
        assertEquals(AutomationSignalValueType.NUMBER, down.id.valueType)
        assertEquals(AutomationSignalValueType.NUMBER, up.id.valueType)
        assertEquals(AutomationSignalSource.APP, AutomationSignalCatalog.preferredSource(altitude.id))
        assertEquals(AutomationSignalSource.APP, AutomationSignalCatalog.preferredSource(rssi.id))
        assertEquals(
            ruEn("Нет данных", "No data"),
            AutomationSignalCatalog.stateOptionLabel(AutomationSignalId.GEO_SPEED_SOURCE, "none"),
        )
        assertEquals(ruEn("Компаньон", "Companion"), AutomationSignalCatalog.stateOptionLabel("companion"))
        assertEquals("Wi‑Fi HTTP", AutomationSignalCatalog.stateOptionLabel("wifi_http"))
        assertTrue(rssi.valueHint().contains("CSQ"))
        assertTrue(down.valueHint().contains("Wi‑Fi HTTP"))
    }

    @Test
    fun appThemeMode_isAppOnlyWithExplicitStates() {
        val descriptor = AutomationSignalCatalog.get(AutomationSignalId.APP_THEME_MODE)
        assertEquals(listOf("manual_day", "manual_night", "auto_day", "auto_night"), descriptor.stateOptions)
        assertEquals(
            AutomationSignalSource.APP,
            AutomationSignalCatalog.preferredSource(descriptor.id),
        )
        assertEquals(
            ruEn("Тема приложения: режим", "App theme: mode"),
            AutomationParameterLabels.signalLabel(AutomationSignalId.APP_THEME_MODE),
        )
        assertEquals(ruEn("День (вручную)", "Day (manual)"), AutomationSignalCatalog.stateOptionLabel("manual_day"))
        assertEquals(ruEn("Ночь (вручную)", "Night (manual)"), AutomationSignalCatalog.stateOptionLabel("manual_night"))
        assertEquals(ruEn("День (авто)", "Day (auto)"), AutomationSignalCatalog.stateOptionLabel("auto_day"))
        assertEquals(ruEn("Ночь (авто)", "Night (auto)"), AutomationSignalCatalog.stateOptionLabel("auto_night"))
        assertTrue(descriptor.valueHint().isNotBlank())
    }

    @Test
    fun appTheme_effectiveThemeIsAppOnlyDayOrNight() {
        val descriptor = AutomationSignalCatalog.get(AutomationSignalId.APP_THEME)
        assertEquals(listOf("day", "night"), descriptor.stateOptions)
        assertEquals(
            AutomationSignalSource.APP,
            AutomationSignalCatalog.preferredSource(descriptor.id),
        )
        assertEquals(
            ruEn("Тема приложения: сейчас", "App theme: now"),
            AutomationParameterLabels.signalLabel(AutomationSignalId.APP_THEME),
        )
        assertTrue(descriptor.valueHint().contains(ruEn("авто-день", "auto day")))
    }

    @Test
    fun huScreenBrightness_isAppOnlyNumericAndAutoBinary() {
        val brightness = AutomationSignalCatalog.get(AutomationSignalId.HU_SCREEN_BRIGHTNESS)
        assertEquals(AutomationSignalValueType.NUMBER, brightness.id.valueType)
        assertEquals(
            AutomationSignalSource.APP,
            AutomationSignalCatalog.preferredSource(brightness.id),
        )
        assertEquals(
            ruEn("Яркость экрана ГУ", "HU screen brightness"),
            AutomationParameterLabels.signalLabel(AutomationSignalId.HU_SCREEN_BRIGHTNESS),
        )
        assertTrue(brightness.valueHint().contains("1…10"))
        assertTrue(brightness.valueHint().contains(ruEn("экран ГУ", "HU screen")))
        assertTrue(brightness.valueHint().contains("HUD"))
        assertTrue(brightness.valueHint().contains("ICM"))

        val auto = AutomationSignalCatalog.get(AutomationSignalId.HU_SCREEN_AUTO_BRIGHTNESS)
        assertEquals(listOf("off", "on"), auto.stateOptions)
        assertEquals(
            AutomationSignalSource.APP,
            AutomationSignalCatalog.preferredSource(auto.id),
        )
        assertEquals(
            ruEn("Автояркость экрана ГУ", "HU screen auto-brightness"),
            AutomationParameterLabels.signalLabel(AutomationSignalId.HU_SCREEN_AUTO_BRIGHTNESS),
        )
    }

    @Test
    fun threeBrightnessLabels_areDistinct() {
        assertEquals(
            ruEn("Яркость экрана ГУ", "HU screen brightness"),
            AutomationParameterLabels.signalLabel(AutomationSignalId.HU_SCREEN_BRIGHTNESS),
        )
        assertEquals(
            ruEn("Яркость HUD", "HUD brightness"),
            AutomationParameterLabels.signalLabel(AutomationSignalId.HUD_BRIGHTNESS),
        )
        assertEquals(
            ruEn("Яркость приборной панели", "Cluster brightness"),
            AutomationParameterLabels.signalLabel(AutomationSignalId.ICM_BRIGHTNESS),
        )
        assertTrue(
            AutomationSignalCatalog.get(AutomationSignalId.HUD_BRIGHTNESS)
                .valueHint()
                .contains(ruEn("не экран ГУ", "not the HU screen")),
        )
        assertTrue(
            AutomationSignalCatalog.get(AutomationSignalId.ICM_BRIGHTNESS)
                .valueHint()
                .contains(ruEn("не экран ГУ", "not the HU screen")),
        )
    }

    @Test
    fun platformMixerVolumes_areAppOnlyWithHeadrestStates() {
        listOf(
            AutomationSignalId.HU_MEDIA_VOLUME,
            AutomationSignalId.HU_PHONE_VOLUME,
            AutomationSignalId.HU_NAVI_VOLUME,
            AutomationSignalId.HU_VOICE_VOLUME,
        ).forEach { id ->
            val descriptor = AutomationSignalCatalog.get(id)
            assertEquals(AutomationSignalValueType.NUMBER, descriptor.id.valueType)
            assertEquals(
                AutomationSignalSource.APP,
                AutomationSignalCatalog.preferredSource(descriptor.id),
            )
            assertTrue(descriptor.valueHint().contains(ruEn("Микшер ГУ", "HU mixer")))
        }
        val headrest = AutomationSignalCatalog.get(AutomationSignalId.HU_HEADREST_SPEAKER)
        assertEquals(listOf("only", "assist", "off"), headrest.stateOptions)
        assertEquals(
            AutomationSignalSource.APP,
            AutomationSignalCatalog.preferredSource(headrest.id),
        )
    }

    @Test
    fun everySignal_hasPickerGroupWithNonBlankLabel() {
        AutomationSignalCatalog.entries.forEach { descriptor ->
            assertTrue(
                descriptor.id.name,
                descriptor.group.label.isNotBlank(),
            )
        }
        assertEquals(
            ruEn("Климат и воздух", "Climate and air"),
            AutomationSignalCatalog.signalGroup(AutomationSignalId.HVAC_FAN_DIRECTION).label,
        )
        assertEquals(
            ruEn("ADAS и безопасность", "ADAS and safety"),
            AutomationSignalCatalog.signalGroup(AutomationSignalId.FCW).label,
        )
        assertEquals(
            ruEn("Поездки и топливо", "Trips and fuel"),
            AutomationSignalCatalog.signalGroup(AutomationSignalId.ACTIVE_TRIP_DISTANCE_KM).label,
        )
    }

    @Test
    fun signalGroups_followDomains() {
        assertEquals(
            AutomationSignalGroup.TRIPS,
            AutomationSignalCatalog.signalGroup(AutomationSignalId.FUEL_LEVEL_LITERS),
        )
        assertEquals(
            AutomationSignalGroup.BODY,
            AutomationSignalCatalog.signalGroup(AutomationSignalId.WINDOW_FRONT_LEFT),
        )
        assertEquals(
            AutomationSignalGroup.ESP,
            AutomationSignalCatalog.signalGroup(AutomationSignalId.ESP_RELAY_0),
        )
        assertEquals(
            AutomationSignalGroup.ROADS,
            AutomationSignalCatalog.signalGroup(AutomationSignalId.ROAD_TUNNEL),
        )
        assertEquals(
            AutomationSignalGroup.MEDIA,
            AutomationSignalCatalog.signalGroup(AutomationSignalId.MEDIA_PLAYING),
        )
    }

    @Test
    fun signalKeywords_coverFolkSynonyms() {
        assertTrue(
            AutomationSignalCatalog.signalKeywords(AutomationSignalId.FRONT_WINDSCREEN_HEAT)
                .contains("обогрев"),
        )
        assertTrue(
            AutomationSignalCatalog.signalKeywords(AutomationSignalId.WIPER_STS)
                .contains("дворники"),
        )
        assertTrue(
            AutomationSignalCatalog.signalKeywords(AutomationSignalId.TURN_SIGNAL_LEFT)
                .contains("поворотник"),
        )
        assertTrue(
            AutomationSignalCatalog.signalKeywords(AutomationSignalId.TRUNK_DOOR)
                .contains("багажник"),
        )
    }

    @Test
    fun signalSearch_findsBySynonymAndKey() {
        // «печка» находит обогрев лобового через синонимы.
        assertTrue(
            AutomationPickerSearch.matches(
                query = "печка",
                label = AutomationSignalCatalog.get(AutomationSignalId.FRONT_WINDSCREEN_HEAT).label,
                groupLabel =
                    AutomationSignalCatalog.signalGroup(AutomationSignalId.FRONT_WINDSCREEN_HEAT)
                        .label,
                keywords =
                    AutomationSignalCatalog.signalKeywords(AutomationSignalId.FRONT_WINDSCREEN_HEAT),
                storageKey = AutomationSignalId.FRONT_WINDSCREEN_HEAT.storageKey,
            ),
        )
        // По ключу находит HVAC-сигнал.
        assertTrue(
            AutomationPickerSearch.matches(
                query = "hvac fan",
                label = AutomationSignalCatalog.get(AutomationSignalId.HVAC_FAN_SPEED).label,
                groupLabel = AutomationSignalCatalog.signalGroup(AutomationSignalId.HVAC_FAN_SPEED)
                    .label,
                keywords = AutomationSignalCatalog.signalKeywords(AutomationSignalId.HVAC_FAN_SPEED),
                storageKey = AutomationSignalId.HVAC_FAN_SPEED.storageKey,
            ),
        )
    }
}
