package vad.dashing.mqtt.wqtt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.mqtt.ha.CanCommandBinding
import vad.dashing.mqtt.ha.CatalogEntity
import vad.dashing.mqtt.ha.EntityGroup
import vad.dashing.mqtt.ha.HaComponent
import vad.dashing.mqtt.ha.WritePair

class WqttDeviceMapperTest {
    @Test
    fun climateBuildsThermostatWithOnOffAndRange() {
        val types = catalog(
            devices = listOf(WqttNamedType(7, "Термостат"), WqttNamedType(2, "Выключатель")),
            ranges = listOf(WqttNamedType(3, "Температура")),
        )
        val devices = WqttDeviceMapper.buildDevices(
            packs = setOf(WqttPackId.CLIMATE),
            entities = listOf(hvacPower(), hvacTemp()),
            topicPrefix = "tbox",
            deviceId = "dashing",
            room = "Автомобиль",
            types = types,
        )
        assertEquals(1, devices.size)
        val climate = devices.single()
        assertEquals("Климат", climate.name)
        assertEquals(7, climate.type)
        assertEquals("Автомобиль", climate.room)
        assertEquals(setOf("hvac_power", "hvac_temperature_left"), climate.objectIds)
        assertEquals("tbox/dashing/hvac_power/set", climate.onOff.single().topicCmd)
        assertEquals("tbox/dashing/hvac_power/state", climate.onOff.single().topicState)
        assertEquals("ON", climate.onOff.single().cmdOn)
        assertEquals("OFF", climate.onOff.single().cmdOff)
        val range = climate.range.single()
        assertEquals(3, range.type)
        assertEquals("tbox/dashing/hvac_temperature_left/set", range.topicCmd)
        assertEquals("tbox/dashing/hvac_temperature_left/state", range.topicState)
        assertEquals(16.0, range.min, 0.0)
        assertEquals(30.0, range.max, 0.0)
        assertEquals(0.5, range.precision, 0.0)
    }

    @Test
    fun sensorsMapTemperatureAndFuelTopics() {
        val types = catalog(
            devices = listOf(
                WqttNamedType(19, "Датчик температуры"),
                WqttNamedType(20, "Датчик"),
            ),
            floats = listOf(
                WqttNamedType(1, "temperature"),
                WqttNamedType(9, "произвольный"),
            ),
        )
        val devices = WqttDeviceMapper.buildDevices(
            packs = setOf(WqttPackId.SENSORS),
            entities = listOf(
                sensor("outside_temperature", "Улица", "°C"),
                sensor("inside_temperature", "Салон", "°C"),
                sensor("fuel_level_percent", "Топливо", "%"),
                sensor("distance_to_empty_km", "Запас хода", "км"),
            ),
            topicPrefix = "tbox",
            deviceId = "dashing",
            room = "Гараж",
            types = types,
        )
        assertEquals(4, devices.size)
        val outside = devices.single { it.stableKey == "sensor_outside_temperature" }
        assertEquals(19, outside.type)
        assertEquals(1, outside.sensorFloat.single().type)
        assertEquals("tbox/dashing/outside_temperature/state", outside.sensorFloat.single().topicState)
        assertTrue(outside.onOff.isEmpty())
        val fuel = devices.single { it.stableKey == "sensor_fuel_level_percent" }
        assertEquals(20, fuel.type)
        assertEquals(9, fuel.sensorFloat.single().type)
        assertEquals("Гараж", fuel.room)
    }

    @Test
    fun bodyPackWarnsViaPackFlagAndMapsWindows() {
        assertTrue(WqttPacks.byId(WqttPackId.BODY).warns)
        val types = catalog(devices = listOf(WqttNamedType(11, "Окно"), WqttNamedType(2, "Выключатель")))
        val devices = WqttDeviceMapper.buildDevices(
            packs = setOf(WqttPackId.BODY),
            entities = listOf(
                window("window_front_left", "Окно водителя"),
                CatalogEntity(
                    objectId = "can_vehicle_99_open",
                    label = "Багажник: Открыть",
                    description = "Кнопка",
                    group = EntityGroup.BODY,
                    component = HaComponent.BUTTON,
                    writable = true,
                    canCommand = CanCommandBinding("vehicle", 99, "trunk_pulse", fixedText = "open"),
                ),
            ),
            topicPrefix = "tbox",
            deviceId = "dashing",
            room = "Автомобиль",
            types = types,
        )
        assertEquals(2, devices.size)
        val window = devices.single { it.stableKey == "body_window_front_left" }
        assertEquals(11, window.type)
        assertEquals("opened", window.onOff.single().cmdOn)
        assertEquals("closed", window.onOff.single().cmdOff)
        val trunk = devices.single { it.stableKey == "body_can_vehicle_99_open" }
        assertEquals("PRESS", trunk.onOff.single().cmdOn)
    }

    @Test
    fun missingEntitiesAreSkipped() {
        val devices = WqttDeviceMapper.buildDevices(
            packs = setOf(WqttPackId.CLIMATE, WqttPackId.SENSORS),
            entities = emptyList(),
            topicPrefix = "tbox",
            deviceId = "dashing",
            room = "Автомобиль",
            types = WqttTypeCatalog(),
        )
        assertTrue(devices.isEmpty())
    }

    @Test
    fun payloadJsonOmitsLocalFields() {
        val payload = WqttDevicePayload(
            stableKey = "climate_thermostat",
            name = "Климат",
            type = 7,
            room = "Автомобиль",
            onOff = listOf(
                WqttOnOff(
                    topicCmd = "tbox/dashing/hvac_power/set",
                    topicState = "tbox/dashing/hvac_power/state",
                ),
            ),
            range = listOf(
                WqttRange(
                    type = 3,
                    topicCmd = "tbox/dashing/hvac_temperature_left/set",
                    topicState = "tbox/dashing/hvac_temperature_left/state",
                    min = 16.0,
                    max = 30.0,
                    precision = 0.5,
                ),
            ),
            objectIds = setOf("hvac_power"),
        )
        val json = payload.toJson()
        assertFalse(json.has("stableKey"))
        assertFalse(json.has("objectIds"))
        assertEquals("Климат", json.getString("name"))
        assertEquals(7, json.getInt("type"))
        assertEquals(1, json.getJSONArray("on_off").length())
        assertEquals(1, json.getJSONArray("range").length())
        assertEquals(
            "tbox/dashing/hvac_temperature_left/state",
            json.getJSONArray("range").getJSONObject(0).getString("topic_state"),
        )
    }

    @Test
    fun typeResolverPrefersRussianKeywords() {
        val types = catalog(
            devices = listOf(
                WqttNamedType(2, "Выключатель / реле"),
                WqttNamedType(19, "Датчик температуры"),
                WqttNamedType(5, "Кондиционер"),
            ),
            ranges = listOf(WqttNamedType(4, "Уставка температуры")),
            floats = listOf(WqttNamedType(1, "Температура")),
        )
        assertEquals(2, WqttTypeResolver.switchType(types))
        assertEquals(19, WqttTypeResolver.temperatureSensorType(types))
        assertEquals(5, WqttTypeResolver.thermostatType(types))
        assertEquals(4, WqttTypeResolver.temperatureRangeType(types))
        assertEquals(1, WqttTypeResolver.temperatureFloatType(types))
    }

    @Test
    fun tokenNormalizationStripsPrefixes() {
        assertEquals("abc", WqttApi.normalizeToken("  Token abc  "))
        assertEquals("abc", WqttApi.normalizeToken("Bearer abc"))
        assertEquals("Token abc", WqttApi.authHeader("abc"))
        assertEquals("Token abc", WqttApi.authHeader("Token abc"))
    }

    private fun catalog(
        devices: List<WqttNamedType> = emptyList(),
        ranges: List<WqttNamedType> = emptyList(),
        floats: List<WqttNamedType> = emptyList(),
    ) = WqttTypeCatalog(
        devices = devices,
        ranges = ranges,
        sensorFloats = floats,
    )

    private fun hvacPower() = CatalogEntity(
        objectId = "hvac_power",
        label = "Питание климата",
        description = "Состояние и команда",
        group = EntityGroup.CLIMATE,
        component = HaComponent.SWITCH,
        writable = true,
        pairs = listOf(WritePair("off", null, "off"), WritePair("on", null, "on")),
        canCommand = CanCommandBinding("vehicle", 11, "set"),
    )

    private fun hvacTemp() = CatalogEntity(
        objectId = "hvac_temperature_left",
        label = "Температура слева",
        description = "°C",
        group = EntityGroup.CLIMATE,
        component = HaComponent.NUMBER,
        unit = "°C",
        writable = true,
        numberMin = 16.0,
        numberMax = 30.0,
        numberStep = 0.5,
        pairs = listOf(WritePair("22", 220.0, null)),
        canCommand = CanCommandBinding("vehicle", 12, "set"),
    )

    private fun sensor(objectId: String, label: String, unit: String) = CatalogEntity(
        objectId = objectId,
        label = label,
        description = unit,
        group = EntityGroup.TRIP,
        component = HaComponent.SENSOR,
        unit = unit,
        signalId = objectId,
    )

    private fun window(objectId: String, label: String) = CatalogEntity(
        objectId = objectId,
        label = label,
        description = "Состояние и команда",
        group = EntityGroup.BODY,
        component = HaComponent.SELECT,
        writable = true,
        options = listOf("closed", "open", "vent", "opened"),
        pairs = listOf(
            WritePair("closed", null, "close"),
            WritePair("vent", null, "vent"),
            WritePair("opened", null, "open"),
        ),
        canCommand = CanCommandBinding("vehicle", 5, "set"),
    )
}
