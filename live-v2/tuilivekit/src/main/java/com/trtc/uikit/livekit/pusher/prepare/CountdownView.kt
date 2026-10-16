package com.trtc.uikit.livekit.pusher.prepare

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import com.trtc.uikit.livekit.pusher.video.PusherVideoView
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.theme.label.AtomicLabel

internal class CountdownView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {
    internal var onCancel: (() -> Unit)? = null

    private val numberView: AtomicLabel
    private val cancelView: AtomicLabel
    private var tickAnimator: AnimatorSet? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.livekit_pusher_countdown_view, this, true)
        numberView = findViewById(R.id.livekit_pusher_countdown_number)
        cancelView = findViewById(R.id.livekit_pusher_countdown_cancel)
        numberView.setShadowLayer(
            resources.getDimension(R.dimen.livekit_pusher_countdown_number_shadow_radius),
            0f,
            resources.getDimension(R.dimen.livekit_pusher_countdown_number_shadow_offset_y),
            ContextCompat.getColor(context, R.color.livekit_pusher_countdown_number_shadow),
        )
        cancelView.setOnClickListener { onCancel?.invoke() }
        applyBottomInset()
    }

    fun show(number: Int) {
        numberView.text = number.toString()
        tickAnimator?.cancel()
        numberView.alpha = 0f
        numberView.scaleX = ENTER_FROM_SCALE
        numberView.scaleY = ENTER_FROM_SCALE

        val enter = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(numberView, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(numberView, View.SCALE_X, ENTER_FROM_SCALE, 1f),
                ObjectAnimator.ofFloat(numberView, View.SCALE_Y, ENTER_FROM_SCALE, 1f),
            )
            duration = ENTER_DURATION_MS
            interpolator = AccelerateDecelerateInterpolator()
        }
        val exit = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(numberView, View.ALPHA, 1f, 0f),
                ObjectAnimator.ofFloat(numberView, View.SCALE_X, 1f, EXIT_TO_SCALE),
                ObjectAnimator.ofFloat(numberView, View.SCALE_Y, 1f, EXIT_TO_SCALE),
            )
            duration = EXIT_DURATION_MS
            startDelay = EXIT_START_MS
            interpolator = AccelerateDecelerateInterpolator()
        }
        tickAnimator = AnimatorSet().apply {
            playTogether(enter, exit)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (tickAnimator === animation) tickAnimator = null
                }
            })
            start()
        }
    }

    fun stopAnimation() {
        tickAnimator?.cancel()
        tickAnimator = null
        numberView.alpha = 0f
        numberView.scaleX = 1f
        numberView.scaleY = 1f
    }

    override fun onDetachedFromWindow() {
        stopAnimation()
        super.onDetachedFromWindow()
    }

    private fun applyBottomInset() {
        val safeAreaGap = resources.getDimensionPixelSize(
            R.dimen.livekit_pusher_countdown_cancel_margin_bottom,
        )
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            val navigationBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            val stageBottomInset = PusherVideoView.bottomInset(context, insets)
            listOf(R.id.livekit_pusher_countdown_circle,
                R.id.livekit_pusher_countdown_number).forEach { id ->
                findViewById<View>(id).updateLayoutParams<MarginLayoutParams> {
                    bottomMargin = stageBottomInset
                }
            }
            findViewById<View>(R.id.livekit_pusher_countdown_dark_bar)
                .updateLayoutParams<LayoutParams> { height = stageBottomInset }
            cancelView.updateLayoutParams<MarginLayoutParams> {
                bottomMargin = navigationBottom + safeAreaGap
            }
            insets
        }
    }

    private companion object {
        const val ENTER_DURATION_MS = 180L
        const val EXIT_START_MS = 800L
        const val EXIT_DURATION_MS = 200L
        const val ENTER_FROM_SCALE = 1.6f
        const val EXIT_TO_SCALE = 0.85f
    }
}
