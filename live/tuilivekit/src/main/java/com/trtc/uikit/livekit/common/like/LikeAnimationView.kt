package com.trtc.uikit.livekit.common.like

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.Keyframe
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PointF
import android.util.AttributeSet
import android.util.Property
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.view.children
import com.trtc.uikit.livekit.R
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

class LikeAnimationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    companion object {
        private const val MAIN_DURATION_MS = 660L
        private const val MAXIMUM_REACTION_COUNT = 14
        private const val CONTINUOUS_TAP_WINDOW_MS = 420L
        private const val STAGE_ICON_SIZE_DP = 40
        private const val AUXILIARY_ORIGIN_MARGIN_END_DP = 28
        private const val AUXILIARY_ORIGIN_MARGIN_BOTTOM_DP = 96
    }

    private data class ActiveReaction(val view: View, val animator: Animator)

    private val stageReactions = mutableListOf<ActiveReaction>()
    private val auxiliaryReactions = mutableListOf<ActiveReaction>()
    private val buttonReactions = mutableListOf<ActiveReaction>()
    private val delayedReactions = mutableListOf<Runnable>()
    private var likeUIStore: LikeUIStore? = null

    private val reactionDrawables = intArrayOf(
        R.drawable.livekit_pusher_like_thumb,
        R.drawable.livekit_pusher_like_thumb,
        R.drawable.livekit_pusher_like_heart,
        R.drawable.livekit_pusher_like_heart,
        R.drawable.livekit_pusher_like_double_heart,
        R.drawable.livekit_pusher_like_fire,
        R.drawable.livekit_pusher_like_sparkle,
    )

    private var touchInteractionEnabled = false
    private var stageTapCallback: ((Float, Float) -> Unit)? = null
    private var lastTapAt: Long = 0L

    init {
        clipChildren = false
        clipToPadding = false
        isFocusable = false
        isClickable = false
        if (id == NO_ID) {
            id = R.id.livekit_like_float_layer
        }
    }

    fun setLikeStore(store: LikeUIStore) {
        this.likeUIStore = store
    }

    fun enableTouchInteraction(onStageTap: ((x: Float, y: Float) -> Unit)? = null) {
        touchInteractionEnabled = true
        stageTapCallback = onStageTap
    }

    fun disableTouchInteraction() {
        touchInteractionEnabled = false
        stageTapCallback = null
        lastTapAt = 0L
    }

    fun showStageLike(point: PointF, auxiliaryOrigin: PointF, stageIconSizeDp: Int = 52) {
        val drawable = randomDrawable()
        showMainReaction(point, drawable, stageIconSizeDp)
        showAuxiliaryReaction(auxiliaryOrigin, drawable)
    }

    fun showReceivedLikes(count: Int, auxiliaryOrigin: PointF) {
        repeat(min(6, max(1, count))) { index ->
            lateinit var reaction: Runnable
            reaction = Runnable {
                delayedReactions.remove(reaction)
                if (isAttachedToWindow) {
                    showAuxiliaryReaction(auxiliaryOrigin, randomDrawable())
                }
            }
            delayedReactions += reaction
            postDelayed(reaction, index * 120L)
        }
    }

    fun emit(count: Int = 1) {
        emitAuxiliaryFromDefaultOrigin(count)
    }

    fun showButtonLike(origin: PointF) {
        showButtonReaction(origin, randomDrawable())
    }

    fun removeAllAnimations() {
        delayedReactions.forEach(::removeCallbacks)
        delayedReactions.clear()
        (stageReactions + auxiliaryReactions + buttonReactions).forEach { reaction ->
            reaction.animator.cancel()
            removeView(reaction.view)
        }
        stageReactions.clear()
        auxiliaryReactions.clear()
        buttonReactions.clear()
        children.toList().forEach { child ->
            child.animate().cancel()
            removeView(child)
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean = false

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!touchInteractionEnabled) return super.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            handleStageTap(event.x, event.y)
            return true
        }
        return super.onTouchEvent(event)
    }

    override fun onDetachedFromWindow() {
        lastTapAt = 0L
        removeAllAnimations()
        super.onDetachedFromWindow()
    }

    private fun handleStageTap(x: Float, y: Float) {
        val now = System.currentTimeMillis()
        val isContinuous = now - lastTapAt <= CONTINUOUS_TAP_WINDOW_MS
        lastTapAt = now
        if (!isContinuous) return

        val point = PointF(x, y)
        val auxiliaryOrigin = defaultAuxiliaryOrigin()
        likeUIStore?.sendLike()
        showStageLike(point, auxiliaryOrigin, STAGE_ICON_SIZE_DP)
        stageTapCallback?.invoke(x, y)
    }

    private fun emitAuxiliaryFromDefaultOrigin(count: Int) {
        val safeCount = count.coerceIn(1, 6)
        val origin = defaultAuxiliaryOrigin()
        for (i in 0 until safeCount) {
            lateinit var reaction: Runnable
            reaction = Runnable {
                delayedReactions.remove(reaction)
                if (isAttachedToWindow) {
                    showAuxiliaryReaction(origin, randomDrawable())
                }
            }
            delayedReactions += reaction
            postDelayed(reaction, (i * 120L))
        }
    }

    private fun defaultAuxiliaryOrigin(): PointF {
        val halfIcon = dp(24) / 2f
        val x = width.toFloat() - dp(AUXILIARY_ORIGIN_MARGIN_END_DP).toFloat() - halfIcon
        val y = height.toFloat() - dp(AUXILIARY_ORIGIN_MARGIN_BOTTOM_DP).toFloat() - halfIcon
        return PointF(x, y)
    }

    private fun showMainReaction(point: PointF, drawable: Int, iconSizeDp: Int) {
        val size = dp(iconSizeDp)
        val reaction = createReaction(drawable, size).apply {
            pivotX = size / 2f
            pivotY = size * 0.62f
            scaleX = 0.04f
            scaleY = 0.04f
            rotation = -28f
            elevation = dp(7).toFloat()
        }
        placeReaction(reaction, point.x - size / 2f, point.y - size)
        addView(reaction)

        val animator = ObjectAnimator.ofPropertyValuesHolder(
            reaction,
            keyframes(View.SCALE_X, 0.04f, 1f, 1f, 1f, 1.18f),
            keyframes(View.SCALE_Y, 0.04f, 1f, 1f, 1f, 1.18f),
            keyframes(View.ROTATION, -28f, -28f, 8f, -4f, -4f),
            keyframes(View.TRANSLATION_Y, 0f, 0f, 0f, 0f, -dp(6).toFloat()),
            keyframes(View.ALPHA, 1f, 1f, 1f, 1f, 0f),
        ).apply {
            duration = MAIN_DURATION_MS
            interpolator = LinearInterpolator()
        }
        startLimitedReaction(stageReactions, reaction, animator)
    }

    private fun showAuxiliaryReaction(origin: PointF, drawable: Int) {
        val size = dp(24)
        val rise = dp(Random.nextInt(120, 141)).toFloat()
        val reaction = createReaction(drawable, size).apply {
            alpha = 0.38f
            elevation = dp(5).toFloat()
        }
        placeReaction(
            reaction,
            origin.x + dp(Random.nextInt(-8, 9)) - size / 2f,
            origin.y - size / 2f,
        )
        addView(reaction)

        val animator = ObjectAnimator.ofPropertyValuesHolder(
            reaction,
            auxiliaryKeyframes(View.TRANSLATION_X, -randomDp(4, 6), -randomDp(1, 3), -randomDp(5, 7), -randomDp(3, 5)),
            auxiliaryKeyframes(View.TRANSLATION_Y, -rise * 0.34f, -rise * 0.66f, -rise * 0.78f, -rise),
            auxiliaryKeyframes(View.ALPHA, 0.38f, 0.38f, 0.38f, 0f),
        ).apply {
            duration = Random.nextLong(1800L, 2051L)
            interpolator = LinearInterpolator()
        }
        startLimitedReaction(auxiliaryReactions, reaction, animator)
    }

    private fun showButtonReaction(origin: PointF, drawable: Int) {
        val size = dp(36)
        val rise = dp(Random.nextInt(120, 151)).toFloat()
        val reaction = createReaction(drawable, size).apply {
            scaleX = 0.2f
            scaleY = 0.2f
            elevation = dp(5).toFloat()
        }
        placeReaction(
            reaction,
            origin.x + dp(Random.nextInt(-5, 6)) - size / 2f,
            origin.y - size / 2f,
        )
        addView(reaction)

        val animator = ObjectAnimator.ofPropertyValuesHolder(
            reaction,
            buttonKeyframes(View.TRANSLATION_X, 0f, -randomDp(4, 6), -randomDp(1, 3), -randomDp(5, 7), -randomDp(3, 5)),
            buttonKeyframes(
                View.TRANSLATION_Y,
                -dp(70).toFloat(),
                -(dp(70) + rise * 0.34f),
                -(dp(70) + rise * 0.66f),
                -(dp(70) + rise * 0.78f),
                -(dp(70) + rise),
            ),
            buttonKeyframes(View.SCALE_X, 1f, 1f, 1f, 1f, 1f),
            buttonKeyframes(View.SCALE_Y, 1f, 1f, 1f, 1f, 1f),
            buttonKeyframes(View.ALPHA, 1f, 0.66f, 0.34f, 0.22f, 0f),
        ).apply {
            duration = Random.nextLong(1500L, 1701L)
            interpolator = LinearInterpolator()
        }
        startLimitedReaction(buttonReactions, reaction, animator)
    }

    private fun startLimitedReaction(
        reactions: MutableList<ActiveReaction>,
        view: View,
        animator: Animator,
    ) {
        if (reactions.size >= MAXIMUM_REACTION_COUNT) {
            val earliest = reactions.removeFirst()
            earliest.animator.cancel()
            removeView(earliest.view)
        }
        val active = ActiveReaction(view, animator)
        reactions += active
        animator.addListener(removalListener(view) { reactions.remove(active) })
        animator.start()
    }

    private fun removalListener(view: View, beforeRemove: () -> Unit = {}): AnimatorListenerAdapter =
        object : AnimatorListenerAdapter() {
            private var removed = false

            override fun onAnimationEnd(animation: Animator) {
                if (removed) return
                removed = true
                beforeRemove()
                removeView(view)
            }

            override fun onAnimationCancel(animation: Animator) {
                if (removed) return
                removed = true
                beforeRemove()
                removeView(view)
            }
        }

    private fun createReaction(drawable: Int, size: Int): AppCompatImageView =
        AppCompatImageView(context).apply {
            setImageResource(drawable)
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LayoutParams(size, size, Gravity.TOP or Gravity.START)
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }

    private fun placeReaction(view: View, left: Float, top: Float) {
        (view.layoutParams as LayoutParams).apply {
            leftMargin = left.roundToInt()
            topMargin = top.roundToInt()
            view.layoutParams = this
        }
    }

    private fun keyframes(property: Property<View, Float>, vararg values: Float): PropertyValuesHolder {
        val fractions = floatArrayOf(0f, 0.30303f, 0.51515f, 0.65152f, 1f)
        return PropertyValuesHolder.ofKeyframe(
            property,
            *values.mapIndexed { index, value -> Keyframe.ofFloat(fractions[index], value) }.toTypedArray(),
        )
    }

    private fun auxiliaryKeyframes(
        property: Property<View, Float>,
        v1: Float,
        v2: Float,
        v3: Float,
        v4: Float,
    ) =
        PropertyValuesHolder.ofKeyframe(
            property,
            Keyframe.ofFloat(0f, if (property == View.ALPHA) 0.38f else 0f),
            Keyframe.ofFloat(0.34f, v1),
            Keyframe.ofFloat(0.66f, v2),
            Keyframe.ofFloat(0.78f, v3),
            Keyframe.ofFloat(1f, v4),
        )

    private fun buttonKeyframes(property: Property<View, Float>, vararg values: Float) =
        PropertyValuesHolder.ofKeyframe(
            property,
            Keyframe.ofFloat(
                0f,
                when (property) {
                    View.ALPHA -> 1f
                    View.SCALE_X, View.SCALE_Y -> 0.2f
                    else -> 0f
                },
            ),
            Keyframe.ofFloat(0.125f, values[0]),
            Keyframe.ofFloat(0.4225f, values[1]),
            Keyframe.ofFloat(0.7025f, values[2]),
            Keyframe.ofFloat(0.8075f, values[3]),
            Keyframe.ofFloat(1f, values[4]),
        )

    private fun randomDrawable(): Int = reactionDrawables.random()

    private fun randomDp(start: Int, endInclusive: Int): Float =
        dp(Random.nextInt(start, endInclusive + 1)).toFloat()

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
