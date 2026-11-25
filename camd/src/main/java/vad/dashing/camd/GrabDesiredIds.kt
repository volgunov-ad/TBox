package vad.dashing.camd

/**
 * Pure union of overlay source ids and recorder physical inputs for `grab.desired`.
 * Stock OPEN ids (40–45) collapse to their physical grabber input via [CamdSources].
 */
internal object GrabDesiredIds {
    fun compute(
        openSourceIds: Collection<Int>,
        recorderPhysicalIds: Collection<Int>,
    ): Set<Int> =
        (openSourceIds.mapNotNull { CamdSources.physicalInput(it) } + recorderPhysicalIds)
            .toSortedSet()

    /**
     * `grab.desired` lines: `<id>` publishes every frame, `<id> <divisor>` every
     * divisor-th — only for an input no window shows, since a window needs the full rate.
     * An older grabber reads the leading id and ignores the rest.
     */
    fun lines(
        openSourceIds: Collection<Int>,
        recorderPhysicalIds: Collection<Int>,
        recorderDivisor: Int,
    ): List<String> {
        val shown = openSourceIds.mapNotNull { CamdSources.physicalInput(it) }.toSet()
        return compute(openSourceIds, recorderPhysicalIds).map { id ->
            if (recorderDivisor > 1 && id !in shown) "$id $recorderDivisor" else "$id"
        }
    }
}
