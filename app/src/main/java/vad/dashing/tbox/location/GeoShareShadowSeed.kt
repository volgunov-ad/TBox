package vad.dashing.tbox.location

import vad.dashing.tbox.esp.LocationSource
import vad.dashing.tbox.location.roadmatch.RoadMatchManualSeed
import vad.dashing.tbox.location.roadmatch.RoadMatchManualSeedRepository
import vad.dashing.tbox.location.roadmatch.RoadMatchOverlayRepository
import vad.dashing.tbox.location.roadmatch.RoadMatchSeedBearing

/**
 * Applies a shared map point to the CONSTANT shadow (same path as F3 «Apply» on the
 * road-match map tile). Travel bearing starts from the current shadow / mock display,
 * then snaps to the nearest road edge within [RoadMatchSeedBearing.MAX_SNAP_DISTANCE_M]
 * (minimal turn, oneway-aware); farther / no graph → course unchanged.
 */
object GeoShareShadowSeed {

    enum class Outcome {
        APPLIED,
        INVALID_POINT,
        MOCK_OFF,
        WRONG_MODE,
        ANDROID_SOURCE,
    }

    fun isShadowSeedMode(power: MockPowerState, storedMode: MockCanSpeedMode): Boolean {
        if (!power.isMockEnabled) return false
        return power.effectiveCanSpeedMode(storedMode).isConstantCalc
    }

    fun currentTravelBearingDeg(): Float {
        val overlay = RoadMatchOverlayRepository.state.value.shadow
        val overlayBearing = overlay.bearingDeg
        if (overlay.visible && overlayBearing != null && overlayBearing.isFinite()) {
            return overlayBearing
        }
        val display = GeoDisplayRepository.state.value.bearingDeg
        if (display != null && display.isFinite()) return display
        return 0f
    }

    fun apply(
        lat: Double,
        lon: Double,
        power: MockPowerState,
        storedMode: MockCanSpeedMode,
        locationSource: LocationSource?,
        travelBearingDeg: Float = currentTravelBearingDeg(),
    ): Outcome {
        if (!power.isMockEnabled) return Outcome.MOCK_OFF
        if (locationSource == LocationSource.ANDROID) return Outcome.ANDROID_SOURCE
        if (!isShadowSeedMode(power, storedMode)) return Outcome.WRONG_MODE
        val bearing = RoadMatchSeedBearing.snapOrKeep(lat, lon, travelBearingDeg)
        val seed = RoadMatchManualSeed.create(lat, lon, bearing)
            ?: return Outcome.INVALID_POINT
        RoadMatchManualSeedRepository.request(seed)
        return Outcome.APPLIED
    }
}
