package vad.dashing.tbox.ui

import android.content.Context
import android.os.SystemClock
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.FrameLayout

/**
 * Forwards touches to children; detects long-press without posting to [android.os.Handler] on
 * every [ACTION_DOWN] (avoids main-thread churn during rapid taps on heavy embedded widgets).
 */
class LongPressInterceptLayout(context: Context) : FrameLayout(context) {

    private var intercepting = false

    var onLongPress: (() -> Unit)? = null
    var interceptLongPress: Boolean = true

    private var downTime = 0L
    private var downX = 0f
    private var downY = 0f

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = interceptLongPress

            override fun onLongPress(e: MotionEvent) {
                if (!interceptLongPress || intercepting) return
                intercepting = true
                onLongPress?.invoke()
                sendCancelEvent()
            }
        }
    ).apply {
        setIsLongpressEnabled(true)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (!interceptLongPress) {
            return false
        }
        return intercepting
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!interceptLongPress) {
            return false
        }
        return intercepting
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (!interceptLongPress) {
            return super.dispatchTouchEvent(ev)
        }
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                intercepting = false
                downTime = ev.downTime
                downX = ev.x
                downY = ev.y
            }
        }
        gestureDetector.onTouchEvent(ev)
        val blockChild = intercepting
        if (ev.actionMasked == MotionEvent.ACTION_UP ||
            ev.actionMasked == MotionEvent.ACTION_CANCEL
        ) {
            intercepting = false
        }
        if (blockChild) {
            return true
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun sendCancelEvent() {
        val now = SystemClock.uptimeMillis()
        val cancelEvent = MotionEvent.obtain(
            downTime,
            now,
            MotionEvent.ACTION_CANCEL,
            downX,
            downY,
            0
        )
        super.dispatchTouchEvent(cancelEvent)
        cancelEvent.recycle()
    }
}
