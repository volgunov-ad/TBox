package vad.dashing.tbox.wifimodem

import vad.dashing.tbox.ruEn

/**
 * Canonical [vad.dashing.tbox.NetState] status codes.
 *
 * Widgets and the legacy broadcast show [regStatusLabel] / [simStatusLabel] /
 * [netStatusLabel] (Russian on the ru flavor, English on en). Automations match
 * these codes, and still accept the old Russian literals.
 */
object ModemStatusCodes {
    /** Registration row: TBox MDC "no registration" (was «нет»). */
    const val REG_NONE = "none"

    /** Registration row: explicit no service (was «нет сети»). */
    const val REG_NO_SERVICE = "no_service"
    const val REG_HOME = "home"
    const val REG_SEARCHING = "searching"
    const val REG_DENIED = "denied"
    const val REG_ROAMING = "roaming"

    const val SIM_NONE = "none"
    const val SIM_READY = "ready"
    const val SIM_PIN = "pin"
    const val SIM_ERROR = "error"

    /** Radio generation unknown / no network. 2G, 3G and 4G stay as-is. */
    const val NET_NONE = "none"

    private val REGISTERED = setOf(REG_HOME, REG_ROAMING, "домашняя сеть", "роуминг")

    fun isRegistered(regStatus: String?): Boolean =
        regStatus?.trim().orEmpty() in REGISTERED
}

fun regStatusLabel(code: String?): String = when (code?.trim().orEmpty()) {
    ModemStatusCodes.REG_HOME, "домашняя сеть" -> ruEn("домашняя сеть", "Home network")
    ModemStatusCodes.REG_ROAMING, "роуминг" -> ruEn("роуминг", "Roaming")
    ModemStatusCodes.REG_SEARCHING, "поиск сети" -> ruEn("поиск сети", "Searching")
    ModemStatusCodes.REG_DENIED, "регистрация отклонена" ->
        ruEn("регистрация отклонена", "Registration denied")
    ModemStatusCodes.REG_NONE, "нет" -> ruEn("нет", "No network")
    ModemStatusCodes.REG_NO_SERVICE, "нет сети" -> ruEn("нет сети", "No network")
    else -> code?.trim().orEmpty()
}

fun simStatusLabel(code: String?): String = when (code?.trim().orEmpty()) {
    ModemStatusCodes.SIM_NONE, "нет SIM" -> ruEn("нет SIM", "No SIM")
    ModemStatusCodes.SIM_READY, "SIM готова" -> ruEn("SIM готова", "SIM ready")
    ModemStatusCodes.SIM_PIN, "требуется PIN" -> ruEn("требуется PIN", "PIN required")
    ModemStatusCodes.SIM_ERROR, "ошибка SIM" -> ruEn("ошибка SIM", "SIM error")
    else -> code?.trim().orEmpty()
}

fun netStatusLabel(code: String?): String = when (code?.trim().orEmpty()) {
    ModemStatusCodes.NET_NONE, "нет сети" -> ruEn("нет сети", "No network")
    else -> code?.trim().orEmpty()
}
