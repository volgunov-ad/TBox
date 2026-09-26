package vad.dashing.tbox.adb

import android.view.Display
import vad.dashing.tbox.freeform.FreeformDisplaySpaces

/**
 * Remaps a stored virtual-display preference (id + optional w×h) onto a freshly
 * discovered HU display catalog. Display ids change when the stock launcher recreates VDs;
 * size (±[SIZE_TOLERANCE_PX]) and inset-app role are stable enough to recover.
 */
object VirtualDisplayTargetResolver {
    const val SIZE_TOLERANCE_PX = 2

    /** Displays shown in the virtual-display picker (never recommend/select display 0). */
    fun forPicker(displays: List<HuDisplayInfo>): List<HuDisplayInfo> =
        displays.filter { it.displayId != Display.DEFAULT_DISPLAY }

    sealed class ResolveResult {
        data class Matched(
            val display: HuDisplayInfo,
            /** True when the resolved id differs from the stored preference. */
            val remapped: Boolean,
        ) : ResolveResult()

        data class Failed(val reason: FailReason, val detail: String = "") : ResolveResult()
    }

    enum class FailReason {
        EmptyCatalog,
        InvalidPreference,
        NoMatch,
    }

    /**
     * Resolve [preferredId] (+ optional [preferredWidth]/[preferredHeight]) against [catalog].
     *
     * Matching order:
     * 1. Same id still present and size compatible (or no size stored) → keep id.
     * 2. Size match among non-default displays (±[SIZE_TOLERANCE_PX]).
     * 3. Among size matches, prefer inset [FreeformDisplaySpaces.pickAppVirtualDisplay] if it
     *    matches size; else prefer previous id if present; else largest area.
     */
    fun resolve(
        preferredId: Int?,
        preferredWidth: Int?,
        preferredHeight: Int?,
        catalog: List<HuDisplayInfo>,
    ): ResolveResult {
        if (catalog.isEmpty()) {
            return ResolveResult.Failed(FailReason.EmptyCatalog)
        }
        if (preferredId == null || preferredId < 0) {
            return ResolveResult.Failed(FailReason.InvalidPreference)
        }
        // Display 0 is not a valid VD target for this mode.
        if (preferredId == Display.DEFAULT_DISPLAY && preferredWidth == null) {
            return ResolveResult.Failed(
                FailReason.InvalidPreference,
                "display 0 is not a virtual-display target",
            )
        }

        val byId = catalog.associateBy { it.displayId }
        val hasSize = preferredWidth != null && preferredWidth > 0 &&
            preferredHeight != null && preferredHeight > 0

        byId[preferredId]?.let { current ->
            if (!hasSize || sizesMatch(preferredWidth!!, preferredHeight!!, current)) {
                return ResolveResult.Matched(current, remapped = false)
            }
        }

        if (!hasSize) {
            return ResolveResult.Failed(
                FailReason.NoMatch,
                "display $preferredId missing; no stored size to remap",
            )
        }

        val sizeMatches = forPicker(catalog).filter { d ->
            sizesMatch(preferredWidth!!, preferredHeight!!, d)
        }
        if (sizeMatches.isEmpty()) {
            return ResolveResult.Failed(
                FailReason.NoMatch,
                "no display matching ${preferredWidth}×${preferredHeight} (was id $preferredId)",
            )
        }

        val picked = pickBestSizeMatch(
            sizeMatches = sizeMatches,
            preferredId = preferredId,
            catalog = catalog,
        )
        return ResolveResult.Matched(picked, remapped = picked.displayId != preferredId)
    }

    private fun pickBestSizeMatch(
        sizeMatches: List<HuDisplayInfo>,
        preferredId: Int,
        catalog: List<HuDisplayInfo>,
    ): HuDisplayInfo {
        sizeMatches.firstOrNull { it.displayId == preferredId }?.let { return it }

        val catalogSizes = catalog.map {
            FreeformDisplaySpaces.DisplaySize(it.displayId, it.widthPx, it.heightPx)
        }
        val inset = FreeformDisplaySpaces.pickAppVirtualDisplay(catalogSizes)
        if (inset != null) {
            sizeMatches.firstOrNull { it.displayId == inset.displayId }?.let { return it }
        }

        return sizeMatches.maxBy {
            it.widthPx.toLong() * it.heightPx.toLong()
        }
    }

    private fun sizesMatch(width: Int, height: Int, display: HuDisplayInfo): Boolean =
        FreeformDisplaySpaces.displaySizesMatch(
            width,
            height,
            display.widthPx,
            display.heightPx,
            tolerancePx = SIZE_TOLERANCE_PX,
        )
}
