package com.trtc.uikit.roomkit.view.main.screenshare

import android.content.Context
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.LinearLayout

/**
 * Screen sharing indicator floating view
 * A draggable floating window that indicates screen sharing is active
 */
class ScreenShareFloatView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr), GestureDetector.OnGestureListener {

    private val windowManager: WindowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val gestureDetector: GestureDetector = GestureDetector(context, this)
    private var layoutParams: WindowManager.LayoutParams? = null
    private var lastX = 0f
    private var lastY = 0f
    private var accumulatedX = 0f
    private var accumulatedY = 0f

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return gestureDetector.onTouchEvent(event)
    }

    override fun onDown(e: MotionEvent): Boolean {
        lastX = e.rawX
        lastY = e.rawY
        return false
    }

    override fun onShowPress(e: MotionEvent) {
    }

    override fun onSingleTapUp(e: MotionEvent): Boolean {
        return false
    }

    override fun onScroll(
        e1: MotionEvent?,
        e2: MotionEvent,
        distanceX: Float,
        distanceY: Float
    ): Boolean {
        if (layoutParams == null) {
            layoutParams = getLayoutParams() as? WindowManager.LayoutParams
        }

        val nowX = e2.rawX
        val nowY = e2.rawY
        val tranX = nowX - lastX
        val tranY = nowY - lastY

        layoutParams?.let { params ->
            accumulatedX += tranX
            accumulatedY += tranY
            
            val deltaX = accumulatedX.toInt()
            val deltaY = accumulatedY.toInt()
            
            if (deltaX != 0 || deltaY != 0) {
                params.x += deltaX
                params.y += deltaY
                windowManager.updateViewLayout(this, params)
                
                accumulatedX -= deltaX
                accumulatedY -= deltaY
            }
        }

        lastX = nowX
        lastY = nowY
        return false
    }

    override fun onLongPress(e: MotionEvent) {
    }

    override fun onFling(
        e1: MotionEvent?,
        e2: MotionEvent,
        velocityX: Float,
        velocityY: Float
    ): Boolean {
        return false
    }
}
