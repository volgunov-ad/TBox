package vad.dashing.mqtt.wqtt

/**
 * Picks WQTT numeric type ids from [WqttTypeCatalog] by Russian name keywords.
 * Fallbacks mirror the public API examples (switch=2, sensor=19, light=0).
 */
object WqttTypeResolver {
    const val FALLBACK_SWITCH = 2
    const val FALLBACK_SENSOR = 19
    const val FALLBACK_LIGHT = 0
    const val FALLBACK_RANGE_BRIGHTNESS = 0
    const val FALLBACK_SENSOR_TEMPERATURE = 0

    fun switchType(catalog: WqttTypeCatalog): Int =
        find(catalog.devices, "выключ", "реле", "переключ") ?: FALLBACK_SWITCH

    fun thermostatType(catalog: WqttTypeCatalog): Int =
        find(catalog.devices, "термостат")
            ?: find(catalog.devices, "кондиционер")
            ?: FALLBACK_LIGHT

    fun temperatureSensorType(catalog: WqttTypeCatalog): Int =
        find(catalog.devices, "температур")
            ?: find(catalog.devices, "датчик")
            ?: FALLBACK_SENSOR

    /** Generic float/number sensor — avoid picking «Датчик температуры». */
    fun floatSensorType(catalog: WqttTypeCatalog): Int =
        findExcluding(catalog.devices, include = listOf("датчик"), exclude = listOf("температур"))
            ?: find(catalog.devices, "датчик")
            ?: FALLBACK_SENSOR

    fun openableType(catalog: WqttTypeCatalog): Int =
        find(catalog.devices, "окн", "штор", "двер", "ворот")
            ?: switchType(catalog)

    fun temperatureRangeType(catalog: WqttTypeCatalog): Int =
        find(catalog.ranges, "температур", "temperature", "уставк")
            ?: FALLBACK_RANGE_BRIGHTNESS

    fun temperatureFloatType(catalog: WqttTypeCatalog): Int =
        find(catalog.sensorFloats, "температур", "temperature", "temp")
            ?: FALLBACK_SENSOR_TEMPERATURE

    fun genericFloatType(catalog: WqttTypeCatalog): Int {
        val exclude = listOf("температур", "temperature", "temp")
        findExcluding(
            catalog.sensorFloats,
            include = listOf("произвольн", "float", "числ", "generic"),
            exclude = exclude,
        )?.let { return it }
        val nonTemp = catalog.sensorFloats.firstOrNull { type ->
            val name = type.name.lowercase()
            exclude.none { bad -> name.contains(bad) }
        }
        return nonTemp?.id
            ?: catalog.sensorFloats.firstOrNull()?.id
            ?: FALLBACK_SENSOR_TEMPERATURE
    }

    fun openEventType(catalog: WqttTypeCatalog): Int =
        find(catalog.sensorEvents, "открыт", "двер")
            ?: catalog.sensorEvents.firstOrNull()?.id
            ?: 0

    private fun find(types: List<WqttNamedType>, vararg needles: String): Int? {
        val lowered = types.map { it to it.name.lowercase() }
        for (needle in needles) {
            val hit = lowered.firstOrNull { (_, name) -> name.contains(needle.lowercase()) }
            if (hit != null) return hit.first.id
        }
        return null
    }

    private fun findExcluding(
        types: List<WqttNamedType>,
        include: List<String>,
        exclude: List<String>,
    ): Int? {
        val lowered = types.map { it to it.name.lowercase() }
        for (needle in include) {
            val hit = lowered.firstOrNull { (_, name) ->
                name.contains(needle.lowercase()) &&
                    exclude.none { bad -> name.contains(bad.lowercase()) }
            }
            if (hit != null) return hit.first.id
        }
        return null
    }
}
