package vad.dashing.camd

/**
 * Minimal dependency-free JSON for the prefs blob and export/import files.
 * Plain data model so unit tests run without Robolectric.
 */
sealed class CamdJson {
    data class Obj(val fields: Map<String, CamdJson>) : CamdJson()
    data class Arr(val items: List<CamdJson>) : CamdJson()
    data class Str(val value: String) : CamdJson()
    data class Num(val value: Double) : CamdJson()
    data class Bool(val value: Boolean) : CamdJson()
    data object Null : CamdJson()

    fun asObj(): Map<String, CamdJson>? = (this as? Obj)?.fields
    fun asArr(): List<CamdJson>? = (this as? Arr)?.items
    fun asStr(): String? = (this as? Str)?.value
    fun asNum(): Double? = (this as? Num)?.value
    fun asInt(): Int? = (this as? Num)?.value?.toInt()
    fun asBool(): Boolean? = (this as? Bool)?.value

    companion object {
        fun str(value: String): CamdJson = Str(value)
        fun num(value: Int): CamdJson = Num(value.toDouble())
        fun num(value: Float): CamdJson = Num(value.toDouble())
        fun bool(value: Boolean): CamdJson = Bool(value)
        fun arr(items: List<CamdJson>): CamdJson = Arr(items)
        fun obj(fields: List<Pair<String, CamdJson>>): CamdJson = Obj(fields.toMap())

        fun write(value: CamdJson): String {
            val sb = StringBuilder()
            writeValue(sb, value)
            return sb.toString()
        }

        fun parse(text: String): CamdJson? {
            val parser = Parser(text)
            val value = parser.parseValue() ?: return null
            if (!parser.atEnd()) return null
            return value
        }

        private fun writeValue(sb: StringBuilder, value: CamdJson) {
            when (value) {
                is Null -> sb.append("null")
                is Bool -> sb.append(if (value.value) "true" else "false")
                is Num -> {
                    val v = value.value
                    if (v == Math.floor(v) && v.isFinite() && Math.abs(v) < 1e15) {
                        sb.append(v.toLong().toString())
                    } else {
                        sb.append(v.toString())
                    }
                }
                is Str -> writeString(sb, value.value)
                is Arr -> {
                    sb.append('[')
                    value.items.forEachIndexed { index, item ->
                        if (index > 0) sb.append(',')
                        writeValue(sb, item)
                    }
                    sb.append(']')
                }
                is Obj -> {
                    sb.append('{')
                    value.fields.entries.forEachIndexed { index, entry ->
                        if (index > 0) sb.append(',')
                        writeString(sb, entry.key)
                        sb.append(':')
                        writeValue(sb, entry.value)
                    }
                    sb.append('}')
                }
            }
        }

        private fun writeString(sb: StringBuilder, value: String) {
            sb.append('"')
            for (ch in value) {
                when (ch) {
                    '"' -> sb.append("\\\"")
                    '\\' -> sb.append("\\\\")
                    '\n' -> sb.append("\\n")
                    '\r' -> sb.append("\\r")
                    '\t' -> sb.append("\\t")
                    '\b' -> sb.append("\\b")
                    '\u000C' -> sb.append("\\f")
                    else -> if (ch < ' ') sb.append("\\u%04x".format(ch.code)) else sb.append(ch)
                }
            }
            sb.append('"')
        }

        private class Parser(private val text: String) {
            private var pos = 0

            fun atEnd(): Boolean {
                skipSpace()
                return pos >= text.length
            }

            fun parseValue(): CamdJson? {
                skipSpace()
                if (pos >= text.length) return null
                return when (text[pos]) {
                    '{' -> parseObject()
                    '[' -> parseArray()
                    '"' -> parseString()?.let { Str(it) }
                    't' -> literal("true", Bool(true))
                    'f' -> literal("false", Bool(false))
                    'n' -> literal("null", Null)
                    else -> parseNumber()
                }
            }

            private fun skipSpace() {
                while (pos < text.length && text[pos] in " \t\r\n") pos++
            }

            private fun literal(word: String, value: CamdJson): CamdJson? {
                if (!text.startsWith(word, pos)) return null
                pos += word.length
                return value
            }

            private fun parseObject(): CamdJson? {
                pos++
                val fields = LinkedHashMap<String, CamdJson>()
                skipSpace()
                if (pos < text.length && text[pos] == '}') {
                    pos++
                    return Obj(fields)
                }
                while (true) {
                    skipSpace()
                    if (pos >= text.length || text[pos] != '"') return null
                    val key = parseString() ?: return null
                    skipSpace()
                    if (pos >= text.length || text[pos] != ':') return null
                    pos++
                    val value = parseValue() ?: return null
                    fields[key] = value
                    skipSpace()
                    if (pos >= text.length) return null
                    when (text[pos]) {
                        ',' -> pos++
                        '}' -> {
                            pos++
                            return Obj(fields)
                        }
                        else -> return null
                    }
                }
            }

            private fun parseArray(): CamdJson? {
                pos++
                val items = ArrayList<CamdJson>()
                skipSpace()
                if (pos < text.length && text[pos] == ']') {
                    pos++
                    return Arr(items)
                }
                while (true) {
                    val value = parseValue() ?: return null
                    items.add(value)
                    skipSpace()
                    if (pos >= text.length) return null
                    when (text[pos]) {
                        ',' -> pos++
                        ']' -> {
                            pos++
                            return Arr(items)
                        }
                        else -> return null
                    }
                }
            }

            private fun parseString(): String? {
                if (pos >= text.length || text[pos] != '"') return null
                pos++
                val sb = StringBuilder()
                while (true) {
                    if (pos >= text.length) return null
                    when (val ch = text[pos]) {
                        '"' -> {
                            pos++
                            return sb.toString()
                        }
                        '\\' -> {
                            pos++
                            if (pos >= text.length) return null
                            when (val escape = text[pos]) {
                                '"' -> sb.append('"')
                                '\\' -> sb.append('\\')
                                '/' -> sb.append('/')
                                'b' -> sb.append('\b')
                                'f' -> sb.append('\u000C')
                                'n' -> sb.append('\n')
                                'r' -> sb.append('\r')
                                't' -> sb.append('\t')
                                'u' -> {
                                    if (pos + 4 >= text.length) return null
                                    val hex = text.substring(pos + 1, pos + 5)
                                    val code = hex.toIntOrNull(16) ?: return null
                                    sb.append(code.toChar())
                                    pos += 4
                                }
                                else -> return null
                            }
                            pos++
                        }
                        else -> {
                            if (ch < ' ') return null
                            sb.append(ch)
                            pos++
                        }
                    }
                }
            }

            private fun parseNumber(): CamdJson? {
                val start = pos
                if (pos < text.length && text[pos] == '-') pos++
                var digits = 0
                while (pos < text.length && text[pos] in '0'..'9') {
                    pos++
                    digits++
                }
                if (digits == 0) return null
                if (pos < text.length && text[pos] == '.') {
                    pos++
                    var fraction = 0
                    while (pos < text.length && text[pos] in '0'..'9') {
                        pos++
                        fraction++
                    }
                    if (fraction == 0) return null
                }
                if (pos < text.length && (text[pos] == 'e' || text[pos] == 'E')) {
                    pos++
                    if (pos < text.length && (text[pos] == '+' || text[pos] == '-')) pos++
                    var exponent = 0
                    while (pos < text.length && text[pos] in '0'..'9') {
                        pos++
                        exponent++
                    }
                    if (exponent == 0) return null
                }
                return text.substring(start, pos).toDoubleOrNull()?.let { Num(it) }
            }
        }
    }
}
