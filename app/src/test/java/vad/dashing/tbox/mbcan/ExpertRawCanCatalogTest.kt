package vad.dashing.tbox.mbcan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpertRawCanCatalogTest {

    @Test
    fun allParams_includesVehicleAndAudioKnownIds() {
        val params = ExpertRawCanCatalog.allParams()
        assertTrue(params.isNotEmpty())
        assertTrue(params.any { it.bus == ExpertRawCanBus.Vehicle })
        assertTrue(params.any { it.bus == ExpertRawCanBus.Audio })
        assertTrue(
            params.any {
                it.mbCanId == MbCanKnownVehiclePropertyId.STEERING_WHEEL_HEAT_SWITCH &&
                    it.name == "STEERING_WHEEL_HEAT_SWITCH"
            },
        )
        assertTrue(
            params.any {
                it.mbCanId == MbCanKnownAudioPropertyId.VOLUME && it.name == "VOLUME"
            },
        )
    }

    @Test
    fun allParams_skipsValueAliasConsts() {
        val params = ExpertRawCanCatalog.allParams()
        assertFalse(params.any { it.name.startsWith("LIGHTCONTROL_") && it.name != "LIGHTCONTROL" })
        assertTrue(
            params.any {
                it.name == "LAS_MODE_SELECTION" &&
                    it.mbCanId == MbCanKnownVehiclePropertyId.LAS_MODE_SELECTION
            },
        )
        assertFalse(params.any { it.name == "LAS_MODE_LDW" || it.name == "LAS_MODE_LKA" || it.name == "LAS_MODE_OFF" })
        assertFalse(params.any { it.name.startsWith("HVAC_FAN_DIRECTION_") })
        assertFalse(params.any { it.name == "SUNROOF_TILT" || it.name.startsWith("WINDOW_A10_") })
        assertTrue(params.any { it.name == "HVAC_FAN_DIRECTION" })
    }

    @Test
    fun allParams_keepsPropertiesThatShareNumbersWithValueCodes() {
        val params = ExpertRawCanCatalog.allParams()
        assertTrue(params.any { it.name == "DOOR_AUTO_LOCK" && it.mbCanId == MbCanKnownVehiclePropertyId.DOOR_AUTO_LOCK })
        assertTrue(params.any { it.name == "DOOR_IGNOFF_UNLOCK" && it.mbCanId == MbCanKnownVehiclePropertyId.DOOR_IGNOFF_UNLOCK })
        assertTrue(params.any { it.name == "DEFENCES_PROMPT" && it.mbCanId == MbCanKnownVehiclePropertyId.DEFENCES_PROMPT })
        assertTrue(
            params.any {
                it.bus == ExpertRawCanBus.Vehicle &&
                    it.name == "MIRROR_AUTOFOLD_SW" &&
                    it.mbCanId == MbCanKnownVehiclePropertyId.MIRROR_AUTOFOLD_SW
            },
        )
        assertFalse(params.any { it.name.startsWith("$") || it.mbCanId == 0 })
    }

    @Test
    fun allParams_includesDecodedDirectVhalIds() {
        val direct = ExpertRawCanCatalog.allParams().filter { it.bus == ExpertRawCanBus.VhalDirect }
        assertEquals(65, direct.size)
        assertTrue(direct.all { it.vhalReadId == it.mbCanId && it.vhalWriteId == it.mbCanId })
        assertTrue(direct.any { it.name == "VHAL_ENGINE_RPM_PROPERTY_ID" && it.mbCanId == FirmwareVehicleJsonMapper.VHAL_ENGINE_RPM_PROPERTY_ID })
        assertTrue(direct.any { it.name == "VHAL_CAR_SPEED_PROPERTY_ID" })
        assertTrue(direct.any { it.name == "VHAL_CEM2_DRIVER_DOOR_STS" })
        assertFalse(direct.any { it.name == "VHAL_MCU_REPLY_SPEED_PROPERTY_ID" })
        assertFalse(direct.any { it.mbCanId == FirmwareVehicleJsonMapper.VHAL_FL_WIN_POSITION })
        assertFalse(direct.any { it.mbCanId == FirmwareVehicleJsonMapper.VHAL_SUNROOF_CMD_STS })
        assertFalse(direct.any { it.mbCanId == FirmwareVehicleJsonMapper.VHAL_SUNSHADE_CMD_STS })
        val rpm = direct.first { it.name == "VHAL_ENGINE_RPM_PROPERTY_ID" }
        assertEquals(
            "vhal=${rpm.mbCanId} (A10 only)",
            ExpertRawCanCatalog.formatIdsSummary(rpm, HeadUnitCanModeLabel.Android9MbCan),
        )
        assertEquals(
            "vhal=${rpm.mbCanId}",
            ExpertRawCanCatalog.formatIdsSummary(rpm, HeadUnitCanModeLabel.Android10Vhal),
        )
    }

    @Test
    fun filterParams_emptyQueryReturnsAll() {
        val params = ExpertRawCanCatalog.allParams()
        assertEquals(params, ExpertRawCanCatalog.filterParams(params, ""))
        assertEquals(params, ExpertRawCanCatalog.filterParams(params, "   "))
        assertTrue(params.size > 20)
    }

    @Test
    fun filterParams_matchesNameAndIds() {
        val params = ExpertRawCanCatalog.allParams()
        val byName = ExpertRawCanCatalog.filterParams(params, "VOLUME")
        assertTrue(byName.any { it.name == "VOLUME" })
        val volume = params.first { it.name == "VOLUME" && it.bus == ExpertRawCanBus.Audio }
        val byId = ExpertRawCanCatalog.filterParams(params, volume.mbCanId.toString())
        assertTrue(byId.any { it.name == "VOLUME" && it.bus == ExpertRawCanBus.Audio })
        assertTrue(ExpertRawCanCatalog.filterParams(params, "zzz-no-such-param").isEmpty())
    }

    @Test
    fun formatIdsSummary_a10_showsSeparateReadWriteWhenTheyDiffer() {
        val param = ExpertRawCanCatalog.allParams().firstOrNull {
            it.mbCanId == MbCanKnownVehiclePropertyId.HVAC_FRONT_OFF
        }
        assertNotNull(param)
        val summary = ExpertRawCanCatalog.formatIdsSummary(
            param!!,
            HeadUnitCanModeLabel.Android10Vhal,
        )
        assertTrue(summary.contains("logical="))
        assertTrue(summary.contains("read=") || summary.contains("vhal="))
    }

    @Test
    fun optionalDecodeHint_a9_toggleBinary() {
        val param = ExpertRawCanParam(
            name = "STEERING_WHEEL_HEAT_SWITCH",
            mbCanId = MbCanKnownVehiclePropertyId.STEERING_WHEEL_HEAT_SWITCH,
            bus = ExpertRawCanBus.Vehicle,
            vhalReadId = null,
            vhalWriteId = null,
        )
        val policy = MbCanCommandRegistry.get(param.mbCanId)?.policy as MbCanCommandPolicy.ToggleBinary
        assertEquals(
            "On(${policy.onValue})",
            ExpertRawCanCatalog.optionalDecodeHint(
                param,
                policy.onValue,
                HeadUnitCanModeLabel.Android9MbCan,
            ),
        )
        assertEquals(
            "Off(${policy.offValue})",
            ExpertRawCanCatalog.optionalDecodeHint(
                param,
                policy.offValue,
                HeadUnitCanModeLabel.Android9MbCan,
            ),
        )
    }
}
