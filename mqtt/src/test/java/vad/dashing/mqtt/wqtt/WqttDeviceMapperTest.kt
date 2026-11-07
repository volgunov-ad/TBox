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
    fun selectedSetMapsOneDevicePerEntity() {
        val types = catalog(
            devices = listOf(
                WqttNamedType(2, "Выключатель"),
                WqttNamedType(19, "Датчик температуры"),
                WqttNamedType(20, "Датчик"),
                WqttNamedType(21, "Кнопка"),
            ),
            floats = listOf(
                WqttNamedType(1, "temperature"),
                WqttNamedType(9, "произвольный"),
            ),
        )
        val entities = listOf(
            hvacPower(),
            sensor("outside_temperature", "Улица", "°C"),
            sensor("fuel_level_percent", "Топливо", "%"),
            button("builtin_open_menu", "Открыть меню"),
        )
        val result = WqttDeviceMapper.buildFromSelected(
            selectedObjectIds = entities.map { it.objectId }.toSet(),
            entities = entities,
            topicPrefix = "tbox",
            deviceId = "dashing",
            room = "Автомобиль",
            types = types,
            compositeClimate = false,
        )
        assertEquals(4, result.devices.size)
        assertTrue(result.skipped.isEmpty())
        assertEquals(
            setOf(
                "entity_hvac_power",
                "entity_outside_temperature",
                "entity_fuel_level_percent",
                "entity_builtin_open_menu",
            ),
            result.devices.map { it.stableKey }.toSet(),
        )
        val power = result.devices.single { it.stableKey == "entity_hvac_power" }
        assertEquals("ON", power.onOff.single().cmdOn)
        assertEquals("tbox/dashing/hvac_power/set", power.onOff.single().topicCmd)
        val outside = result.devices.single { it.stableKey == "entity_outside_temperature" }
        assertEquals("tbox/dashing/outside_temperature/state", outside.sensorFloat.single().topicState)
        val button = result.devices.single { it.stableKey == "entity_builtin_open_menu" }
        assertEquals("PRESS", button.onOff.single().cmdOn)
        assertEquals(21, button.type)
    }

    @Test
    fun trunkPulseAndAutomationUseButtonTypeWithPress() {
        val types = catalog(
            devices = listOf(
                WqttNamedType(2, "Выключатель / реле"),
                WqttNamedType(21, "Кнопка"),
            ),
        )
        val entities = listOf(
            CatalogEntity(
                objectId = "can_vehicle_99_close",
                label = "Багажник: Закрыть",
                description = "Кнопка",
                group = EntityGroup.BODY,
                component = HaComponent.BUTTON,
                writable = true,
                canCommand = CanCommandBinding("vehicle", 99, "trunk_pulse", fixedText = "close"),
            ),
            CatalogEntity(
                objectId = "automation_abc",
                label = "Прогрев",
                description = "Выполнить сейчас",
                group = EntityGroup.OTHER,
                component = HaComponent.BUTTON,
                writable = true,
                automationId = "abc",
            ),
        )
        val result = WqttDeviceMapper.buildFromSelected(
            selectedObjectIds = entities.map { it.objectId }.toSet(),
            entities = entities,
            topicPrefix = "tbox",
            deviceId = "dashing",
            room = "Автомобиль",
            types = types,
        )
        assertEquals(2, result.devices.size)
        result.devices.forEach { device ->
            assertEquals(21, device.type)
            assertEquals("PRESS", device.onOff.single().cmdOn)
            assertEquals("PRESS", device.onOff.single().cmdOff)
            assertTrue(device.onOff.single().topicCmd.endsWith("/set"))
            assertEquals("", device.onOff.single().topicState)
        }
        assertEquals(21, WqttTypeResolver.buttonType(types))
        assertEquals(2, WqttTypeResolver.buttonType(catalog(devices = listOf(WqttNamedType(2, "Выключатель")))))
    }

    @Test
    fun sunshadeMapsLikeWindowOpenable() {
        val types = catalog(devices = listOf(WqttNamedType(11, "Штора"), WqttNamedType(2, "Выключатель")))
        val shade = CatalogEntity(
            objectId = "sunshade",
            label = "Шторка",
            description = "Состояние и команда",
            group = EntityGroup.BODY,
            component = HaComponent.SELECT,
            writable = true,
            options = listOf("closed", "opened"),
            pairs = listOf(
                WritePair("closed", null, "close"),
                WritePair("opened", null, "open"),
            ),
            canCommand = CanCommandBinding("vehicle", 8, "set"),
        )
        val result = WqttDeviceMapper.buildFromSelected(
            selectedObjectIds = setOf("sunshade"),
            entities = listOf(shade),
            topicPrefix = "tbox",
            deviceId = "dashing",
            room = "Автомобиль",
            types = types,
        )
        assertEquals(1, result.devices.size)
        assertTrue(result.skipped.isEmpty())
        val device = result.devices.single()
        assertEquals(11, device.type)
        assertEquals("opened", device.onOff.single().cmdOn)
        assertEquals("closed", device.onOff.single().cmdOff)
    }

    @Test
    fun climateCompositeWhenBothPowerAndTempSelected() {
        val types = catalog(
            devices = listOf(WqttNamedType(7, "Термостат"), WqttNamedType(2, "Выключатель")),
            ranges = listOf(WqttNamedType(3, "Температура")),
        )
        val result = WqttDeviceMapper.buildFromSelected(
            selectedObjectIds = setOf("hvac_power", "hvac_temperature_left", "outside_temperature"),
            entities = listOf(hvacPower(), hvacTemp(), sensor("outside_temperature", "Улица", "°C")),
            topicPrefix = "tbox",
            deviceId = "dashing",
            room = "Автомобиль",
            types = types,
        )
        assertEquals(2, result.devices.size)
        val climate = result.devices.single { it.stableKey == "climate_thermostat" }
        assertEquals("Климат", climate.name)
        assertEquals(7, climate.type)
        assertEquals(setOf("hvac_power", "hvac_temperature_left"), climate.objectIds)
        assertEquals("tbox/dashing/hvac_power/set", climate.onOff.single().topicCmd)
        assertEquals(16.0, climate.range.single().min, 0.0)
        assertTrue(result.devices.none { it.stableKey == "entity_hvac_power" })
        assertTrue(result.devices.any { it.stableKey == "entity_outside_temperature" })
    }

    @Test
    fun unmappedEntitiesAreSkippedWithReason() {
        val result = WqttDeviceMapper.buildFromSelected(
            selectedObjectIds = setOf("drive_mode", "geo_position", "media", "missing_id"),
            entities = listOf(
                CatalogEntity(
                    objectId = "drive_mode",
                    label = "Режим вождения",
                    description = "",
                    group = EntityGroup.MOTION,
                    component = HaComponent.SELECT,
                    writable = true,
                    options = listOf("ECO", "NOR", "SPT"),
                    pairs = listOf(WritePair("NOR", 0.0, null)),
                    canCommand = CanCommandBinding("vehicle", 10, "set"),
                ),
                CatalogEntity(
                    objectId = "geo_position",
                    label = "Местоположение",
                    description = "",
                    group = EntityGroup.TRIP,
                    component = HaComponent.DEVICE_TRACKER,
                    signalId = "geo_position",
                ),
                CatalogEntity(
                    objectId = "media",
                    label = "Музыка",
                    description = "",
                    group = EntityGroup.OTHER,
                    component = HaComponent.SENSOR,
                    writable = true,
                    media = vad.dashing.mqtt.ha.MediaBundle(
                        signalIds = setOf("media_title"),
                        source = "app",
                        playAction = "media_play",
                        pauseToggleAction = "media_play_pause",
                        nextAction = "media_next",
                        previousAction = "media_previous",
                        volumeAction = "set_media_volume",
                        volumeMin = 0,
                        volumeMax = 31,
                    ),
                ),
            ),
            topicPrefix = "tbox",
            deviceId = "dashing",
            room = "Автомобиль",
            types = WqttTypeCatalog(),
        )
        assertTrue(result.devices.isEmpty())
        assertEquals(4, result.skipped.size)
        assertTrue(result.skipped.any { it.objectId == "drive_mode" && it.reason.contains("select") })
        assertTrue(result.skipped.any { it.objectId == "geo_position" && it.reason.contains("местоположение") })
        assertTrue(result.skipped.any { it.objectId == "media" && it.reason.contains("Музыка") })
        assertTrue(result.skipped.any { it.objectId == "missing_id" && it.reason.contains("каталоге") })
    }

    @Test
    fun packApplySelectsKnownEntities() {
        val entities = listOf(
            hvacPower(),
            hvacTemp(),
            sensor("outside_temperature", "Улица", "°C"),
            sensor("fuel_level_percent", "Топливо", "%"),
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
        )
        assertEquals(
            setOf("hvac_power", "hvac_temperature_left"),
            WqttPacks.objectIdsForPack(WqttPackId.CLIMATE, entities),
        )
        assertTrue(
            WqttPacks.objectIdsForPack(WqttPackId.SENSORS, entities)
                .containsAll(setOf("outside_temperature", "fuel_level_percent")),
        )
        val body = WqttPacks.objectIdsForPack(WqttPackId.BODY, entities)
        assertTrue(body.contains("window_front_left"))
        assertTrue(body.contains("can_vehicle_99_open"))
        assertTrue(WqttPacks.byId(WqttPackId.BODY).warns)
        assertTrue(
            WqttPacks.isPackFullySelected(
                WqttPackId.CLIMATE,
                setOf("hvac_power", "hvac_temperature_left", "other"),
                entities,
            ),
        )
        assertFalse(
            WqttPacks.isPackFullySelected(WqttPackId.CLIMATE, setOf("hvac_power"), entities),
        )
    }

    @Test
    fun windowAndNumberMapIndividually() {
        val types = catalog(
            devices = listOf(WqttNamedType(11, "Окно"), WqttNamedType(7, "Термостат")),
            ranges = listOf(WqttNamedType(3, "Температура")),
        )
        val result = WqttDeviceMapper.buildFromSelected(
            selectedObjectIds = setOf("window_front_left", "hvac_temperature_left"),
            entities = listOf(window("window_front_left", "Окно водителя"), hvacTemp()),
            topicPrefix = "tbox",
            deviceId = "dashing",
            room = "Автомобиль",
            types = types,
        )
        assertEquals(2, result.devices.size)
        val win = result.devices.single { it.stableKey == "entity_window_front_left" }
        assertEquals(11, win.type)
        assertEquals("opened", win.onOff.single().cmdOn)
        val temp = result.devices.single { it.stableKey == "entity_hvac_temperature_left" }
        assertEquals(7, temp.type)
        assertEquals(1, temp.range.size)
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
            objectIds = setOf("hvac_power"),
        )
        val json = payload.toJson()
        assertFalse(json.has("stableKey"))
        assertFalse(json.has("objectIds"))
        assertEquals("Климат", json.getString("name"))
    }

    @Test
    fun tokenNormalizationStripsPrefixes() {
        assertEquals("abc", WqttApi.normalizeToken("  Token abc  "))
        assertEquals("Token abc", WqttApi.authHeader("abc"))
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

    private fun button(objectId: String, label: String) = CatalogEntity(
        objectId = objectId,
        label = label,
        description = "Кнопка",
        group = EntityGroup.OTHER,
        component = HaComponent.BUTTON,
        writable = true,
        builtinAction = "open_menu",
    )
}
