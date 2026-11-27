package io.trtc.tuikit.chat.uikit.components.common
import android.graphics.Rect
import android.view.MotionEvent
import android.view.TouchDelegate
import android.view.View
import kotlin.math.ceil
import kotlin.math.max

data class TouchTargetExpansion(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
)

object TouchTargetBoundsPolicy {
    const val DEFAULT_MIN_TOUCH_TARGET_DP = 48f

    fun calculateExpansion(
        widthPx: Int,
        heightPx: Int,
        minTouchTargetPx: Int
    ): TouchTargetExpansion {
        val horizontalGap = max(0, minTouchTargetPx - widthPx)
        val verticalGap = max(0, minTouchTargetPx - heightPx)
        val left = horizontalGap / 2
        val top = verticalGap / 2
        return TouchTargetExpansion(
            left = left,
            top = top,
            right = horizontalGap - left,
            bottom = verticalGap - top
        )
    }
}

fun View.expandTouchTarget(
    minTouchTargetDp: Float = TouchTargetBoundsPolicy.DEFAULT_MIN_TOUCH_TARGET_DP
) {
    val parentView = parent as? View ?: return
    parentView.post {
        val minTouchTargetPx = ceil(minTouchTargetDp * resources.displayMetrics.density).toInt()
        val hitRect = Rect()
        getHitRect(hitRect)
        val expansion = TouchTargetBoundsPolicy.calculateExpansion(
            widthPx = hitRect.width(),
            heightPx = hitRect.height(),
            minTouchTargetPx = minTouchTargetPx
        )
        if (expansion.left == 0 && expansion.top == 0 && expansion.right == 0 && expansion.bottom == 0) {
            return@post
        }
        hitRect.left -= expansion.left
        hitRect.top -= expansion.top
        hitRect.right += expansion.right
        hitRect.bottom += expansion.bottom
        val compositeDelegate = parentView.touchDelegate as? CompositeTouchDelegate
            ?: CompositeTouchDelegate(parentView).also { parentView.touchDelegate = it }
        compositeDelegate.putDelegate(this, hitRect)
    }
}

private class CompositeTouchDelegate(parentView: View) : TouchDelegate(Rect(), parentView) {
    private val delegates = linkedMapOf<View, TouchDelegate>()

    fun putDelegate(view: View, bounds: Rect) {
        delegates[view] = TouchDelegate(Rect(bounds), view)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        delegates.entries.toList().asReversed().forEach { (view, delegate) ->
            if (view.visibility != View.VISIBLE || !view.isEnabled) {
                return@forEach
            }
            val eventCopy = MotionEvent.obtain(event)
            try {
                if (delegate.onTouchEvent(eventCopy)) {
                    return true
                }
            } finally {
                eventCopy.recycle()
            }
        }
        return false
    }
}
