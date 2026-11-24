package vad.dashing.mqtt.settings

internal enum class KeystoreFailure {
    NOT_READY,
    CORRUPT,
    OTHER,
}

/**
 * A locked or not-yet-started keystore is retried. Corruption is only a positive marker
 * (bad ciphertext, a broken protobuf, a dead key blob). A bare `KeyStoreException` is not
 * enough: Android reports "System error" and "User authentication required" that way,
 * and Tink throws "exists but is unusable" instead of regenerating the key.
 */
internal fun classifyKeystoreFailure(error: Throwable): KeystoreFailure {
    val text = generateSequence(error) { it.cause }
        .joinToString(" ") { "${it.javaClass.name} ${it.message.orEmpty()}" }
        .lowercase()
    val corrupt = text.contains("aeadbadtag") ||
        text.contains("invalidprotocolbuffer") ||
        text.contains("key blob corrupted") ||
        text.contains("permanently invalidated")
    if (corrupt) return KeystoreFailure.CORRUPT
    val notReady = text.contains("timed out") ||
        text.contains("not initialized") ||
        text.contains("keystore isn") ||
        text.contains("unavailable") ||
        text.contains("system error") ||
        text.contains("user authentication required") ||
        text.contains("unusable") ||
        text.contains("keystore operation failed") ||
        (text.contains("androidkeystore") && text.contains("null"))
    return if (notReady) KeystoreFailure.NOT_READY else KeystoreFailure.OTHER
}

/** How long the same process must keep seeing a corrupt keyset before it is wiped. */
internal const val CORRUPT_REPEAT_MS = 60_000L

/**
 * The first corrupt open is recorded and kept. A later one wipes only from another process,
 * or when [minGapMs] has passed since the first sighting in this process.
 */
internal fun corruptRepeats(
    previousAtElapsedMs: Long,
    previousPid: Int,
    nowElapsedMs: Long,
    pid: Int,
    minGapMs: Long = CORRUPT_REPEAT_MS,
): Boolean {
    if (previousAtElapsedMs <= 0L) return false
    if (previousPid != pid) return true
    return nowElapsedMs - previousAtElapsedMs >= minGapMs
}

internal data class KeystoreRetry<T>(
    val value: T?,
    val error: Throwable?,
    val sleeps: Int,
)

/** [times] attempts. Sleeps [backoffMs] between failures, and not after the last one. */
internal fun <T> retryKeystore(
    times: Int,
    backoffMs: Long,
    sleep: (Long) -> Unit,
    block: () -> T,
): KeystoreRetry<T> {
    var last: Throwable? = null
    var sleeps = 0
    val attempts = times.coerceAtLeast(1)
    for (attempt in 1..attempts) {
        try {
            return KeystoreRetry(block(), null, sleeps)
        } catch (error: Throwable) {
            last = error
            if (attempt < attempts) {
                sleep(backoffMs)
                sleeps += 1
            }
        }
    }
    return KeystoreRetry(null, last, sleeps)
}
