package vad.dashing.tbox.mbcan

/**
 * Expert raw window «Снимок / Сравнить»: read every readable row, flip something
 * in the car, read again and list only what changed. Pure helpers for tests.
 */
object ExpertRawSnapshot {
    data class Change(val key: String, val before: String?, val after: String?)

    const val FAILED_VALUE = "fail"

    fun key(param: ExpertRawCanParam): String = "${param.bus.name}/${param.name}(${param.mbCanId})"

    fun entries(param: ExpertRawCanParam, result: ExpertRawGetResult): List<Pair<String, String>> {
        val key = key(param)
        if (!result.success) return listOf(key to FAILED_VALUE)
        result.fields?.let { fields -> return fields.map { (field, value) -> "$key.$field" to value } }
        return listOf(key to (result.rawValue?.toString() ?: "null"))
    }

    fun toMap(results: List<Pair<ExpertRawCanParam, ExpertRawGetResult>>): Map<String, String> {
        val map = LinkedHashMap<String, String>()
        results.forEach { (param, result) -> entries(param, result).forEach { (k, v) -> map[k] = v } }
        return map
    }

    fun diff(before: Map<String, String>, after: Map<String, String>): List<Change> {
        val keys = LinkedHashSet<String>(before.keys).apply { addAll(after.keys) }
        return keys.mapNotNull { key ->
            val old = before[key]
            val new = after[key]
            if (old == new) null else Change(key, old, new)
        }
    }

    fun okCount(snapshot: Map<String, String>): Int = snapshot.values.count { it != FAILED_VALUE }

    fun formatChange(change: Change): String =
        "${change.key}: ${change.before ?: "—"} → ${change.after ?: "—"}"
}
