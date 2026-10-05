package vad.dashing.tbox.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vad.dashing.tbox.HeadUnitCanMode
import vad.dashing.tbox.mbcan.BodyComfortDomain
import vad.dashing.tbox.mbcan.BodyComfortWrite
import vad.dashing.tbox.mbcan.MbCanKnownVehiclePropertyId
import vad.dashing.tbox.mbcan.VhalBinaryToggleCodec

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AutomationCanValueCodecTest {
    @Test
    fun semanticOnOff_resolvesToMbCanPolicyOnBothHeadUnits() {
        val heat = AutomationAction.CanCommand(
            bus = AutomationCanBus.VEHICLE,
            propertyId = MbCanKnownVehiclePropertyId.STEERING_WHEEL_HEAT_SWITCH,
            operation = AutomationCanOperation.SET,
            valueKey = "on",
        ).let(AutomationCanValueCodec::canonicalize)

        assertEquals(AutomationCanValueCodec.KEY_ON, heat.valueKey)
        assertEquals(2, heat.value)
        assertEquals(
            2,
            AutomationCanValueCodec.resolveWriteValue(heat, HeadUnitCanMode.Android9MbCan),
        )
        assertEquals(
            2,
            AutomationCanValueCodec.resolveWriteValue(heat, HeadUnitCanMode.Android10Vhal),
        )
        // A10 SetProperty remaps A9 on(2) → VHAL on(1)
        assertEquals(
            1,
            VhalBinaryToggleCodec.encodeMbCanToggleSetValue(
                propertyId = MbCanKnownVehiclePropertyId.STEERING_WHEEL_HEAT_SWITCH,
                mbCanValue = 2,
                offValue = 1,
                onValue = 2,
            ),
        )
        assertEquals(
            2,
            VhalBinaryToggleCodec.encodeMbCanToggleSetValue(
                propertyId = MbCanKnownVehiclePropertyId.STEERING_WHEEL_HEAT_SWITCH,
                mbCanValue = 1,
                offValue = 1,
                onValue = 2,
            ),
        )
    }

    @Test
    fun semanticWindow_mapsA9PercentAndA10Commands() {
        val close = AutomationAction.CanCommand(
            bus = AutomationCanBus.VEHICLE,
            propertyId = MbCanKnownVehiclePropertyId.WINDOW_FL_POS,
            operation = AutomationCanOperation.SET,
            valueKey = "close",
        ).let(AutomationCanValueCodec::canonicalize)

        assertEquals(0, close.value)
        assertEquals(
            0,
            AutomationCanValueCodec.resolveWriteValue(close, HeadUnitCanMode.Android9MbCan),
        )
        assertEquals(
            MbCanKnownVehiclePropertyId.WINDOW_A10_CLOSE,
            AutomationCanValueCodec.resolveWriteValue(close, HeadUnitCanMode.Android10Vhal),
        )

        val vent = close.copy(valueKey = "vent").let(AutomationCanValueCodec::canonicalize)
        assertEquals(
            BodyComfortDomain.WINDOW_A9_VENT_PERCENT,
            AutomationCanValueCodec.resolveWriteValue(vent, HeadUnitCanMode.Android9MbCan),
        )
        assertEquals(
            MbCanKnownVehiclePropertyId.WINDOW_A10_VENT,
            AutomationCanValueCodec.resolveWriteValue(vent, HeadUnitCanMode.Android10Vhal),
        )

        val open = close.copy(valueKey = "open").let(AutomationCanValueCodec::canonicalize)
        assertEquals(
            100,
            AutomationCanValueCodec.resolveWriteValue(open, HeadUnitCanMode.Android9MbCan),
        )
        assertEquals(
            MbCanKnownVehiclePropertyId.WINDOW_A10_OPEN,
            AutomationCanValueCodec.resolveWriteValue(open, HeadUnitCanMode.Android10Vhal),
        )
    }

    @Test
    fun legacyA9WindowInt_remapsToA10Commands() {
        val a9Open = AutomationAction.CanCommand(
            bus = AutomationCanBus.VEHICLE,
            propertyId = MbCanKnownVehiclePropertyId.WINDOW_FL_POS,
            operation = AutomationCanOperation.SET,
            value = 100,
        )
        assertEquals(
            MbCanKnownVehiclePropertyId.WINDOW_A10_OPEN,
            AutomationCanValueCodec.resolveWriteValue(a9Open, HeadUnitCanMode.Android10Vhal),
        )
        assertEquals(
            100,
            AutomationCanValueCodec.resolveWriteValue(a9Open, HeadUnitCanMode.Android9MbCan),
        )

        val a9Vent = a9Open.copy(value = BodyComfortDomain.WINDOW_A9_VENT_PERCENT)
        assertEquals(
            MbCanKnownVehiclePropertyId.WINDOW_A10_VENT,
            AutomationCanValueCodec.resolveWriteValue(a9Vent, HeadUnitCanMode.Android10Vhal),
        )

        val a10Close = a9Open.copy(value = MbCanKnownVehiclePropertyId.WINDOW_A10_CLOSE)
        assertEquals(
            0,
            AutomationCanValueCodec.resolveWriteValue(a10Close, HeadUnitCanMode.Android9MbCan),
        )
        assertEquals(
            MbCanKnownVehiclePropertyId.WINDOW_A10_CLOSE,
            AutomationCanValueCodec.resolveWriteValue(a10Close, HeadUnitCanMode.Android10Vhal),
        )
    }

    @Test
    fun bodyComfortWrite_remapWindowValueForMode_matchesCodec() {
        assertEquals(
            MbCanKnownVehiclePropertyId.WINDOW_A10_OPEN,
            BodyComfortWrite.remapWindowValueForMode(80, HeadUnitCanMode.Android10Vhal),
        )
        assertEquals(
            BodyComfortDomain.WINDOW_A9_VENT_PERCENT,
            BodyComfortWrite.remapWindowValueForMode(
                MbCanKnownVehiclePropertyId.WINDOW_A10_VENT,
                HeadUnitCanMode.Android9MbCan,
            ),
        )
        assertNull(
            BodyComfortWrite.remapWindowValueForMode(50, HeadUnitCanMode.Android10Vhal),
        )
    }

    @Test
    fun legacyInt_roundTrip_keepsRawValueWithoutForcingKey() {
        val heat = AutomationAction.CanCommand(
            bus = AutomationCanBus.VEHICLE,
            propertyId = MbCanKnownVehiclePropertyId.STEERING_WHEEL_HEAT_SWITCH,
            operation = AutomationCanOperation.SET,
            value = 2,
        )
        val json = AutomationCodec.encode(
            AutomationDocument(
                automations = listOf(
                    AutomationDefinition(
                        id = "a",
                        name = "heat",
                        triggers = listOf(
                            AutomationTrigger.SystemEvent(
                                id = "1",
                                event = AutomationSystemEvent.MENU_OPENED,
                            ),
                        ),
                        actions = listOf(heat),
                    ),
                ),
            ),
        )
        assertTrue(json.contains("\"value\":2"))
        assertFalse(json.contains("\"value\":\"on\""))

        val decoded = AutomationCodec.decode(json).getOrThrow().automations.single()
        val action = decoded.actions.single() as AutomationAction.CanCommand
        assertEquals(2, action.value)
        assertNull(action.valueKey)
    }

    @Test
    fun semanticString_roundTrip_preservesKey() {
        val heat = AutomationCanValueCodec.withPortableKey(
            AutomationAction.CanCommand(
                bus = AutomationCanBus.VEHICLE,
                propertyId = MbCanKnownVehiclePropertyId.STEERING_WHEEL_HEAT_SWITCH,
                operation = AutomationCanOperation.SET,
                value = 2,
            ),
        )
        assertEquals(AutomationCanValueCodec.KEY_ON, heat.valueKey)

        val json = AutomationCodec.encode(
            AutomationDocument(
                automations = listOf(
                    AutomationDefinition(
                        id = "a",
                        name = "heat",
                        triggers = listOf(
                            AutomationTrigger.SystemEvent(
                                id = "1",
                                event = AutomationSystemEvent.MENU_OPENED,
                            ),
                        ),
                        actions = listOf(heat),
                    ),
                ),
            ),
        )
        assertTrue(json.contains("\"value\":\"on\""))

        val decoded = AutomationCodec.decode(json).getOrThrow().automations.single()
        val action = decoded.actions.single() as AutomationAction.CanCommand
        assertEquals(AutomationCanValueCodec.KEY_ON, action.valueKey)
        assertEquals(2, action.value)
    }

    @Test
    fun catalog_acceptsSemanticWindowOnEitherBackend() {
        val action = AutomationCanValueCodec.canonicalize(
            AutomationAction.CanCommand(
                bus = AutomationCanBus.VEHICLE,
                propertyId = MbCanKnownVehiclePropertyId.WINDOW_FL_POS,
                operation = AutomationCanOperation.SET,
                valueKey = "vent",
            ),
        )
        assertTrue(AutomationCanCatalog.isAllowed(action))
        assertTrue(
            AutomationCanValueCodec.isResolvable(action, HeadUnitCanMode.Android9MbCan),
        )
        assertTrue(
            AutomationCanValueCodec.isResolvable(action, HeadUnitCanMode.Android10Vhal),
        )
    }

    @Test
    fun samePolarityBinary_keepsA9ValuesOnA10Encode() {
        // Parking radar: A9 and A10 both use 1=off, 2=on.
        assertEquals(
            2,
            VhalBinaryToggleCodec.encodeMbCanToggleSetValue(
                propertyId = MbCanKnownVehiclePropertyId.PARKING_RADAR_SWITCH,
                mbCanValue = 2,
                offValue = 1,
                onValue = 2,
            ),
        )
        assertEquals(
            1,
            VhalBinaryToggleCodec.encodeMbCanToggleSetValue(
                propertyId = MbCanKnownVehiclePropertyId.PARKING_RADAR_SWITCH,
                mbCanValue = 1,
                offValue = 1,
                onValue = 2,
            ),
        )
    }

    @Test
    fun encodeMbCanToggleSetValue_rejectsUnknownProperty() {
        assertNull(
            VhalBinaryToggleCodec.encodeMbCanToggleSetValue(
                propertyId = MbCanKnownVehiclePropertyId.SYSTEM_REBOOT,
                mbCanValue = 2,
                offValue = 1,
                onValue = 2,
            ),
        )
        assertNotNull(
            VhalBinaryToggleCodec.encodeMbCanToggleSetValue(
                propertyId = MbCanKnownVehiclePropertyId.AVH_SWITCH,
                mbCanValue = 2,
                offValue = 1,
                onValue = 2,
            ),
        )
    }
}
