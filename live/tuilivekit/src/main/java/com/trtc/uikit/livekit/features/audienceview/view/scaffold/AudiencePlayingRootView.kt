package com.trtc.uikit.livekit.features.audienceview.view.scaffold

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import com.trtc.uikit.livekit.R

class AudiencePlayingRootView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    companion object {
        private const val MOVE_ANIMATION_DURATION_MS = 200L
        private val MOVE_ANIMATION_INTERPOLATOR = LinearInterpolator()
    }

    private val gestureDetector: GestureDetector
    private lateinit var playingView: View
    private lateinit var recoverLayout: FrameLayout
    private lateinit var recoverImageView: ImageView

    init {
        gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                return when {
                    velocityX > 0 -> {
                        // Move Right
                        startMoveXAnimation(playingView.x, playingView.width.toFloat())
                        true
                    }
                    velocityX < 0 -> {
                        // Move Left
                        startMoveXAnimation(playingView.x, 0f)
                        true
                    }
                    else -> super.onFling(e1, e2, velocityX, velocityY)
                }
            }

            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean {
                val newX = playingView.x - distanceX
                if (newX >= 0 && newX <= playingView.width) {
                    playingView.x = newX
                    return true
                }
                return super.onScroll(e1, e2, distanceX, distanceY)
            }
        })
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        playingView = findViewById(R.id.fl_playing)
        recoverLayout = findViewById(R.id.fl_recover)
        recoverImageView = findViewById(R.id.iv_recover)

        recoverLayout.visibility = GONE
        recoverImageView.setOnClickListener {
            if (!recoverImageView.isEnabled) {
                return@setOnClickListener
            }
            recoverImageView.isEnabled = false
            startMoveXAnimation(playingView.x, 0f)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (gestureDetector.onTouchEvent(event)) {
            parent.requestDisallowInterceptTouchEvent(true)
            return true
        }
        val action = event.action
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            val w = playingView.width.toFloat()
            val startX = playingView.x
            val endX = if (startX > w / 2) w else 0f
            startMoveXAnimation(startX, endX)
        }
        return super.onTouchEvent(event)
    }

    fun resetClearScreenState() {
        if (::playingView.isInitialized) {
            playingView.x = 0f
        }
        if (::recoverLayout.isInitialized) {
            recoverLayout.visibility = GONE
        }
    }

    private fun startMoveXAnimation(startX: Float, endX: Float) {
        val animator = ObjectAnimator.ofFloat(playingView, "X", startX, endX)
        animator.interpolator = MOVE_ANIMATION_INTERPOLATOR
        animator.duration = MOVE_ANIMATION_DURATION_MS
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationCancel(animation: Animator) {
                onFinished(true)
            }

            override fun onAnimationEnd(animation: Animator) {
                onFinished(false)
            }

            private fun onFinished(cancel: Boolean) {
                recoverImageView.isEnabled = true
                val end = if (cancel) playingView.x else endX
                if (end == playingView.width.toFloat()) {
                    recoverLayout.visibility = VISIBLE
                } else {
                    recoverLayout.visibility = GONE
                }
            }
        })
        animator.start()
    }
}
