package vad.dashing.tbox.obd

/**
 * Pure ELM327 / OBD-II text protocol helpers (no I/O).
 */
object Elm327Protocol {
    val INIT_COMMANDS: List<String> = listOf(
        "ATZ",
        "ATE0",
        "ATL0",
        "ATS0",
        "ATH0",
        "ATSP0",
    )

    const val ADAPTER_VOLTAGE_REQUEST = "ATRV"
    const val STORED_DTC_REQUEST = "03"

    fun mode01Request(pid: Int): String =
        "01" + "%02X".format(pid and 0xFF)

    fun isElmError(response: String): Boolean {
        val u = response.uppercase()
        return u.contains("NO DATA") ||
            u.contains("UNABLE TO CONNECT") ||
            u.contains("BUS INIT") ||
            u.contains("CAN ERROR") ||
            u.contains("BUFFER FULL") ||
            u.contains("STOPPED") ||
            u.contains("?") ||
            u.contains("ERROR")
    }

    fun normalizeResponse(raw: String): String =
        raw
            .replace(">", "")
            .replace("SEARCHING...", "", ignoreCase = true)
            .replace("\r", " ")
            .replace("\n", " ")
            .trim()

    fun extractHexBytes(raw: String): List<Int> {
        val cleaned = normalizeResponse(raw)
            .uppercase()
            .replace(" ", "")
            .filter { it in '0'..'9' || it in 'A'..'F' }
        if (cleaned.length < 2) return emptyList()
        val even = if (cleaned.length % 2 == 0) cleaned else cleaned.dropLast(1)
        return even.chunked(2).mapNotNull { pair -> pair.toIntOrNull(16) }
    }

    fun parseMode01DataBytes(raw: String, pid: Int): ByteArray? {
        if (isElmError(raw) && !normalizeResponse(raw).uppercase().contains("41")) {
            return null
        }
        val bytes = extractHexBytes(raw)
        if (bytes.isEmpty()) return null
        val wantPid = pid and 0xFF
        var i = 0
        while (i + 1 < bytes.size) {
            if (bytes[i] == 0x41 && bytes[i + 1] == wantPid) {
                return bytes.drop(i + 2).map { it.toByte() }.toByteArray()
            }
            i++
        }
        return null
    }

    fun decodeMode01Pid(pid: Int, data: ByteArray): Double? {
        if (data.isEmpty()) return null
        fun u(i: Int): Int? = data.getOrNull(i)?.toInt()?.and(0xFF)
        val a = u(0) ?: return null
        val b = u(1)
        return when (pid and 0xFF) {
            0x04 -> a * 100.0 / 255.0
            0x05 -> a - 40.0
            0x0B -> a.toDouble()
            0x0C -> {
                val bb = b ?: return null
                ((a * 256) + bb) / 4.0
            }
            0x0D -> a.toDouble()
            0x0E -> a / 2.0 - 64.0
            0x0F -> a - 40.0
            0x10 -> {
                val bb = b ?: return null
                ((a * 256) + bb) / 100.0
            }
            0x11 -> a * 100.0 / 255.0
            0x1F -> {
                val bb = b ?: return null
                ((a * 256) + bb).toDouble()
            }
            0x2F -> a * 100.0 / 255.0
            0x31 -> {
                val bb = b ?: return null
                ((a * 256) + bb).toDouble()
            }
            0x42 -> {
                val bb = b ?: return null
                ((a * 256) + bb) / 1000.0
            }
            0x46 -> a - 40.0
            else -> null
        }
    }

    fun parseAdapterVoltage(raw: String): Double? {
        val n = normalizeResponse(raw).uppercase().replace("V", "").trim()
        if (isElmError(raw) && !n.any { it.isDigit() }) return null
        val match = Regex("""(\d+(?:\.\d+)?)""").find(n) ?: return null
        return match.groupValues[1].toDoubleOrNull()
    }

    fun parseAdapterVersion(initResponse: String): String? {
        val match = Regex("""(?i)ELM327[ \t]*v?[0-9][^\r\n>]*""").find(initResponse) ?: return null
        return match.value.trim().takeIf { it.isNotEmpty() }
    }

    /**
     * Result of one Mode 01 PID-support bitfield (`0100`, `0120`, …).
     *
     * [supportedPids] are live-data PID bytes in this 32-slot window (excludes the
     * next-bitfield marker PID itself, e.g. `0x20` / `0x40`).
     * [nextBitfieldPid] is set when the ECU reports another support page.
     */
    data class PidSupportBitfield(
        val supportedPids: Set<Int>,
        val nextBitfieldPid: Int?,
    )

    /**
     * Parse `41 xx` support bitfield for [bitfieldPid] (`0x00`, `0x20`, …).
     * Bit A7 → [bitfieldPid]+1, …, bit D0 → [bitfieldPid]+0x20 (next page flag).
     */
    fun parsePidSupportBitfield(raw: String, bitfieldPid: Int): Result<PidSupportBitfield> {
        val base = bitfieldPid and 0xFF
        if (isElmError(raw) && !normalizeResponse(raw).uppercase().contains("41")) {
            return Result.failure(IllegalStateException(normalizeResponse(raw).take(80).ifBlank { "elm_error" }))
        }
        val data = parseMode01DataBytes(raw, base)
            ?: return Result.failure(IllegalStateException("no_bitfield"))
        if (data.size < 4) {
            return Result.failure(IllegalStateException("short_bitfield"))
        }
        val supported = linkedSetOf<Int>()
        var next: Int? = null
        val nextMarker = (base + 0x20) and 0xFF
        for (byteIndex in 0..3) {
            val b = data[byteIndex].toInt() and 0xFF
            for (bit in 7 downTo 0) {
                if ((b shr bit) and 1 == 0) continue
                val offset = byteIndex * 8 + (7 - bit) // 0..31
                val pid = (base + 1 + offset) and 0xFF
                if (pid == nextMarker) {
                    next = nextMarker
                } else {
                    supported.add(pid)
                }
            }
        }
        return Result.success(PidSupportBitfield(supportedPids = supported, nextBitfieldPid = next))
    }

    /** Persist Mode 01 PID bytes as sorted uppercase hex: `04,0C,0D`. */
    fun encodeSupportedPids(pids: Set<Int>): String =
        pids.map { it and 0xFF }.toSortedSet().joinToString(",") { "%02X".format(it) }

    fun decodeSupportedPids(raw: String): Set<Int> =
        raw.split(',')
            .mapNotNull { token ->
                token.trim().takeIf { it.isNotEmpty() }?.toIntOrNull(16)?.and(0xFF)
            }
            .toSet()

    fun parseStoredDtcs(raw: String): Result<List<ObdDtc>> {
        val normalized = normalizeResponse(raw)
        if (normalized.isBlank()) {
            return Result.failure(IllegalStateException("empty"))
        }
        val upper = normalized.uppercase()
        if (upper.contains("NO DATA")) {
            return Result.success(emptyList())
        }
        if (isElmError(raw) && !upper.contains("43")) {
            return Result.failure(IllegalStateException(normalized.take(80)))
        }
        val bytes = extractHexBytes(raw)
        if (bytes.isEmpty()) {
            return Result.failure(IllegalStateException("no hex"))
        }
        val codes = linkedSetOf<ObdDtc>()
        var i = 0
        while (i < bytes.size) {
            if (bytes[i] != 0x43) {
                i++
                continue
            }
            i++
            if (i >= bytes.size) break
            val remaining = bytes.size - i
            val maybeCount = bytes[i]
            val payloadStart = if (
                remaining >= 1 &&
                maybeCount in 0..16 &&
                (remaining - 1) >= maybeCount * 2 &&
                (remaining - 1) % 2 == 0
            ) {
                i + 1
            } else {
                i
            }
            var p = payloadStart
            while (p + 1 < bytes.size) {
                if (bytes[p] == 0x43) break
                val dtc = ObdDtc.fromBytes(bytes[p], bytes[p + 1])
                if (dtc != null) codes.add(dtc)
                p += 2
            }
            i = p
        }
        if (codes.isEmpty() && !upper.contains("43") && !isElmError(raw)) {
            return Result.success(emptyList())
        }
        if (codes.isEmpty() && !upper.contains("43") && isElmError(raw)) {
            return Result.failure(IllegalStateException(normalized.take(80)))
        }
        return Result.success(codes.toList())
    }
}
