package vad.dashing.voice.nlu

/**
 * Extracts absolute / relative / on-off / open-close / repeat slots from a RU voice phrase.
 * Used with catalog [vad.dashing.voice.api.CatalogWriteSchema] to build invoke values.
 */
object VoiceSlotParser {
    sealed class Slot {
        data class Relative(val steps: Int) : Slot()
        data class AbsoluteNumber(val value: Double) : Slot()
        data class Binary(val on: Boolean) : Slot()
        data class Pulse(val open: Boolean) : Slot()
        /** «ещё» / «ещё раз» — replay last relative adjust. */
        data object RepeatLast : Slot()
        data object None : Slot()
    }

    fun parse(phrase: String): Slot {
        val normalized = normalizeKeepingDecimals(phrase)
        if (normalized.isEmpty()) return Slot.None

        // «ещё потеплее» → relative; bare «ещё» → repeat last.
        relativeSteps(normalized)?.let { return Slot.Relative(it) }
        if (isRepeatCue(normalized)) return Slot.RepeatLast
        pulseCue(normalized)?.let { return Slot.Pulse(it) }
        binaryCue(normalized)?.let { return Slot.Binary(it) }
        absoluteNumber(normalized)?.let { return Slot.AbsoluteNumber(it) }
        return Slot.None
    }

    fun isRepeatCue(phrase: String): Boolean {
        val normalized = normalizeKeepingDecimals(phrase)
        if (normalized.isEmpty()) return false
        return normalized == "еще" ||
            normalized == "еще раз" ||
            normalized == "еще чуть" ||
            normalized == "еще немножко" ||
            normalized == "еще немного" ||
            normalized.startsWith("еще ")
    }

    /** Like [TextNormalizer], but keeps `.` / `,` so «22.5» stays one token. */
    private fun normalizeKeepingDecimals(raw: String): String =
        raw.lowercase()
            .replace('ё', 'е')
            .replace(Regex("[^\\p{L}\\p{N}\\s.,]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun relativeSteps(phrase: String): Int? {
        val warmer = listOf("потеплее", "теплее", "погорячее", "горячее")
        val cooler = listOf("похолоднее", "холоднее", "попрохладнее", "прохладнее")
        val louder = listOf("громче", "погромче")
        val quieter = listOf("тише", "потише")
        val brighter = listOf("ярче", "посветлее", "светлее")
        val darker = listOf("темнее", "потемнее")
        val fanUp = listOf("сильнее обдув", "обдув сильнее", "вентилятор сильнее")
        val fanDown = listOf("слабее обдув", "обдув слабее", "вентилятор слабее")
        val cruiseUp = listOf(
            "круиз плюс",
            "плюс круиз",
            "увеличь круиз",
            "быстрее круиз",
            "круиз выше",
        )
        val cruiseDown = listOf(
            "круиз минус",
            "минус круиз",
            "убавь круиз",
            "медленнее круиз",
            "круиз ниже",
        )
        return when {
            warmer.any { phrase.contains(it) } || louder.any { phrase.contains(it) } ||
                brighter.any { phrase.contains(it) } || fanUp.any { phrase.contains(it) } ||
                cruiseUp.any { phrase.contains(it) } -> +1
            cooler.any { phrase.contains(it) } || quieter.any { phrase.contains(it) } ||
                darker.any { phrase.contains(it) } || fanDown.any { phrase.contains(it) } ||
                cruiseDown.any { phrase.contains(it) } -> -1
            else -> null
        }
    }

    private fun binaryCue(phrase: String): Boolean? {
        val onCues = listOf(
            "включи",
            "включить",
            "включил",
            "активируй",
            "запусти климат",
        )
        val offCues = listOf(
            "выключи",
            "выключить",
            "отключи",
            "отключить",
            "вырубил",
        )
        return when {
            offCues.any { phrase.contains(it) } -> false
            onCues.any { phrase.contains(it) } -> true
            else -> null
        }
    }

    private fun pulseCue(phrase: String): Boolean? {
        val openCues = listOf("открой", "открыть", "открой багажник", "подними багажник")
        val closeCues = listOf("закрой", "закрыть", "опусти багажник")
        return when {
            closeCues.any { phrase.contains(it) } -> false
            openCues.any { phrase.contains(it) } -> true
            else -> null
        }
    }

    private fun absoluteNumber(phrase: String): Double? {
        val match = Regex("""(?<![а-яa-z])(\d+(?:[.,]\d+)?)(?![а-яa-z])""")
            .findAll(phrase)
            .mapNotNull { it.groupValues[1].replace(',', '.').toDoubleOrNull() }
            .toList()
        if (match.isEmpty()) return null
        return match.last()
    }
}
