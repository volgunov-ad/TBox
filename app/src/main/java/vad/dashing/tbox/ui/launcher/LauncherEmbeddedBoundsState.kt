package vad.dashing.tbox.ui.launcher

import android.graphics.Rect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Screen-pixel zones for embedded / split app windows. */
object LauncherEmbeddedBoundsState {
    var leftPanelBounds: Rect? by mutableStateOf(null)
    var embeddedZoneBounds: Rect? by mutableStateOf(null)
    var dockGridTopPx: Int by mutableIntStateOf(0)
    var topHeaderBottomPx: Int by mutableIntStateOf(0)
    var rightFooterBottomPx: Int by mutableIntStateOf(0)
    var contentRowBounds: Rect? by mutableStateOf(null)
    var bottomBarTopPx: Int by mutableIntStateOf(0)

    private const val SPLIT_GAP_PX = 8
    private const val BLEED_RIGHT_PX = 8
    private const val MARGIN_LEFT_PX = 0
    /** Pull top edge up over metric cards (screen pixels). */
    private const val OVERLAP_METRICS_TOP_PX = 12
    /** Extend bottom edge down over footer pills (screen pixels). */
    private const val OVERLAP_FOOTER_BOTTOM_PX = 10

    fun embeddedBounds(): Rect? {
        val row = contentRowBounds ?: return null
        val leftEdge = leftPanelBounds?.right?.toInt() ?: row.left
        val topCandidate = when {
            embeddedZoneBounds != null -> embeddedZoneBounds!!.top
            topHeaderBottomPx > 0 -> topHeaderBottomPx
            else -> row.top
        }
        val top = topCandidate - OVERLAP_METRICS_TOP_PX
        val right = row.right + BLEED_RIGHT_PX
        val bottom = when {
            rightFooterBottomPx > 0 -> rightFooterBottomPx + OVERLAP_FOOTER_BOTTOM_PX
            embeddedZoneBounds != null -> embeddedZoneBounds!!.bottom + OVERLAP_FOOTER_BOTTOM_PX
            bottomBarTopPx > 0 -> bottomBarTopPx
            else -> row.bottom
        }
        if (bottom <= top) return null
        return Rect(leftEdge + MARGIN_LEFT_PX, top, right, bottom)
    }

    fun splitBounds(): Rect? = embeddedBounds()

    fun splitPaneBounds(leftRatio: Float): Pair<Rect, Rect>? {
        val base = splitBounds() ?: return null
        val ratio = leftRatio.coerceIn(0.2f, 0.8f)
        val gap = SPLIT_GAP_PX
        val splitX = base.left + (base.width() * ratio).toInt()
        val left = Rect(base.left, base.top, splitX - gap / 2, base.bottom)
        val right = Rect(splitX + gap / 2, base.top, base.right, base.bottom)
        return left to right
    }

    fun fullScreenBounds(): Rect? {
        val row = contentRowBounds ?: return null
        val top = (topHeaderBottomPx.takeIf { it > 0 } ?: row.top) - OVERLAP_METRICS_TOP_PX
        val bottom = (bottomBarTopPx.takeIf { it > 0 } ?: row.bottom)
        if (bottom <= top) return null
        return Rect(0, top, row.right + BLEED_RIGHT_PX, bottom)
    }
}
