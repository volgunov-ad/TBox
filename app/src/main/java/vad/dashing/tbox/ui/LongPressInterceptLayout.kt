package vad.dashing.tbox.ui

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout

class LongPressInterceptLayout(context: Context) : FrameLayout(context) {
    private val handler = Handler(Looper.getMainLooper())
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()

    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var isDown = false
    private var intercepting = false

    var onLongPress: (() -> Unit)? = null
    var interceptLongPress: Boolean = true
        set(value) {
            field = value
            if (!value) {
                cancelLongPressCheck()
            }
        }

    private val longPressRunnable = Runnable {
        if (isDown && !intercepting) {
            intercepting = true
            onLongPress?.invoke()
            sendCancelEvent()
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (!interceptLongPress) {
            return false
        }
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isDown = true
                intercepting = false
                downX = ev.x
                downY = ev.y
                downTime = ev.downTime
                handler.postDelayed(longPressRunnable, longPressTimeout)
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDown && !intercepting) {
                    val dx = ev.x - downX
                    val dy = ev.y - downY
                    if (dx * dx + dy * dy > touchSlop * touchSlop) {
                        cancelLongPressCheck()
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                cancelLongPressCheck()
            }
        }
        return intercepting
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!interceptLongPress) {
            return false
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                cancelLongPressCheck()
            }
        }
        return intercepting
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacks(longPressRunnable)
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

    private fun cancelLongPressCheck() {
        handler.removeCallbacks(longPressRunnable)
        isDown = false
        intercepting = false
    }
}
