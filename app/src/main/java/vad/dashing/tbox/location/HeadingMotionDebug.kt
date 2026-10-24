package vad.dashing.tbox.location

/**
 * Which live channels the Geoposition → Data & debug screen should show
 * for the heading source the user selected (and for an active fallback).
 */
enum class HeadingMotionDebugChannel {
    HU_IMU,
    STEER,
    WHEELS,
    PHONE,
}

data class HeadingMotionDebug(
    val selected: MockHeadingSource,
    val effective: MockHeadingSource,
    val fallbackReason: String?,
    val channels: Set<HeadingMotionDebugChannel>,
)

/**
 * [fallback] is trusted only when it was published for [selected].
 * The repository default is [MockHeadingSource.GYRO] until the mock job runs,
 * and that must not replace a phone / steer / wheels selection on the debug screen.
 */
fun headingMotionDebug(
    selected: MockHeadingSource,
    fallback: HeadingFallbackStatus = HeadingFallbackStatus(),
): HeadingMotionDebug {
    val published = fallback.selectedSource == selected
    val effective = if (published) fallback.effectiveSource else selected
    val reason = if (published) fallback.fallbackReason else null
    val channels = buildSet {
        if (selected.usesGyro || effective.usesGyro) add(HeadingMotionDebugChannel.HU_IMU)
        if (selected.usesSteer || effective.usesSteer) add(HeadingMotionDebugChannel.STEER)
        if (selected.usesWheels || effective.usesWheels) add(HeadingMotionDebugChannel.WHEELS)
        if (selected.usesPhone || effective.usesPhone) add(HeadingMotionDebugChannel.PHONE)
    }
    return HeadingMotionDebug(
        selected = selected,
        effective = effective,
        fallbackReason = reason,
        channels = channels,
    )
}
