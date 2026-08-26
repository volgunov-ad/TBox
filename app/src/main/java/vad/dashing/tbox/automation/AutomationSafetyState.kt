package vad.dashing.tbox.automation

import kotlin.math.abs

/**
 * Latest validity-aware samples required by unattended safety guards.
 *
 * Entries disappear as soon as the provider reports source unavailable. The signal provider keeps
 * the required speed/PRND interests active whenever a guarded action exists.
 */
object AutomationSafetyState {
    const val STATIONARY_SPEED_EPSILON_KMH = 0.01

    @Volatile
    private var samples: Map<AutomationSignalKey, AutomationSignalSample> = emptyMap()

    fun update(sample: AutomationSignalSample) {
        samples = if (sample.value == AutomationSignalValue.Unavailable) {
            samples - sample.key
        } else {
            samples + (sample.key to sample)
        }
    }

    fun snapshot(): Map<AutomationSignalKey, AutomationSignalSample> = samples

    fun clear() {
        samples = emptyMap()
    }

    /**
     * Trunk and similar unattended actuators require every currently live source to independently
     * confirm speed 0 and PRND P. Missing samples fail closed.
     */
    fun isStationaryInPark(
        availableSources: Set<AutomationSignalSource>,
        samples: Map<AutomationSignalKey, AutomationSignalSample> = snapshot(),
    ): Boolean {
        if (availableSources.isEmpty()) return false
        return availableSources.all { source ->
            val speed = (
                samples[AutomationSignalKey(AutomationSignalId.CAR_SPEED, source)]
                    ?.value as? AutomationSignalValue.Number
                )?.value
            val gear = (
                samples[AutomationSignalKey(AutomationSignalId.GEAR_MODE, source)]
                    ?.value as? AutomationSignalValue.State
                )?.value
            speed != null &&
                speed.isFinite() &&
                abs(speed) <= STATIONARY_SPEED_EPSILON_KMH &&
                gear.equals("P", ignoreCase = true)
        }
    }
}
