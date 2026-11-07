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
    fun climateAndSensorsMapToWqttPayloads() {
        val types = catalog(
            devices = listOf(
                WqttNamedType(2, "Выключатель"),
                WqttNamedType(7, "Термостат"),
                WqttNamedType(19, "Кнопка"),
            ),
            ranges = listOf(WqttNamedType(3, "Температура")),
            floats = listOf(WqttNamedType(1, "Температура")),
        )
        val result = WqttDeviceMapper.buildFromSelected(
            selectedObjectIds = setOf("hvac_power", "outside_temperature"),
            entities = listOf(hvacPower(), sensor("outside_temperature", "Улица", "°C")),
            topicPrefix = "tbox",
            deviceId = "dashing",
            room = "Автомобиль",
            types = types,
            compositeClimate = false,
        )
        assertEquals(2, result.devices.size)
        assertTrue(result.skipped.isEmpty())
        val power = result.devices.single { it.stableKey == "entity_hvac_power" }
        assertEquals(2, power.type)
        assertEquals("ON", power.onOff.single().cmdOn)
        val outside = result.devices.single { it.stableKey == "entity_outside_temperature" }
        assertEquals(1, outside.sensorFloat.single().type)
    }

    @Test
    fun buttonPrefersNamedButtonType() {
        val types = catalog(
            devices = listOf(
                WqttNamedType(2, "Выключатель"),
                WqttNamedType(19, "Кнопка"),
            ),
        )
        val result = WqttDeviceMapper.buildFromSelected(
            selectedObjectIds = setOf("can_vehicle_99_open"),
            entities = listOf(
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
        val device = result.devices.single()
        assertEquals(19, device.type)
        assertEquals("PRESS", device.onOff.single().cmdOn)
        assertEquals("PRESS", device.onOff.single().cmdOff)
        assertEquals("tbox/dashing/can_vehicle_99_open/set", device.onOff.single().topicCmd)
    }

    @Test
    fun windowExpandsToStateSensorAndOptionButtons() {
        val types = catalog(
            devices = listOf(WqttNamedType(19, "Кнопка"), WqttNamedType(2, "Выключатель")),
            floats = listOf(WqttNamedType(5, "Событие")),
        )
        val result = WqttDeviceMapper.buildFromSelected(
            selectedObjectIds = setOf("window_front_left"),
            entities = listOf(window("window_front_left", "Окно водителя")),
            topicPrefix = "tbox",
            deviceId = "dashing",
            room = "Автомобиль",
            types = types,
        )
        assertEquals(4, result.devices.size)
        assertTrue(result.skipped.isEmpty())
        val state = result.devices.single { it.stableKey == "entity_window_front_left_state" }
        assertEquals("Окно водителя", state.name)
        assertEquals(1, state.sensorEvent.size)
        assertEquals("tbox/dashing/window_front_left/state", state.sensorEvent.single().topicState)

        val open = result.devices.single { it.stableKey == "entity_window_front_left_opt_opened" }
        assertEquals("Окно водителя: открыть", open.name)
        assertEquals(19, open.type)
        assertEquals("opened", open.onOff.single().cmdOn)
        assertEquals("tbox/dashing/window_front_left/set", open.onOff.single().topicCmd)

        val close = result.devices.single { it.stableKey == "entity_window_front_left_opt_closed" }
        assertEquals("Окно водителя: закрыть", close.name)
        assertEquals("closed", close.onOff.single().cmdOn)

        val vent = result.devices.single { it.stableKey == "entity_window_front_left_opt_vent" }
        assertEquals("Окно водителя: щель", vent.name)
        assertEquals("vent", vent.onOff.single().cmdOn)
    }

    @Test
    fun sunshadeExpandsLikeWindow() {
        val types = catalog(
            devices = listOf(WqttNamedType(19, "Кнопка")),
            floats = listOf(WqttNamedType(5, "Событие")),
        )
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
        assertEquals(3, result.devices.size)
        assertTrue(result.devices.any { it.stableKey == "entity_sunshade_state" })
        assertTrue(result.devices.any { it.name == "Шторка: открыть" && it.onOff.single().cmdOn == "opened" })
        assertTrue(result.devices.any { it.name == "Шторка: закрыть" && it.onOff.single().cmdOn == "closed" })
    }

    @Test
    fun driveModeExpandsToStateSensorAndOptionButtons() {
        val types = catalog(devices = listOf(WqttNamedType(19, "Кнопка")))
        val result = WqttDeviceMapper.buildFromSelected(
            selectedObjectIds = setOf("drive_mode"),
            entities = listOf(
                CatalogEntity(
                    objectId = "drive_mode",
                    label = "Режим вождения",
                    description = "",
                    group = EntityGroup.MOTION,
                    component = HaComponent.SELECT,
                    writable = true,
                    options = listOf("ECO", "NOR", "SPT"),
                    pairs = listOf(
                        WritePair("ECO", 1.0, null),
                        WritePair("NOR", 0.0, null),
                        WritePair("SPT", 2.0, null),
                    ),
                    canCommand = CanCommandBinding("vehicle", 10, "set"),
                ),
            ),
            topicPrefix = "tbox",
            deviceId = "dashing",
            room = "Автомобиль",
            types = types,
        )
        assertEquals(4, result.devices.size)
        assertTrue(result.skipped.isEmpty())
        assertEquals("Режим вождения", result.devices.single { it.stableKey == "entity_drive_mode_state" }.name)
        val eco = result.devices.single { it.stableKey == "entity_drive_mode_opt_ECO" }
        assertEquals("Режим вождения: ECO", eco.name)
        assertEquals("ECO", eco.onOff.single().cmdOn)
        assertEquals("tbox/dashing/drive_mode/set", eco.onOff.single().topicCmd)
        assertTrue(result.devices.any { it.name == "Режим вождения: NOR" })
        assertTrue(result.devices.any { it.name == "Режим вождения: SPT" })
    }

    @Test
    fun climateCompositePlusIndividualCards() {
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
        assertEquals(4, result.devices.size)
        val climate = result.devices.single { it.stableKey == "climate_thermostat" }
        assertEquals("Климат", climate.name)
        assertEquals(7, climate.type)
        assertEquals(setOf("hvac_power", "hvac_temperature_left"), climate.objectIds)
        assertEquals("tbox/dashing/hvac_power/set", climate.onOff.single().topicCmd)
        assertEquals(16.0, climate.range.single().min, 0.0)
        assertTrue(result.devices.any { it.stableKey == "entity_hvac_power" })
        assertTrue(result.devices.any { it.stableKey == "entity_hvac_temperature_left" })
        assertTrue(result.devices.any { it.stableKey == "entity_outside_temperature" })
    }

    @Test
    fun trunkPulseButtonsStaySeparateFromTrunkDoorSensor() {
        val types = catalog(
            devices = listOf(WqttNamedType(19, "Кнопка")),
            floats = listOf(WqttNamedType(5, "Событие")),
        )
        val trunkDoor = CatalogEntity(
            objectId = "trunk_door",
            label = "Багажник",
            description = "Открыт/закрыт",
            group = EntityGroup.BODY,
            component = HaComponent.BINARY_SENSOR,
            deviceClass = "door",
            signalId = "trunk_door",
        )
        val openBtn = CatalogEntity(
            objectId = "can_vehicle_99_open",
            label = "Багажник: Открыть",
            description = "Кнопка",
            group = EntityGroup.BODY,
            component = HaComponent.BUTTON,
            writable = true,
            canCommand = CanCommandBinding("vehicle", 99, "trunk_pulse", fixedText = "open"),
        )
        val closeBtn = CatalogEntity(
            objectId = "can_vehicle_99_close",
            label = "Багажник: Закрыть",
            description = "Кнопка",
            group = EntityGroup.BODY,
            component = HaComponent.BUTTON,
            writable = true,
            canCommand = CanCommandBinding("vehicle", 99, "trunk_pulse", fixedText = "close"),
        )
        val result = WqttDeviceMapper.buildFromSelected(
            selectedObjectIds = setOf("trunk_door", "can_vehicle_99_open", "can_vehicle_99_close"),
            entities = listOf(trunkDoor, openBtn, closeBtn),
            topicPrefix = "tbox",
            deviceId = "dashing",
            room = "Автомобиль",
            types = types,
        )
        assertEquals(3, result.devices.size)
        assertTrue(result.devices.any { it.stableKey == "entity_trunk_door" && it.sensorEvent.isNotEmpty() })
        assertTrue(result.devices.any { it.stableKey == "entity_can_vehicle_99_open" && it.onOff.single().cmdOn == "PRESS" })
        assertTrue(result.devices.any { it.stableKey == "entity_can_vehicle_99_close" && it.onOff.single().cmdOn == "PRESS" })
    }

    @Test
    fun unmappedEntitiesAreSkippedWithReason() {
        val result = WqttDeviceMapper.buildFromSelected(
            selectedObjectIds = setOf("geo_position", "media", "missing_id"),
            entities = listOf(
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
        assertEquals(3, result.skipped.size)
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
    fun numberAloneMapsIndividually() {
        val types = catalog(
            devices = listOf(WqttNamedType(0, "Свет"), WqttNamedType(7, "Термостат")),
            ranges = listOf(WqttNamedType(3, "Температура"), WqttNamedType(0, "Яркость")),
        )
        val result = WqttDeviceMapper.buildFromSelected(
            selectedObjectIds = setOf("hvac_temperature_left"),
            entities = listOf(hvacTemp()),
            topicPrefix = "tbox",
            deviceId = "dashing",
            room = "Автомобиль",
            types = types,
        )
        assertEquals(1, result.devices.size)
        val temp = result.devices.single { it.stableKey == "entity_hvac_temperature_left" }
        assertEquals(7, temp.type)
        assertEquals(3, temp.range.single().type)
        assertEquals(1, temp.range.size)
    }

    @Test
    fun emptyUnitVolumeSensorMapsAsFloatNotSkipped() {
        val types = catalog(
            devices = listOf(WqttNamedType(19, "Датчик")),
            floats = listOf(WqttNamedType(4, "Произвольное")),
        )
        val volume = CatalogEntity(
            objectId = "hu_media_volume",
            label = "Громкость медиа",
            description = "",
            group = EntityGroup.OTHER,
            component = HaComponent.SENSOR,
            unit = "",
            signalId = "hu_media_volume",
        )
        val result = WqttDeviceMapper.buildFromSelected(
            selectedObjectIds = setOf("hu_media_volume"),
            entities = listOf(volume),
            topicPrefix = "tbox",
            deviceId = "dashing",
            room = "Автомобиль",
            types = types,
        )
        assertTrue(result.skipped.isEmpty())
        val device = result.devices.single()
        assertEquals("entity_hu_media_volume", device.stableKey)
        assertEquals(1, device.sensorFloat.size)
        assertEquals(4, device.sensorFloat.single().type)
        assertTrue(WqttDeviceMapper.isRangeLike(volume))
    }

    @Test
    fun writableBrightnessMapsToDimmerWithRangeDefaults() {
        val types = catalog(
            devices = listOf(WqttNamedType(0, "Свет"), WqttNamedType(7, "Термостат")),
            ranges = listOf(WqttNamedType(3, "Температура"), WqttNamedType(1, "Яркость")),
        )
        val brightness = CatalogEntity(
            objectId = "hu_screen_brightness",
            label = "Яркость экрана",
            description = "",
            group = EntityGroup.OTHER,
            component = HaComponent.NUMBER,
            unit = "",
            writable = true,
            canCommand = CanCommandBinding("vehicle", 50, "set"),
        )
        val result = WqttDeviceMapper.buildFromSelected(
            selectedObjectIds = setOf("hu_screen_brightness"),
            entities = listOf(brightness),
            topicPrefix = "tbox",
            deviceId = "dashing",
            room = "Автомобиль",
            types = types,
        )
        assertTrue(result.skipped.isEmpty())
        val device = result.devices.single()
        assertEquals(0, device.type)
        assertEquals(1, device.range.single().type)
        assertEquals(1.0, device.range.single().min, 0.0)
        assertEquals(10.0, device.range.single().max, 0.0)
        assertEquals("tbox/dashing/hu_screen_brightness/set", device.range.single().topicCmd)
        assertEquals(1.0 to 10.0, WqttDeviceMapper.defaultRangeBounds(brightness))
        assertEquals(
            0.0 to 31.0,
            WqttDeviceMapper.defaultRangeBounds(
                CatalogEntity(
                    objectId = "hu_phone_volume",
                    label = "Телефон",
                    description = "",
                    group = EntityGroup.OTHER,
                    component = HaComponent.NUMBER,
                ),
            ),
        )
    }

    @Test
    fun optionActionLabelsPreferRussianForWindows() {
        assertEquals("открыть", WqttDeviceMapper.optionActionLabel("opened"))
        assertEquals("закрыть", WqttDeviceMapper.optionActionLabel("closed"))
        assertEquals("щель", WqttDeviceMapper.optionActionLabel("vent"))
        assertEquals("ECO", WqttDeviceMapper.optionActionLabel("ECO"))
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
}
