package vad.dashing.tbox.location

/**
 * Parse a pasted map point into WGS84 lat/lon.
 *
 * Accepts Yandex / Google / 2GIS / OSM / geo: URLs (including `dgis:` and
 * `lat_to`/`lon_to` from Yandex Navigator), decimal pairs
 * (`lat, lon` with comma / space / semicolon / slash), N/E/S/W or
 * с.ш./ю.ш./в.д./з.д., and DMS. Default order is latitude then longitude
 * (Yandex card copy). Yandex `ll=` / `pt=` and 2GIS `geo/` are lon,lat.
 */
object GeoCoordinateParse {
    data class LatLon(val lat: Double, val lon: Double)

    fun parse(raw: String?): LatLon? {
        val text = raw?.replace('\u00A0', ' ')?.replace('\u202F', ' ')?.trim().orEmpty()
        if (text.isEmpty()) return null
        return parseUrl(text)
            ?: parseLabeled(text)
            ?: parseDmsPair(text)
            ?: parseHemisphereDecimals(text)
            ?: parseTwoDecimals(text)
    }

    private fun parseUrl(text: String): LatLon? {
        val yandex = text.contains("yandex.", ignoreCase = true) ||
            text.contains("ya.ru", ignoreCase = true)
        if (yandex) {
            yandexLonLat.find(text)?.let { return lonLat(it) }
        }
        gisLonLat.find(text)?.let { return lonLat(it) }
        googleAt.find(text)?.let { return latLon(it) }
        geoUri.find(text)?.let { return latLon(it) }
        osmHash.find(text)?.let { return latLon(it) }
        queryLatLon.find(text)?.let { return latLon(it) }
        if (!yandex) {
            appleLl.find(text)?.let { return latLon(it) }
        }
        return null
    }

    private val yandexLonLat = Regex(
        """(?:[?&#](?:ll|pt)=)(-?\d+(?:[.,]\d+)?)(?:%2[Cc]|,)(-?\d+(?:[.,]\d+)?)""",
        RegexOption.IGNORE_CASE,
    )
    private val gisLonLat = Regex(
        """(?:2gis\.|dgis:)[^\s]*/geo/(-?\d+(?:[.,]\d+)?),(-?\d+(?:[.,]\d+)?)""",
        RegexOption.IGNORE_CASE,
    )
    private val googleAt = Regex("""@(-?\d+(?:[.,]\d+)?),(-?\d+(?:[.,]\d+)?)""")
    private val queryLatLon = Regex(
        """[?&](?:q|query|sll)=(-?\d+(?:[.,]\d+)?),(-?\d+(?:[.,]\d+)?)""",
        RegexOption.IGNORE_CASE,
    )
    private val appleLl = Regex(
        """[?&]ll=(-?\d+(?:[.,]\d+)?),(-?\d+(?:[.,]\d+)?)""",
        RegexOption.IGNORE_CASE,
    )
    private val geoUri = Regex(
        """geo:(-?\d+(?:[.,]\d+)?),(-?\d+(?:[.,]\d+)?)""",
        RegexOption.IGNORE_CASE,
    )
    private val osmHash = Regex(
        """#map=\d+/(-?\d+(?:[.,]\d+)?)/(-?\d+(?:[.,]\d+)?)""",
        RegexOption.IGNORE_CASE,
    )

    private fun lonLat(m: MatchResult): LatLon? {
        val lon = m.groupValues[1].toCoord() ?: return null
        val lat = m.groupValues[2].toCoord() ?: return null
        return pair(lat, lon)
    }

    private fun latLon(m: MatchResult): LatLon? {
        val lat = m.groupValues[1].toCoord() ?: return null
        val lon = m.groupValues[2].toCoord() ?: return null
        return pair(lat, lon)
    }

    private val labeledLat = Regex(
        """(?:lat(?:itude|_to|_from)?|широт[аы])\s*[:=]?\s*(-?\d+(?:[.,]\d+)?)""",
        RegexOption.IGNORE_CASE,
    )
    private val labeledLon = Regex(
        """(?:lon(?:gitude|_to|_from)?|lng|долгот[аы])\s*[:=]?\s*(-?\d+(?:[.,]\d+)?)""",
        RegexOption.IGNORE_CASE,
    )

    private fun parseLabeled(text: String): LatLon? {
        val lat = labeledLat.find(text)?.groupValues?.get(1)?.toCoord() ?: return null
        val lon = labeledLon.find(text)?.groupValues?.get(1)?.toCoord() ?: return null
        return pair(lat, lon)
    }

    private val hemiToken = """(?:[NSWE]|[сС]\s*\.?\s*[шШ]|[юЮ]\s*\.?\s*[шШ]|[вВ]\s*\.?\s*[дД]|[зЗ]\s*\.?\s*[дД])"""
    private val dmsBody =
        """(-?\d{1,3})\s*[°º]\s*(\d{1,2})(?:\s*['′’]\s*|\s+)(\d{1,2}(?:[.,]\d+)?)?\s*[\"″”]?"""
    private val dmsSpaced =
        """(-?\d{1,3})\s+(\d{1,2})\s+(\d{1,2}(?:[.,]\d+)?)"""

    private val dmsToken = Regex(
        """($hemiToken)?\s*(?:$dmsBody|$dmsSpaced)\s*($hemiToken)?""",
        setOf(RegexOption.IGNORE_CASE),
    )

    private fun parseDmsPair(text: String): LatLon? {
        val matches = dmsToken.findAll(text).toList()
        if (matches.size < 2) return null
        val first = dmsFrom(matches[0]) ?: return null
        val second = dmsFrom(matches[1]) ?: return null
        return orderByHemisphere(first, second)
    }

    private data class SignedCoord(val value: Double, val hemi: Char?)

    private fun dmsFrom(m: MatchResult): SignedCoord? {
        val deg = (m.groupValues[2].ifBlank { m.groupValues[5] }).toCoord() ?: return null
        val min = (m.groupValues[3].ifBlank { m.groupValues[6] }).toCoord() ?: return null
        val secRaw = m.groupValues[4].ifBlank { m.groupValues[7] }
        val sec = if (secRaw.isBlank()) 0.0 else secRaw.toCoord() ?: return null
        if (min !in 0.0..60.0 || sec !in 0.0..60.0) return null
        val abs = kotlin.math.abs(deg) + min / 60.0 + sec / 3600.0
        val hemi = hemisphereOf(m.groupValues[1]) ?: hemisphereOf(m.groupValues[8])
        val signed = applyHemisphere(abs, hemi) ?: if (deg < 0) -abs else abs
        return SignedCoord(signed, hemi)
    }

    private val hemiDecimal = Regex(
        """($hemiToken)\s*([+-]?\d{1,3}(?:[.,]\d+)?)|""" +
            """([+-]?\d{1,3}(?:[.,]\d+)?)\s*($hemiToken)""",
        setOf(RegexOption.IGNORE_CASE),
    )

    private fun parseHemisphereDecimals(text: String): LatLon? {
        val tokens = hemiDecimal.findAll(text).mapNotNull { m ->
            val prefixHemi = hemisphereOf(m.groupValues[1])
            val prefixNum = m.groupValues[2]
            val suffixNum = m.groupValues[3]
            val suffixHemi = hemisphereOf(m.groupValues[4])
            when {
                prefixNum.isNotBlank() && prefixHemi != null -> {
                    val value = prefixNum.toCoord() ?: return@mapNotNull null
                    SignedCoord(applyHemisphere(kotlin.math.abs(value), prefixHemi) ?: value, prefixHemi)
                }
                suffixNum.isNotBlank() && suffixHemi != null -> {
                    val value = suffixNum.toCoord() ?: return@mapNotNull null
                    SignedCoord(applyHemisphere(kotlin.math.abs(value), suffixHemi) ?: value, suffixHemi)
                }
                else -> null
            }
        }.toList()
        if (tokens.size < 2) return null
        return orderByHemisphere(tokens[0], tokens[1])
    }

    private fun orderByHemisphere(a: SignedCoord, b: SignedCoord): LatLon? {
        val aNs = a.hemi != null && a.hemi in NS
        val bEw = b.hemi != null && b.hemi in EW
        val aEw = a.hemi != null && a.hemi in EW
        val bNs = b.hemi != null && b.hemi in NS
        return when {
            aNs && bEw -> pair(a.value, b.value)
            aEw && bNs -> pair(b.value, a.value)
            else -> pair(a.value, b.value)
        }
    }

    private val NS = setOf('N', 'S')
    private val EW = setOf('E', 'W')

    private fun hemisphereOf(raw: String): Char? {
        val t = raw.trim().lowercase().replace(".", "").replace(" ", "")
        if (t.isEmpty()) return null
        return when {
            t == "n" || t == "сш" -> 'N'
            t == "s" || t == "юш" -> 'S'
            t == "e" || t == "вд" -> 'E'
            t == "w" || t == "зд" -> 'W'
            else -> null
        }
    }

    private fun applyHemisphere(abs: Double, hemi: Char?): Double? {
        if (hemi == null) return null
        return when (hemi) {
            'S', 'W' -> -abs
            else -> abs
        }
    }

    private val ruCommaPair = Regex(
        """([+-]?\d{1,3}),(\d{3,})\s*[,;]\s*([+-]?\d{1,3}),(\d{3,})""",
    )
    private val decimalToken = Regex("""[+-]?(?:\d+(?:[.,]\d+)?|\.\d+)""")

    private fun parseTwoDecimals(text: String): LatLon? {
        ruCommaPair.find(text)?.let { m ->
            val lat = "${m.groupValues[1]}.${m.groupValues[2]}".toCoord()
            val lon = "${m.groupValues[3]}.${m.groupValues[4]}".toCoord()
            if (lat != null && lon != null) return pair(lat, lon)
        }
        val nums = decimalToken.findAll(text).mapNotNull { match ->
            val value = match.value.toCoord() ?: return@mapNotNull null
            Num(value, match.value.contains('.') || match.value.contains(','))
        }.toList()
        val pool = if (nums.count { it.fractional } >= 2) nums.filter { it.fractional } else nums
        if (pool.size < 2) return null
        val a = pool[0].value
        val b = pool[1].value
        return when {
            a in -90.0..90.0 && b in -180.0..180.0 -> pair(a, b)
            b in -90.0..90.0 && a in -180.0..180.0 -> pair(b, a)
            else -> null
        }
    }

    private data class Num(val value: Double, val fractional: Boolean)

    private fun String.toCoord(): Double? {
        val n = replace(',', '.').toDoubleOrNull() ?: return null
        return if (n.isFinite()) n else null
    }

    private fun pair(lat: Double, lon: Double): LatLon? {
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        if (lat == 0.0 && lon == 0.0) return null
        return LatLon(lat, lon)
    }
}
