package com.example.atomicxcore.components

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.Path
import android.util.AttributeSet
import android.view.Gravity
import android.view.animation.AccelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import com.example.atomicxcore.R
import com.google.android.material.button.MaterialButton
import io.trtc.tuikit.atomicxcore.api.live.LikeListener
import io.trtc.tuikit.atomicxcore.api.live.LikeStore
import io.trtc.tuikit.atomicxcore.api.live.LiveUserInfo
import kotlin.random.Random

/**
 * Like button component (particle effects)
 *
 * Related APIs:
 * - LikeStore.create(liveID) - Create a like manager instance
 * - LikeStore.sendLike(count, completion) - Send likes
 * - LikeStore.addLikeListener(listener) - Like event listener
 *
 * Features:
 * - Circular floating like button
 * - Plays heart particle animation on tap or when receiving likes from others
 */
class LikeButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var likeStore: LikeStore? = null
    private val button: MaterialButton
    private val dp40 = (40 * resources.displayMetrics.density).toInt()
    private val dp50 = (50 * resources.displayMetrics.density).toInt()

    /** Heart color pool */
    private val heartColors = intArrayOf(
        Color.parseColor("#FF4068"),  // Pink
        Color.parseColor("#FF6666"),  // Coral
        Color.parseColor("#F24D99"),  // Rose
        Color.parseColor("#CC33CC"),  // Purple
        Color.parseColor("#6699FF"),  // Blue-violet
        Color.parseColor("#FF8C00"),  // Orange
        Color.parseColor("#FFCC00"),  // Gold
    )

    private val likeListener = object : LikeListener() {
        override fun onReceiveLikesMessage(liveID: String, count: Long, sender: LiveUserInfo) {
            post { emitParticles(1) }
        }
    }

    init {
        layoutParams = LayoutParams(dp50, dp50)
        clipChildren = false
        clipToPadding = false

        button = MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            icon = context.getDrawable(R.drawable.ic_heart_outline)
            iconTint = android.content.res.ColorStateList.valueOf(Color.parseColor("#FF4081"))
            iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
            iconPadding = 0
            text = ""
            insetTop = 0
            insetBottom = 0
            setPadding(0, 0, 0, 0)
            cornerRadius = dp40 / 2
            setBackgroundColor(Color.WHITE)
            elevation = 4 * resources.displayMetrics.density
            layoutParams = LayoutParams(dp40, dp40, Gravity.CENTER)
            setOnClickListener { onLikeTapped() }
        }
        addView(button)
    }

    /**
     * Initialize the like component
     */
    fun initialize(liveID: String) {
        likeStore = LikeStore.create(liveID)
        likeStore?.addLikeListener(likeListener)
    }

    private fun onLikeTapped() {
        likeStore?.sendLike(1, null)

        // Button scale feedback
        val scaleUpX = ObjectAnimator.ofFloat(button, "scaleX", 1f, 1.3f).apply { duration = 100 }
        val scaleUpY = ObjectAnimator.ofFloat(button, "scaleY", 1f, 1.3f).apply { duration = 100 }
        val scaleDownX = ObjectAnimator.ofFloat(button, "scaleX", 1.3f, 1f).apply { duration = 100 }
        val scaleDownY = ObjectAnimator.ofFloat(button, "scaleY", 1.3f, 1f).apply { duration = 100 }

        AnimatorSet().apply {
            playSequentially(
                AnimatorSet().apply { playTogether(scaleUpX, scaleUpY) },
                AnimatorSet().apply { playTogether(scaleDownX, scaleDownY) }
            )
            start()
        }

        emitParticles(1)
    }

    /** Emit heart particle animations */
    private fun emitParticles(count: Int) {
        for (i in 0 until count) {
            postDelayed({ launchSingleHeart() }, (i * 50).toLong())
        }
    }

    /** Launch a single heart particle */
    private fun launchSingleHeart() {
        val heartSize = Random.nextInt(20, 37)
        val heartSizePx = (heartSize * resources.displayMetrics.density).toInt()

        val heartView = ImageView(context).apply {
            setImageResource(R.drawable.ic_heart)
            setColorFilter(heartColors[Random.nextInt(heartColors.size)])
            layoutParams = LayoutParams(heartSizePx, heartSizePx)
            alpha = 1f
            scaleX = 0.2f
            scaleY = 0.2f
        }

        addView(heartView)

        // Start point
        val startX = (width / 2 - heartSizePx / 2).toFloat()
        val startY = (button.top - heartSizePx).toFloat()
        heartView.x = startX
        heartView.y = startY

        // End point
        val endY = startY - Random.nextInt(120, 221) * resources.displayMetrics.density
        val endX = startX + Random.nextInt(-50, 51) * resources.displayMetrics.density

        val duration = Random.nextLong(2000, 3000)

        // Path animation
        val path = Path().apply {
            moveTo(startX, startY)
            val cp1X = startX + Random.nextInt(-40, 41) * resources.displayMetrics.density
            val cp1Y = startY - Random.nextInt(40, 81) * resources.displayMetrics.density
            val cp2X = endX + Random.nextInt(-30, 31) * resources.displayMetrics.density
            val cp2Y = endY + Random.nextInt(20, 61) * resources.displayMetrics.density
            cubicTo(cp1X, cp1Y, cp2X, cp2Y, endX, endY)
        }

        val pathAnimator = ObjectAnimator.ofFloat(heartView, "x", "y", path).apply {
            this.duration = duration
        }

        // Pop-up scale animation
        val scaleUp = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(heartView, "scaleX", 0.2f, 1f),
                ObjectAnimator.ofFloat(heartView, "scaleY", 0.2f, 1f)
            )
            this.duration = 200
            interpolator = OvershootInterpolator(2f)
        }

        // Shrink and fade out in the second half
        val fadeOut = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(heartView, "alpha", 1f, 0f),
                ObjectAnimator.ofFloat(heartView, "scaleX", 1f, 0.3f),
                ObjectAnimator.ofFloat(heartView, "scaleY", 1f, 0.3f)
            )
            this.duration = (duration * 0.6).toLong()
            startDelay = (duration * 0.4).toLong()
            interpolator = AccelerateInterpolator()
        }

        pathAnimator.start()
        scaleUp.start()
        fadeOut.start()

        // Remove after animation ends
        postDelayed({ removeView(heartView) }, duration)
    }

    fun release() {
        likeStore?.removeLikeListener(likeListener)
    }
}
