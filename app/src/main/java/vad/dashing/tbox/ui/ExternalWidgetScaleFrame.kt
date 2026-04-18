package vad.dashing.tbox.ui

import android.content.Context
import android.view.Gravity
import android.widget.FrameLayout

/**
 * Host for a scaled [LongPressInterceptLayout]: avoids clipping when scale &gt; 1 by laying out the
 * child at (cellSize / scale) then scaling up; when scale &lt; 1 the child is full cell size and
 * scaled down (fits with letterboxing).
 */
class ExternalWidgetScaleFrame(context: Context) : FrameLayout(context) {

    var interceptChild: LongPressInterceptLayout? = null
        private set

    var displayScale: Float = 1f
        set(value) {
            field = value.coerceIn(0.1f, 2f)
            applyScaleLayout()
        }

    init {
        // Do not clip scaled children; embedded widgets (e.g. media) often draw slightly outside
        // nominal bounds and would look cropped inside rounded tiles.
        clipChildren = false
        clipToPadding = false
    }

    fun attachIntercept(intercept: LongPressInterceptLayout) {
        if (interceptChild === intercept && childCount == 1) {
            applyScaleLayout()
            return
        }
        removeAllViews()
        interceptChild = intercept
        addView(
            intercept,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
            )
        )
        applyScaleLayout()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        applyScaleLayout()
    }

    private fun applyScaleLayout() {
        val intercept = interceptChild ?: return
        val s = displayScale
        val w = width
        val h = height
        if (w <= 0 || h <= 0) return

        val cw: Int
        val ch: Int
        if (s >= 1f) {
            cw = (w / s).toInt().coerceAtLeast(1)
            ch = (h / s).toInt().coerceAtLeast(1)
        } else {
            cw = w
            ch = h
        }

        val lp = LayoutParams(cw, ch)
        lp.gravity = Gravity.CENTER
        intercept.layoutParams = lp
        intercept.pivotX = cw / 2f
        intercept.pivotY = ch / 2f
        intercept.scaleX = s
        intercept.scaleY = s
    }
}
