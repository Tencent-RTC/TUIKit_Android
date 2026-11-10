package com.tencent.uikit.app.live

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs

class SwipeBackLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    var onSwipeBack: (() -> Unit)? = null
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val swipeDistance = 72 * resources.displayMetrics.density
    private var downX = 0f
    private var downY = 0f
    private var trackingSwipe = false
    private var ignoreGesture = false

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                trackingSwipe = false
                ignoreGesture = false
            }
            MotionEvent.ACTION_POINTER_DOWN -> ignoreGesture = true
            MotionEvent.ACTION_MOVE -> {
                if (ignoreGesture) return false
                val dx = event.x - downX
                val dy = event.y - downY
                if (maxOf(abs(dx), abs(dy)) > touchSlop) {
                    trackingSwipe = dx < 0 && abs(dx) > abs(dy) * 1.5f
                    ignoreGesture = !trackingSwipe
                    if (trackingSwipe) return true
                }
            }
        }
        return super.onInterceptTouchEvent(event)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!trackingSwipe) return super.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN -> ignoreGesture = true
            MotionEvent.ACTION_UP -> {
                val dx = downX - event.x
                val dy = abs(event.y - downY)
                val shouldGoBack = !ignoreGesture && dx >= swipeDistance && dx > dy * 1.5f
                trackingSwipe = false
                if (shouldGoBack) onSwipeBack?.invoke()
            }
            MotionEvent.ACTION_CANCEL -> trackingSwipe = false
        }
        return true
    }
}
