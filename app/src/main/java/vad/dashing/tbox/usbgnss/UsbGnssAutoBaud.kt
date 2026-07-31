package vad.dashing.tbox.usbgnss

/**
 * One-shot USB GNSS baud detection (GPS Connector–style “detect”, not a permanent mode).
 */
object UsbGnssAutoBaud {
    /** Listen window per candidate after applying baud / vendor-init. */
    const val PROBE_MS_PER_BAUD = 2_500L

    /**
     * Try [preferred] first, then common rates, then the rest of [UsbGnssDeviceIds.BAUD_OPTIONS].
     */
    fun candidateBauds(preferred: Int): List<Int> {
        val commonFirst = listOf(
            115_200, 57_600, 9_600, 38_400, 19_200, 230_400, 460_800,
        )
        val out = LinkedHashSet<Int>()
        if (preferred > 0) out.add(preferred)
        for (b in commonFirst) {
            if (b in UsbGnssDeviceIds.BAUD_OPTIONS) out.add(b)
        }
        out.addAll(UsbGnssDeviceIds.BAUD_OPTIONS)
        return out.toList()
    }

    /**
     * NMEA sentence with valid XOR checksum (`$…*HH`).
     * Rejects bare `$` garbage typical of wrong baud.
     */
    fun hasValidChecksum(line: String): Boolean {
        val s = line.trim()
        if (!s.startsWith("$") || s.length < 5) return false
        val star = s.lastIndexOf('*')
        if (star < 2 || star + 3 > s.length) return false
        val expected = s.substring(star + 1, (star + 3).coerceAtMost(s.length))
            .toIntOrNull(16)
            ?: return false
        var xor = 0
        for (i in 1 until star) {
            xor = xor xor s[i].code
        }
        return (xor and 0xff) == expected
    }
}
