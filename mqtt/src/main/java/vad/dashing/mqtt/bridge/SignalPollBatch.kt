package vad.dashing.mqtt.bridge

data class SignalRef(
    val id: String,
    val source: String,
)

data class SignalBatch(
    val source: String,
    val ids: List<String>,
)

fun batchSignals(
    signals: List<SignalRef>,
    maxPerRequest: Int = 50,
): List<SignalBatch> {
    val limit = maxPerRequest.coerceAtLeast(1)
    return signals
        .filter { it.id.isNotBlank() && it.source.isNotBlank() }
        .groupBy { it.source }
        .toSortedMap()
        .flatMap { (source, refs) ->
            refs.map { it.id }
                .distinct()
                .chunked(limit)
                .map { SignalBatch(source, it) }
        }
}
