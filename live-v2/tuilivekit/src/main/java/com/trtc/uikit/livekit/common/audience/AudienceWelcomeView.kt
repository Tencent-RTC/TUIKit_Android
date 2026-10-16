package com.trtc.uikit.livekit.common.audience

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.core.content.ContextCompat
import com.trtc.uikit.livekit.R
import com.trtc.uikit.livekit.common.theme.LiveTheme
import com.trtc.uikit.livekit.common.theme.LevelBadgeDrawable
import java.util.ArrayDeque

data class AudienceWelcome(
    val userID: String,
    val userName: String,
    val level: Int,
)

class AudienceWelcomeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val FADE_IN_DURATION_MS = 420L
        private const val SLIDE_IN_DURATION_MS = 680L
        private const val DISPLAY_DURATION_MS = 3200L
        private const val FADE_OUT_DURATION_MS = 320L
    }

    private val density = resources.displayMetrics.density
    private val queue = ArrayDeque<AudienceWelcome>()
    private val bannerRect = RectF()
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
    }
    private val namePaint = textPaint(LiveTheme.TextRole.ACTION, ContextCompat.getColor(context, R.color.livekit_nickname))
    private val actionPaint = textPaint(LiveTheme.TextRole.ACTION, Color.rgb(242, 245, 250))
    private val actionText = context.getString(R.string.livekit_pusher_living_audience_entered)
    private var activeEntrance: AudienceWelcome? = null
    private var activeAnimation: AnimatorSet? = null
    private var activeToken = 0

    init {
        visibility = GONE
        isClickable = false
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    internal fun enqueue(entrance: AudienceWelcome) {
        queue.addLast(entrance)
        showNextIfNeeded()
    }

    fun reset() {
        activeToken += 1
        activeAnimation?.cancel()
        activeAnimation = null
        removeCallbacks(dismissRunnable)
        queue.clear()
        activeEntrance = null
        alpha = 0f
        translationX = 0f
        visibility = GONE
    }

    override fun onDetachedFromWindow() {
        reset()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val entrance = activeEntrance ?: return
        val left = dp(12f)
        bannerRect.set(left, 0f, left + dp(228f), dp(28f))
        val tier = EntranceTier.fromLevel(entrance.level)
        drawBackground(canvas, tier)

        var textLeft = left + dp(9f)
        val badge = LevelBadgeDrawable.forLevel(context, entrance.level)
        badge.setBounds(
            textLeft.toInt(),
            dp(6.5f).toInt(),
            textLeft.toInt() + badge.intrinsicWidth,
            dp(21.5f).toInt(),
        )
        badge.draw(canvas)
        textLeft += badge.intrinsicWidth + dp(5f)

        val displayName = entrance.userName.ifEmpty { entrance.userID }
        val nameWidth = (bannerRect.right - dp(9f) - textLeft - dp(6f) - actionPaint.measureText(actionText)).coerceAtLeast(0f)
        val name = TextUtils.ellipsize(displayName, namePaint, nameWidth, TextUtils.TruncateAt.END)
        val baseline = centeredBaseline(namePaint, bannerRect.centerY())
        canvas.drawText(name, 0, name.length, textLeft, baseline, namePaint)
        val actionLeft = textLeft + namePaint.measureText(name.toString()) + dp(6f)
        canvas.drawText(actionText, actionLeft, baseline, actionPaint)
    }

    private fun showNextIfNeeded() {
        if (activeEntrance != null || queue.isEmpty()) return
        activeEntrance = queue.removeFirst()
        activeToken += 1
        visibility = VISIBLE
        alpha = 0f
        translationX = -dp(252f)
        invalidate()

        val fade = ObjectAnimator.ofFloat(this, ALPHA, 0f, 1f).apply {
            duration = FADE_IN_DURATION_MS
        }
        val slide = ObjectAnimator.ofFloat(this, TRANSLATION_X, translationX, 0f).apply {
            duration = SLIDE_IN_DURATION_MS
            setInterpolator(android.view.animation.PathInterpolator(0.18f, 0.78f, 0.22f, 1f))
        }
        activeAnimation = AnimatorSet().apply {
            playTogether(fade, slide)
            start()
        }
        postDelayed(dismissRunnable, DISPLAY_DURATION_MS)
    }

    private val dismissRunnable = Runnable {
        val token = activeToken
        val fade = ObjectAnimator.ofFloat(this, ALPHA, alpha, 0f)
        val slide = ObjectAnimator.ofFloat(this, TRANSLATION_X, translationX, -dp(30f))
        activeAnimation = AnimatorSet().apply {
            duration = FADE_OUT_DURATION_MS
            playTogether(fade, slide)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (token != activeToken) return
                    activeEntrance = null
                    translationX = 0f
                    visibility = GONE
                    activeAnimation = null
                    showNextIfNeeded()
                }
            })
            start()
        }
    }

    private fun drawBackground(canvas: Canvas, tier: EntranceTier) {
        backgroundPaint.clearShadowLayer()
        if (tier == EntranceTier.ELITE) {
            backgroundPaint.setShadowLayer(dp(8f), 0f, 0f, Color.argb(46, 219, 148, 69))
        }
        backgroundPaint.shader = tier.colors?.let { colors ->
            LinearGradient(
                bannerRect.left,
                bannerRect.top + bannerRect.height() * 0.37f,
                bannerRect.right,
                bannerRect.top + bannerRect.height() * 0.63f,
                colors,
                floatArrayOf(0f, 0.58f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        // Shader colors already include their alpha; transparent paint would hide the gradient.
        backgroundPaint.color = if (tier.colors == null) tier.solidColor else Color.WHITE
        canvas.drawRoundRect(bannerRect, dp(12f), dp(12f), backgroundPaint)
        backgroundPaint.shader = null
        if (tier.borderColor != Color.TRANSPARENT) {
            borderPaint.color = tier.borderColor
            val inset = borderPaint.strokeWidth / 2f
            bannerRect.inset(inset, inset)
            canvas.drawRoundRect(bannerRect, dp(12f), dp(12f), borderPaint)
            bannerRect.inset(-inset, -inset)
        }
        actionPaint.color = tier.actionColor
    }

    private fun textPaint(role: LiveTheme.TextRole, color: Int) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            role.sizeSp,
            resources.displayMetrics,
        )
        typeface = Typeface.create(
            if (role.semibold) "sans-serif-medium" else "sans-serif",
            Typeface.NORMAL,
        )
    }

    private fun centeredBaseline(paint: Paint, centerY: Float): Float {
        val metrics = paint.fontMetrics
        return centerY - (metrics.ascent + metrics.descent) / 2f
    }

    private fun dp(value: Float): Float = value * density

    private enum class EntranceTier(
        val solidColor: Int,
        val colors: IntArray?,
        val borderColor: Int,
        val actionColor: Int,
    ) {
        BASIC(Color.argb(87, 10, 10, 15), null, Color.TRANSPARENT, Color.rgb(242, 245, 250)),
        PREMIUM(
            Color.TRANSPARENT,
            intArrayOf(Color.argb(209, 79, 56, 171), Color.argb(179, 128, 64, 184), Color.argb(138, 31, 20, 59)),
            Color.argb(51, 212, 173, 255),
            Color.rgb(242, 245, 250),
        ),
        ELITE(
            Color.TRANSPARENT,
            intArrayOf(Color.argb(209, 150, 92, 36), Color.argb(184, 135, 71, 156), Color.argb(143, 38, 26, 69)),
            Color.argb(77, 255, 230, 158),
            Color.rgb(255, 248, 221),
        );

        companion object {
            fun fromLevel(level: Int): EntranceTier = when {
                level >= 40 -> ELITE
                level >= 25 -> PREMIUM
                else -> BASIC
            }
        }
    }
}
