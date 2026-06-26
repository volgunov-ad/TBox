package vad.dashing.tbox

import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

const val TIME_WIDGET_DATA_KEY = "timeWidget"
const val DATE_WIDGET_DATA_KEY = "dateWidget"

fun isDateTimeWidgetDataKey(dataKey: String): Boolean =
    dataKey == TIME_WIDGET_DATA_KEY || dataKey == DATE_WIDGET_DATA_KEY

fun normalizeDateTimeWidgetFormat(dataKey: String, rawFormat: String): String {
    if (!isDateTimeWidgetDataKey(dataKey)) return ""
    return normalizeRussianDateTimePattern(rawFormat.trim(), dataKey)
}

fun sanitizeDateTimeWidgetFormat(dataKey: String, rawFormat: String): String {
    val normalized = normalizeDateTimeWidgetFormat(dataKey, rawFormat)
    if (normalized.isBlank()) return ""
    return if (createCustomDateTimeWidgetFormatOrNull(dataKey, normalized, Locale.getDefault()) != null) {
        normalized
    } else {
        ""
    }
}

fun isValidDateTimeWidgetFormat(dataKey: String, rawFormat: String): Boolean {
    if (!isDateTimeWidgetDataKey(dataKey)) return false
    if (rawFormat.isBlank()) return true
    return createCustomDateTimeWidgetFormatOrNull(dataKey, rawFormat, Locale.getDefault()) != null
}

fun formatDateTimeWidgetValue(
    dataKey: String,
    rawFormat: String,
    date: Date = Date(),
    locale: Locale = Locale.getDefault(),
): String {
    return createDateTimeWidgetDateFormat(dataKey, rawFormat, locale).format(date)
}

fun previewDateTimeWidgetFormat(dataKey: String, rawFormat: String): String? {
    if (!isDateTimeWidgetDataKey(dataKey)) return null
    return runCatching { formatDateTimeWidgetValue(dataKey, rawFormat) }.getOrNull()
}

internal fun createDateTimeWidgetDateFormat(
    dataKey: String,
    rawFormat: String,
    locale: Locale,
): DateFormat {
    if (rawFormat.isNotBlank()) {
        createCustomDateTimeWidgetFormatOrNull(dataKey, rawFormat, locale)?.let { return it }
    }
    return if (dataKey == TIME_WIDGET_DATA_KEY) {
        DateFormat.getTimeInstance(DateFormat.SHORT, locale)
    } else {
        DateFormat.getDateInstance(DateFormat.SHORT, locale)
    }
}

private fun createCustomDateTimeWidgetFormatOrNull(
    dataKey: String,
    rawFormat: String,
    locale: Locale,
): DateFormat? {
    val normalized = normalizeDateTimeWidgetFormat(dataKey, rawFormat)
    if (normalized.isBlank()) return null
    return runCatching {
        SimpleDateFormat(normalized, locale).apply {
            isLenient = false
        }
    }.getOrNull()
}

private fun normalizeRussianDateTimePattern(input: String, dataKey: String): String {
    val out = StringBuilder(input.length)
    var i = 0
    var inQuote = false
    while (i < input.length) {
        val ch = input[i]
        if (ch == '\'') {
            out.append(ch)
            if (i + 1 < input.length && input[i + 1] == '\'') {
                out.append(input[i + 1])
                i += 2
            } else {
                inQuote = !inQuote
                i++
            }
            continue
        }
        if (!inQuote) {
            val lowerTail = input.substring(i).lowercase(Locale.ROOT)
            when {
                lowerTail.startsWith("день") -> {
                    out.append("EEEE")
                    i += 4
                    continue
                }
                lowerTail.startsWith("дн") -> {
                    out.append("EEE")
                    i += 2
                    continue
                }
            }
            val mapped = russianPatternChar(ch, dataKey)
            if (mapped != null) {
                out.append(mapped)
                i++
                continue
            }
        }
        out.append(ch)
        i++
    }
    return out.toString()
}

private fun russianPatternChar(ch: Char, dataKey: String): Char? =
    when (ch) {
        'д', 'Д' -> 'd'
        'г', 'Г' -> 'y'
        'ч', 'Ч' -> 'H'
        'с', 'С' -> 's'
        'Е', 'е' -> 'E'
        'М' -> 'M'
        'м' -> if (dataKey == TIME_WIDGET_DATA_KEY) 'm' else 'M'
        else -> null
    }
