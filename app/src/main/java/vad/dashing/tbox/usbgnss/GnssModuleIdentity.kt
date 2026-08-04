package vad.dashing.tbox.usbgnss

import org.json.JSONObject
import vad.dashing.tbox.esp.Um980Commands

/** Vendor / protocol family of a GNSS receiver behind a USB UART bridge. */
enum class GnssModuleFamily {
    UNICORE,
    UBLOX,
    MEDIATEK,
    UNKNOWN,
}

/**
 * Persisted probe result for one USB GNSS [stableId].
 *
 * @param model product token when known (e.g. `UM980`)
 * @param versionLabel human-readable version from probe (may be empty)
 */
data class GnssModuleIdentity(
    val family: GnssModuleFamily,
    val model: String = "",
    val versionLabel: String = "",
    val probedAtMs: Long = 0L,
) {
    val isKnown: Boolean get() = family != GnssModuleFamily.UNKNOWN

    /**
     * Shared Unicore UM980 settings dialog applies to the whole [GnssModuleFamily.UNICORE]
     * family. Probe often stores model as plain `"Unicore"` when the VERSIONA product
     * token is missing — reboot already keys off family, so settings must too.
     */
    val isUm980: Boolean
        get() = family == GnssModuleFamily.UNICORE

    fun displayLabel(): String = when {
        versionLabel.isNotBlank() -> versionLabel
        model.isNotBlank() -> model
        isKnown -> family.name
        else -> ""
    }

    /** Pure-Kotlin JSON object (works in JVM unit tests without Android JSON mocks). */
    fun toJsonObjectString(): String = buildString {
        append('{')
        append("\"family\":\"").append(family.name).append('"')
        append(",\"model\":\"").append(escapeJson(model)).append('"')
        append(",\"versionLabel\":\"").append(escapeJson(versionLabel)).append('"')
        append(",\"probedAtMs\":").append(probedAtMs)
        append('}')
    }

    companion object {
        fun fromJsonObject(obj: JSONObject): GnssModuleIdentity? {
            val familyName = obj.optString("family", "").ifBlank { return null }
            val family = runCatching { GnssModuleFamily.valueOf(familyName) }
                .getOrDefault(GnssModuleFamily.UNKNOWN)
            return GnssModuleIdentity(
                family = family,
                model = obj.optString("model", ""),
                versionLabel = obj.optString("versionLabel", ""),
                probedAtMs = obj.optLong("probedAtMs", 0L),
            )
        }

        fun fromJsonObjectString(raw: String): GnssModuleIdentity? {
            val family = Regex("\"family\"\\s*:\\s*\"([^\"]+)\"").find(raw)?.groupValues?.get(1)
                ?: return null
            val model = Regex("\"model\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")
                .find(raw)?.groupValues?.get(1)?.let { unescapeJson(it) }.orEmpty()
            val versionLabel = Regex("\"versionLabel\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")
                .find(raw)?.groupValues?.get(1)?.let { unescapeJson(it) }.orEmpty()
            val probedAtMs = Regex("\"probedAtMs\"\\s*:\\s*(-?\\d+)")
                .find(raw)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            val fam = runCatching { GnssModuleFamily.valueOf(family) }
                .getOrDefault(GnssModuleFamily.UNKNOWN)
            return GnssModuleIdentity(fam, model, versionLabel, probedAtMs)
        }

        fun unknown(atMs: Long = System.currentTimeMillis()): GnssModuleIdentity =
            GnssModuleIdentity(
                family = GnssModuleFamily.UNKNOWN,
                probedAtMs = atMs,
            )
    }
}

internal fun escapeJson(s: String): String =
    s.replace("\\", "\\\\").replace("\"", "\\\"")

internal fun unescapeJson(s: String): String =
    s.replace("\\\"", "\"").replace("\\\\", "\\")

object GnssModuleIdentityCodec {
    fun encodeMap(map: Map<String, GnssModuleIdentity>): String {
        if (map.isEmpty()) return "{}"
        val sb = StringBuilder(64 + map.size * 64)
        sb.append('{')
        var first = true
        for ((id, identity) in map) {
            if (id.isBlank()) continue
            if (!first) sb.append(',')
            first = false
            sb.append('"').append(escapeJson(id)).append('"').append(':')
            sb.append(identity.toJsonObjectString())
        }
        sb.append('}')
        return sb.toString()
    }

    fun decodeMap(raw: String?): Map<String, GnssModuleIdentity> {
        if (raw.isNullOrBlank() || raw == "{}") return emptyMap()
        return decodeMapPure(raw)
    }

    /** Pure parser for `{"id":{...},...}` without org.json.put. */
    fun decodeMapPure(raw: String): Map<String, GnssModuleIdentity> {
        val result = linkedMapOf<String, GnssModuleIdentity>()
        var i = raw.indexOf('{') + 1
        while (i > 0 && i < raw.length) {
            while (i < raw.length && (raw[i].isWhitespace() || raw[i] == ',')) i++
            if (i >= raw.length || raw[i] == '}') break
            if (raw[i] != '"') break
            val idEnd = raw.indexOf('"', i + 1)
            if (idEnd < 0) break
            val id = unescapeJson(raw.substring(i + 1, idEnd))
            i = raw.indexOf('{', idEnd)
            if (i < 0) break
            var depth = 0
            val start = i
            while (i < raw.length) {
                when (raw[i]) {
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) {
                            val obj = raw.substring(start, i + 1)
                            GnssModuleIdentity.fromJsonObjectString(obj)?.let { result[id] = it }
                            i++
                            break
                        }
                    }
                }
                i++
            }
        }
        return result
    }

    /**
     * When USB upgrades `vid:pid` → `vid:pid:serial`, copy the identity onto the new key
     * and drop the short key if it matches the same vid/pid prefix.
     */
    fun migrateStableId(
        map: Map<String, GnssModuleIdentity>,
        fromId: String,
        toId: String,
    ): Map<String, GnssModuleIdentity> {
        if (fromId.isBlank() || toId.isBlank() || fromId == toId) return map
        val existing = map[fromId] ?: return map
        if (map.containsKey(toId)) {
            return map - fromId
        }
        return map - fromId + (toId to existing)
    }
}

object GnssModuleCommands {
    /** Soft (hot) reboot payload when the family is known; null if unsupported. */
    fun softRebootAscii(family: GnssModuleFamily): String? = when (family) {
        GnssModuleFamily.UNICORE -> "RESET"
        GnssModuleFamily.MEDIATEK -> "\$PMTK101*32"
        GnssModuleFamily.UBLOX -> null // binary UBX-CFG-RST via [softRebootUbloxBytes]
        GnssModuleFamily.UNKNOWN -> null
    }

    /** UBX-CFG-RST hot start (navBbrMask=0x0000, resetMode=0x01). */
    fun softRebootUbloxBytes(): ByteArray {
        // payload: 00 00 01 00
        val payload = byteArrayOf(0x00, 0x00, 0x01, 0x00)
        return ubxFrame(classId = 0x06, msgId = 0x04, payload)
    }

    /** UBX-MON-VER poll (empty payload). */
    fun ubloxMonVerPollBytes(): ByteArray = ubxFrame(0x0A, 0x04, ByteArray(0))

    private fun ubxFrame(classId: Int, msgId: Int, payload: ByteArray): ByteArray {
        val len = payload.size
        val body = ByteArray(4 + len)
        body[0] = classId.toByte()
        body[1] = msgId.toByte()
        body[2] = (len and 0xFF).toByte()
        body[3] = ((len shr 8) and 0xFF).toByte()
        System.arraycopy(payload, 0, body, 4, len)
        var ckA = 0
        var ckB = 0
        for (b in body) {
            ckA = (ckA + (b.toInt() and 0xFF)) and 0xFF
            ckB = (ckB + ckA) and 0xFF
        }
        val out = ByteArray(2 + body.size + 2)
        out[0] = 0xB5.toByte()
        out[1] = 0x62
        System.arraycopy(body, 0, out, 2, body.size)
        out[2 + body.size] = ckA.toByte()
        out[3 + body.size] = ckB.toByte()
        return out
    }

    fun mtkQueryVersionAscii(): String = "\$PMTK605*31"

    fun unicoreVersionAscii(): String = "VERSIONA"

    /**
     * Parse probe reply lines into an identity, or null if nothing matched.
     */
    fun parseProbeReplies(lines: List<String>): GnssModuleIdentity? {
        for (raw in lines) {
            val line = raw.trim()
            if (line.contains("VERSIONA", ignoreCase = true) ||
                (line.startsWith("#") && line.contains("UM9", ignoreCase = true))
            ) {
                val label = Um980Commands.formatVersionLine(line)
                val quoted = Regex("\"([^\"]+)\"").findAll(
                    line.substringAfter(';', missingDelimiterValue = line)
                        .substringBefore('*'),
                ).map { it.groupValues[1] }.toList()
                val model = quoted.firstOrNull().orEmpty().ifBlank {
                    when {
                        label.contains("UM980", ignoreCase = true) -> "UM980"
                        label.contains("UM982", ignoreCase = true) -> "UM982"
                        else -> "Unicore"
                    }
                }
                return GnssModuleIdentity(
                    family = GnssModuleFamily.UNICORE,
                    model = model,
                    versionLabel = label,
                    probedAtMs = System.currentTimeMillis(),
                )
            }
            val upper = line.uppercase()
            if (upper.contains("PMTK705") || upper.startsWith("\$PMTK705")) {
                val model = line.substringAfter("PMTK705,").substringBefore('*').trim()
                    .ifBlank { "MediaTek" }
                return GnssModuleIdentity(
                    family = GnssModuleFamily.MEDIATEK,
                    model = model.take(40),
                    versionLabel = model.take(80),
                    probedAtMs = System.currentTimeMillis(),
                )
            }
            if (upper.contains("PUBX") && upper.contains("MOD=")) {
                return GnssModuleIdentity(
                    family = GnssModuleFamily.UBLOX,
                    model = "u-blox",
                    versionLabel = line.take(80),
                    probedAtMs = System.currentTimeMillis(),
                )
            }
        }
        return null
    }

    /** Detect u-blox from raw RX that may contain binary UBX-MON-VER. */
    fun parseUbloxMonVerFromRaw(raw: ByteArray): GnssModuleIdentity? {
        // Look for UBX header + class 0x0A id 0x04
        var i = 0
        while (i + 8 < raw.size) {
            if (raw[i] == 0xB5.toByte() && raw[i + 1] == 0x62.toByte() &&
                raw[i + 2] == 0x0A.toByte() && raw[i + 3] == 0x04.toByte()
            ) {
                val payloadLen = (raw[i + 4].toInt() and 0xFF) or
                    ((raw[i + 5].toInt() and 0xFF) shl 8)
                val start = i + 6
                val end = (start + payloadLen).coerceAtMost(raw.size)
                if (end <= start) {
                    i++
                    continue
                }
                val text = raw.copyOfRange(start, end)
                    .toString(Charsets.US_ASCII)
                    .replace('\u0000', ' ')
                    .trim()
                    .replace(Regex("\\s+"), " ")
                if (text.isNotBlank()) {
                    val model = text.split(' ').firstOrNull()?.take(24).orEmpty().ifBlank { "u-blox" }
                    return GnssModuleIdentity(
                        family = GnssModuleFamily.UBLOX,
                        model = model,
                        versionLabel = text.take(120),
                        probedAtMs = System.currentTimeMillis(),
                    )
                }
            }
            i++
        }
        return null
    }
}
