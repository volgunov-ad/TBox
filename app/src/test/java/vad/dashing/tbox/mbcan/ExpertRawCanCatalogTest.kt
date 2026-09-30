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
        assertFalse(params.any { it.name.startsWith("LAS_MODE_") })
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
