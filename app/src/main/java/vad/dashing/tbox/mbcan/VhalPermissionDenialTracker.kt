package vad.dashing.tbox.mbcan

/**
 * Tracks VHAL [SecurityException] denials per property id so one denied property
 * does not derate unrelated telemetry (RPM/speed/etc.).
 */
internal class VhalPermissionDenialTracker {
    private val deniedPropertyIds = mutableSetOf<Int>()

    @Synchronized
    fun markDenied(propertyId: Int) {
        deniedPropertyIds.add(propertyId)
    }

    @Synchronized
    fun isDenied(propertyId: Int): Boolean = propertyId in deniedPropertyIds

    /** True when [propertyIds] is non-empty and every id is denied. */
    @Synchronized
    fun areAllDenied(propertyIds: Set<Int>): Boolean =
        propertyIds.isNotEmpty() && propertyIds.all { it in deniedPropertyIds }

    @Synchronized
    fun clear() {
        deniedPropertyIds.clear()
    }

    @Synchronized
    fun deniedIdsSnapshot(): Set<Int> = deniedPropertyIds.toSet()
}
