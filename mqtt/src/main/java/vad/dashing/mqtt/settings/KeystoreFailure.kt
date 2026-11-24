package vad.dashing.mqtt.settings

internal enum class KeystoreFailure {
    NOT_READY,
    CORRUPT,
    OTHER,
}

/** Early boot is retried. A bad keyset is wiped once. Anything else is just a failed open. */
internal fun classifyKeystoreFailure(error: Throwable): KeystoreFailure {
    val text = generateSequence(error) { it.cause }
        .joinToString(" ") { "${it.javaClass.name} ${it.message.orEmpty()}" }
        .lowercase()
    val notReady = text.contains("timed out") ||
        text.contains("not initialized") ||
        text.contains("keystore isn") ||
        text.contains("unavailable") ||
        (text.contains("androidkeystore") && text.contains("null"))
    if (notReady) return KeystoreFailure.NOT_READY
    val corrupt = text.contains("keyset") ||
        text.contains("aeadbadtag") ||
        text.contains("invalidprotocolbuffer") ||
        text.contains("mac verification") ||
        text.contains("signature/mac") ||
        text.contains("verifymac") ||
        text.contains("keystoreexception")
    return if (corrupt) KeystoreFailure.CORRUPT else KeystoreFailure.OTHER
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
