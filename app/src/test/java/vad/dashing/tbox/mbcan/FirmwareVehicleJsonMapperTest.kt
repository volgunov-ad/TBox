package vad.dashing.tbox.mbcan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Explicit mbCAN→VHAL maps and intentional absences (no send.json on unit-test hosts).
 * Robolectric stubs [android.util.Log] used when [FirmwareVehicleJsonMapper] probes firmware JSON.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class FirmwareVehicleJsonMapperTest {

    @Test
    fun hvacFrontOff_resolvesStockReadAndWriteIds() {
        assertEquals(
            289_415_175,
            FirmwareVehicleJsonMapper.resolveReadPropertyId(MbCanKnownVehiclePropertyId.HVAC_FRONT_OFF),
        )
        assertEquals(
            289_415_301,
            FirmwareVehicleJsonMapper.resolveWritePropertyId(MbCanKnownVehiclePropertyId.HVAC_FRONT_OFF),
        )
    }

    @Test
    fun hvacBlowerDelay_resolvesStockAcCleanWhenLockedIds() {
        assertEquals(
            289_415_189,
            FirmwareVehicleJsonMapper.resolveReadPropertyId(MbCanKnownVehiclePropertyId.HVAC_BLOWER_DELAY),
        )
        assertEquals(
            289_412_666,
            FirmwareVehicleJsonMapper.resolveWritePropertyId(MbCanKnownVehiclePropertyId.HVAC_BLOWER_DELAY),
        )
    }

    @Test
    fun driveMode6dctWet_usesSameTPropertyForReadAndWrite_asStockCarSettings() {
        // Stock A10 CarSettings registers/reads T_0401_IHU_9_DriveMode_6DCT_Wet for both directions.
        assertEquals(
            289_412_692,
            FirmwareVehicleJsonMapper.resolveReadPropertyId(MbCanKnownVehiclePropertyId.VEHICLE_DRIVEMODE_6DCT_WET),
        )
        assertEquals(
            289_412_692,
            FirmwareVehicleJsonMapper.resolveWritePropertyId(MbCanKnownVehiclePropertyId.VEHICLE_DRIVEMODE_6DCT_WET),
        )
    }

    @Test
    fun driveMode_usesSeparateReadAndWriteIds() {
        assertEquals(
            289_412_123,
            FirmwareVehicleJsonMapper.resolveReadPropertyId(MbCanKnownVehiclePropertyId.VEHICLE_DRIVEMODE),
        )
        assertEquals(
            289_412_695,
            FirmwareVehicleJsonMapper.resolveWritePropertyId(MbCanKnownVehiclePropertyId.VEHICLE_DRIVEMODE),
        )
    }

    @Test
    fun systemReboot_hasNoExplicitVhalMap() {
        // Stock A10 uses PowerManager.reboot(), not a VHAL property for eSYSTEM_REBOOT(74).
        assertNull(
            FirmwareVehicleJsonMapper.resolveWritePropertyId(MbCanKnownVehiclePropertyId.SYSTEM_REBOOT),
        )
        assertNull(
            FirmwareVehicleJsonMapper.resolveReadPropertyId(MbCanKnownVehiclePropertyId.SYSTEM_REBOOT),
        )
    }

    @Test
    fun speedLimiter_hasNoExplicitVhalMap_unsupportedOnDashing() {
        assertNull(
            FirmwareVehicleJsonMapper.resolveWritePropertyId(
                MbCanKnownVehiclePropertyId.VEHICLE_SPEEDLIMIT_SWITCH,
            ),
        )
        assertNull(
            FirmwareVehicleJsonMapper.resolveReadPropertyId(
                MbCanKnownVehiclePropertyId.VEHICLE_SPEEDLIMIT_SWITCH,
            ),
        )
        assertNull(
            FirmwareVehicleJsonMapper.resolveWritePropertyId(
                MbCanKnownVehiclePropertyId.VEHICLE_SPEEDLIMIT_VALUESET,
            ),
        )
        assertNull(
            FirmwareVehicleJsonMapper.resolveReadPropertyId(
                MbCanKnownVehiclePropertyId.VEHICLE_SPEEDLIMIT_VALUESET,
            ),
        )
    }

    @Test
    fun mirrorFold_resolvesWriteId() {
        assertEquals(
            289_412_705,
            FirmwareVehicleJsonMapper.resolveWritePropertyId(MbCanKnownVehiclePropertyId.MIRROR_FOLD_SWITCH),
        )
    }
}
