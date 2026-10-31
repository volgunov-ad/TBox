package vad.dashing.mqtt.bridge

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import vad.dashing.mqtt.ha.CanCommandBinding
import vad.dashing.mqtt.ha.CatalogEntity
import vad.dashing.mqtt.ha.EntityGroup
import vad.dashing.mqtt.ha.HaComponent
import vad.dashing.mqtt.ha.WritePair

class CommandPayloadTest {
    @Test
    fun selectUsesThePairedWriteValue() {
        val result = commandToInvoke(driveMode(), "SPT")
        val action = actions(result!!.request).getJSONObject(0)
        assertEquals("can_command", action.getString("type"))
        assertEquals("set", action.getString("operation"))
        assertEquals(1, action.getInt("value"))
        assertEquals("SPT", result.publishedState)
    }

    @Test
    fun unknownSelectAndOutOfRangeNumberAreRejected() {
        assertNull(commandToInvoke(driveMode(), "NOPE"))
        val temp = numberEntity()
        assertNull(commandToInvoke(temp, "40"))
        val nearest = commandToInvoke(temp, "22.4")
        assertEquals(225, actions(nearest!!.request).getJSONObject(0).getInt("value"))
        assertEquals("22.5", nearest.publishedState)
    }

    @Test
    fun switchMapsOnOffAndButtonPressesOnce() {
        val power = driveMode().copy(
            objectId = "hvac_power",
            component = HaComponent.SWITCH,
            pairs = listOf(WritePair("off", null, "off"), WritePair("on", null, "on")),
        )
        val off = commandToInvoke(power, "OFF")
        assertEquals("off", actions(off!!.request).getJSONObject(0).getString("value"))
        assertEquals("OFF", off.publishedState)
        val button = driveMode().copy(
            component = HaComponent.BUTTON,
            builtinAction = "media_next",
            canCommand = null,
            pairs = emptyList(),
        )
        val press = commandToInvoke(button, "PRESS")
        assertEquals("media_next", actions(press!!.request).getJSONObject(0).getString("actionType"))
        assertNull(commandToInvoke(button, "ON"))
    }

    @Test
    fun unavailableTemperatureIsNotPublished() {
        assertNull(StateFormat.numberText(-40.0, "°C"))
        assertEquals("12.5", StateFormat.numberText(12.5, "°C"))
        assertEquals("NOR", StateFormat.text("NOR", "", HaComponent.SENSOR))
    }

    @Test
    fun onOffBecomesUpperCaseOnlyForBinaryPayloads() {
        assertEquals("ON", StateFormat.text("on", "", HaComponent.BINARY_SENSOR))
        assertEquals("OFF", StateFormat.text("off", "", HaComponent.SWITCH))
        assertEquals("off", StateFormat.text("off", "", HaComponent.SELECT))
        assertEquals("on", StateFormat.text("on", "", HaComponent.SENSOR))
    }

    private fun actions(request: InvokeRequest): JSONArray {
        val body = (request as InvokeRequest.Actions).json
        return JSONObject(body).getJSONArray("actions")
    }

    private fun driveMode() = CatalogEntity(
        objectId = "drive_mode",
        label = "Режим вождения",
        description = "",
        group = EntityGroup.MOTION,
        component = HaComponent.SELECT,
        writable = true,
        options = listOf("ECO", "NOR", "SPT"),
        pairs = listOf(
            WritePair("ECO", 2.0, null),
            WritePair("NOR", 0.0, null),
            WritePair("SPT", 1.0, null),
        ),
        canCommand = CanCommandBinding("vehicle", 10, "set"),
    )

    private fun numberEntity() = driveMode().copy(
        objectId = "hvac_temperature_left",
        component = HaComponent.NUMBER,
        numberMin = 16.0,
        numberMax = 30.0,
        numberStep = 0.5,
        pairs = listOf(
            WritePair("22", 220.0, null),
            WritePair("22.5", 225.0, null),
        ),
    )
}
