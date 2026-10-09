package vad.dashing.tbox

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import vad.dashing.tbox.automation.AutomationHardKeyStatus
import vad.dashing.tbox.automation.AutomationTriggerEspGpioBtnEventBus

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class WidgetButtonBindingTest {
    @Test
    fun normalize_rejectsBlankBleAndOutOfRangeGpio() {
        assertNull(
            normalizeWidgetButtonBinding(
                WidgetButtonBinding.EspBle(mac = "", btn = 1),
            ),
        )
        assertNull(
            normalizeWidgetButtonBinding(
                WidgetButtonBinding.EspGpio(channel = 9),
            ),
        )
        assertNull(
            normalizeWidgetButtonBinding(
                WidgetButtonBinding.HardKey(keyCode = 0),
            ),
        )
    }

    @Test
    fun codec_roundTrip() {
        val original = WidgetButtonBinding.EspBle(
            mac = "AA:BB:CC:DD:EE:FF",
            btn = 2,
        )
        val encoded = encodeWidgetButtonBinding(normalizeWidgetButtonBinding(original)!!)
        val decoded = decodeWidgetButtonBinding(encoded)
        assertEquals(
            WidgetButtonBinding.EspBle(mac = "aa:bb:cc:dd:ee:ff", btn = 2),
            decoded,
        )
    }

    @Test
    fun widgetConfigCodec_persistsButtonBinding() {
        val tile = FloatingDashboardWidgetConfig(
            dataKey = "steeringWheelHeatWidget",
            buttonBinding = WidgetButtonBinding.EspGpio(channel = 1),
        )
        val parsed = parseWidgetConfigsFromString(serializeWidgetConfigs(listOf(tile))).single()
        assertEquals(WidgetButtonBinding.EspGpio(channel = 1), parsed.buttonBinding)
    }

    @Test
    fun widgetConfigCodec_dropsBindingOnUnsupportedTile() {
        val raw = JSONObject()
            .put("dataKey", "frontLeftSeatHeatVentWidget")
            .put(
                "buttonBinding",
                encodeWidgetButtonBinding(WidgetButtonBinding.HardKey(115)),
            )
            .toString()
        val parsed = parseWidgetConfigsFromString("[$raw]").single()
        assertNull(parsed.buttonBinding)
    }

    @Test
    fun supportsWidgetButtonBinding_deniesMultiZone() {
        assertFalse(supportsWidgetButtonBinding("frontLeftSeatHeatVentWidget"))
        assertFalse(supportsWidgetButtonBinding(MUSIC_WIDGET_DATA_KEY))
        assertTrue(supportsWidgetButtonBinding("steeringWheelHeatWidget"))
        assertTrue(supportsWidgetButtonBinding(FRONT_LEFT_SEAT_HEAT_VENT_SINGLE_WIDGET_DATA_KEY))
    }

    @Test
    fun registry_dispatchesMatchingBindings() {
        WidgetButtonBindingRegistry.clearForTests()
        var singles = 0
        var doubles = 0
        val binding = WidgetButtonBinding.HardKey(115)
        val id = WidgetButtonBindingRegistry.register(
            binding = binding,
            onSingle = { singles++ },
            onDouble = { doubles++ },
        )
        WidgetButtonBindingRegistry.dispatch(binding, WidgetButtonBindingTap.SINGLE)
        WidgetButtonBindingRegistry.dispatch(binding, WidgetButtonBindingTap.DOUBLE)
        WidgetButtonBindingRegistry.dispatch(WidgetButtonBinding.HardKey(116), WidgetButtonBindingTap.SINGLE)
        assertEquals(1, singles)
        assertEquals(1, doubles)
        WidgetButtonBindingRegistry.unregister(id)
        assertEquals(0, WidgetButtonBindingRegistry.sizeForTests())
    }

    @Test
    fun registry_failingTargetDoesNotBreakOtherTiles() {
        WidgetButtonBindingRegistry.clearForTests()
        val binding = WidgetButtonBinding.HardKey(115)
        WidgetButtonBindingRegistry.register(
            binding = binding,
            onSingle = { throw IllegalStateException("boom") },
            onDouble = {},
        )
        var singles = 0
        val healthyId = WidgetButtonBindingRegistry.register(
            binding = binding,
            onSingle = { singles++ },
            onDouble = {},
        )
        WidgetButtonBindingRegistry.dispatch(binding, WidgetButtonBindingTap.SINGLE)
        assertEquals(1, singles)
        WidgetButtonBindingRegistry.unregister(healthyId)
        WidgetButtonBindingRegistry.clearForTests()
    }

    @Test
    fun coordinator_routesGpioGestureToTileTap() = runBlocking {
        WidgetButtonBindingRegistry.clearForTests()
        var singles = 0
        val id = WidgetButtonBindingRegistry.register(
            binding = WidgetButtonBinding.EspGpio(channel = 2),
            onSingle = { singles++ },
            onDouble = {},
        )
        val coordinator = WidgetButtonBindingCoordinator(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            dispatchContext = Dispatchers.Unconfined,
        )
        coordinator.start()
        AutomationTriggerEspGpioBtnEventBus.publish(2, AutomationHardKeyStatus.SINGLE)
        AutomationTriggerEspGpioBtnEventBus.publish(2, AutomationHardKeyStatus.LONG)
        AutomationTriggerEspGpioBtnEventBus.publish(3, AutomationHardKeyStatus.SINGLE)
        coordinator.stop()
        WidgetButtonBindingRegistry.unregister(id)
        assertEquals(1, singles)
        WidgetButtonBindingRegistry.clearForTests()
    }
}
