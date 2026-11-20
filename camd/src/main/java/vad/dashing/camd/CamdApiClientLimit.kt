package vad.dashing.camd

/**
 * Cap for concurrent `camd-api-client` threads that serve one TCP request each.
 * Extra accepted sockets queue on the pool until a worker is free (P2-3).
 */
internal object CamdApiClientLimit {
    const val MAX_CONCURRENT = 8
}
