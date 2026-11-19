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
}
