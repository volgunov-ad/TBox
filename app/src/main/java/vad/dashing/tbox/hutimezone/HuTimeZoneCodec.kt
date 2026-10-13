package vad.dashing.tbox.hutimezone

/**
 * Adayo `com.adayo.midware.settings` keeps its own `time_zone` (factory `PRC`) and re-applies it
 * on boot and whenever MCU clock sync kicks in, overriding `persist.sys.timezone`. Its binder
 * `setTimeZone` persists the zone, so it is the only change that survives reboot.
 * Transaction codes come from `ISettingsServiceInterfaceAIDL$Stub` in stock `CarSetting.apk`.
 */
internal object HuTimeZoneCodec {
    const val SERVICE = "adayo.setting.v2.0"
    private const val TRANSACTION_SET_TIME_ZONE = 15
    private const val TRANSACTION_GET_TIME_ZONE = 49

    val ZONES = listOf(
        "Europe/Kaliningrad",
        "Europe/Moscow",
        "Europe/Minsk",
        "Europe/Samara",
        "Asia/Yekaterinburg",
        "Asia/Omsk",
        "Asia/Novosibirsk",
        "Asia/Krasnoyarsk",
        "Asia/Irkutsk",
        "Asia/Yakutsk",
        "Asia/Vladivostok",
        "Asia/Magadan",
        "Asia/Kamchatka",
    )

    private val zoneIdPattern = Regex("^[A-Za-z]+(/[A-Za-z_+-]+)+$")
    private val addressPrefix = Regex("0x[0-9a-fA-F]+:")
    private val word = Regex("\\b[0-9a-fA-F]{8}\\b")

    fun readCommand(): String = "service call $SERVICE $TRANSACTION_GET_TIME_ZONE"

    fun writeCommand(zoneId: String): String {
        require(zoneIdPattern.matches(zoneId)) { "bad zone id" }
        return "service call $SERVICE $TRANSACTION_SET_TIME_ZONE s16 $zoneId"
    }

    /** `String` reply of `service call`: words = [exception, length, UTF-16 pairs low-half first]. */
    fun parseString(output: String): String? {
        val words = parcelWords(output)
        if (words.size < 2 || words[0] != 0L) return null
        val length = words[1].toInt()
        if (length < 0 || words.size < 2 + (length + 1) / 2) return null
        return buildString {
            for (k in 0 until length) {
                val w = words[2 + k / 2]
                append((if (k % 2 == 0) w and 0xFFFF else w ushr 16).toInt().toChar())
            }
        }
    }

    /** `int` reply: 0 means the service accepted the zone. */
    fun parseInt(output: String): Int? {
        val words = parcelWords(output)
        if (words.size < 2 || words[0] != 0L) return null
        return words[1].toInt()
    }

    private fun parcelWords(output: String): List<Long> {
        if (!output.contains("Parcel(")) return emptyList()
        return output.substringAfter("Parcel(")
            .lineSequence()
            .map { line -> addressPrefix.replace(line.substringBefore('\''), "") }
            .flatMap { line -> word.findAll(line).map { it.value.toLong(16) } }
            .toList()
    }
}
