package vad.dashing.tbox.freeform

/**
 * Pure freeform-support decision (unit-testable without Android runtime).
 *
 * [advertised]: FEATURE_FREEFORM / enable_freeform_support / force_resizable /
 * config_supportsFreeformWindowManagement.
 * [adayoOrAndroid10Hu]: Adayo launcher present or HU mode Android 10.
 * [canBuildActivityOptions]: setLaunchWindowingMode + setLaunchBounds available.
 */
internal object FreeformSupportDecision {
    fun evaluate(
        advertised: Boolean,
        adayoOrAndroid10Hu: Boolean,
        canBuildActivityOptions: Boolean,
    ): Boolean {
        if (advertised) return true
        // Jetour Adayo A10 (and selected Android 10 HU mode): try when APIs exist.
        return adayoOrAndroid10Hu && canBuildActivityOptions
    }
}
