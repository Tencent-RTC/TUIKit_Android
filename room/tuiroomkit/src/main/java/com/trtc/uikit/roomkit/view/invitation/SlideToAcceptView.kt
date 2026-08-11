package com.trtc.uikit.roomkit.view.invitation

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.customview.widget.ViewDragHelper
import com.trtc.uikit.roomkit.R

class SlideToAcceptView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private companion object {
        const val SLIDE_SPEED = 1000
        const val SENSITIVITY = 0.3f
        const val ACCEPT_THRESHOLD_PX = 2
    }

    private val imgSlide: ImageView
    private val dragHelper: ViewDragHelper
    private var listener: AcceptListener? = null
    private var isAccepted = false

    init {
        setBackgroundResource(R.drawable.roomkit_bg_invitation_accept)
        LayoutInflater.from(context).inflate(R.layout.roomkit_view_slide_to_accept, this, true)
        imgSlide = findViewById(R.id.img_slide)
        dragHelper = ViewDragHelper.create(this, SENSITIVITY, DragCallback())
    }

    fun setListener(listener: AcceptListener) {
        this.listener = listener
    }

    fun reset() {
        isAccepted = false
        val target = paddingStart
        if (imgSlide.left != target) {
            imgSlide.offsetLeftAndRight(target - imgSlide.left)
        }
        invalidate()
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (!isEnabled) return false
        return dragHelper.shouldInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        dragHelper.processTouchEvent(event)
        if (event.action == MotionEvent.ACTION_UP &&
            dragHelper.viewDragState != ViewDragHelper.STATE_DRAGGING
        ) {
            performClick()
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun computeScroll() {
        super.computeScroll()
        if (dragHelper.continueSettling(true)) {
            invalidate()
        }
    }

    private inner class DragCallback : ViewDragHelper.Callback() {

        private var capturedChildTop = 0

        override fun tryCaptureView(child: View, pointerId: Int): Boolean =
            !isAccepted && child === imgSlide

        override fun clampViewPositionHorizontal(child: View, left: Int, dx: Int): Int {
            val slideIconWidth = imgSlide.width
            val rootWidth = width
            val start = paddingStart
            val maxDistance = rootWidth - paddingEnd - slideIconWidth
            return when {
                left < start -> start
                left > maxDistance -> maxDistance
                else -> left
            }
        }

        override fun clampViewPositionVertical(child: View, top: Int, dy: Int): Int {
            return height / 2 - child.height / 2
        }

        override fun onViewCaptured(capturedChild: View, activePointerId: Int) {
            super.onViewCaptured(capturedChild, activePointerId)
            capturedChildTop = capturedChild.top
        }

        override fun onViewReleased(releasedChild: View, xVel: Float, yVel: Float) {
            super.onViewReleased(releasedChild, xVel, yVel)
            val currentLeft = releasedChild.left
            val slideIconWidth = imgSlide.width
            val rootWidth = width
            val halfWidth = rootWidth / 2
            if (currentLeft <= halfWidth && xVel < SLIDE_SPEED) {
                dragHelper.settleCapturedViewAt(paddingStart, capturedChildTop)
            } else {
                dragHelper.settleCapturedViewAt(rootWidth - paddingEnd - slideIconWidth, capturedChildTop)
            }
            invalidate()
        }

        override fun onViewDragStateChanged(state: Int) {
            super.onViewDragStateChanged(state)
            if (state != ViewDragHelper.STATE_IDLE) return
            val maxDistance = width - paddingEnd - imgSlide.width
            if (imgSlide.left >= maxDistance - ACCEPT_THRESHOLD_PX && !isAccepted) {
                isAccepted = true
                listener?.onAccept()
            }
        }
    }

    fun interface AcceptListener {
        fun onAccept()
    }
}